package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.ui.components.horizontalFractions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * A day's chart spans the whole day, whatever hours the data happens to cover.
 *
 * Without a fixed extent the plot ends at the last reading, so midday sits wherever the data
 * stops: the axis means something different at 09:00 than it will at 21:00, and the hour you
 * are looking for slides across the screen as the day fills in.
 */
class ChartExtentTest {

    private val midnight: Instant = Instant.parse("2026-08-28T00:00:00Z")
    private val nextMidnight: Instant = midnight.plusSeconds(24 * 3600)
    private val day = midnight..nextMidnight

    private fun at(hour: Long): Instant = midnight.plusSeconds(hour * 3600)

    private fun series(vararg hours: Long): List<Point> =
        hours.map { Point(at(it), it.toDouble()) }

    @Test
    fun `midday sits in the middle of a day-long extent`() {
        // A morning's worth of data, as a day in progress would have.
        val fractions = horizontalFractions(series(0, 3, 6, 9, 12), day)
        assertEquals(0.5f, fractions.last(), 0.0001f)
    }

    /**
     * The point of fixing the extent: the same hour lands in the same place whether the day
     * has just begun or is nearly over.
     */
    @Test
    fun `an hour lands in the same place however much of the day has been recorded`() {
        val earlyDay = horizontalFractions(series(0, 6), day)
        val fullDay = horizontalFractions(series(0, 6, 12, 18, 23), day)

        assertEquals(earlyDay[1], fullDay[1], 0.0001f)
    }

    /** Without an extent the series still spans the plot, which is right for a week. */
    @Test
    fun `without an extent the series spans the whole width`() {
        val fractions = horizontalFractions(series(0, 6, 12))
        assertEquals(0f, fractions.first(), 0.0001f)
        assertEquals(1f, fractions.last(), 0.0001f)
    }

    /**
     * The line must stop where the data stops. Stretching a morning across the full day would
     * invent readings for hours that have not happened yet.
     */
    @Test
    fun `a morning of data does not reach the end of the day`() {
        val fractions = horizontalFractions(series(0, 3, 6, 9), day)
        assertTrue("a partial day reached the right edge", fractions.last() < 0.5f)
    }

    /**
     * A reading can fall outside a fixed extent -- a sleep session running past midnight is
     * the usual case -- and a fraction outside 0..1 would draw off the plot.
     */
    @Test
    fun `readings outside the extent are clamped onto the plot`() {
        val spillingOver = listOf(
            Point(midnight.minusSeconds(3600), 1.0),
            Point(at(12), 2.0),
            Point(nextMidnight.plusSeconds(3600), 3.0),
        )
        val fractions = horizontalFractions(spillingOver, day)

        assertEquals(0f, fractions.first(), 0.0001f)
        assertEquals(1f, fractions.last(), 0.0001f)
    }
}
