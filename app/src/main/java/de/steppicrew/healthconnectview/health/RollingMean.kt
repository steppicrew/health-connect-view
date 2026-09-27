package de.steppicrew.healthconnectview.health

import java.time.LocalDate

/**
 * The mean of the [days] daily values ending on each date from [first] to [last], inclusive.
 *
 * A reference line for a value that moves a few units from day to day around a level that
 * matters: a resting heart rate jumps by 5 bpm night to night, and a rise of a few beats over
 * the wearer's own level -- illness, poor recovery -- is invisible in that noise without one.
 *
 * Trailing, so each point uses only days up to its own, as the reader lived them. A date with
 * fewer than [minDays] values behind it gets none: a "four-week mean" of three days would move
 * with every one of them.
 */
fun rollingMean(
    daily: Map<LocalDate, Double>,
    first: LocalDate,
    last: LocalDate,
    days: Int = ROLLING_DAYS,
    minDays: Int = days / 2,
): List<Pair<LocalDate, Double>> =
    generateSequence(first) { it.plusDays(1) }
        .takeWhile { !it.isAfter(last) }
        .mapNotNull { date ->
            val values = (0L until days).mapNotNull { daily[date.minusDays(it)] }
            if (values.size < minDays) null else date to values.average()
        }
        .toList()

/** Four weeks: long enough to settle over a bad night, short enough to follow a season. */
const val ROLLING_DAYS = 28
