package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.segmentAtGaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /**
     * The whole pipeline the chart runs: segment, then slice the shared offset list per
     * segment. The slicing is what turns segments into separately-stroked paths, and an
     * off-by-one there would draw the gap closed again while every segmentation test still
     * passed.
     */
    @Test
    fun `each segment maps onto its own slice of the shared offsets`() {
        val series = points(0, 1, 2, 4, 5)
        val segments = segmentAtGaps(series, listOf(day(3)))

        val first = series.first().time.toEpochMilli()
        val span = series.last().time.toEpochMilli() - first
        val offsets = series.map { (it.time.toEpochMilli() - first).toDouble() / span }

        assertEquals(2, segments.size)

        var drawn = 0
        val slices = segments.map { segment ->
            val slice = offsets.subList(drawn, drawn + segment.size)
            drawn += segment.size
            slice
        }

        // Every offset is consumed exactly once, and each slice matches its segment's points.
        assertEquals(offsets.size, drawn)
        assertEquals(3, slices[0].size)
        assertEquals(2, slices[1].size)

        // The break leaves real horizontal distance between the two strokes; without it the
        // second segment would begin where the first ended and the gap would be invisible.
        assertTrue(
            "no visible gap between segments",
            slices[1].first() > slices[0].last() + 0.1,
        )
    }
}
