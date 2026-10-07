package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.health.HrvStanding
import de.steppicrew.healthconnectview.health.HrvSummary
import android.app.Application
import android.util.Log
import de.steppicrew.healthconnectview.export.ExportResult
import android.net.Uri
import java.io.ByteArrayOutputStream
import android.provider.DocumentsContract
import de.steppicrew.healthconnectview.export.ExportPeriod
import de.steppicrew.healthconnectview.export.Exporter
import de.steppicrew.healthconnectview.export.Gpx
import de.steppicrew.healthconnectview.health.RoutePoint
import de.steppicrew.healthconnectview.health.toPoints
import androidx.health.connect.client.records.ExerciseRouteResult
import de.steppicrew.healthconnectview.ui.components.ExportKind
import de.steppicrew.healthconnectview.util.appLabelFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
import de.steppicrew.healthconnectview.health.fullestWriter
import de.steppicrew.healthconnectview.health.recordsIn
import de.steppicrew.healthconnectview.health.DayPartSplit
import de.steppicrew.healthconnectview.health.PressureReading
import de.steppicrew.healthconnectview.health.dayPartWindow
import de.steppicrew.healthconnectview.health.splitByDayPart
import androidx.health.connect.client.records.BloodPressureRecord
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.TrendResult
import de.steppicrew.healthconnectview.health.StreakSummary
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.goalCrossing
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.ValueZones
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import de.steppicrew.healthconnectview.health.personalRecord
import de.steppicrew.healthconnectview.health.PersonalRecord
import kotlinx.coroutines.delay
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
    /** The type's second value where it has one -- diastolic beside systolic -- else empty. */
    val secondaryPoints: List<Point> = emptyList(),
    val secondaryTotal: Double? = null,
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
    /**
     * The run up to this day and the year's longest: of the goal met for a ring, of days with
     * an activity for exercise. Null where the tile has no streak.
     */
    val streak: StreakSummary? = null,
    /** The type's best over the year before today, where it has one. See [RecordKind]. */
    val record: PersonalRecord? = null,
    /** Blood pressure only: the window's morning and evening averages, kept apart. */
    val dayParts: DayPartSplit? = null,
    /**
     * True while the record list and the source picker are still being read. The chart is
     * shown first and these follow; see `loadData`.
     */
    val listPending: Boolean = false,
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
    /** Bands for [secondaryPoints]' line, where the type classifies its second value too. */
    val secondaryZones: ValueZones? = null,
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
    /** The night and the week against its usual range, for a type judged night by night. */
    val hrv: HrvSummary? = null,
    /** Per point of [points], where it sits against its usual range; empty for other types. */
    val pointStandings: List<HrvStanding?> = emptyList(),
    /** The single nights behind an HRV week's mean, over a week or four; empty otherwise. */
    val nightPoints: List<Point> = emptyList(),
    /** The four-week rolling mean behind the series, for a type with `rollingBaseline`. */
    val baseline: List<Point> = emptyList(),
    /** The series is each bucket's mean of readings the platform cannot aggregate. */
    val dailyFromReadings: Boolean = false,
    /**
     * How many records the window holds, where the chart's read counted them all; null where
     * counting would take a read of its own -- a year of heart rate is minutes of paging.
     */
    val recordCount: Int? = null,
    /** The stretch the list covers while the chart is zoomed; null for the whole window. */
    val listRange: ClosedRange<Instant>? = null,
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
) {
    /**
     * Whether there is a series worth drawing. Not on a day of a type with one value a day:
     * that chart is a lone dot on an empty axis, and the value is already the headline.
     */
    val drawsChart: Boolean
        get() = points.isNotEmpty() && !(spec.tile.dailyValue && extent != null)
}


/** One metric measured over a session's window, for the session detail sheet. */
data class SessionStat(val spec: RecordTypeSpec<*>, val value: Double)

/**
 * One type, over a span that can be stepped backwards and forwards.
 *
 * Separate from TypeDetailViewModel, which shows a fixed trailing range plus the raw record
 * list. This is the chart-first view reached from a dashboard tile, and it is the only place
 * that can reach data older than a year.
 */
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
     * Whether [kind] over [period] would hold any data. Asked before the save dialog opens;
     * when it would not, says so through [exportResults] and the dialog stays shut, so an empty
     * period no longer leaves a file of headers. A failed check lets the export go ahead:
     * the write reports its own failure.
     */
    suspend fun canExport(kind: ExportKind, period: ExportPeriod): Boolean {
        val spec = _spec.value ?: return false
        val origins = selectedSource?.let { setOf(DataOrigin(it)) } ?: emptySet()
        val exporter = Exporter(getApplication(), repository)
        val any = runCatching {
            when (kind) {
                ExportKind.RECORDS -> exporter.hasRecords(spec, period.start(), period.end(), origins)
                ExportKind.DAILY -> exporter.hasDailyTotals(spec, period.first, period.last.plusDays(1), origins)
                ExportKind.REPORT, ExportKind.PRINT -> exporter.hasReportData(spec, period.first, period.last, origins)
            }
        }.getOrDefault(true)
        if (!any) _exportResults.tryEmit(ExportResult.Empty)
        return any
    }

    /**
     * Writes this type, this source filter and [period] to [uri].
     *
     * A failed export removes the file it started, so a half-written CSV is not left looking
     * like a complete one.
     */
    fun export(kind: ExportKind, period: ExportPeriod, uri: Uri) {
        val spec = _spec.value ?: return
        val origins = selectedSource?.let { setOf(DataOrigin(it)) } ?: emptySet()
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val exporter = Exporter(getApplication(), repository)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requireNotNull(resolver.openOutputStream(uri)) { "cannot open $uri" }.use { out ->
                        when (kind) {
                            ExportKind.RECORDS -> ExportResult.Written(
                                exporter.writeRecords(spec, period.start(), period.end(), origins, out),
                                uri,
                                kind.mimeType,
                            )
                            ExportKind.DAILY -> ExportResult.Written(
                                exporter.writeDailyTotals(spec, period.first, period.last.plusDays(1), origins, out),
                                uri,
                                kind.mimeType,
                            )
                            // Printing never reaches a file; see renderReport.
                            ExportKind.PRINT -> error("print is not saved")
                            ExportKind.REPORT -> ExportResult.Report(
                                exporter.writeReport(
                                    spec,
                                    period.first,
                                    period.last,
                                    origins,
                                    selectedSource?.let { getApplication<Application>().appLabelFor(it) },
                                    out,
                                ),
                                uri,
                                kind.mimeType,
                            )
                        }
                    }
                }
            }
            result.onFailure {
                Log.w(TAG, "export failed: ${it.javaClass.simpleName}")
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            }
            _exportResults.tryEmit(result.getOrDefault(ExportResult.Failed))
        }
    }

    /**
     * Whether data older than 30 days is readable, as of the last load; without it an export
     * reaching further back is cut short, and the period dialog says so.
     */
    private val _historyGranted = MutableStateFlow(true)
    val historyGranted: StateFlow<Boolean> = _historyGranted.asStateFlow()

    /**
     * The report for [period] as PDF bytes in memory, for the print preview; null if it failed.
     * Read now, while the app is in front: Health Connect refuses reads once the preview covers
     * it. Held only until the preview has taken it -- never written anywhere by the app.
     */
    suspend fun renderReport(period: ExportPeriod): ByteArray? {
        val spec = _spec.value ?: return null
        val origins = selectedSource?.let { setOf(DataOrigin(it)) } ?: emptySet()
        val source = selectedSource?.let { getApplication<Application>().appLabelFor(it) }
        return runCatching {
            withContext(Dispatchers.IO) {
                ByteArrayOutputStream().also { out ->
                    Exporter(getApplication(), repository).writeReport(spec, period.first, period.last, origins, source, out)
                }.toByteArray()
            }
        }.onFailure { Log.w(TAG, "report failed: ${it.javaClass.simpleName}") }.getOrNull()
    }

    fun reportNoViewer() {
        _exportResults.tryEmit(ExportResult.NoViewer)
    }

    private fun ExportPeriod.start(): Instant = first.atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()

    private fun ExportPeriod.end(): Instant = last.plusDays(1).atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()

    /**
     * Share of the current load's steps finished, 0 to 1, or null when not loading.
     *
     * Counted in steps -- chart, total, list, the source picker's two reads, sessions where the
     * type has them -- because those are what is known: Health Connect reports no progress
     * within a request. It moves in real jumps rather than a guessed time.
     */
    private val _progress = MutableStateFlow<Float?>(null)

    /** The personal record to show under an empty window's message; null otherwise. */
    private val _emptyRecord = MutableStateFlow<PersonalRecord?>(null)
    val emptyRecord: StateFlow<PersonalRecord?> = _emptyRecord.asStateFlow()
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

    /** The window holding [date], or the current one when it is empty or unparseable. */
    private fun offsetForDate(date: String, span: Span = _span.value): Int =
        runCatching { LocalDate.parse(date) }.getOrNull()?.let(span::offsetOf) ?: 0

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

    /** The load in flight, cancelled when a newer one starts. */
    private var loadJob: Job? = null

    /**
     * Reloads, replacing any load still running. With the list read after the chart is shown,
     * swiping on through years while one loads would otherwise let the older load finish last
     * and put its window's list under the newer window's chart.
     */
    private fun reload() {
        val spec = RecordRegistry.specOrNull(typeName ?: return) ?: run {
            _state.update { UiState.Error("Unknown type") }
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { UiState.Loading }

            val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
            if (spec.permission !in granted) {
                _state.update { UiState.NoPermission }
                return@launch
            }

            _historyGranted.value = RecordRegistry.HISTORY_PERMISSION in granted
            val span = _span.value
            val offset = _offset.value
            val capped = span.needsHistoryPermission(offset) &&
                RecordRegistry.HISTORY_PERMISSION !in granted

            val result = runCatching {
                loadData(spec, span, offset, capped, selectedSource) { chart ->
                    ensureActive()
                    _progress.value = null
                    // Only when there is something to show: a partial that turns out empty
                    // would flash a chart-less screen before the empty message.
                    if (chart.points.isNotEmpty() || chart.total != null || chart.sessions.isNotEmpty()) {
                        _state.update { UiState.Data(chart) }
                    }
                }
            }
            // runCatching also catches cancellation, and the reads inside swallow it into
            // defaults, so a superseded load can reach this point with a result. It must not
            // put that result, or an error, over the newer load's screen.
            ensureActive()
            _progress.value = null
            result.fold(
                onSuccess = { data ->
                    val empty = data.points.isEmpty() && data.total == null &&
                        data.records.isEmpty() && data.sessions.isEmpty()
                    // An empty window still has a best: VO2 max is measured now and then, and
                    // most single days of it would otherwise never show its record.
                    _emptyRecord.value = data.record.takeIf { empty }
                    _state.update { if (empty) UiState.Empty else UiState.Data(data) }
                },
                onFailure = { error ->
                    _state.update { UiState.Error(error.message ?: "Could not read data") }
                },
            )
        }
    }


    /**
     * The route of [session], read now that the session is open: its points, a note that the
     * user must consent to this one first, or nothing. Held by the open sheet only.
     */
    suspend fun routeFor(session: Session): RouteLoad {
        val ref = session.route ?: return RouteLoad.Missing
        // Read now rather than trusting the list's note: consent given for this session
        // earlier, or the standing permission granted since, makes the route readable.
        return when (val result = runCatching { repository.routeOf(ref.recordId) }.getOrNull()) {
            is ExerciseRouteResult.Data -> RouteLoad.Shown(result.exerciseRoute.toPoints())
            is ExerciseRouteResult.ConsentRequired -> RouteLoad.NeedsConsent
            null -> RouteLoad.Failed
            else -> RouteLoad.Missing
        }
    }

    /**
     * Writes a route the user is looking at to [uri] as GPX. The points come from the open
     * sheet, so nothing is read again; like every export, a failure removes the file.
     */
    fun exportRoute(points: List<RoutePoint>, name: String, uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requireNotNull(resolver.openOutputStream(uri)) { "cannot open $uri" }.use { out ->
                        Gpx.write(points, name, out.bufferedWriter(Charsets.UTF_8))
                    }
                }
                ExportResult.Written(points.size, uri, GPX_MIME)
            }
            result.onFailure {
                Log.w(TAG, "route export failed: ${it.javaClass.simpleName}")
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            }
            _exportResults.tryEmit(result.getOrDefault(ExportResult.Failed))
        }
    }

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

    /** The whole window's list, kept to restore when the chart is zoomed back out. */
    private var windowList: List<Record>? = null

    /** Personal records already read, by type, source and day; see [loadData]. */
    private val personalRecords = mutableMapOf<String, PersonalRecord?>()
    private var listJob: Job? = null

    /**
     * Lists the records of the stretch the chart shows while zoomed, or the whole window's
     * again for null. Debounced, since a pinch reports every frame: the read starts once the
     * fingers have settled, and a newer range cancels an older read.
     */
    fun showListFor(range: ClosedRange<Instant>?) {
        val current = (_state.value as? UiState.Data)?.value ?: return
        if (current.listRange == range) return
        val spec = current.spec
        val origins = selectedSource?.let { setOf(DataOrigin(it)) } ?: emptySet()
        listJob?.cancel()
        listJob = viewModelScope.launch {
            delay(LIST_DEBOUNCE_MS)
            val records = if (range == null) {
                windowList ?: return@launch
            } else {
                runCatching {
                    repository.recordsIn(spec, range.start, range.endInclusive, origins, HealthRepository.LIST_RECORDS)
                }.getOrElse { return@launch }
            }
            _state.update { state ->
                val data = (state as? UiState.Data)?.value ?: return@update state
                UiState.Data(
                    data.copy(
                        records = records,
                        truncated = records.size >= HealthRepository.LIST_RECORDS,
                        listRange = range,
                    ),
                )
            }
        }
    }

    private suspend fun loadData(
        spec: RecordTypeSpec<*>,
        span: Span,
        offset: Int,
        historyCapped: Boolean,
        source: String?,
        onChartReady: (TileDetailData) -> Unit = {},
    ): TileDetailData = coroutineScope {
        val metric = spec.aggregate
        val origins = source?.let { setOf(DataOrigin(it)) } ?: emptySet()
        val windowStart = windowStart(span, offset)
        val windowEnd = windowEnd(span, offset)

        val loader = TileChartLoader(repository, dashboardStore)
        val chart = loader
            .chart(spec, span, offset, historyCapped, source, deferExtras = true) { _progress.value = it }

        // The chart first, then the list and the picker.
        //
        // Health Connect serves one app's requests largely in turn, so reading these alongside
        // the chart only queued the chart behind them: a year of heart rate showed nothing for
        // ~13.5 s. Handing the chart over first puts it on screen in about half that, while the
        // picker keeps its place and the list says it is loading.
        onChartReady(chart)

        val recordsRead = async {
            runCatching {
                repository.recordsIn(spec, windowStart, windowEnd, origins, HealthRepository.LIST_RECORDS)
            }.getOrDefault(emptyList())
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
                }.getOrDefault(emptySet())
            }
        }
        val aggregateOriginsRead = metric?.let { aggregate ->
            async {
                runCatching { repository.contributingApps(aggregate, span.localFilter(offset)) }
                    .getOrDefault(emptySet())
            }
        }

        // Its own read rather than the list's: a reading at 01:00 counts for the evening before,
        // so the window runs 04:00 to 04:00 and differs from the calendar one at both ends.
        val dayPartsRead = if (spec.type == BloodPressureRecord::class) {
            async {
                val (from, to) = dayPartWindow(
                    span.startDate(offset),
                    span.endDate(offset).minusDays(1),
                    HealthRepository.DEFAULT_ZONE,
                )
                runCatching {
                    val readings = repository.recordsIn(spec, from, to, origins)
                        .filterIsInstance<BloodPressureRecord>()
                        .map {
                            PressureReading(
                                it.time,
                                it.systolic.inMillimetersOfMercury,
                                it.diastolic.inMillimetersOfMercury,
                            )
                        }
                    splitByDayPart(readings, HealthRepository.DEFAULT_ZONE)
                }.getOrNull()
            }
        } else {
            null
        }

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
        // A capped list names too few: the newest 500 readings of a respiratory rate are ten
        // hours, and a writer from the morning would drop out of the picker. Then one page
        // of the window names them, as the unfiltered list of 5,000 used to.
        val writers = otherWritersRead?.await()
            ?: if (records.size >= HealthRepository.LIST_RECORDS) {
                runCatching {
                    repository.recordsIn(spec, windowStart, windowEnd, maxRecords = HealthRepository.PAGE_SIZE)
                        .map { spec.originOf(it) }
                        .toSet()
                }.getOrDefault(emptySet()) + records.map { spec.originOf(it) }
            } else {
                records.map { spec.originOf(it) }.toSet()
            }
        val contributors = writers + (aggregateOriginsRead?.await() ?: emptySet())

        windowList = records
        val listed = chart.copy(
            records = records,
            truncated = records.size >= HealthRepository.LIST_RECORDS,
            contributingApps = contributors,
            dayParts = dayPartsRead?.await()?.takeIf { it.morning != null || it.evening != null },
            listPending = false,
        )
        // The list is on screen before the extras: a streak reaching back a year took 12.5 s
        // on the phone, and holding the list behind it left the screen half empty for that long.
        onChartReady(listed)
        // Off the main thread: only the Health Connect calls themselves leave it, and building
        // and counting the sessions behind a 48-day activity streak then ran on it while the
        // screen was already in use -- dragging a route's marker turned visibly laggy.
        val extras = withContext(Dispatchers.Default) { loader.extras() }
        // Last, and once per type and source: it does not depend on the window on screen, and
        // reading a year again on every swipe would queue behind the next window's chart.
        val recordKey = "${spec.type.simpleName}|$source|${LocalDate.now()}"
        // containsKey, not getOrPut: "no record" is an answer too, and must not be read again.
        val record = if (personalRecords.containsKey(recordKey)) {
            personalRecords[recordKey]
        } else {
            // Only an answer is kept: a cancelled or failed read is tried again next time.
            try {
                withContext(Dispatchers.Default) { repository.personalRecord(spec, origins, LocalDate.now()) }
                    .also { personalRecords[recordKey] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        listed.copy(baseline = extras.baseline, trend = extras.trend, streak = extras.streak, record = record)
    }

    private companion object {
        const val TAG = "TileDetail"
        /** How long a zoom must settle before the list is re-read for it. */
        const val LIST_DEBOUNCE_MS = 400L
        /**
         * A record at least this long is a whole-day summary rather than an event, and says
         * nothing about when within the day it happened.
         */
        const val MAX_CONCURRENT_STATS = 4
    }

}

/** An open session's route: its points, consent needed first, none recorded, or a failed read. */
sealed interface RouteLoad {
    data class Shown(val points: List<RoutePoint>) : RouteLoad
    data object NeedsConsent : RouteLoad
    data object Missing : RouteLoad
    data object Failed : RouteLoad
}

/** GPX's registered type; save dialogs and track apps both know it. */
const val GPX_MIME = "application/gpx+xml"
