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
 * The streak ending on [date], whose own value is [dayValue] -- the tile's figure, so the ring
 * and the count agree about the day on screen.
 *
 * Today is still running: short of the goal it breaks nothing yet, and the streak is the one
 * leading up to it. Any earlier day short of the goal has no streak at all.
 *
 * The days before come from daily buckets, read back [STREAK_CHUNK_DAYS] at a time until one
 * breaks the run or [STREAK_MAX_DAYS] are covered. Past 30 days that needs the history
 * permission; without it the platform returns nothing older, and those days read as gaps, so
 * the count stops growing rather than going wrong.
 */
suspend fun HealthRepository.goalStreak(
    metric: AggregateMetric<*>,
    goal: Double,
    date: LocalDate,
    dayValue: Double?,
    origins: Set<DataOrigin>,
    today: LocalDate = LocalDate.now(),
): Int {
    var streak = streakStart(dayValue, goal, running = date == today) ?: return 0
    var end = date
    var covered = 0
    while (!streak.broken && covered < STREAK_MAX_DAYS) {
        val days = minOf(STREAK_CHUNK_DAYS, STREAK_MAX_DAYS - covered)
        val start = end.minusDays(days.toLong())
        val byDay = bucketedTotals(
            metric,
            TimeRangeFilter.between(start.atStartOfDay(), end.atStartOfDay()),
            Period.ofDays(1),
            origins,
        ).associate { bucket ->
            bucket.startTime.toLocalDate() to bucket.result[metric]?.let { numericAggregate(it, metric) }
        }
        streak = streak.continued(List(days) { byDay[end.minusDays(it + 1L)] }, goal)
        end = start
        covered += days
    }
    return streak.count
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

/** Days read per request when counting back. */
const val STREAK_CHUNK_DAYS = 30

/** The longest run of days without data a streak is carried across. */
const val STREAK_MAX_GAP_DAYS = 30

/** How far back a streak is counted: a year, as far as the chart reaches. */
const val STREAK_MAX_DAYS = 365
