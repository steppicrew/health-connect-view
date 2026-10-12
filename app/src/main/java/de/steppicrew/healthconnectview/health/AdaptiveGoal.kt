package de.steppicrew.healthconnectview.health

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * A daily goal that follows the days before it: raised after days above it, lowered after
 * days below, by a share of the gap.
 *
 * The rule was read off a year-old record of the owner's watch, 32 days of a hiking holiday
 * with the goal it set each day (12.10.2026), and reproduces 27 of its 31 changes to within
 * ten steps: a tenth of the gap on the first change in a direction, a fifth from the second
 * in the same direction on, rises capped at [maxRise] a day, falls not capped, each change
 * rounded up to [ROUNDING]. The four it misses are a reset the watch made on its own and days
 * it left unchanged though they held data -- presumably totals that arrived late.
 *
 * Nothing is stored: the goals are replayed from [seed], the user's fixed goal, over the days
 * before the one asked about. A tenth to a fifth of the gap a day forgets the seed within a
 * few weeks, so a replay starting [WARMUP_DAYS] before the first day it answers for gives
 * the same goals whatever day it started on, to within the rounding.
 */
data class AdaptiveGoalRule(
    /** The largest rise in one day, in the metric's stored unit. */
    val maxRise: Double,
    /** The lowest the goal may fall to; null for none beyond [MIN_GOAL]. */
    val floor: Double? = null,
)

/**
 * The goal in force on each day from [first] to [last], replayed from [seed] over the days
 * from [replayFrom] on. A day's goal is set by the days before it, so the shown day's own
 * total never moves its goal.
 *
 * A day without data changes nothing, neither the goal nor the direction remembered: a watch
 * left on the charger says nothing about whether the goal suited the day.
 */
fun adaptiveGoals(
    seed: Double,
    rule: AdaptiveGoalRule,
    values: (LocalDate) -> Double?,
    replayFrom: LocalDate,
    first: LocalDate,
    last: LocalDate,
): Map<LocalDate, Double> {
    val lowest = maxOf(rule.floor ?: 0.0, MIN_GOAL)
    var goal = rounded(seed).coerceAtLeast(lowest)
    var direction = 0
    val goals = mutableMapOf<LocalDate, Double>()
    var day = replayFrom
    while (!day.isAfter(last)) {
        if (!day.isBefore(first)) goals[day] = goal
        val value = values(day)
        if (value != null) {
            val gap = value - goal
            val towards = gap.compareTo(0.0)
            if (towards != 0) {
                val share = if (towards == direction) SECOND_SHARE else FIRST_SHARE
                val change = (share * gap).coerceAtMost(rule.maxRise)
                goal = (goal + roundedUp(change)).coerceAtLeast(lowest)
                direction = towards
            }
        }
        day = day.plusDays(1)
    }
    return goals
}

private fun rounded(value: Double): Double = (value / ROUNDING).roundToLong() * ROUNDING

/**
 * A change rounded up to the next [ROUNDING], as the watch's were: all 13 of its rises fit and
 * 7 of its 9 falls, where rounding to the nearest left the goal 20 off after a week.
 */
private fun roundedUp(change: Double): Double = ceil(change / ROUNDING) * ROUNDING

/** The share of the gap on the first change in a direction, and on every later one. */
private const val FIRST_SHARE = 0.1
private const val SECOND_SHARE = 0.2

/** Goals move in steps of ten, as the watch's did. */
private const val ROUNDING = 10.0

/** A goal never falls to nothing, floor or not. */
private const val MIN_GOAL = 10.0

/** Days replayed before the first one answered for, to forget the seed. */
const val WARMUP_DAYS = 56L

/** The steps goal's largest rise in a day, as the watch's. */
const val STEPS_MAX_RISE = 2_000.0

/**
 * The goal on each day up to [date], with the daily values it was replayed from.
 *
 * [daily] serves the replayed days from memory, so a streak counted against [goal] reads them
 * once: an adaptive goal needs the year before the shown day anyway, which is as far back as a
 * streak counts.
 */
class ResolvedGoal(val goal: DayGoal, val daily: DailyValues)

/**
 * A fixed goal as itself; an adaptive one ([rule] not null) replayed from [fixed] over the
 * [STREAK_MAX_DAYS] before [date] and [WARMUP_DAYS] more, read through [daily] in one request.
 * The shown day's own value is not read: a day's goal comes from the days before it.
 */
suspend fun resolveGoal(fixed: Double, rule: AdaptiveGoalRule?, date: LocalDate, daily: DailyValues): ResolvedGoal {
    if (rule == null) return ResolvedGoal({ fixed }, daily)
    val first = date.minusDays(STREAK_MAX_DAYS.toLong() + 1)
    val from = first.minusDays(WARMUP_DAYS)
    val values = daily(from, date)
    val goals = adaptiveGoals(fixed, rule, values, from, first, date)
    val cached: DailyValues = { start, end ->
        if (!start.isBefore(from) && !end.isAfter(date)) values else daily(start, end)
    }
    return ResolvedGoal({ day -> goals[day] ?: fixed }, cached)
}
