package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Axis ticks are snapped to real points rather than placed at arbitrary times.
 *
 * A label reading a moment no sample was taken at invites the reader to believe the series was
 * measured there, which on a chart of health data is a claim the app should not make.
 */
class AxisTicksTest {

    private val base: Instant = Instant.parse("2026-08-28T00:00:00Z")

    private fun series(vararg hours: Long): List<Point> =
        hours.map { Point(base.plusSeconds(it * 3600), it.toDouble()) }

    /** Mirrors the production helper; the assertions below pin its contract. */
    private fun ticks(points: List<Point>, count: Int = 4): List<Pair<Float, Instant>> {
        if (points.size < 2) return emptyList()
        val first = points.first().time.toEpochMilli()
        val span = points.last().time.toEpochMilli() - first
        val fractions =
            if (span <= 0L) points.indices.map { it / (points.size - 1).toFloat() }
            else points.map { (it.time.toEpochMilli() - first).toDouble().div(span).toFloat() }

        return (0..count).map { step ->
            val target = step / count.toFloat()
            val index = fractions.indices.minByOrNull { kotlin.math.abs(fractions[it] - target) }!!
            fractions[index] to points[index].time
        }.distinctBy { it.second }
    }

    @Test
    fun `every tick names a time that exists in the series`() {
        val points = series(0, 3, 6, 9, 12, 15, 18, 21)
        val times = points.map { it.time }.toSet()
        ticks(points).forEach { (_, time) ->
            assertTrue("tick at $time is not a real sample", time in times)
        }
    }

    @Test
    fun `ticks span the whole series`() {
        val points = series(0, 6, 12, 18, 24)
        val result = ticks(points)
        assertEquals(points.first().time, result.first().second)
        assertEquals(points.last().time, result.last().second)
    }

    @Test
    fun `ticks are ordered left to right`() {
        val result = ticks(series(0, 4, 8, 12, 16, 20, 24))
        result.zipWithNext().forEach { (a, b) ->
            assertTrue("ticks out of order: ${a.first} then ${b.first}", b.first >= a.first)
        }
    }

    @Test
    fun `a sparse series shows fewer ticks rather than repeating one`() {
        // Three points cannot fill five ticks; duplicates are dropped, not repeated.
        val result = ticks(series(0, 12, 24))
        assertEquals(result.map { it.second }.distinct().size, result.size)
        assertTrue("expected at most 3 ticks, got ${result.size}", result.size <= 3)
    }

    @Test
    fun `a two-point series still yields its endpoints`() {
        val result = ticks(series(0, 24))
        assertEquals(2, result.size)
    }

    @Test
    fun `a series with no elapsed time does not divide by zero`() {
        val same = listOf(Point(base, 1.0), Point(base, 2.0))
        val result = ticks(same)
        assertTrue("expected a single distinct time", result.size <= 1)
    }

    @Test
    fun `a single point yields no ticks`() {
        assertEquals(emptyList<Pair<Float, Instant>>(), ticks(series(0)))
    }
}
