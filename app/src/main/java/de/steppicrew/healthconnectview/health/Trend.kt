package de.steppicrew.healthconnectview.health

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Whether the recent week sits above, below or level with the month before it.
 *
 * Direction only, never a judgement: a rising resting heart rate and rising steps point the
 * same way and mean opposite things, so neither is drawn as good or bad.
 */
enum class Trend { UP, FLAT, DOWN }

/**
 * Compares the mean of the last [SHORT_DAYS] of [daily] with the mean of all of it.
 *
 * [daily] is one value per complete day, oldest first, ending the day *before* the one on
 * screen: today is always partial, and a step count at ten in the morning would otherwise
 * point down every day. A null is a day with nothing recorded and is left out, never counted
 * as zero -- the same rule as the tile's own dash.
 *
 * "Flat" is a difference smaller than half the ordinary day-to-day spread, which scales with
 * each metric by itself: a kilo on a steady weight shows, a thousand steps on a restless step
 * count does not. With too few days on either side there is no trend at all rather than a
 * confident arrow drawn from three readings.
 */
fun trendOf(daily: List<Double?>): Trend? {
    val long = daily.filterNotNull()
    val short = daily.takeLast(SHORT_DAYS).filterNotNull()
    if (long.size < MIN_LONG_DAYS || short.size < MIN_SHORT_DAYS) return null

    val longMean = long.average()
    val shortMean = short.average()
    val spread = sqrt(long.sumOf { (it - longMean) * (it - longMean) } / long.size)
    val difference = shortMean - longMean
    return when {
        abs(difference) <= spread * FLAT_FRACTION -> Trend.FLAT
        difference > 0 -> Trend.UP
        else -> Trend.DOWN
    }
}

/** Days in the comparison window, ending the day before the one shown. */
const val TREND_DAYS = 30
const val SHORT_DAYS = 7
private const val MIN_LONG_DAYS = 15
private const val MIN_SHORT_DAYS = 4
private const val FLAT_FRACTION = 0.5
