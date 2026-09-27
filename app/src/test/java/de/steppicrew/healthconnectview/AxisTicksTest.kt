package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.ui.components.calendarTicks
import de.steppicrew.healthconnectview.ui.components.maxZoomFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Across days the axis is a calendar ruler over the *visible* window, so zooming makes it
 * finer. It used to be ticks snapped to points of the whole series, only stretched by the
 * zoom: zoomed between two of them there was no label left to say where the chart was.
 */
class AxisTicksTest {

    private val zone = ZoneOffset.UTC
    private fun day(d: LocalDate) = d.atStartOfDay(zone).toInstant()
    private val sep1 = LocalDate.of(2026, 9, 1)

    @Test
    fun `a week is ticked by day`() {
        val ticks = calendarTicks(day(sep1), day(sep1.plusDays(3)), zone)
        assertEquals(4, ticks.size)
        assertEquals(day(sep1), ticks.first().time)
    }

    @Test
    fun `four weeks are ticked on Mondays`() {
        val ticks = calendarTicks(day(sep1), day(sep1.plusDays(27)), zone)
        assertTrue(ticks.isNotEmpty())
        ticks.forEach { assertEquals(DayOfWeek.MONDAY, it.time.atZone(zone).dayOfWeek) }
    }

    @Test
    fun `a year is ticked on the first of months`() {
        val ticks = calendarTicks(day(sep1.minusYears(1)), day(sep1), zone)
        assertTrue(ticks.size in 3..6)
        ticks.forEach { assertEquals(1, it.time.atZone(zone).dayOfMonth) }
    }

    @Test
    fun `zooming into a year ends at single days`() {
        // A tenth of a year, as a pinch into the year view leaves on screen.
        val ticks = calendarTicks(day(sep1), day(sep1.plusDays(4)), zone)
        assertTrue("got ${ticks.size}", ticks.size >= 3)
    }

    @Test
    fun `ticks sit where their time falls and in order`() {
        val start = day(sep1).plusSeconds(6 * 3600)
        val end = day(sep1.plusDays(4)).plusSeconds(6 * 3600)
        val ticks = calendarTicks(start, end, zone)
        ticks.forEach { assertTrue(it.fraction in 0f..1f) }
        assertEquals(ticks.sortedBy { it.fraction }, ticks)
        val expected = 18f / 96f
        assertEquals(expected, ticks.first().fraction, 1e-4f)
    }

    @Test
    fun `an empty window has no ticks`() {
        assertTrue(calendarTicks(day(sep1), day(sep1), zone).isEmpty())
    }

    @Test
    fun `zoom stops with four points on screen`() {
        // A week of bars: seven evenly spaced points, four of them fill half the width.
        val week = (0..6).map { it / 6f }
        assertEquals(2f, maxZoomFor(week), 1e-4f)
    }

    @Test
    fun `a dense day keeps the full zoom range`() {
        val day = (0..2000).map { it / 2000f }
        assertEquals(24f, maxZoomFor(day), 1e-4f)
    }

    @Test
    fun `three points or fewer cannot be zoomed`() {
        assertEquals(1f, maxZoomFor(listOf(0f, 0.5f, 1f)), 1e-4f)
    }
}
