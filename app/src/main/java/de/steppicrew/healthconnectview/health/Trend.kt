package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period
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
 * A trend with the two averages behind it.
 *
 * The arrow alone was misread on the phone: floors pointed up on a day with 4 climbed after a
 * day with 10, because it compares a week with a month, not today with yesterday. The numbers
 * are what make that visible, so they travel with the direction.
 */
data class TrendResult(
    val direction: Trend,
    /** Mean over the recorded days of the last [SHORT_DAYS]. */
    val recent: Double,
    /** Mean over the recorded days of all [TREND_DAYS]. */
    val baseline: Double,
)

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
fun trendOf(daily: List<Double?>): TrendResult? {
    val long = daily.filterNotNull()
    val short = daily.takeLast(SHORT_DAYS).filterNotNull()
    if (long.size < MIN_LONG_DAYS || short.size < MIN_SHORT_DAYS) return null

    val longMean = long.average()
    val shortMean = short.average()
    val spread = sqrt(long.sumOf { (it - longMean) * (it - longMean) } / long.size)
    val difference = shortMean - longMean
    val direction = when {
        abs(difference) <= spread * FLAT_FRACTION -> Trend.FLAT
        difference > 0 -> Trend.UP
        else -> Trend.DOWN
    }
    return TrendResult(direction, recent = shortMean, baseline = longMean)
}

/**
 * The trend leading up to [date], from one aggregate call: daily buckets for the [TREND_DAYS]
 * complete days before it. The day itself is left out because today is always partial. Days
 * are placed by their own date, so a day the platform returns no bucket for stays a gap.
 * Shared by the tile and its full-screen view, so the two cannot disagree.
 */
suspend fun HealthRepository.trendBefore(
    metric: AggregateMetric<*>,
    date: LocalDate,
    origins: Set<DataOrigin>,
): TrendResult? {
    val start = date.minusDays(TREND_DAYS.toLong())
    val byDay = bucketedTotals(
        metric,
        TimeRangeFilter.between(start.atStartOfDay(), date.atStartOfDay()),
        Period.ofDays(1),
        origins,
    ).associate { bucket ->
        bucket.startTime.toLocalDate() to bucket.result[metric]?.let(::numericAggregate)
    }
    return trendOf(List(TREND_DAYS) { byDay[start.plusDays(it.toLong())] })
}

/** Days in the comparison window, ending the day before the one shown. */
const val TREND_DAYS = 30
const val SHORT_DAYS = 7
private const val MIN_LONG_DAYS = 15
private const val MIN_SHORT_DAYS = 4
private const val FLAT_FRACTION = 0.5
