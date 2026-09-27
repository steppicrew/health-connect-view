package de.steppicrew.healthconnectview.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One bucket of readings reduced to what a chart draws: the mean, and the spread behind it. */
data class ReadingBucket(val start: LocalDate, val mean: Double, val low: Double, val high: Double, val count: Int)

/**
 * Readings reduced to one mean per bucket while they are paged, for a type Health Connect
 * cannot aggregate.
 *
 * Respiratory rate and oxygen saturation have no aggregate, so across days they were charted
 * reading by reading: 5,000 in four weeks drew a solid wall of strokes. A bucket's mean with
 * its low and high as a band is what the aggregated types already show.
 *
 * One writer per day, the one with the most readings that day, as for HRV's nights: two apps
 * copying the same watch would otherwise count every reading twice. A reading is a moment, so
 * a single writer's readings never overlap and a plain mean of them is safe.
 *
 * Only running sums are kept -- per day and writer, a count, a sum, a low and a high -- so a
 * year of hundreds of thousands of readings costs a few thousand numbers, not the readings.
 */
class DailyReadings(
    private val zone: ZoneId,
    /** The first day of the window; buckets are counted from it. */
    private val first: LocalDate,
    /** Days per bucket: 1 for a week or four, 7 for a year, as the aggregated charts use. */
    private val bucketDays: Int = 1,
) {
    private class Tally(var count: Int = 0, var sum: Double = 0.0, var low: Double = Double.MAX_VALUE, var high: Double = -Double.MAX_VALUE) {
        fun add(value: Double) {
            count++
            sum += value
            if (value < low) low = value
            if (value > high) high = value
        }
    }

    private val tallies = HashMap<LocalDate, HashMap<String, Tally>>()

    fun add(time: Instant, value: Double, origin: String) {
        val day = time.atZone(zone).toLocalDate()
        tallies.getOrPut(day) { HashMap() }.getOrPut(origin) { Tally() }.add(value)
    }

    /** Each day's chosen writer's mean, for a reference line built on daily values. */
    fun dailyMeans(): Map<LocalDate, Double> = chosen().mapValues { (_, tally) -> tally.sum / tally.count }

    /** One bucket per [bucketDays] from [first], in order; a bucket with no readings is absent. */
    fun buckets(): List<ReadingBucket> =
        chosen()
            .filterKeys { !it.isBefore(first) }
            .entries
            .groupBy { (day, _) -> first.plusDays(java.time.temporal.ChronoUnit.DAYS.between(first, day) / bucketDays * bucketDays) }
            .map { (start, days) ->
                val count = days.sumOf { it.value.count }
                ReadingBucket(
                    start = start,
                    mean = days.sumOf { it.value.sum } / count,
                    low = days.minOf { it.value.low },
                    high = days.maxOf { it.value.high },
                    count = count,
                )
            }
            .sortedBy { it.start }

    /** The mean of every counted reading from [first] on, weighted by how many each day had. */
    fun overallMean(): Double? {
        val days = chosen().filterKeys { !it.isBefore(first) }.values
        val count = days.sumOf { it.count }
        return if (count == 0) null else days.sumOf { it.sum } / count
    }

    private fun chosen(): Map<LocalDate, Tally> =
        tallies.mapValues { (_, writers) -> writers.values.maxBy { it.count } }
}
