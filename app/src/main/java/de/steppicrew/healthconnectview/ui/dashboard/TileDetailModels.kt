package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.health.YearHeatmap
import de.steppicrew.healthconnectview.health.HrvStanding
import de.steppicrew.healthconnectview.health.HrvSummary
import androidx.annotation.StringRes
import androidx.health.connect.client.records.Record
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.DayPartSplit
import de.steppicrew.healthconnectview.health.NutrientTotal
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.TrendResult
import de.steppicrew.healthconnectview.health.StreakSummary
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.goalCrossing
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.registry.ValueZones
import kotlinx.coroutines.flow.first
import de.steppicrew.healthconnectview.health.SourceCoverage
import de.steppicrew.healthconnectview.health.PersonalRecord
import de.steppicrew.healthconnectview.health.MIN_TRAINING
import de.steppicrew.healthconnectview.health.TrainingNights
import de.steppicrew.healthconnectview.health.trainingDays
import java.time.Instant
import java.time.LocalDate

// What the detail screen shows, as its view model hands it over. Split from TileDetailViewModel.kt.

/** One bucket's total split into the components that make it up, drawn bottom-up. */
data class StackedBucket(val time: Instant, val parts: List<Double>)

/**
 * A day of a type measured now and then, set against the days before it: each recent day's
 * value (newest last, the shown day among them) and the shown day's own low and high.
 */
data class ReadingContext(
    val days: List<Point>,
    /** The second value per day, diastolic beside systolic; empty for other types. */
    val secondaryDays: List<Point>,
    val dayLow: Double,
    val dayHigh: Double,
)

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
    /** The second value's spread, diastolic beneath systolic; empty for other types. */
    val secondaryRangeBand: List<ValueBand> = emptyList(),
    /** Per-bucket components of a stacked bar, empty where the type declares none. */
    val stack: List<StackedBucket> = emptyList(),
    /** Labels for [stack]'s components, bottom-up. */
    val stackLabels: List<Int> = emptyList(),
    /** True where the bars count sessions per day rather than summing a metric. */
    val sessionCounts: Boolean = false,
    /** What a session type's bars are, per day or per week, where it draws bars; else null. */
    @param:StringRes val sessionCaption: Int? = null,
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
    /**
     * Days with a workout of [MIN_TRAINING] or more, marked along the bottom of a resting heart
     * rate or HRV chart across days ([marksTraining]); empty everywhere else.
     */
    val trainingDays: Set<LocalDate> = emptySet(),
    /** Resting heart rate across days: the night's lowest after training days and rest days. */
    val trainingNights: TrainingNights? = null,
    /**
     * Workouts across days: each bucket's training time in hours, at the same times as the
     * counts in [points], for the lighter bars beside them. Empty everywhere else.
     */
    val timePoints: List<Point> = emptyList(),
    /** Whether [timePoints] is moving time, breaks left out; across a year it is whole durations. */
    val timeMoving: Boolean = true,
    /** Blood pressure only: the window's morning and evening averages, kept apart. */
    val dayParts: DayPartSplit? = null,
    /** Nutrition only: the window's nutrients beyond energy; empty where none was recorded. */
    val nutrients: List<NutrientTotal> = emptyList(),
    /**
     * True while the record list and the source picker are still being read. The chart is
     * shown first and these follow; see `loadData`.
     */
    val listPending: Boolean = false,
    /**
     * True until the boxes read after the chart -- trend, streak, record -- have arrived, so
     * the screen can hold their place rather than push everything below down on arrival.
     */
    val extrasPending: Boolean = false,
    /** Resting heart rate across days: true until the night section has been read, found or not. */
    val trainingNightsPending: Boolean = false,
    /** The year as a calendar, on a year with Pro; read last, see [heatmapFor]. */
    val heatmap: YearHeatmap? = null,
    /** True until [heatmap] has been read, found or not. */
    val heatmapPending: Boolean = false,
    /**
     * The same window a year earlier, moved onto this one's days, while the comparison is on;
     * null until read, empty where nothing was recorded then. See [TileDetailViewModel.setComparing].
     */
    val previousYear: List<Point>? = null,
    /** The year-earlier window's own figure, as [total] is this one's. */
    val previousTotal: Double? = null,
    val previousSecondaryTotal: Double? = null,
    /** Whether this window has a heatmap at all, read or locked: a type's year. */
    val heatmapOffered: Boolean = false,
    /**
     * A year's daily values where the chart's own read supplied them -- nights, workouts,
     * daily means of readings; empty where a type's daily aggregate must be read for them.
     */
    val dayValues: Map<LocalDate, Double> = emptyMap(),
    /** The apps behind the chart's buckets, where it read buckets; null where it did not. */
    val aggregateOrigins: Set<String>? = null,
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
    /** The wearer's usual range at each point, for a type with `usualRange`. */
    val usualBand: List<ValueBand> = emptyList(),
    /** A day of a type measured now and then, against its recent readings; see TileSpec. */
    val readingContext: ReadingContext? = null,
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
    /** The day a reading context is shown for: a day window's own date. */
    val readingContextDay: java.time.LocalDate?
        get() = extent?.let { points.firstOrNull()?.time?.atZone(HealthRepository.DEFAULT_ZONE)?.toLocalDate() }

    val drawsChart: Boolean
        get() = points.isNotEmpty() && !(spec.tile.dailyValue && extent != null) && readingContext == null

    /**
     * The sessions to shade behind the chart. Not behind a session type's own bars across
     * days: there each bar already is its sessions, and a year of nights drew 366 hairline
     * bands behind 52 bars.
     */
    val bandSessions: List<Session>
        get() = if (spec.tile.form == TileSpec.Form.SESSIONS && extent == null) emptyList() else sessions

    /** Sleep across days: the headline is the mean of the recorded nights, not their sum. */
    val perNight: Boolean
        get() = spec.tile.sessionKind == Session.Kind.SLEEP && spec.tile.form == TileSpec.Form.SESSIONS &&
            extent == null
}


/** A source comparison in progress, done, or failed. */
sealed interface CoverageLoad {
    data object Loading : CoverageLoad
    data class Done(val coverage: SourceCoverage) : CoverageLoad
    data object Failed : CoverageLoad
}

/** The app chosen in settings as the default source, and whether a type overrides it. */
data class SourceDefault(val preferred: String?, val ownChoice: Boolean)
