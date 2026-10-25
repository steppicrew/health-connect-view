package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.health.YearHeatmap
import de.steppicrew.healthconnectview.health.HrvStanding
import de.steppicrew.healthconnectview.dashboard.TileFace
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.ui.insights.Insight
import de.steppicrew.healthconnectview.dashboard.Tile
import de.steppicrew.healthconnectview.health.Availability
import de.steppicrew.healthconnectview.health.CycleNow
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Trend
import de.steppicrew.healthconnectview.health.currentStreak
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.night
import de.steppicrew.healthconnectview.health.totalDuration
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.TileSpec
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// What the dashboard shows, as its view model hands it over. Split from DashboardViewModel.kt.

/**
 * What one tile shows.
 *
 * [value] is null when the day holds nothing, which is deliberately distinct from a value of
 * zero: "no steps recorded" and "zero steps" mean different things, and rendering the first as
 * "0" is the misreading the roadmap calls out.
 */
/**
 * One part on a weight tile's body face: its type, the latest value in the shown unit, and its
 * day where that is not the weight's -- null where both were measured on the same day.
 */
data class BodyReading(val spec: RecordTypeSpec<*>, val value: Double, val date: LocalDate?)

/** The body face's parts, in the order the tile lists them. */
internal val BODY_PARTS = listOf("BodyFatRecord", "BodyWaterMassRecord", "BoneMassRecord")

data class TileData(
    val tile: Tile,
    val spec: RecordTypeSpec<*>,
    /** The large tile's second curve, as [Tile.companion] names it, where it was read. */
    val companionCurves: List<Pair<Session, List<Point>>> = emptyList(),
    val value: Double? = null,
    /** The type's second value where it has one -- blood pressure's diastolic. */
    val secondaryValue: Double? = null,
    /**
     * The part of [value] its stack draws on top -- total calories' active part -- from the
     * same writers and span, drawn in its own colour in the ring.
     */
    val stackedValue: Double? = null,
    /** Recent readings for a curve tile; empty for every other form. */
    val curve: List<Point> = emptyList(),
    /**
     * The day's sessions for a [TileSpec.Form.SESSIONS] tile; empty for every other form.
     *
     * The tile's face is the count of these and its subtitle their total duration, so the
     * list itself is what the tile is showing rather than a derived number: "three
     * activities, 1h 40m" cannot be recovered from a summed duration alone.
     */
    val sessions: List<Session> = emptyList(),
    val granted: Boolean = true,
    val loading: Boolean = true,
    /**
     * The read threw rather than coming back empty -- Health Connect still starting after a
     * restart, or refusing a caller behind another screen. Kept apart from a null [value],
     * which means nothing was recorded: drawn the same, a refused read read as an empty day.
     */
    val failed: Boolean = false,
    /**
     * The single app this tile is filtered to, or null for the combined deduplicated view.
     * Shown on the tile, because a filtered number differs from the one the same tile shows
     * unfiltered and the difference would otherwise be unexplained.
     */
    val source: String? = null,
    /**
     * The app this tile was asked to filter to, before [source] was settled: the preferred app
     * is dropped for a type it never wrote. Kept so a re-read asking the same can keep the
     * value on screen -- compared against [source], the weight tile, filtered to an app that
     * writes no weight, matched nothing and showed a spinner on every return to the dashboard.
     */
    val requestedSource: String? = null,
    /**
     * The week before the shown day against the 30 days before it, or null where the type has
     * no aggregate or too few recorded days to say.
     */
    val trend: Trend? = null,
    /**
     * The day [value] was measured on, when it is carried from before the shown day; null when
     * it belongs to the shown day itself. See [TileSpec.carryLastReading].
     */
    val valueDate: LocalDate? = null,
    /**
     * The window [value] describes: the tile's own span where it is drawn large, the day
     * otherwise. A single cell always shows the day, whatever its stored span.
     */
    val shownSpan: Span = Span.DAY,
    /** The window's chart, for a large tile showing one; null for every other tile. */
    val chart: TileDetailData? = null,
    /**
     * For a type judged night by night: where the week's mean in [value] sits against the
     * usual range. Null for every other type, and where there are too few nights to say.
     */
    val standing: HrvStanding? = null,
    /**
     * Days in a row up to the shown day: of the goal met, for a ring showing a day, or with an
     * activity, for the activities tile. Zero where there is no run. See [currentStreak].
     */
    val streak: Int = 0,
    /**
     * The day's goal where it adapts ([Tile.goalRule]), in the shown unit; null until it has
     * been replayed, read with the streak from the same year of daily totals.
     */
    val adaptiveGoal: Double? = null,
    /**
     * A weight tile showing the body face: body fat, water and bone mass, each its latest
     * reading up to the shown day, in that order, those never measured left out. Null on every
     * other tile.
     */
    val bodyReadings: List<BodyReading>? = null,
    /**
     * A 2x2 body face over a week, four weeks or a year: each part's daily means through the
     * window, weight included, by type name, and the window they are drawn across. Empty on a
     * 2x1 tile and on a day, which show the numbers alone.
     */
    val bodyCurves: Map<String, List<Point>> = emptyMap(),
    val bodyWindow: ClosedRange<Instant>? = null,
    /** The calendar face's year, where read; see [TileFace.CALENDAR]. */
    val heatmap: YearHeatmap? = null,
    /** Whether that year could reach back only 30 days, without the history permission. */
    val heatmapCapped: Boolean = false,
) {
    /** Everything the day's sessions covered, for the subtitle under a session count. */
    val sessionDuration: Duration get() = sessions.totalDuration()

    /**
     * Sleep across a window, per recorded night credited to the morning it ended, as the
     * detail screen's headline: a 4-week tile read "192h 0m" above a screen saying 6h 53m.
     */
    val nightlyDuration: Duration
        get() {
            val nights = sessions.groupBy { it.end.atZone(HealthRepository.DEFAULT_ZONE).toLocalDate() }.size
            return if (nights == 0) Duration.ZERO else sessionDuration.dividedBy(nights.toLong())
        }

    /**
     * The workout a tap opens directly: the one drawn as a curve, or the day's only workout.
     * Several on a timeline go to the day's list instead, since the tile names none of them.
     */
    val openedWorkout: Session?
        get() {
            if (shownSpan != Span.DAY) return null
            val drawn = companionCurves.map { it.first }.filter { it.kind == Session.Kind.EXERCISE }
            return drawn.singleOrNull() ?: sessions.filter { it.kind == Session.Kind.EXERCISE }.singleOrNull()
        }

    /**
     * The shown day's goal, in the shown unit, for a ring. Null on a week or a year, since a
     * goal is per day -- a week's steps against a daily 5,000 would always read full -- and
     * where there is none. Goals are stored metric; an adaptive goal is null until the day's
     * own is known, rather than standing in for it with the starting goal.
     */
    val dayGoal: Double?
        get() {
            if (shownSpan != Span.DAY) return null
            val goal = if (tile.goalRule != null) adaptiveGoal else tile.effectiveGoal?.let(spec::display)
            return goal?.takeIf { it > 0.0 }
        }

    /**
     * Fraction of [dayGoal], for a ring; above 1 where the goal was beaten, so 180 % reads
     * differently from 107 %. Null when there is no goal or nothing to show.
     */
    val progress: Float?
        get() {
            val goal = dayGoal ?: return null
            val current = value ?: return null
            return (current / goal).toFloat().coerceAtLeast(0f)
        }
}

data class DashboardUiState(
    val availability: Availability = Availability.Available,
    val date: LocalDate = LocalDate.now(),
    val tiles: List<TileData> = emptyList(),
    val loading: Boolean = true,
    /** The insights tile where one is pinned; it sits among [tiles] at its place in [layout]. */
    val insights: InsightsTileData? = null,
    /** The cycle tile where one is pinned; placed by [layout] like the insights tile. */
    val cycle: CycleTileData? = null,
    /** Every tile's id in the stored order, the insights tile's among them. */
    val layout: List<String> = emptyList(),
) {
    /** Today is the newest day with data; stepping forward past it is meaningless. */
    val canStepForward: Boolean get() = date.isBefore(LocalDate.now())
}

/**
 * The insights tile: what moved this week, across every type. [insights] is null while it is
 * read; [locked] without Pro, where the tile offers the purchase instead.
 */
data class InsightsTileData(
    val tile: Tile,
    val insights: List<Insight>? = null,
    val locked: Boolean = false,
)

/** The share of [TileData.value] its stack draws on top, 0 where there is none or it cannot fit. */
fun TileData.stackedShare(): Float {
    val total = value ?: return 0f
    val part = stackedValue ?: return 0f
    return if (total > 0.0 && part in 0.0..total) (part / total).toFloat() else 0f
}

/**
 * The cycle tile: the running cycle on the dashboard's day. [now] is null where no period was
 * recorded in the year up to it; [loaded] tells that apart from still reading.
 */
data class CycleTileData(
    val tile: Tile,
    val now: CycleNow? = null,
    val loaded: Boolean = false,
    /** Not allowed to read menstruation, which every cycle starts from. */
    val noAccess: Boolean = false,
    val failed: Boolean = false,
)

/** A type the add picker offers, and whether a tile of it is already pinned. */
data class AddCandidate(val spec: RecordTypeSpec<*>, val pinned: Boolean)
