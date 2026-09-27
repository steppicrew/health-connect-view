package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period

/**
 * Days in a row a daily goal was met, counted back from the shown day.
 *
 * [count] is the met days only. A day with nothing recorded is stepped over, neither counted
 * nor breaking the run: a watch left on the charger says nothing about whether the goal would
 * have been met, and ending a month's streak over it would punish missing data as if it were a
 * missed goal. A day with a value below the goal ends the run, and so does a gap of more than
 * [STREAK_MAX_GAP_DAYS]: after a month with nothing recorded, what came before is another run.
 *
 * [gap] is the days without data since the last recorded one, carried between chunks.
 */
data class Streak(val count: Int, val broken: Boolean, val gap: Int = 0)

/**
 * Walks [newestFirst] -- one value per day, null for no data -- continuing this streak.
 *
 * Split out so the days can arrive in chunks, newest first, and the reading stop as soon as
 * a day breaks the run: most streaks end within a week, and a year of buckets for each would
 * be spent to find that out.
 */
fun Streak.continued(newestFirst: List<Double?>, goal: Double): Streak {
    if (broken) return this
    var count = count
    var gap = gap
    for (value in newestFirst) {
        when {
            value == null -> if (++gap > STREAK_MAX_GAP_DAYS) return Streak(count, broken = true)
            value >= goal -> {
                count++
                gap = 0
            }
            else -> return Streak(count, broken = true)
        }
    }
    return Streak(count, broken = false, gap = gap)
}

/**
 * The run as the shown day leaves it, before any earlier day: null where that day already
 * ends it -- a finished day short of the goal. Today short of it breaks nothing yet.
 */
fun streakStart(dayValue: Double?, goal: Double, running: Boolean): Streak? = when {
    dayValue == null -> Streak(0, broken = false, gap = 1)
    dayValue >= goal -> Streak(1, broken = false)
    running -> Streak(0, broken = false)
    else -> null
}

/** The longest run in a stretch of days: how many were met, and the first and last of them. */
data class LongestStreak(val count: Int, val first: LocalDate, val last: LocalDate)

/**
 * The longest run in [oldestFirst], by the same rules as [continued]: gaps bridged up to
 * [STREAK_MAX_GAP_DAYS], a day short of the goal ending it. Null where no day met the goal.
 */
fun longestStreak(oldestFirst: List<Pair<LocalDate, Double?>>, goal: Double): LongestStreak? {
    var best: LongestStreak? = null
    var count = 0
    var gap = 0
    var first: LocalDate? = null
    for ((day, value) in oldestFirst) {
        when {
            value == null -> if (++gap > STREAK_MAX_GAP_DAYS) count = 0
            value >= goal -> {
                if (count == 0) first = day
                count++
                gap = 0
                if (count > (best?.count ?: 0)) best = LongestStreak(count, first ?: day, day)
            }
            else -> {
                count = 0
                gap = 0
            }
        }
    }
    return best
}

/**
 * One value per day for `[start, end)`, looked up by date; null for a day with no data.
 * Where a day without data counts as zero -- no activity recorded -- the source says so by
 * returning zero rather than null.
 */
typealias DailyValues = suspend (start: LocalDate, end: LocalDate) -> (LocalDate) -> Double?

/** Daily deduplicated totals of [metric], the same buckets the charts draw. */
fun HealthRepository.dailyTotalsOf(metric: AggregateMetric<*>, origins: Set<DataOrigin>): DailyValues =
    { start, end ->
        val byDay = bucketedTotals(
            metric,
            TimeRangeFilter.between(start.atStartOfDay(), end.atStartOfDay()),
            Period.ofDays(1),
            origins,
        ).associate { bucket ->
            bucket.startTime.toLocalDate() to bucket.result[metric]?.let { numericAggregate(it, metric) }
        }
        byDay::get
    }

/**
 * Exercise sessions per day, a day credited as the chart's bars credit it, by when a session
 * ended. Zero rather than null for a day without one: Health Connect cannot tell "no activity"
 * from "no recording", and the tile already reads an empty day as zero activities, so here a
 * missing day ends the run.
 */
fun HealthRepository.dailyActivities(): DailyValues = { start, end ->
    val zone = HealthRepository.DEFAULT_ZONE
    val counts = sessionsIn(
        start.atStartOfDay(zone).toInstant(),
        end.atStartOfDay(zone).toInstant(),
        setOf(Session.Kind.EXERCISE),
    ).groupingBy { it.end.atZone(zone).toLocalDate() }.eachCount()
    val lookup: (LocalDate) -> Double? = { day -> (counts[day] ?: 0).toDouble() }
    lookup
}

/**
 * The streak ending on [date], whose own value is [dayValue] -- the tile's figure, so the ring
 * and the count agree about the day on screen.
 *
 * Today is still running: short of the goal it breaks nothing yet, and the streak is the one
 * leading up to it. Any earlier day short of the goal has no streak at all.
 *
 * The days before are read back [STREAK_CHUNK_DAYS] at a time until one breaks the run or
 * [STREAK_MAX_DAYS] are covered. Past 30 days that needs the history permission; without it
 * the platform returns nothing older, so the count stops growing rather than going wrong.
 */
suspend fun currentStreak(
    goal: Double,
    date: LocalDate,
    dayValue: Double?,
    daily: DailyValues,
    today: LocalDate = LocalDate.now(),
): Int {
    var streak = streakStart(dayValue, goal, running = date == today) ?: return 0
    var end = date
    var covered = 0
    while (!streak.broken && covered < STREAK_MAX_DAYS) {
        val days = minOf(STREAK_CHUNK_DAYS, STREAK_MAX_DAYS - covered)
        val start = end.minusDays(days.toLong())
        val values = daily(start, end)
        streak = streak.continued(List(days) { values(end.minusDays(it + 1L)) }, goal)
        end = start
        covered += days
    }
    return streak.count
}

/** The current run and the year's longest, for the detail screen. */
data class StreakSummary(val current: Int, val longest: LongestStreak?)

/**
 * The current streak and the longest of the [STREAK_MAX_DAYS] up to [date], from one read of
 * the whole year: the longest needs every day anyway, so the current run is taken from the same
 * days rather than read again. The shown day counts as [currentStreak] counts it, and today
 * short of the goal is left out of the longest as it is left out of the current run.
 */
suspend fun streakSummary(
    goal: Double,
    date: LocalDate,
    dayValue: Double?,
    daily: DailyValues,
    today: LocalDate = LocalDate.now(),
): StreakSummary {
    val running = date == today
    val start = date.minusDays(STREAK_MAX_DAYS.toLong())
    val values = daily(start, date)
    val before = List(STREAK_MAX_DAYS) { start.plusDays(it.toLong()).let { day -> day to values(day) } }
    val shown = dayValue?.takeUnless { running && it < goal }
    val current = streakStart(dayValue, goal, running)
        ?.continued(before.reversed().map { it.second }, goal)?.count ?: 0
    return StreakSummary(current, longestStreak(before + (date to shown), goal))
}

/** Days read per request when counting back. */
const val STREAK_CHUNK_DAYS = 30

/** The longest run of days without data a streak is carried across. */
const val STREAK_MAX_GAP_DAYS = 30

/** How far back a streak is counted: a year, as far as the chart reaches. */
const val STREAK_MAX_DAYS = 365
