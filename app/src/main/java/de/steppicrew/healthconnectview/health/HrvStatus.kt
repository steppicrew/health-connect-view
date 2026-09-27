package de.steppicrew.healthconnectview.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One heart rate variability reading, reduced to what the nightly value needs. */
data class HrvReading(val time: Instant, val millis: Double, val origin: String)

/** A night's HRV: the mean of the readings taken during it, credited to the morning it ended. */
data class HrvNight(val date: LocalDate, val mean: Double)

/** Where a week's mean sits against the usual range. */
enum class HrvStanding { BELOW, WITHIN, ABOVE }

/**
 * The week ending on [date] against the weeks before it.
 *
 * [weekMean] is null with fewer than [MIN_WEEK_NIGHTS] nights in the week; the usual range is
 * null with fewer than [MIN_BASELINE_NIGHTS] nights behind it, and [standing] with it.
 */
data class HrvDay(
    val date: LocalDate,
    val weekMean: Double?,
    val usualLow: Double?,
    val usualHigh: Double?,
) {
    val standing: HrvStanding?
        get() {
            val mean = weekMean ?: return null
            val low = usualLow ?: return null
            val high = usualHigh ?: return null
            return when {
                mean < low -> HrvStanding.BELOW
                mean > high -> HrvStanding.ABOVE
                else -> HrvStanding.WITHIN
            }
        }
}

/**
 * Each night's mean HRV, from the readings taken inside that night's sleep.
 *
 * Health Connect holds HRV only as raw readings -- about one every five minutes through the
 * night on the phone, from Health Sync copying Garmin -- and no aggregate at all, so the
 * nightly figure is computed here. Only readings inside a sleep session count: the same
 * writer keeps sampling after waking, and a morning's readings are a different state.
 *
 * A plain mean is safe because a reading is a moment, not an interval, so nothing overlaps.
 * Two writers copying the same night would still count it twice, so one writer is kept per
 * night -- the one with the most readings -- as sleep stages do.
 */
fun nightlyHrv(readings: List<HrvReading>, sleeps: List<Session>, zone: ZoneId): List<HrvNight> {
    val sorted = readings.sortedBy { it.time }
    return sleeps
        .filter { it.kind == Session.Kind.SLEEP }
        .groupBy { it.end.atZone(zone).toLocalDate() }
        .mapNotNull { (date, nights) ->
            val inside = nights.flatMap { night ->
                sorted.filter { !it.time.isBefore(night.start) && !it.time.isAfter(night.end) }
            }.distinct()
            val writer = inside.groupBy { it.origin }.maxByOrNull { it.value.size }?.value
                ?: return@mapNotNull null
            HrvNight(date, writer.map { it.millis }.average())
        }
        .sortedBy { it.date }
}

/**
 * The 7-day mean of nightly values for each of [dates], against the usual range.
 *
 * The usual range is the middle half -- 25th to 75th percentile -- of the nights in the
 * [BASELINE_DAYS] before the week, so the week being judged never counts towards its own
 * yardstick. The middle half rather than a mean and a standard deviation: one night of
 * illness would widen a deviation for a month, and "half your nights fell in this range" is a
 * sentence a reader can check against the chart. It is this app's range, not Garmin's, whose
 * method is not published.
 */
fun hrvDays(nights: List<HrvNight>, dates: List<LocalDate>): List<HrvDay> {
    val byDate = nights.associate { it.date to it.mean }
    return dates.map { date ->
        val week = (0L until WEEK_DAYS).mapNotNull { byDate[date.minusDays(it)] }
        val baseline = (WEEK_DAYS until WEEK_DAYS + BASELINE_DAYS)
            .mapNotNull { byDate[date.minusDays(it)] }
            .sorted()
        val enoughBaseline = baseline.size >= MIN_BASELINE_NIGHTS
        HrvDay(
            date = date,
            weekMean = week.takeIf { it.size >= MIN_WEEK_NIGHTS }?.average(),
            usualLow = if (enoughBaseline) baseline.percentile(LOW_QUANTILE) else null,
            usualHigh = if (enoughBaseline) baseline.percentile(HIGH_QUANTILE) else null,
        )
    }
}

/** Linear interpolation between ranks, on an already sorted list. */
private fun List<Double>.percentile(quantile: Double): Double {
    val position = quantile * (size - 1)
    val lower = position.toInt()
    val upper = minOf(lower + 1, size - 1)
    return this[lower] + (this[upper] - this[lower]) * (position - lower)
}

private const val WEEK_DAYS = 7L

/** Four weeks, as Garmin's comparison uses. */
private const val BASELINE_DAYS = 28L

/** Fewer than this in a week and its mean is one or two nights, not a week. */
private const val MIN_WEEK_NIGHTS = 3

/** Half the baseline: a range from a handful of nights would move with every one of them. */
private const val MIN_BASELINE_NIGHTS = 14

private const val LOW_QUANTILE = 0.25
private const val HIGH_QUANTILE = 0.75

/** How many days before the first shown day the readings have to reach back. */
const val HRV_LOOKBACK_DAYS: Long = WEEK_DAYS + BASELINE_DAYS
