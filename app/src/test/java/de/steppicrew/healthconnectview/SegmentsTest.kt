package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.segmentAtGaps
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * A day with no recorded data must not be drawn through. The chart previously dropped empty
 * buckets and joined the surviving points, which drew a straight line across the gap and
 * claimed a value for a day that had none.
 */
class SegmentsTest {

    private val day0: Instant = Instant.parse("2026-08-24T00:00:00Z")
    private fun day(n: Long): Instant = day0.plusSeconds(n * 86_400)
    private fun points(vararg days: Long): List<Point> =
        days.map { Point(day(it), it.toDouble()) }

    @Test
    fun `a series with no gaps stays one segment`() {
        val series = points(0, 1, 2, 3)
        assertEquals(listOf(series), segmentAtGaps(series, emptyList()))
    }

    @Test
    fun `a gap splits the series in two`() {
        // Days 0, 1, then nothing on day 2, then days 3, 4.
        val series = points(0, 1, 3, 4)
        val segments = segmentAtGaps(series, listOf(day(2)))
        assertEquals(2, segments.size)
        assertEquals(points(0, 1), segments[0])
        assertEquals(points(3, 4), segments[1])
    }

    @Test
    fun `several gaps split into several segments`() {
        val series = points(0, 2, 4)
        val segments = segmentAtGaps(series, listOf(day(1), day(3)))
        assertEquals(3, segments.size)
        segments.forEach { assertEquals(1, it.size) }
    }

    @Test
    fun `a run of consecutive empty days is one break, not several`() {
        val series = points(0, 4)
        val segments = segmentAtGaps(series, listOf(day(1), day(2), day(3)))
        assertEquals(2, segments.size)
    }

    @Test
    fun `a gap before the first point breaks nothing`() {
        val series = points(2, 3)
        assertEquals(listOf(series), segmentAtGaps(series, listOf(day(0), day(1))))
    }

    @Test
    fun `a gap after the last point breaks nothing`() {
        val series = points(0, 1)
        assertEquals(listOf(series), segmentAtGaps(series, listOf(day(5))))
    }

    @Test
    fun `every point survives segmentation`() {
        val series = points(0, 2, 4, 6)
        val segments = segmentAtGaps(series, listOf(day(1), day(5)))
        assertEquals(series, segments.flatten())
    }

    @Test
    fun `an empty series yields no segments`() {
        assertEquals(emptyList<List<Point>>(), segmentAtGaps(emptyList(), listOf(day(1))))
    }
}
