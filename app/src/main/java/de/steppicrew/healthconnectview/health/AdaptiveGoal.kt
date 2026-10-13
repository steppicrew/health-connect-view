package de.steppicrew.healthconnectview.health

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * A daily goal that follows the days before it: raised after days above it, lowered after
 * days below, by a share of the gap.
 *
 * The rule was read off a year-old record of the owner's watch, 32 days of a hiking holiday
 * with the goal it set each day (12.10.2026), and reproduces 27 of its 31 changes to within
 * ten steps: a tenth of the gap on the first change in a direction, a fifth from the second
 * in the same direction on, rises capped at [maxRise] a day ([cap]), falls not capped, each change
 * rounded up to [step]. The four it misses are a reset the watch made on its own and days
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
    /**
     * The largest rise as a share of the goal it starts from, where that is more than
     * [maxRise]; null where [maxRise] is the cap whatever the goal, as on the watch.
     */
    val maxRiseShare: Double? = null,
    /**
     * What goals move in, in the metric's stored unit: every change is rounded up to it, and
     * no goal falls below one of it, floor or not.
     */
    val step: Double,
    /** The lowest the goal may fall to; null for none beyond one [step]. */
    val floor: Double? = null,
) {
    /** The largest rise in one day from [goal]. */
    fun cap(goal: Double): Double = maxOf(maxRise, (maxRiseShare ?: 0.0) * goal)
}

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
    val lowest = maxOf(rule.floor ?: 0.0, rule.step)
    var goal = (rounded(seed / rule.step) * rule.step).coerceAtLeast(lowest)
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
                val change = (share * gap).coerceAtMost(rule.cap(goal))
                // Counted in whole steps, so a goal in hundredths of a kilometre stays on them.
                val steps = rounded(goal / rule.step) + roundedUp(change / rule.step)
                goal = (steps * rule.step).coerceAtLeast(lowest)
                direction = towards
            }
        }
        day = day.plusDays(1)
    }
    return goals
}

private fun rounded(steps: Double): Double = steps.roundToLong().toDouble()

/**
 * A change rounded up to the next whole step, as the watch's were: all 13 of its rises fit and
 * 7 of its 9 falls, where rounding to the nearest left the goal 20 off after a week. A change
 * of a whole number of steps stays that many, whatever the division left behind it.
 */
private fun roundedUp(steps: Double): Double {
    val whole = rounded(steps)
    return if (abs(steps - whole) < STEP_SLACK) whole else ceil(steps)
}

/** The share of the gap on the first change in a direction, and on every later one. */
private const val FIRST_SHARE = 0.1
private const val SECOND_SHARE = 0.2

/** How far from a whole number of steps a division's rounding error may leave a change. */
private const val STEP_SLACK = 1e-9

/** Days replayed before the first one answered for, to forget the seed. */
const val WARMUP_DAYS = 56L

/** The steps goal: rises of at most 2,000 a day, in steps of ten, both as the watch's. */
val STEPS_GOAL_RULE = AdaptiveGoalRule(maxRise = 2_000.0, step = 10.0)

/**
 * The distance goal, in kilometres. No watch record to read this one from. The cap is the
 * fifth of the default goal that 2,000 steps are, and matters more here, since distance counts
 * cycling too and one long ride is a gap no day on foot produces. It is a fifth of the goal
 * itself where that is more: people's days differ far more in distance than in steps, and on
 * the owner's phone (13.10.2026, weekdays of 16 to 41 km by bike, weekends of 1) a flat
 * kilometre a day could not win back in five weekdays what a weekend took off, so the goal
 * sat at 13 to 17 km, below nearly every weekday. Ten metres are about what
 * ten steps cover; a coarser step would hold the goal up, since a fall is rounded up too and
 * a day less than five steps' worth short of its goal lowers nothing -- half a kilometre, at a
 * tenth of one.
 */
val DISTANCE_GOAL_RULE = AdaptiveGoalRule(maxRise = 1.0, maxRiseShare = 0.2, step = 0.01)

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
