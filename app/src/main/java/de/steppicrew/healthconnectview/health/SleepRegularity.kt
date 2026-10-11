package de.steppicrew.healthconnectview.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * When the nights began and ended, described rather than scored: for each, the middle time and
 * the times the middle half of the nights fell between. A wide spread is shown, not graded.
 */
data class SleepRegularity(val nights: Int, val start: ClockSpread, val end: ClockSpread)

/**
 * Clock times as minutes of the day, 0 until [MINUTES_PER_DAY]: [usual] the median, [early]
 * and [late] the quarter marks, [all] every night's own -- in the same order for a night's start
 * and its end, so the two pair up again.
 */
data class ClockSpread(val usual: Int, val early: Int, val late: Int, val all: List<Int>)

/**
 * The regularity of [nights] -- each morning's night, as [nightsByMorning] picks it. Null with
 * fewer than [MIN_REGULARITY_NIGHTS]: four nights have no middle half worth naming.
 */
fun sleepRegularity(nights: Map<LocalDate, Session>, zone: ZoneId): SleepRegularity? {
    if (nights.size < MIN_REGULARITY_NIGHTS) return null
    fun minutes(of: (Session) -> Instant) = nights.values.map {
        val time = of(it).atZone(zone).toLocalTime()
        time.hour * MINUTES_PER_HOUR + time.minute
    }
    return SleepRegularity(nights.size, clockSpread(minutes { it.start }), clockSpread(minutes { it.end }))
}

/**
 * The median and quarter marks of clock [times], read around midnight: each time as its
 * distance from their circular mean, so 23:30 and 00:30 are an hour apart, not 23 hours, and a
 * night worker's 09:00 starts are as regular as anyone's 23:00.
 */
fun clockSpread(times: List<Int>): ClockSpread {
    val angles = times.map { it * 2 * PI / MINUTES_PER_DAY }
    val mean = atan2(angles.sumOf { sin(it) }, angles.sumOf { cos(it) })
    val centre = (mean * MINUTES_PER_DAY / (2 * PI)).roundToInt().mod(MINUTES_PER_DAY)
    val offsets = times.map { (it - centre + MINUTES_PER_DAY / 2).mod(MINUTES_PER_DAY) - MINUTES_PER_DAY / 2 }.sorted()
    fun at(share: Double) = (centre + quantile(offsets, share).roundToInt()).mod(MINUTES_PER_DAY)
    return ClockSpread(usual = at(MEDIAN), early = at(LOWER_QUARTER), late = at(UPPER_QUARTER), all = times)
}

/** The value [share] of the way through [sorted], between neighbours where it falls between. */
private fun quantile(sorted: List<Int>, share: Double): Double {
    val position = share * sorted.lastIndex
    val below = position.toInt()
    val above = minOf(below + 1, sorted.lastIndex)
    return sorted[below] + (sorted[above] - sorted[below]) * (position - below)
}

const val MINUTES_PER_DAY = 1440
const val MIN_REGULARITY_NIGHTS = 5
private const val MINUTES_PER_HOUR = 60
private const val MEDIAN = 0.5
private const val LOWER_QUARTER = 0.25
private const val UPPER_QUARTER = 0.75
