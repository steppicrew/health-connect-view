package de.steppicrew.healthconnectview.ui.dashboard

import android.app.Application
import android.util.Log
import de.steppicrew.healthconnectview.export.ExportResult
import android.net.Uri
import android.provider.DocumentsContract
import de.steppicrew.healthconnectview.export.Exporter
import de.steppicrew.healthconnectview.ui.components.ExportKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.dashboard.DashboardStore
import de.steppicrew.healthconnectview.dashboard.SourceStore
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.widenToSessions
import de.steppicrew.healthconnectview.health.fullestWriter
import de.steppicrew.healthconnectview.health.sessionsIn
import de.steppicrew.healthconnectview.health.recordsIn
import de.steppicrew.healthconnectview.health.totalDuration
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.TrendResult
import de.steppicrew.healthconnectview.health.trendBefore
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.health.numericAggregate
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.goalCrossing
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.ui.UiState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** One bucket's total split into the components that make it up, drawn bottom-up. */
data class StackedBucket(val time: Instant, val parts: List<Double>)

/** A bucket's low and high, for the spread drawn behind a multi-day mean. */
data class ValueBand(val time: Instant, val low: Double, val high: Double)

data class TileDetailData(
    val spec: RecordTypeSpec<*>,
    val points: List<Point>,
    val total: Double?,
    /** True when [points] came from Health Connect's deduplicating aggregation. */
    val aggregated: Boolean,
    /** True when a bucket is wider than a day, so the caption must not say "daily". */
    val weeklyBuckets: Boolean,
    /**
     * Draw [points] as one bar per bucket rather than as a line.
     *
     * A multi-day window buckets by day, and a day is a count rather than a moment: sleep
     * hours, or how many sessions there were. A line between such points implies a value in
     * between that nothing measured.
     */
    val bars: Boolean = false,
    /**
     * Per-bucket spread drawn behind the line, empty where the type defines none.
     *
     * Only on a multi-day window: within a day the line already *is* every reading, and a
     * band around it would repeat the same data as a wider version of itself.
     */
    val rangeBand: List<ValueBand> = emptyList(),
    /** Per-bucket components of a stacked bar, empty where the type declares none. */
    val stack: List<StackedBucket> = emptyList(),
    /** Labels for [stack]'s components, bottom-up. */
    val stackLabels: List<Int> = emptyList(),
    /** True where the bars count sessions per day rather than summing a metric. */
    val sessionCounts: Boolean = false,
    /** True when the window reaches past 30 days without the history permission. */
    val historyCapped: Boolean,
    /** Apps that wrote into this window, so every number on screen names its source. */
    val contributingApps: Set<String>,
    /** The single source being shown, or null for the deduplicated all-sources view. */
    val selectedSource: String?,
    /**
     * Goal to draw as a reference line, when the chart is a cumulative day and the type has
     * one. Meaningless on a multi-day chart, where each point is a separate day's total.
     */
    val goal: Double?,
    /**
     * The same trend the dashboard tile draws, with its averages, for a day window only --
     * the tile shows a day, and a trend "before" a week or a year would be a different claim.
     */
    val trend: TrendResult? = null,
    /** True when the series accumulates through the day rather than showing each bucket. */
    val cumulative: Boolean,
    /**
     * Bucket starts that held no data at all. Distinct from a bucket whose value is zero:
     * "nothing was recorded" and "you did none" are different claims, and a line drawn
     * straight through the first states the second.
     */
    val emptyBuckets: List<Instant>,
    /** When the series first reached the goal, interpolated; null if it never did. */
    val goalCrossing: Instant?,
    /** Sleep or exercise spans shaded behind the chart, associated by time overlap only. */
    val sessions: List<Session>,
    /** True when heart rate is not granted, so a missing curve is a permission, not a gap. */
    val heartRateLocked: Boolean = false,
    /**
     * Value bands for the session curves (see `curveFor`), the user's for heart rate where they set them, so the
     * same reading is the same colour here as on the dashboard tile. Fixed rather than
     * window-relative: bands from each session's own extent would paint a calm walk in the
     * full sweep and make two sessions incomparable.
     */
    val sessionCurveZones: ValueZones? = null,
    /** Unit for the readout on a session's curve, from the heart-rate type's own spec. */
    @param:StringRes val sessionCurveUnitRes: Int? = null,
    /**
     * Value bands to colour the chart's line by, or null for a plain line.
     *
     * Only within a day, and only where the type declares a scale on its tile. Across days
     * each point is a daily average rather than a reading, so colouring one red would claim
     * an alarming measurement where the data says an unremarkable mean.
     */
    val lineZones: ValueZones? = null,
    /**
     * Horizontal extent the chart is drawn across, or null to span exactly the readings.
     *
     * Fixed to midnight-to-midnight for a single day, including today: an axis that ends at
     * the last reading means something different at 09:00 than it will at 21:00, and the hour
     * you are looking for slides across the screen as the day fills in. The line still stops
     * at its last real point, so the empty remainder reads as a day in progress rather than as
     * readings that were never taken.
     *
     * Widened backwards where a session began before midnight, since a night belongs to the
     * day it ended on -- see `widenToSessions`. Only then: a day whose sessions sit inside it
     * keeps the fixed axis exactly as before.
     */
    val extent: ClosedRange<Instant>? = null,
    /**
     * True when the curve's intermediate values were rescaled to match the deduplicated
     * total. The end value and the timing are right; the points between are apportioned.
     */
    val approximated: Boolean,
    /**
     * The writer whose records gave a multi-source curve its shape, when more than one app
     * contributed. The total stays deduplicated across all of them; only the path is one
     * device's, and saying so is the difference between a simplification and a silent
     * substitution.
     */
    val shapeSource: String?,
    val start: LocalDate,
    val end: LocalDate,
    /**
     * The raw records behind the chart. Inspecting exactly what each app stored is the point
     * of the app, and it is the only place a whole-day summary record can be told apart from
     * an itemised one.
     */
    val records: List<Record>,
    val truncated: Boolean,
)

/**
 * One type, over a span that can be stepped backwards and forwards.
 *
 * Separate from TypeDetailViewModel, which shows a fixed trailing range plus the raw record
 * list. This is the chart-first view reached from a dashboard tile, and it is the only place
 * that can reach data older than a year.
 */
/**
 * Turns per-bucket values into a running total, so a day reads as progress rather than as
 * disconnected bars.
 */
/** Whether this type and span would produce a cumulative chart. */
private fun cumulativeCandidate(spec: RecordTypeSpec<*>, span: Span): Boolean =
    span.intradayBucket != null && spec.tile.cumulativeIntraday

/**
 * Rescales a running total so it finishes on [target], preserving the shape.
 *
 * Needed because hourly buckets do not deduplicate overlapping writers the way the daily
 * aggregate does: a whole-day summary record from one app lands in every hourly bucket, and
 * the running total then ends at the sum of every writer rather than the deduplicated figure.
 *
 * The buckets still carry the timing, so scaling keeps *when* activity happened while taking
 * *how much* from the platform's authoritative total. Returns the input unchanged when there
 * is nothing to correct, so the caller can tell whether the values are exact.
 */
private fun scaleToTotal(points: List<Point>, target: Double?): List<Point> {
    if (target == null || points.isEmpty()) return points
    val last = points.last().value
    if (last <= 0.0) return points
    // Only correct a real discrepancy; floating point noise is not worth relabelling the
    // chart as approximate over.
    if (kotlin.math.abs(last - target) < TOTAL_TOLERANCE) return points
    val factor = target / last
    return points.map { Point(time = it.time, value = it.value * factor) }
}

/** Below this the aggregate and the bucket sum agree, allowing for floating point. */
private const val TOTAL_TOLERANCE = 0.01

private fun List<Point>.runningTotal(): List<Point> {
    var sum = 0.0
    return map { point ->
        sum += point.value
        Point(time = point.time, value = sum)
    }
}

/** One metric measured over a session's window, for the session detail sheet. */
data class SessionStat(val spec: RecordTypeSpec<*>, val value: Double)

class TileDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)
    private val sourceStore = SourceStore(application)
    private val dashboardStore = DashboardStore(application)

    private val _state = MutableStateFlow<UiState<TileDetailData>>(UiState.Loading)
    val state: StateFlow<UiState<TileDetailData>> = _state.asStateFlow()

    // Opens on the day, matching the tile that was tapped: landing on a week would show a
    // different number from the one just touched, and the point of opening a tile is to see
    // that figure in more detail.
    /** Outcome of the last export, for a snackbar; one event per export. */
    private val _exportResults = MutableSharedFlow<ExportResult>(extraBufferCapacity = 1)
    val exportResults: SharedFlow<ExportResult> = _exportResults.asSharedFlow()

    /**
     * Writes what is on screen -- this type, this window, this source filter -- to [uri].
     *
     * A failed export removes the file it started, so a half-written CSV is not left looking
     * like a complete one.
     */
    fun export(kind: ExportKind, uri: Uri) {
        val spec = _spec.value ?: return
        val span = _span.value
        val offset = _offset.value
        val origins = selectedSource?.let { setOf(DataOrigin(it)) } ?: emptySet()
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val exporter = Exporter(getApplication(), repository)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requireNotNull(resolver.openOutputStream(uri)) { "cannot open $uri" }.use { out ->
                        when (kind) {
                            ExportKind.RECORDS -> exporter.writeRecords(
                                spec, windowStart(span, offset), windowEnd(span, offset), origins, out,
                            )
                            ExportKind.DAILY -> exporter.writeDailyTotals(
                                spec, span.startDate(offset), span.endDate(offset), origins, out,
                            )
                        }
                    }
                }
            }
            result.onFailure {
                Log.w(TAG, "export failed: ${it.javaClass.simpleName}")
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            }
            _exportResults.tryEmit(result.fold({ ExportResult.Written(it) }, { ExportResult.Failed }))
        }
    }

    /**
     * Share of the current load's steps finished, 0 to 1, or null when not loading.
     *
     * Counted in steps -- chart, total, list, the source picker's two reads, sessions where the
     * type has them -- because those are what is known: Health Connect reports no progress
     * within a request. It moves in real jumps rather than a guessed time.
     */
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    private val _span = MutableStateFlow(Span.DAY)
    val span: StateFlow<Span> = _span.asStateFlow()

    /** Steps back from the present; 0 is the current window. Never negative. */
    private val _offset = MutableStateFlow(0)
    val offset: StateFlow<Int> = _offset.asStateFlow()

    private var typeName: String? = null
    private var selectedSource: String? = null

    /**
     * The type being shown, known from the moment it is asked for.
     *
     * Separate from the loaded data so the screen can title itself while loading, and -- more
     * to the point -- while empty: a day with nothing recorded was showing a blank title bar,
     * which left no way to tell which type had nothing in it.
     */
    private val _spec = MutableStateFlow<RecordTypeSpec<*>?>(null)
    val spec: StateFlow<RecordTypeSpec<*>?> = _spec.asStateFlow()

    /**
     * Filters every read and aggregate to one app.
     *
     * All sources stay the default: Health Connect's deduplicated total is the correct answer
     * for the metric, and is deliberately not the same as any single app's figure. Selecting a
     * source answers the different question of what one app recorded.
     */
    fun selectSource(packageName: String?) {
        selectedSource = packageName
        val type = typeName ?: return
        viewModelScope.launch {
            sourceStore.select(type, packageName)
            reload()
        }
    }

    /**
     * [date] is the day the dashboard was showing, as an ISO string. The offset is derived
     * from it so the detail view opens on the same day the tapped tile described; an empty or
     * unparseable value simply opens on today.
     */
    fun load(typeName: String, date: String = "", span: String = "") {
        this.typeName = typeName
        _spec.update { RecordRegistry.specOrNull(typeName) }
        // Span first: the offset is counted in the span's own periods, so it cannot be
        // derived before the span is known.
        val chosenSpan = Span.entries.firstOrNull { it.name.equals(span, ignoreCase = true) }
        chosenSpan?.let { _span.value = it }
        _offset.update { offsetForDate(date, chosenSpan ?: _span.value) }
        viewModelScope.launch {
            selectedSource = resolveSource(typeName)
            reload()
        }
    }

    /**
     * The source this screen opens on: the per-type choice, else the preferred app.
     *
     * The preference is honoured only where that app actually wrote this type, so a type it
     * never writes opens on all sources rather than on an empty chart. Establishing that
     * costs one unfiltered read of the current window -- the same read the picker below is
     * built from, before any filter narrows it.
     */
    private suspend fun resolveSource(typeName: String): String? {
        val selections = runCatching { sourceStore.selections.first() }.getOrDefault(emptyMap())
        selections[typeName]?.let { return it }

        val preferred = runCatching { sourceStore.preferred.first() }.getOrNull() ?: return null
        val spec = RecordRegistry.specOrNull(typeName) ?: return null
        val writers = runCatching {
            repository.recordsIn(
                spec,
                windowStart(_span.value, _offset.value),
                windowEnd(_span.value, _offset.value),
            ).map { spec.originOf(it) }
                .toSet()
        }.getOrDefault(emptySet())
        return sourceStore.effective(typeName, selections, preferred, writers)
    }

    /**
     * How many [span]-sized steps back the window holding [date] sits.
     *
     * Counted in the span's own periods, not in days: an offset is fed to [Span.endDate],
     * which steps by a week, four weeks or a year as well as by a day, so a day count used
     * directly sent a month view 28 times too far back. Stepping until the window contains
     * the date keeps one definition of "which window is this", whatever the period.
     */
    private fun offsetForDate(date: String, span: Span = _span.value): Int {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return 0
        val today = LocalDate.now()
        if (!parsed.isBefore(today)) return 0

        var offset = 0
        while (offset < MAX_OFFSET_STEPS && parsed.isBefore(span.startDate(offset, today))) {
            offset++
        }
        return offset
    }

    /** Changing span resets the offset: "three weeks ago" has no meaning as "three years ago". */
    fun setSpan(span: Span) {
        _span.update { span }
        _offset.update { 0 }
        reload()
    }

    fun stepBack() {
        _offset.update { it + 1 }
        reload()
    }

    /** Stepping forward past the current window would show an empty future. */
    fun stepForward() {
        if (_offset.value == 0) return
        _offset.update { (it - 1).coerceAtLeast(0) }
        reload()
    }

    val canStepForward: Boolean get() = _offset.value > 0

    private fun reload() {
        val spec = RecordRegistry.specOrNull(typeName ?: return) ?: run {
            _state.update { UiState.Error("Unknown type") }
            return
        }

        viewModelScope.launch {
            _state.update { UiState.Loading }

            val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
            if (spec.permission !in granted) {
                _state.update { UiState.NoPermission }
                return@launch
            }

            val span = _span.value
            val offset = _offset.value
            val capped = span.needsHistoryPermission(offset) &&
                RecordRegistry.HISTORY_PERMISSION !in granted

            val result = runCatching { loadData(spec, span, offset, capped, selectedSource) }
            _progress.value = null
            result.fold(
                onSuccess = { data ->
                    _state.update {
                        if (data.points.isEmpty() && data.total == null &&
                            data.records.isEmpty() && data.sessions.isEmpty()
                        ) {
                            UiState.Empty
                        } else {
                            UiState.Data(data)
                        }
                    }
                },
                onFailure = { error ->
                    _state.update { UiState.Error(error.message ?: "Could not read data") }
                },
            )
        }
    }

    /**
     * A step-shaped running total built from the individual records.
     *
     * Each record contributes a step at the moment it ended, so the line is flat while
     * nothing was happening and rises exactly when it was. Anchored at zero at the start of
     * the day so the first step is visible as a step rather than as the chart's baseline.
     *
     * Records whose interval covers most of the day are dropped: an app that posts one
     * whole-day summary says nothing about *when*, and including it would either add a single
     * huge step at midnight or, if spread, reintroduce the smearing this avoids. Their
     * contribution is still reflected, because the series is rescaled to the deduplicated
     * daily total afterwards.
     */
    private suspend fun cumulativeFromRecords(
        spec: RecordTypeSpec<*>,
        span: Span,
        offset: Int,
        origins: Set<DataOrigin>,
    ): List<Point> {
        val windowStart = span.startDate(offset)
            .atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()
        val allRecords = runCatching {
            repository.readForChart(spec.type, span.instantFilter(offset), origins = origins)
        }.getOrDefault(emptyList())

        // Several writers describing the same activity interleave: measured on a real device,
        // three step sources produced 140 cross-writer overlaps in one day, so a series built
        // from the merged records zigzags backwards however carefully each ramp is clamped.
        // Each writer is internally consistent, so the shape is taken from whichever
        // contributed most in this window; the magnitude still comes from the deduplicated
        // aggregate, applied by the caller. The result is one device's real timeline scaled to
        // the platform's total, rather than an interleaving of three that matches none of them.
        //
        // Ranked by itemised records first, then by contribution. A writer posting one
        // whole-day summary can only draw a ramp, so choosing it for the *shape* throws away
        // a timeline that another writer actually has: measured on the phone, Garmin's two
        // climbs (05:30 and 07:15) and Health Sync's single 00:00-24:00 summary both totalled
        // 8 floors, and the tie on value alone handed the shape to the summary and drew a
        // straight line through a day whose steps were known.
        // One definition of "summary", shared with the itemised filter below: a record judged
        // itemised when ranking writers must not then be dropped when building the shape.
        val windowSpan = Duration.between(
            windowStart,
            span.endDate(offset).atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant(),
        )
        val summaryThreshold = windowSpan.multipliedBy(SUMMARY_PERCENT).dividedBy(100)

        val byWriter = allRecords.groupBy { spec.originOf(it) }
        val dominant = if (origins.isEmpty() && byWriter.size > 1) {
            byWriter.entries.maxWithOrNull(
                compareBy(
                    { entry ->
                        entry.value.any { record ->
                            val start = spec.timeOf(record)
                            val end = spec.endTimeOf(record) ?: start
                            Duration.between(start, end) < summaryThreshold
                        }
                    },
                    { entry ->
                        entry.value.sumOf { record ->
                            spec.pointsOf(record).sumOf { point -> point.value }
                        }
                    },
                ),
            )
        } else {
            null
        }
        chosenShapeWriter = dominant?.key
        val records = dominant?.value ?: allRecords

        val intervals = records.map { record ->
            val start = spec.timeOf(record)
            val end = spec.endTimeOf(record) ?: start
            Interval(
                start = start,
                end = end,
                value = spec.pointsOf(record).sumOf { it.value },
            )
        }

        // A whole-day summary says nothing about *when*, so it is normally dropped: including
        // it would either add one huge step at midnight or, if spread, reintroduce the
        // smearing this path exists to avoid. Its contribution still reaches the chart,
        // because the series is rescaled to the deduplicated daily total afterwards.
        //
        // "Whole-day" means covering essentially the entire window, not merely being long. A
        // 12-hour cutoff also caught legitimate measured intervals: on the phone, Garmin's
        // total-calories day was six contiguous records of which the last ran 06:56-23:59 and
        // held 51% of the day's kcal. Dropping that as a summary built the shape from the
        // morning alone and then rescaled it to the full total, which inflated the hours
        // before 07:00 and left the remaining seventeen flat -- the opposite of what the
        // records said. A real summary spans the window itself, so the test is against the
        // window rather than an absolute duration.
        val itemised = intervals.filter {
            Duration.between(it.start, it.end) < summaryThreshold
        }

        // Unless the summary is all there is. On a real device one app wrote a single
        // whole-day floors record and nothing else, and dropping it left the chart empty
        // while the total and the record list below both showed the figure -- which reads as
        // a rendering fault rather than as "this app only reported a daily total".
        //
        // Drawn as the one honest thing such a record supports: a single rise across the
        // interval it actually covers. The caption already tells the reader the shape is
        // apportioned rather than measured.
        val steps = itemised.ifEmpty { intervals }.sortedBy { it.start }
        // Recorded for the caption: a curve built only from whole-day summaries is a straight
        // ramp whose intermediate points are apportioned, not measured, and the chart has to
        // say so rather than presenting it as a timeline.
        shapeFromWholeDayOnly = itemised.isEmpty() && intervals.isNotEmpty()

        if (steps.isEmpty()) return emptyList()

        // The rise spans the interval the activity actually occupied, rather than jumping at
        // a single instant: the record says the climb took from 05:30 to 05:45, so the line
        // rises across those fifteen minutes. Holding the previous level until the interval
        // opens keeps the plateaus flat.
        // Records can overlap -- a device writing every few minutes commonly emits intervals
        // that abut or overlap -- so the next record's start may precede the previous one's
        // end. Emitting both unchanged sends the series backwards in time, which a running
        // total cannot do and which draws as a zigzag. Each point is therefore clamped to be
        // no earlier than the one before it.
        var sum = 0.0
        var lastTime = windowStart
        return buildList {
            add(Point(time = windowStart, value = 0.0))
            steps.forEach { step ->
                val rampStart = maxOf(step.start, lastTime)
                val rampEnd = maxOf(step.end, rampStart)
                // Hold the level up to the moment the rise begins, unless a previous record
                // already carried the line past that point.
                if (rampStart.isAfter(lastTime)) {
                    add(Point(time = rampStart, value = sum))
                }
                sum += step.value
                add(Point(time = rampEnd, value = sum))
                lastTime = rampEnd
            }
            // Carry the final level to the end of the window so the day does not appear to
            // stop at the last recorded activity.
            val windowEnd = minOf(
                span.endDate(offset).atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant(),
                Instant.now(),
            )
            if (windowEnd.isAfter(lastTime)) {
                add(Point(time = windowEnd, value = sum))
            }
        }
    }

    /**
     * Set by [cumulativeFromRecords] when it took the curve's shape from a single writer.
     * Read straight afterwards on the same coroutine, so no synchronisation is needed.
     */
    private var chosenShapeWriter: String? = null

    /**
     * Set when the curve could only be built from whole-day summary records.
     *
     * Read straight afterwards on the same coroutine, like [chosenShapeWriter]. The resulting
     * line is a single rise across the day: honest about the total, and saying nothing real
     * about when within the day anything happened.
     */
    private var shapeFromWholeDayOnly: Boolean = false

    /** Set while building the points, read straight afterwards on the same coroutine. */
    private var rangeBand: List<ValueBand> = emptyList()

    /** Set while building the points, read straight afterwards on the same coroutine. */
    private var stack: List<StackedBucket> = emptyList()

    /** One record reduced to the span it covered and the amount it contributed. */
    private data class Interval(val start: Instant, val end: Instant, val value: Double)

    /**
     * Everything recorded during one session, assembled by time overlap.
     *
     * ExerciseSessionRecord itself carries no distance, power or calories -- only its type,
     * title, notes, segments, laps and route. Those metrics are separate record types written
     * over the same window, so a session's statistics exist but have to be gathered rather
     * than read. On a real indoor bike session this found 647 kcal active, 25.5 km and a mean
     * of 138 bpm across 54 heart-rate records.
     */
    suspend fun statisticsFor(session: Session): List<SessionStat> = coroutineScope {
        val window = TimeRangeFilter.between(session.start, session.end)
        val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
        val gate = Semaphore(MAX_CONCURRENT_STATS)

        RecordRegistry.all
            .filter { it.permission in granted && it.aggregate != null && it.isChartable }
            .map { spec ->
                async {
                    gate.withPermit {
                        val metric = spec.aggregate ?: return@withPermit null
                        val value = runCatching { repository.total(metric, window) }.getOrNull()
                        value?.let { SessionStat(spec = spec, value = it) }
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    /**
     * Heart rate through each session's own window, keyed by the session's start.
     *
     * Read raw rather than aggregated, and that is safe here in a way it would not be for a
     * total: heart rate is instantaneous, so two apps writing the same beat duplicate a point
     * on the curve rather than inflating a sum. Sessions with nothing recorded are left out
     * of the map entirely, so the UI can say "none was recorded" rather than draw an empty
     * box.
     *
     * The association is by time, like every other session statistic in this app: Health
     * Connect stores no session id on a sample, so these are the readings taken during the
     * session and deliberately not readings tagged as belonging to it.
     */
    /**
     * Heart rate through one session's own window, read when its row comes on screen.
     *
     * Reading every session's curve up front made the year view of Trainings wait for all 728
     * of them before showing anything -- about 66 ms each on the phone, so near 48 s -- for rows
     * mostly never scrolled to. Now the list appears at once and each curve is read as its row
     * is shown, at most [MAX_CONCURRENT_STATS] at a time, and kept for this screen only: memory,
     * like every other reading here, never disk. Null means no heart rate was recorded then,
     * which the row says in its own words.
     */
    suspend fun curveFor(session: Session): List<Point>? {
        curveCache[session]?.let { return it.points }
        val spec = heartRateSpec() ?: return null
        val points = curveGate.withPermit {
            val records = runCatching {
                repository.readForChart(spec.type, TimeRangeFilter.between(session.start, session.end))
            }.getOrDefault(emptyList())

            // One writer's samples rather than everyone's merged. Heart rate is
            // instantaneous, so aggregation cannot deduplicate it: two apps mirroring the
            // same session sample at slightly different instants and values would interleave
            // into a zigzag between two accounts of one heart rate.
            fullestWriter(
                records.groupBy { spec.originOf(it) }
                    .mapValues { (_, group) -> group.flatMap { spec.pointsOf(it) } },
                session.start,
                session.end,
            ).takeIf { it.size > 1 }
        }
        curveCache[session] = CachedCurve(points)
        return points
    }

    /** Wrapper so a session with no curve is remembered as such, not read again. */
    private class CachedCurve(val points: List<Point>?)

    private val curveCache = java.util.concurrent.ConcurrentHashMap<Session, CachedCurve>()
    private val curveGate = Semaphore(MAX_CONCURRENT_STATS)

    private fun heartRateSpec(): RecordTypeSpec<*>? = RecordRegistry.specOrNull(HEART_RATE)

    private suspend fun heartRateGranted(): Boolean {
        val permission = heartRateSpec()?.permission ?: return false
        val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
        return permission in granted
    }

    /**
     * Midnight to midnight for a single day, widened to contain any session that started
     * before it. Null for every other span.
     *
     * Only the day span: across a week or a month the chart already runs edge to edge, since
     * every bucket in the window produces a point whether or not anything was recorded in it.
     * It is within a day that the series stops at the last reading.
     *
     * **Why a night may push the start earlier.** A night is credited to the day it *ends* on
     * but begins the previous evening -- measured 22:18 to 08:58. Pinned to midnight, the
     * 1h 42m before it has nowhere to go: `horizontalFractions` clamps anything outside the
     * extent onto the plot edge, so the band was drawn 00:00-08:58 while the headline above it
     * read 10h 40m. The same screen gave two answers to "how long did I sleep", which is the
     * defect the headline itself was fixed for in section 5.
     *
     * Widening keeps the band and the headline agreeing, and keeps the honest claim that the
     * shaded width *is* the session. The cost is that the axis no longer always starts at
     * midnight, which section 5 deliberately fixed it to -- so it is paid only on the days
     * that need it, and only by the types that draw sessions at all. A day whose sessions sit
     * inside it is midnight to midnight exactly as before.
     */
    private fun windowStart(span: Span, offset: Int): Instant =
        span.startDate(offset).atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()

    private fun windowEnd(span: Span, offset: Int): Instant =
        span.endDate(offset).atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()

    private fun dayExtent(
        span: Span,
        offset: Int,
        sessions: List<Session>,
    ): ClosedRange<Instant>? {
        if (span.intradayBucket == null) return null
        val zone = HealthRepository.DEFAULT_ZONE
        val start = span.startDate(offset).atStartOfDay(zone).toInstant()
        val end = span.endDate(offset).atStartOfDay(zone).toInstant()

        return widenToSessions(start..end, sessions)
    }

    /**
     * Sleep and exercise spans overlapping the window, for the bands behind the chart.
     *
     * The widening, deduplication and clipping all live in [sessionsIn], shared with the
     * dashboard so a session counted on a tile is the same session shaded on the chart.
     */
    private suspend fun loadSessions(
        span: Span,
        offset: Int,
        kinds: Set<Session.Kind>,
    ): List<Session> = repository.sessionsIn(
        start = span.startDate(offset).atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant(),
        end = span.endDate(offset).atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant(),
        kinds = kinds,
    )

    /**
     * The value bands in force for a type: the user's override if they set one, else the
     * type's default. Read from the same store as the goal, for the same reason.
     */
    private suspend fun zonesFor(spec: RecordTypeSpec<*>?): ValueZones? {
        val name = spec?.type?.simpleName ?: return null
        val stored = runCatching { dashboardStore.config.first() }.getOrNull()
        return stored?.tiles?.firstOrNull { it.typeName == name }?.effectiveZones
            ?: spec.tile.defaultZones
    }

    /** The user's goal for this type if they set one, else the type's default. */
    private suspend fun goalFor(spec: RecordTypeSpec<*>): Double? {
        val typeName = spec.type.simpleName ?: return spec.tile.defaultGoal
        val stored = runCatching { dashboardStore.config.first() }.getOrNull()
        return stored?.tiles?.firstOrNull { it.typeName == typeName }?.effectiveGoal
            ?: spec.tile.defaultGoal
    }

    private suspend fun loadData(
        spec: RecordTypeSpec<*>,
        span: Span,
        offset: Int,
        historyCapped: Boolean,
        source: String?,
    ): TileDetailData = coroutineScope {
        val metric = spec.aggregate
        val origins = source?.let { setOf(DataOrigin(it)) } ?: emptySet()

        val hasSessions = spec.tile.form == TileSpec.Form.SESSIONS ||
            (span.intradayBucket != null && spec.tile.overlaySessions.isNotEmpty())
        val steps = 5 + if (hasSessions) 1 else 0
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        fun stepDone() {
            _progress.value = done.incrementAndGet().toFloat() / steps
        }
        _progress.value = 0f

        // The list and the source picker's reads depend on nothing the chart computes, so they
        // run alongside it rather than after it. Measured on the phone for a year of heart rate
        // the steps ran one after another for ~20 s; the chart alone was ~5 s of that.
        val windowStart = windowStart(span, offset)
        val windowEnd = windowEnd(span, offset)
        val recordsRead = async {
            runCatching { repository.recordsIn(spec, windowStart, windowEnd, origins) }
                .getOrDefault(emptyList())
                .also { stepDone() }
        }
        // Only to name the other writers when one is selected, so one page is enough: the
        // aggregate's origins below already name every writer that reaches a total, and this
        // catches the ones that do not. Reading the full 5000 again took 7 s for a year.
        val otherWritersRead = if (origins.isEmpty()) {
            null
        } else {
            async {
                runCatching {
                    repository.recordsIn(spec, windowStart, windowEnd, maxRecords = HealthRepository.PAGE_SIZE)
                        .map { spec.originOf(it) }
                        .toSet()
                }.getOrDefault(emptySet()).also { stepDone() }
            }
        }
        if (otherWritersRead == null) stepDone()
        val aggregateOriginsRead = metric?.let { aggregate ->
            async {
                runCatching { repository.contributingApps(aggregate, span.localFilter(offset)) }
                    .getOrDefault(emptySet())
                    .also { stepDone() }
            }
        }
        if (aggregateOriginsRead == null) stepDone()

        // Filled in by the bucketed branch below; empty for every other shape of series.
        var emptyBuckets: List<Instant> = emptyList()
        // Whether the points on screen actually came from aggregation. A type can have an
        // aggregate metric and still be charted from its raw readings within a day, and the
        // caption must describe the series that was drawn rather than the metric that exists.
        var seriesAggregated = metric != null
        // Cleared per load: a value left over from the previous span would name a writer that
        // had nothing to do with the series now on screen.
        chosenShapeWriter = null
        shapeFromWholeDayOnly = false
        rangeBand = emptyList()
        stack = emptyList()

        // Totals and bucketed series both come from aggregation wherever the type supports
        // it: several apps can write the same metric, so summing raw records double-counts.
        val points = if (metric != null) {
            val period = span.bucket
            val duration = span.intradayBucket
            when {
                // A single day sliced by a day-wide bucket would be one point, so the day span
                // aggregates by duration instead and shows the shape within the day.
                // A cumulative day is built from the records themselves rather than from
                // time buckets. Buckets smear a whole-day summary record evenly across the
                // day, which produces a steady climb through hours when nothing happened;
                // records carry the actual moment and amount, so the line steps exactly where
                // the activity was and stays flat in between -- which is what the data says.
                duration != null && spec.tile.cumulativeIntraday ->
                    cumulativeFromRecords(spec, span, offset, origins)

                // A day of an instantaneous type is charted from the readings themselves.
                //
                // Hourly averages threw away almost everything: one day held 7,323 heart-rate
                // samples at a median 15-second cadence, and the chart drew 24 points -- a
                // blocky line that hid every peak and trough the day actually had. Aggregation
                // is there to deduplicate *overlapping intervals*; a heart rate sample is a
                // moment, so two apps recording the same beat duplicate a point rather than
                // inflating a total, and there is nothing for aggregation to resolve.
                //
                // Interval types keep the bucketed path, where overlap is real and summing
                // raw records would double-count.
                duration != null && spec.shape != RecordTypeSpec.Shape.INTERVAL -> {
                    seriesAggregated = false
                    repository.readForChart(spec.type, span.instantFilter(offset), origins = origins)
                        .flatMap { spec.pointsOf(it) }
                        .sortedBy { it.time }
                }

                duration != null -> repository
                    .intradayTotals(metric, span.instantFilter(offset), duration, origins)
                    .mapNotNull { bucket ->
                        val value = bucket.result[metric]?.let(::numericAggregate)
                            ?: return@mapNotNull null
                        Point(time = bucket.startTime, value = value)
                    }

                period != null -> {
                    val bandMetrics = spec.rangeAggregates
                    val stackMetrics = spec.stackComponents
                    val buckets = repository.bucketedTotals(
                        metric,
                        span.localFilter(offset),
                        period,
                        origins,
                        also = (bandMetrics?.toList().orEmpty() + stackMetrics.map { it.second })
                            .toSet(),
                    )

                    // The day's total split into its parts, from the same buckets as the
                    // total itself so the segments cannot sum to something other than the bar.
                    // A bucket missing any component is left out rather than drawn short,
                    // which would read as a day of less rather than a day not fully known.
                    stack = if (stackMetrics.isEmpty()) {
                        emptyList()
                    } else {
                        buckets.mapNotNull { bucket ->
                            val parts = stackMetrics.map { (_, partMetric) ->
                                bucket.result[partMetric]?.let(::numericAggregate)
                                    ?: return@mapNotNull null
                            }
                            StackedBucket(
                                time = bucket.startTime
                                    .atZone(HealthRepository.DEFAULT_ZONE).toInstant(),
                                parts = parts,
                            )
                        }
                    }

                    // The spread behind the mean, where the type defines one. Built from the
                    // same buckets so a band cannot drift from the point it belongs to; a
                    // bucket missing either end contributes no band rather than a half-open
                    // one, which would read as a range reaching to zero.
                    rangeBand = bandMetrics?.let { (lowMetric, highMetric) ->
                        buckets.mapNotNull { bucket ->
                            val low = bucket.result[lowMetric]?.let(::numericAggregate)
                            val high = bucket.result[highMetric]?.let(::numericAggregate)
                            if (low == null || high == null) return@mapNotNull null
                            ValueBand(
                                time = bucket.startTime
                                    .atZone(HealthRepository.DEFAULT_ZONE).toInstant(),
                                low = low,
                                high = high,
                            )
                        }
                    }.orEmpty()

                    // A bucket with no value is a day nothing was recorded, which is not the
                    // same as a day with a value of zero. Both the empty times and the points
                    // are carried forward so the chart can break the line rather than draw
                    // through the gap and imply a reading that never existed.
                    // Only gaps *between* recorded days matter. A window commonly begins or
                    // ends on days with nothing yet recorded -- today, most obviously -- and
                    // those break no line, so counting them would claim a hole that is not
                    // there.
                    //
                    // Not for the types a reading is *taken* of -- weight, body fat, blood
                    // pressure. Those are measured when someone chooses to measure, so a day
                    // without one carries no information: weighing in on Monday and the
                    // Monday after is a fortnight's trend, not two isolated facts. Breaking
                    // there stranded every point in a segment of its own, and a one-point
                    // segment draws as a bare dot with no line at all -- so a weekly weigh-in
                    // produced a chart with no line anywhere. `markReadings` is already the
                    // registry's name for that distinction ("the gap between two of them is a
                    // fact about the data"), and for these types the honest reading of that
                    // fact is a connecting line, with the dots saying where the measurements
                    // actually fell.
                    //
                    // A counted quantity keeps the break: a day with no steps recorded is not
                    // a day of zero steps, and drawing through it would claim a number nobody
                    // wrote.
                    val withValue = buckets.filter { it.result[metric] != null }
                    val firstRecorded = withValue.firstOrNull()?.startTime
                    val lastRecorded = withValue.lastOrNull()?.startTime
                    emptyBuckets = if (
                        firstRecorded == null || lastRecorded == null || spec.tile.markReadings
                    ) {
                        emptyList()
                    } else {
                        buckets
                            .filter { it.result[metric] == null }
                            .filter { it.startTime > firstRecorded && it.startTime < lastRecorded }
                            .map { it.startTime.atZone(HealthRepository.DEFAULT_ZONE).toInstant() }
                    }

                    buckets.mapNotNull { bucket ->
                        val value = bucket.result[metric]?.let(::numericAggregate)
                            ?: return@mapNotNull null
                        Point(
                            time = bucket.startTime
                                .atZone(HealthRepository.DEFAULT_ZONE).toInstant(),
                            value = value,
                        )
                    }
                }

                else -> emptyList()
            }
        } else {
            // No aggregate metric: chart the readings themselves, via the path that spans the
            // whole window rather than stopping at the newest records.
            repository.readForChart(spec.type, span.instantFilter(offset), origins = origins)
                .flatMap { spec.pointsOf(it) }
                .sortedBy { it.time }
        }

        // Aggregation returns nothing for an interval as wide as its own bucket: an app that
        // posts one record per day produces null buckets while readRecords still returns the
        // record. Measured on a real device -- a whole-day summary from one writer aggregated
        // to null while its raw value was plainly there. Charting the records themselves is
        // correct here precisely because a single source cannot overlap itself, so there is
        // nothing to deduplicate.
        val chartPoints = points.ifEmpty {
            if (metric != null && source != null) {
                runCatching {
                    repository.readForChart(spec.type, span.instantFilter(offset), origins = origins)
                        .flatMap { spec.pointsOf(it) }
                        .sortedBy { it.time }
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }

        stepDone() // the chart
        val aggregatedTotal = if (metric != null) {
            runCatching { repository.total(metric, span.localFilter(offset), origins) }.getOrNull()
        } else {
            null
        }

        // Same bucket-wide-interval case as above: when a single source is selected and its
        // aggregate comes back null, summing that one app's records is safe -- one writer
        // cannot overlap itself. Never do this for the combined view, where overlapping
        // writers are exactly what aggregation exists to resolve.
        val total = aggregatedTotal ?: if (metric != null && source != null) {
            chartPoints.takeIf { it.isNotEmpty() }?.let { pts ->
                // Already a running total when cumulative, so the last point is the sum.
                if (cumulativeCandidate(spec, span)) pts.last().value else pts.sumOf { it.value }
            }
        } else {
            null
        }

        // A goal line only means something against a running total for one day; across days
        // each point is its own day's total and the goal would be a different comparison.
        val cumulative = span.intradayBucket != null && spec.tile.cumulativeIntraday
        stepDone() // the total
        val goal = if (cumulative) goalFor(spec) else null

        // A counted quantity bucketed across days is a total per bucket, not a reading taken
        // at a moment, so it gets the mark bars carry: read by comparing heights from zero.
        // `cumulativeIntraday` is already the registry's "this quantity adds up" flag -- steps,
        // distance, floors, calories, hydration -- and its own KDoc notes that across days
        // each bucket is a daily total. The same reasoning the roadmap gives for drawing
        // sessions as bars applies unchanged: a bucket that is a whole day is a count.
        //
        // Means keep the line. A resting heart rate averaged over a day is still a reading,
        // and a bar from zero would bury the small movements that are the point of watching
        // it -- which is why this keys off the flag rather than off the span alone.
        val bucketedTotals = span.bucket != null && spec.tile.cumulativeIntraday

        // Hourly buckets do not deduplicate the way the daily total does. Where one app posts
        // a whole-day summary record and another itemises, the day-long record contributes to
        // every hourly bucket and the running total ends at the sum of both writers -- 24.6
        // where the day's deduplicated total is 12, measured on a real device.
        //
        // The buckets still say *when* activity happened, which is what gives the curve its
        // shape, so they are kept for timing and rescaled to finish exactly on the
        // authoritative aggregate. Magnitude comes from the platform; only the distribution
        // comes from the buckets.
        // Which writer the curve's shape came from, when it was taken from just one.
        val shapeSource = if (cumulative && source == null) chosenShapeWriter else null

        val scaledPoints = when {
            // A session type has no series worth drawing. Its aggregate is a duration, and
            // slicing that into hourly buckets smears one 7h33m night across the day: the
            // line then climbs to 1 and falls to 0.2, which reads as a measurement and is
            // not one. The sessions themselves say everything the chart was trying to, and
            // say it correctly -- so the screen shows the timeline and the list instead.
            spec.tile.form == TileSpec.Form.SESSIONS -> emptyList()

            cumulative -> scaleToTotal(chartPoints, aggregatedTotal)

            else -> chartPoints
        }
        // Only true when the points between are genuinely apportioned rather than measured.
        // A record-built curve is rescaled too, but every one of its points is a real record,
        // so calling it approximate would understate what the chart is showing. Scaling shows
        // up instead as the shape-source note, which says exactly whose readings these are.
        // Also when the only records available were whole-day summaries: every point between
        // the ends is then apportioned by construction, whichever writer supplied it.
        val approximated = cumulative &&
            (shapeFromWholeDayOnly || (scaledPoints !== chartPoints && chosenShapeWriter == null))

        // Two different reasons to load sessions, and they need different windows.
        //
        // As bands behind someone else's chart they are context, so they are only worth
        // drawing within a day: across weeks a band would be thinner than the line it sits
        // behind and would say nothing. As the content of a session type's own screen they
        // are the point of the view, so they are listed for whatever window is shown.
        val sessionKind = spec.tile.sessionKind.takeIf { spec.tile.form == TileSpec.Form.SESSIONS }
        val sessions = when {
            sessionKind != null -> loadSessions(span, offset, setOf(sessionKind))

            span.intradayBucket != null && spec.tile.overlaySessions.isNotEmpty() ->
                loadSessions(span, offset, spec.tile.overlaySessions)

            else -> emptyList()
        }
        if (hasSessions) stepDone()

        // A multi-day window asks a different question of a sessions type than a day does.
        //
        // Within a day the per-session heart-rate traces answer "what was this activity
        // like". Across four weeks they overlap into noise, and the honest question becomes
        // how much there was per day: hours slept, or how many sessions. One bar per day,
        // attributed by the same rule the list uses -- a night belongs to the day it ended on.
        val perDayPoints = if (sessionKind != null && span.intradayBucket == null) {
            val zone = HealthRepository.DEFAULT_ZONE
            sessions
                .groupBy { it.end.atZone(zone).toLocalDate() }
                .toSortedMap()
                .map { (day, ofDay) ->
                    Point(
                        time = day.atStartOfDay(zone).toInstant(),
                        value = when (sessionKind) {
                            // Sleep is asked in hours; an exercise day is asked as a count,
                            // since two rides of unequal length are still two rides.
                            Session.Kind.SLEEP -> numericAggregate(ofDay.totalDuration()) ?: 0.0
                            else -> ofDay.size.toDouble()
                        },
                    )
                }
        } else {
            emptyList()
        }

        // The samples are already there, taken during the session; drawing them per session
        // is the only place they answer "what was this activity like" rather than "what did
        // the day look like". Only for a session type's own screen -- elsewhere the sessions
        // are bands behind a chart that is already showing something.
        val heartRateGranted = sessionKind != null && heartRateGranted()

        // Newest first, matching how the other list reads.
        val records = recordsRead.await()

        // Deliberately unfiltered: this drives the source picker, so it must list every app
        // that wrote into the window. Scoping it to the current selection would collapse the
        // picker to that one app and strand the user there with no way back. So the records
        // above are reused only when nothing is filtered, which is the common case.
        //
        // The writers are taken from the records and unioned with the aggregate's origins,
        // never from the origins alone. The two legitimately disagree: a writer whose records
        // do not reach the aggregate is absent from the origins while still plainly present
        // in the list below. The whole-day-summary case is exactly that -- a record as wide
        // as its bucket aggregates to nothing (see CLAUDE.md) -- and with one contributor
        // left the picker hid itself, so the user saw two writers listed and no way to choose
        // between them. Origins still contribute because some types aggregate without storing
        // records at all, where the records alone would name nobody.
        val writers = otherWritersRead?.await() ?: records.map { spec.originOf(it) }.toSet()
        val contributors = writers + (aggregateOriginsRead?.await() ?: emptySet())

        // A sessions tile answers "how much did these sessions cover", so its own list is the
        // authority: already deduplicated across writers, and already attributed by the rule
        // that a night belongs to the day it ended on.
        //
        // The aggregate answers a different question -- how much sleep fell inside this
        // calendar day -- and on 11.09 the two disagreed openly on screen: a headline of
        // 2h 28m (the 21:30 tail before midnight) above a list whose sessions summed to
        // 16h 13m. One screen must not give two answers to the same question.
        val headlineTotal = if (spec.tile.form == TileSpec.Form.SESSIONS && sessions.isNotEmpty()) {
            numericAggregate(sessions.totalDuration())
        } else {
            total
        }

        TileDetailData(
            spec = spec,
            points = perDayPoints.ifEmpty { scaledPoints },
            // Bars wherever a point is a whole bucket rather than a moment: a sessions window
            // counted per day, a total split into components that only read as parts when
            // drawn stacked, or a counted quantity bucketed across days.
            bars = perDayPoints.isNotEmpty() || stack.isNotEmpty() || bucketedTotals,
            rangeBand = rangeBand,
            stack = stack,
            stackLabels = spec.stackComponents.map { it.first },
            sessionCounts = perDayPoints.isNotEmpty() && sessionKind != Session.Kind.SLEEP,
            total = headlineTotal,
            aggregated = seriesAggregated,
            contributingApps = contributors,
            selectedSource = source,
            goal = goal,
            trend = if (span == Span.DAY && metric != null && spec.tile.form != TileSpec.Form.SESSIONS) {
                runCatching { repository.trendBefore(metric, span.startDate(offset), origins) }.getOrNull()
            } else {
                null
            },
            cumulative = cumulative,
            // Suppressed on an apportioned curve: "reached at 19:59" on a straight ramp is
            // reading a time off a line that was drawn, not measured.
            goalCrossing = if (shapeFromWholeDayOnly) null else goalCrossing(scaledPoints, goal),
            emptyBuckets = emptyBuckets,
            sessions = sessions,
            sessionCurveZones = zonesFor(heartRateSpec()),
            sessionCurveUnitRes = heartRateSpec()?.unitRes,
            lineZones = zonesFor(spec).takeIf { span.intradayBucket != null },
            extent = dayExtent(span, offset, sessions),
            heartRateLocked = sessionKind != null && !heartRateGranted,
            approximated = approximated,
            shapeSource = shapeSource,
            weeklyBuckets = (span.bucket?.days ?: 0) > 1,
            historyCapped = historyCapped,
            start = span.startDate(offset),
            end = span.endDate(offset).minusDays(1),
            records = records,
            truncated = records.size >= HealthRepository.MAX_RECORDS,
        )
    }

    private companion object {
        const val TAG = "TileDetail"
        /**
         * A record at least this long is a whole-day summary rather than an event, and says
         * nothing about when within the day it happened.
         */
        const val MAX_CONCURRENT_STATS = 4

        /** The type whose readings describe a session from the inside. */
        const val HEART_RATE = "HeartRateRecord"

        /**
         * How much of the window a record must cover to count as a summary of it rather than
         * a measurement within it, as a percentage.
         *
         * Set high on purpose. The case this exists for is a record spanning the window
         * exactly -- 00:00 to 24:00 -- while a writer legitimately filling a long quiet
         * stretch (measured: Garmin's 06:56-23:59 calories block, 71% of the day) must stay
         * in the shape, because dropping it moves half the day's total into the morning.
         */
        const val SUMMARY_PERCENT: Long = 95

        /** Ceiling on the offset search, so an absurd date cannot spin. Years at a day each. */
        const val MAX_OFFSET_STEPS = 4000
    }

}
