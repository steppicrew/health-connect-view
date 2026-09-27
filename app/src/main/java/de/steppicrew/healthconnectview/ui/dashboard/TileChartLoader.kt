package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.health.HrvStanding
import de.steppicrew.healthconnectview.health.hrvWindow
import de.steppicrew.healthconnectview.health.HrvSummary
import androidx.health.connect.client.records.metadata.DataOrigin
import de.steppicrew.healthconnectview.dashboard.DashboardStore
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.widenToSessions
import de.steppicrew.healthconnectview.health.sessionsIn
import de.steppicrew.healthconnectview.health.DIASTOLIC_ZONES
import de.steppicrew.healthconnectview.health.SYSTOLIC_ZONES
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.aggregate.AggregateMetric
import de.steppicrew.healthconnectview.health.totalDuration
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.trendBefore
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.health.numericAggregate
import de.steppicrew.healthconnectview.health.atLeast
import de.steppicrew.healthconnectview.health.dayTotalFilter
import de.steppicrew.healthconnectview.health.openTally
import de.steppicrew.healthconnectview.health.PageProgress
import de.steppicrew.healthconnectview.health.DailyReadings
import androidx.health.connect.client.records.Record
import de.steppicrew.healthconnectview.health.ROLLING_DAYS
import de.steppicrew.healthconnectview.health.rollingMean
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.goalCrossing
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.registry.TileSpec
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period

/**
 * Builds the chart for one type over one window: the series, its total, goal, bands and
 * sessions -- everything a chart draws, and nothing of the record list or source picker.
 *
 * Shared by the detail screen and a large dashboard tile showing a chart, so the two cannot
 * disagree about a day: a running total, a whole-day summary, a curve that stops at now are
 * one piece of code, not two that drift.
 *
 * One instance per load. A few intermediate results are handed between steps in fields
 * (the shape writer, the band, the stack), so an instance must not serve two loads at once.
 */
internal class TileChartLoader(
    private val repository: HealthRepository,
    private val dashboardStore: DashboardStore,
) {

    suspend fun chart(
        spec: RecordTypeSpec<*>,
        span: Span,
        offset: Int,
        historyCapped: Boolean,
        source: String?,
        onProgress: (Float) -> Unit = {},
    ): TileDetailData = coroutineScope {
        val metric = spec.aggregate
        val origins = source?.let { setOf(DataOrigin(it)) } ?: emptySet()

        val hasSessions = spec.tile.form == TileSpec.Form.SESSIONS ||
            (span.intradayBucket != null && spec.tile.overlaySessions.isNotEmpty())
        // Only what comes before the chart can be shown: the list and picker follow it.
        // Weighted by where the time goes: the chart's read is the long one -- a year of
        // respiratory rate pages for a minute or more -- and the total and sessions are single
        // requests. Counted equally, the chart's progress filled half the bar and the rest
        // jumped.
        val steps = CHART_WEIGHT + 1 + if (hasSessions) 1 else 0
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        fun stepDone(weight: Int = 1) {
            onProgress(done.addAndGet(weight).toFloat() / steps)
        }
        // Progress within the chart's read, so the bar moves while it pages instead of sitting
        // at 0 and then jumping to the end.
        fun stepPart(fraction: Float) {
            onProgress((done.get() + fraction * CHART_WEIGHT) / steps)
        }
        val readProgress = PageProgress<Record>(spec::timeOf, ::stepPart)
        onProgress(0f)

        val windowStart = windowStart(span, offset)
        val windowEnd = windowEnd(span, offset)

        // Filled in by the bucketed branch below; empty for every other shape of series.
        var emptyBuckets: List<Instant> = emptyList()
        // Filled in wherever the type has a second value, from the same read as the first.
        var secondaryPoints: List<Point> = emptyList()
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
        pointStandings = emptyList()

        // A type judged night by night: across days its series is the week's mean of nightly
        // values with the usual range behind it, not thousands of five-minute readings drawn
        // as one zigzag. Within a day the readings themselves stay, as for any reading.
        val hrv = if (spec.tile.nightlyStatus) {
            runCatching {
                repository.hrvWindow(span.startDate(offset), span.endDate(offset).minusDays(1), origins, onProgress = ::stepPart)
            }.getOrNull()
        } else {
            null
        }
        val hrvSeries = hrv?.takeIf { span.intradayBucket == null }

        // Totals and bucketed series both come from aggregation wherever the type supports
        // it: several apps can write the same metric, so summing raw records double-counts.
        // Filled where readings with no aggregate are reduced to daily means.
        var dailyReadings: DailyReadings? = null
        val points = if (hrvSeries != null) {
            seriesAggregated = false
            val zone = HealthRepository.DEFAULT_ZONE
            rangeBand = hrvSeries.days.mapNotNull { day ->
                val low = day.usualLow ?: return@mapNotNull null
                val high = day.usualHigh ?: return@mapNotNull null
                ValueBand(day.date.atStartOfDay(zone).toInstant(), low, high)
            }
            val shown = hrvSeries.days.filter { it.weekMean != null }
            pointStandings = shown.map { it.standing }
            shown.map { day -> Point(day.date.atStartOfDay(zone).toInstant(), day.weekMean!!) }
        } else if (metric != null) {
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
                    cumulativeFromRecords(spec, span, offset, origins, readProgress)

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
                    val readings = repository.readForChart(spec.type, span.instantFilter(offset), origins = origins, progress = readProgress)
                    secondaryPoints = readings.flatMap { spec.secondaryPointsOf(it) }.sortedBy { it.time }
                    readings.flatMap { spec.pointsOf(it) }.sortedBy { it.time }
                }

                duration != null -> repository
                    .intradayTotals(metric, span.instantFilter(offset), duration, origins)
                    .mapNotNull { bucket ->
                        val value = bucket.result[metric]?.let { numericAggregate(it, metric) }
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
                        also = (
                            bandMetrics?.toList().orEmpty() + stackMetrics.map { it.second } +
                                listOfNotNull(spec.secondaryAggregate)
                            ).toSet(),
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
                                bucket.result[partMetric]?.let { numericAggregate(it, partMetric) }
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
                            val low = bucket.result[lowMetric]?.let { numericAggregate(it, lowMetric) }
                            val high = bucket.result[highMetric]?.let { numericAggregate(it, highMetric) }
                            if (low == null || high == null) return@mapNotNull null
                            ValueBand(
                                time = bucket.startTime
                                    .atZone(HealthRepository.DEFAULT_ZONE).toInstant(),
                                low = low,
                                high = high,
                            )
                        }
                        // Only where some day actually had a spread. One reading a day -- a
                        // resting rate, most people's weight -- makes every band zero wide, and
                        // the legend and caption then named a range the chart did not draw.
                    }?.takeIf { bands -> bands.any { it.high > it.low } }.orEmpty()

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

                    fun series(of: AggregateMetric<*>): List<Point> = buckets.mapNotNull { bucket ->
                        val value = bucket.result[of]?.let { numericAggregate(it, of) }
                            ?: return@mapNotNull null
                        Point(
                            time = bucket.startTime
                                .atZone(HealthRepository.DEFAULT_ZONE).toInstant(),
                            value = value,
                        )
                    }
                    secondaryPoints = spec.secondaryAggregate?.let(::series).orEmpty()
                    series(metric)
                }

                else -> emptyList()
            }
        } else if (spec.tile.dailyMeans && span.bucket != null) {
            val bucketDays = span.bucket?.days ?: 1
            // Readings with no aggregate, across days: one mean per bucket with its spread,
            // not every reading -- see DailyReadings. Read from four weeks earlier where a
            // rolling mean is drawn, so its first day already has its weeks behind it.
            seriesAggregated = false
            val zone = HealthRepository.DEFAULT_ZONE
            val first = span.startDate(offset)
            val readFrom = if (spec.tile.rollingBaseline) first.minusDays(ROLLING_DAYS - 1L) else first
            val daily = DailyReadings(zone, first, bucketDays)
            runCatching {
                repository.forEachPage(
                    spec.type,
                    TimeRangeFilter.between(readFrom.atStartOfDay(zone).toInstant(), windowEnd),
                    origins,
                    readProgress,
                ) { page ->
                    page.forEach { record ->
                        val origin = record.metadata.dataOrigin.packageName
                        spec.pointsOf(record).forEach { daily.add(it.time, it.value, origin) }
                    }
                }
            }
            dailyReadings = daily
            val buckets = daily.buckets()
            rangeBand = buckets
                .map { ValueBand(it.start.atStartOfDay(zone).toInstant(), it.low, it.high) }
                .takeIf { bands -> bands.any { it.high > it.low } }
                .orEmpty()
            buckets.map { Point(it.start.atStartOfDay(zone).toInstant(), it.mean) }
        } else {
            // No aggregate metric: chart the readings themselves, via the path that spans the
            // whole window rather than stopping at the newest records.
            repository.readForChart(spec.type, span.instantFilter(offset), origins = origins, progress = readProgress)
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
                    repository.readForChart(spec.type, span.instantFilter(offset), origins = origins, progress = readProgress)
                        .flatMap { spec.pointsOf(it) }
                        .sortedBy { it.time }
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }

        stepDone(CHART_WEIGHT) // the chart
        val aggregatedTotal = if (metric != null) {
            val platform = runCatching { repository.total(metric, span.totalFilter(offset), origins) }.getOrNull()
            if (offset == 0) withOpenTally(spec, metric, span, platform, origins) else platform
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
                if (cumulativeCandidate(spec, span)) pts.last().value else spec.combine(pts.map { it.value })
            }
        } else {
            // Readings reduced to daily means: the window's mean of the same counted readings.
            dailyReadings?.overallMean()
        }
        // HRV has no aggregate, so its headline is computed: the night's own value on a day,
        // the week's mean at the window's end across days -- the figure the chart ends on.
        val hrvSummary = hrv?.let { window ->
            HrvSummary(
                night = window.nights.lastOrNull { it.date == span.endDate(offset).minusDays(1) }?.mean,
                day = window.days.lastOrNull(),
            )
        }
        val headline = total ?: hrvSummary?.let { if (hrvSeries != null) it.day?.weekMean else it.night }

        // A goal line only means something against a running total for one day; across days
        // each point is its own day's total and the goal would be a different comparison.
        val cumulative = span.intradayBucket != null && spec.tile.cumulativeIntraday
        val secondaryTotal = spec.secondaryAggregate?.let { second ->
            runCatching { repository.total(second, span.totalFilter(offset), origins) }.getOrNull()
        }
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
            headline
        }

        // The wearer's own level behind a noisy daily value, across days only: a day of a
        // one-value-a-day type has no chart to put it on. Read from 27 days before the window,
        // so its first day already has four weeks behind it; a failed read just leaves it out.
        val shownPoints = perDayPoints.ifEmpty { scaledPoints }
        val baseline = if (spec.tile.rollingBaseline && span.bucket != null && shownPoints.isNotEmpty()) {
            runCatching {
                val zone = HealthRepository.DEFAULT_ZONE
                val first = span.startDate(offset)
                val daily = dailyReadings?.dailyMeans() ?: metric?.let { m ->
                    repository.bucketedTotals(
                        m,
                        TimeRangeFilter.between(
                            first.minusDays(ROLLING_DAYS - 1L).atStartOfDay(),
                            span.endDate(offset).atStartOfDay(),
                        ),
                        Period.ofDays(1),
                        origins,
                    ).mapNotNull { bucket ->
                        val value = bucket.result[m]?.let { numericAggregate(it, m) }
                            ?: return@mapNotNull null
                        bucket.startTime.toLocalDate() to value
                    }.toMap()
                }.orEmpty()
                // Within the series' own span: a year's last point is its week's start, and a
                // line running past it would leave the plot.
                rollingMean(
                    daily,
                    shownPoints.first().time.atZone(zone).toLocalDate(),
                    shownPoints.last().time.atZone(zone).toLocalDate(),
                ).map { (date, mean) -> Point(date.atStartOfDay(zone).toInstant(), mean) }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val chart = TileDetailData(
            spec = spec,
            points = shownPoints,
            // Bars wherever a point is a whole bucket rather than a moment: a sessions window
            // counted per day, a total split into components that only read as parts when
            // drawn stacked, or a counted quantity bucketed across days.
            bars = perDayPoints.isNotEmpty() || stack.isNotEmpty() || bucketedTotals,
            rangeBand = rangeBand,
            stack = stack,
            stackLabels = spec.stackComponents.map { it.first },
            sessionCounts = perDayPoints.isNotEmpty() && sessionKind != Session.Kind.SLEEP,
            total = headlineTotal,
            secondaryPoints = secondaryPoints,
            secondaryTotal = secondaryTotal,
            aggregated = seriesAggregated,
            contributingApps = emptySet(),
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
            sessionCurveUnitRes = heartRateSpec()?.displayUnitRes,
            // Blood pressure is coloured by its grade at every span: unlike a heart-rate zone,
            // a grade is what a day's mean is read for, not only a single reading.
            lineZones = if (spec.type == BloodPressureRecord::class) {
                SYSTOLIC_ZONES
            } else {
                zonesFor(spec).takeIf { span.intradayBucket != null }
            },
            secondaryZones = DIASTOLIC_ZONES.takeIf { spec.type == BloodPressureRecord::class },
            extent = dayExtent(span, offset, sessions),
            heartRateLocked = sessionKind != null && !heartRateGranted,
            approximated = approximated,
            hrv = hrvSummary,
            pointStandings = pointStandings,
            // Each night behind the weekly line, over a week or four. A year would be 365 dots
            // burying the line they explain.
            nightPoints = if (hrvSeries != null && span != Span.YEAR) {
                val first = span.startDate(offset)
                hrvSeries.nights
                    .filter { !it.date.isBefore(first) }
                    .map { Point(it.date.atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant(), it.mean) }
            } else {
                emptyList()
            },
            baseline = baseline,
            dailyFromReadings = dailyReadings != null,
            shapeSource = shapeSource,
            weeklyBuckets = (span.bucket?.days ?: 0) > 1,
            historyCapped = historyCapped,
            start = span.startDate(offset),
            end = span.endDate(offset).minusDays(1),
            records = emptyList(),
            truncated = false,
            listPending = true,
        )

        chart
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
        progress: PageProgress<Record>,
    ): List<Point> {
        val windowStart = span.startDate(offset)
            .atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()
        val allRecords = runCatching {
            repository.readForChart(spec.type, span.instantFilter(offset), origins = origins, progress = progress)
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
        //
        // Nothing is drawn past now. A writer keeping a running tally posts today's record as
        // 00:00-23:59 and raises its value through the day -- Garmin's total calories do -- so
        // the record's end is a label, not a time anything happened by. Ending the ramp there
        // drew the day's figure reached at midnight, a line through hours still to come.
        val now = Instant.now()
        var sum = 0.0
        var lastTime = windowStart
        return buildList {
            add(Point(time = windowStart, value = 0.0))
            steps.forEach { step ->
                val rampStart = minOf(maxOf(step.start, lastTime), now)
                val rampEnd = minOf(maxOf(step.end, rampStart), now)
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
                now,
            )
            if (windowEnd.isAfter(lastTime)) {
                add(Point(time = windowEnd, value = sum))
            }
        }
    }

    /**
     * [platform] with today's share raised to the fullest writer's own tally, where a record
     * claims hours still to come. See [openTally].
     *
     * On a day span the window *is* today. On a wider one only today's part is short, so the
     * shortfall against today's own total is added rather than the tally replacing the lot.
     */
    private suspend fun withOpenTally(
        spec: RecordTypeSpec<*>,
        metric: AggregateMetric<*>,
        span: Span,
        platform: Double?,
        origins: Set<DataOrigin>,
    ): Double? {
        val tally = runCatching { repository.openTally(spec, origins) }.getOrNull() ?: return platform
        if (span == Span.DAY) return atLeast(platform, tally)
        val today = runCatching {
            repository.total(metric, dayTotalFilter(LocalDate.now()), origins)
        }.getOrNull() ?: 0.0
        val shortfall = (tally - today).coerceAtLeast(0.0)
        return (platform ?: 0.0) + shortfall
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

    /** Set with an HRV series, one per point; read straight afterwards like [stack]. */
    private var pointStandings: List<HrvStanding?> = emptyList()

    /** One record reduced to the span it covered and the amount it contributed. */
    private data class Interval(val start: Instant, val end: Instant, val value: Double)

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

    /**
     * The user's goal for this type if they set one, else the type's default -- in the shown
     * unit, since goals are stored metric and the chart is not.
     */
    private suspend fun goalFor(spec: RecordTypeSpec<*>): Double? {
        val typeName = spec.type.simpleName ?: return spec.tile.defaultGoal?.let(spec::display)
        val stored = runCatching { dashboardStore.config.first() }.getOrNull()
        return (stored?.tiles?.firstOrNull { it.typeName == typeName }?.effectiveGoal ?: spec.tile.defaultGoal)
            ?.let(spec::display)
    }

}

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

/** The window's first instant, for reading raw records. */
/** The chart's share of the progress bar, against one for each single-request step. */
private const val CHART_WEIGHT = 8

internal fun windowStart(span: Span, offset: Int): Instant =
    span.startDate(offset).atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()

/** The window's end, exclusive. */
internal fun windowEnd(span: Span, offset: Int): Instant =
    span.endDate(offset).atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()

/** The type whose readings describe a session from the inside. */
internal const val HEART_RATE = "HeartRateRecord"

internal fun heartRateSpec(): RecordTypeSpec<*>? = RecordRegistry.specOrNull(HEART_RATE)

/**
 * How much of the window a record must cover to count as a summary of it rather than
 * a measurement within it, as a percentage.
 *
 * Set high on purpose. The case this exists for is a record spanning the window
 * exactly -- 00:00 to 24:00 -- while a writer legitimately filling a long quiet
 * stretch (measured: Garmin's 06:56-23:59 calories block, 71% of the day) must stay
 * in the shape, because dropping it moves half the day's total into the morning.
 */
private const val SUMMARY_PERCENT: Long = 95
