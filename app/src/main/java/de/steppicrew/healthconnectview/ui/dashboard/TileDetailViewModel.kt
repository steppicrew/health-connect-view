package de.steppicrew.healthconnectview.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.dashboard.DashboardStore
import de.steppicrew.healthconnectview.dashboard.SourceStore
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.dedupeSessions
import de.steppicrew.healthconnectview.health.toSession
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.health.numericAggregate
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.goalCrossing
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
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

data class TileDetailData(
    val spec: RecordTypeSpec<*>,
    val points: List<Point>,
    val total: Double?,
    /** True when [points] came from Health Connect's deduplicating aggregation. */
    val aggregated: Boolean,
    /** True when a bucket is wider than a day, so the caption must not say "daily". */
    val weeklyBuckets: Boolean,
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
    private val _span = MutableStateFlow(Span.DAY)
    val span: StateFlow<Span> = _span.asStateFlow()

    /** Steps back from the present; 0 is the current window. Never negative. */
    private val _offset = MutableStateFlow(0)
    val offset: StateFlow<Int> = _offset.asStateFlow()

    private var typeName: String? = null
    private var selectedSource: String? = null

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
    fun load(typeName: String, date: String = "") {
        this.typeName = typeName
        _offset.update { offsetForDate(date) }
        viewModelScope.launch {
            selectedSource = sourceStore.selections.first()[typeName]
            reload()
        }
    }

    private fun offsetForDate(date: String): Int {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return 0
        val days = java.time.temporal.ChronoUnit.DAYS.between(parsed, LocalDate.now())
        return days.toInt().coerceAtLeast(0)
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
            result.fold(
                onSuccess = { data ->
                    _state.update {
                        if (data.points.isEmpty() && data.total == null && data.records.isEmpty()) {
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
        val byWriter = allRecords.groupBy { spec.originOf(it) }
        val dominant = if (origins.isEmpty() && byWriter.size > 1) {
            byWriter.maxByOrNull { (_, group) ->
                group.sumOf { record -> spec.pointsOf(record).sumOf { it.value } }
            }
        } else {
            null
        }
        chosenShapeWriter = dominant?.key
        val records = dominant?.value ?: allRecords

        val steps = records
            .mapNotNull { record ->
                val start = spec.timeOf(record)
                val end = spec.endTimeOf(record) ?: start
                val value = spec.pointsOf(record).sumOf { it.value }
                if (Duration.between(start, end) >= WHOLE_DAY_THRESHOLD) {
                    null
                } else {
                    Interval(start = start, end = end, value = value)
                }
            }
            .sortedBy { it.start }

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
     * Sleep and exercise spans overlapping the window.
     *
     * Read unfiltered by source: a session written by any app is still a fact about what the
     * user was doing, and the source filter is about which app's *measurements* to trust.
     * Overlapping duplicates are collapsed, preferring the writer that named the activity.
     */
    private suspend fun loadSessions(
        span: Span,
        offset: Int,
        kinds: Set<Session.Kind>,
    ): List<Session> {
        // Widened by a night either side. A sleep session belongs to the morning it ends on
        // but starts the previous evening -- measured on a real device, 22:48 to 05:15 -- so a
        // day-bounded read is the wrong query for it whatever the filter's overlap semantics.
        // Bands are clipped to the visible range when drawn, so the margin costs nothing.
        val visibleStart = span.startDate(offset)
            .atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()
        val visibleEnd = span.endDate(offset)
            .atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()
        val range = TimeRangeFilter.between(
            visibleStart.minus(SESSION_MARGIN),
            visibleEnd.plus(SESSION_MARGIN),
        )

        val exercise = if (Session.Kind.EXERCISE in kinds) {
            runCatching {
                repository.read(ExerciseSessionRecord::class, range).map { it.toSession() }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val sleep = if (Session.Kind.SLEEP in kinds) {
            runCatching {
                repository.read(SleepSessionRecord::class, range).map { it.toSession() }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        // Only sessions that actually touch the window are worth drawing; the margin was for
        // catching one that started outside it, not for showing the neighbouring night.
        return dedupeSessions(exercise + sleep)
            .filter { it.start < visibleEnd && it.end > visibleStart }
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
    ): TileDetailData {
        val metric = spec.aggregate
        val origins = source?.let { setOf(DataOrigin(it)) } ?: emptySet()

        // Filled in by the bucketed branch below; empty for every other shape of series.
        var emptyBuckets: List<Instant> = emptyList()
        // Cleared per load: a value left over from the previous span would name a writer that
        // had nothing to do with the series now on screen.
        chosenShapeWriter = null

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

                duration != null -> repository
                    .intradayTotals(metric, span.instantFilter(offset), duration, origins)
                    .mapNotNull { bucket ->
                        val value = bucket.result[metric]?.let(::numericAggregate)
                            ?: return@mapNotNull null
                        Point(time = bucket.startTime, value = value)
                    }

                period != null -> {
                    val buckets = repository
                        .bucketedTotals(metric, span.localFilter(offset), period, origins)

                    // A bucket with no value is a day nothing was recorded, which is not the
                    // same as a day with a value of zero. Both the empty times and the points
                    // are carried forward so the chart can break the line rather than draw
                    // through the gap and imply a reading that never existed.
                    // Only gaps *between* recorded days matter. A window commonly begins or
                    // ends on days with nothing yet recorded -- today, most obviously -- and
                    // those break no line, so counting them would claim a hole that is not
                    // there.
                    val withValue = buckets.filter { it.result[metric] != null }
                    val firstRecorded = withValue.firstOrNull()?.startTime
                    val lastRecorded = withValue.lastOrNull()?.startTime
                    emptyBuckets = if (firstRecorded == null || lastRecorded == null) {
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
        val goal = if (cumulative) goalFor(spec) else null

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

        val scaledPoints = if (cumulative) {
            scaleToTotal(chartPoints, aggregatedTotal)
        } else {
            chartPoints
        }
        // Only true when the points between are genuinely apportioned rather than measured.
        // A record-built curve is rescaled too, but every one of its points is a real record,
        // so calling it approximate would understate what the chart is showing. Scaling shows
        // up instead as the shape-source note, which says exactly whose readings these are.
        val approximated = cumulative && scaledPoints !== chartPoints && chosenShapeWriter == null

        // Only within a day: across weeks a band would be thinner than the line it sits
        // behind and would say nothing.
        val sessions = if (span.intradayBucket != null && spec.tile.overlaySessions.isNotEmpty()) {
            loadSessions(span, offset, spec.tile.overlaySessions)
        } else {
            emptyList()
        }

        // Deliberately unfiltered: this drives the source picker, so it must list every app
        // that wrote into the window. Scoping it to the current selection would collapse the
        // picker to that one app and strand the user there with no way back.
        val contributors = if (metric != null) {
            runCatching { repository.contributingApps(metric, span.localFilter(offset)) }
                .getOrDefault(emptySet())
        } else {
            runCatching {
                repository.read(spec.type, span.instantFilter(offset))
                    .map { spec.originOf(it) }
                    .toSet()
            }.getOrDefault(emptySet())
        }

        // Newest first, matching how the other list reads.
        val records = runCatching {
            repository.read(spec.type, span.instantFilter(offset), origins = origins)
        }.getOrDefault(emptyList())

        return TileDetailData(
            spec = spec,
            points = scaledPoints,
            total = total,
            aggregated = metric != null,
            contributingApps = contributors,
            selectedSource = source,
            goal = goal,
            cumulative = cumulative,
            goalCrossing = goalCrossing(scaledPoints, goal),
            emptyBuckets = emptyBuckets,
            sessions = sessions,
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
        /**
         * A record at least this long is a whole-day summary rather than an event, and says
         * nothing about when within the day it happened.
         */
        const val MAX_CONCURRENT_STATS = 4

        val WHOLE_DAY_THRESHOLD: Duration = Duration.ofHours(12)

        /**
         * How far outside the visible window sessions are searched. A night's sleep begins
         * well before the midnight it is credited to -- measured on a device, 22:48 to 05:15
         * -- so half a day either side catches it without dragging in the night before.
         */
        val SESSION_MARGIN: Duration = Duration.ofHours(12)
    }

}
