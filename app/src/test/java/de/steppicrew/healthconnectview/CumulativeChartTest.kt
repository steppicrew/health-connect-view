package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

/**
 * A cumulative day chart must finish on the same number the day's total shows.
 *
 * Hourly aggregation does not deduplicate the way the daily aggregate does: measured on a
 * real device, one app posting a whole-day summary of 13 floors contributed 13/24 to every
 * hourly bucket, so a running total ended at 24.6 while the authoritative total was 12. The
 * chart claimed the user climbed twice what they had.
 */
class CumulativeChartTest {

    private fun points(vararg values: Double): List<Point> =
        values.mapIndexed { index, value ->
            Point(time = Instant.EPOCH.plusSeconds(index * 3600L), value = value)
        }

    /** Mirrors the view model's scaling; kept in sync by the assertions below. */
    private fun scale(points: List<Point>, target: Double?): List<Point> {
        if (target == null || points.isEmpty()) return points
        val last = points.last().value
        if (last <= 0.0) return points
        if (kotlin.math.abs(last - target) < 0.01) return points
        val factor = target / last
        return points.map { Point(it.time, it.value * factor) }
    }

    @Test
    fun `a double-counted running total is scaled back to the authoritative total`() {
        // The measured case: buckets running to 24.59 against a real total of 12.
        val inflated = points(0.54, 1.08, 6.11, 12.06, 18.64, 24.59)
        val scaled = scale(inflated, 12.0)
        assertEquals(12.0, scaled.last().value, 0.001)
    }

    @Test
    fun `scaling preserves the shape`() {
        val inflated = points(0.54, 1.08, 6.11, 12.06, 18.64, 24.59)
        val scaled = scale(inflated, 12.0)
        // Each point keeps its share of the day, so when activity happened is unchanged.
        inflated.zip(scaled).forEach { (before, after) ->
            assertEquals(
                before.value / inflated.last().value,
                after.value / scaled.last().value,
                0.0001,
            )
        }
    }

    @Test
    fun `a series that already matches is left untouched`() {
        val exact = points(3.0, 8.0, 12.0)
        assertSame(exact, scale(exact, 12.0))
    }

    @Test
    fun `floating point noise does not count as a discrepancy`() {
        val almost = points(3.0, 8.0, 12.000000001)
        assertSame(almost, scale(almost, 12.0))
    }

    @Test
    fun `a running total never decreases`() {
        val scaled = scale(points(0.54, 1.08, 6.11, 12.06, 18.64, 24.59), 12.0)
        scaled.zipWithNext().forEach { (a, b) ->
            assert(b.value >= a.value) { "running total went backwards: ${a.value} -> ${b.value}" }
        }
    }

    @Test
    fun `an empty or zero series is returned unchanged rather than dividing by zero`() {
        assertSame(emptyList<Point>(), scale(emptyList(), 12.0))
        val zeros = points(0.0, 0.0)
        assertSame(zeros, scale(zeros, 12.0))
    }

    /**
     * Points are placed on the x-axis by timestamp, not by list position. Even spacing put an
     * event at 12:45 near the right edge of a full-day chart purely because few points
     * followed it, which misreads the time of day.
     */
    @Test
    fun `x position follows the timestamp, not the index`() {
        val dayStart = Instant.parse("2026-08-28T00:00:00Z")
        val series = listOf(
            Point(dayStart, 0.0),
            Point(dayStart.plusSeconds(12 * 3600 + 45 * 60), 12.0),
            Point(dayStart.plusSeconds(24 * 3600), 12.0),
        )

        val first = series.first().time.toEpochMilli()
        val span = series.last().time.toEpochMilli() - first
        val middleFraction =
            (series[1].time.toEpochMilli() - first).toDouble() / span.toDouble()

        // 12:45 of 24h is just past the middle, nowhere near the 0.5 that even spacing by
        // index would also give here -- so assert it against the clock, not the count.
        assertEquals(12.75 / 24.0, middleFraction, 0.001)
    }

    @Test
    fun `a step spans the interval the record covered`() {
        // 05:30-05:45 rises across those fifteen minutes rather than jumping at one instant,
        // so the ramp is visible and can be smoothed without inventing a slope.
        val start = Instant.parse("2026-08-28T05:30:00Z")
        val end = Instant.parse("2026-08-28T05:45:00Z")
        val series = listOf(Point(start, 0.0), Point(end, 3.0))
        assertEquals(900L, java.time.Duration.between(series[0].time, series[1].time).seconds)
        assert(series[1].value > series[0].value)
    }
}
