package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.dailyReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyReportTest {
    private val first = LocalDate.of(2026, 9, 1)

    @Test
    fun `a summed type totals its days and keeps empty days empty`() {
        val values = mapOf(first to 4_000.0, first.plusDays(2) to 10_000.0)
        val report = dailyReport(first, first.plusDays(2), values, averaged = false)

        assertEquals(14_000.0, report.overall!!, 1e-9)
        assertEquals(7_000.0, report.stats!!.mean, 1e-9)
        assertNull(report.days[1].second)
        assertEquals(first, report.lowest)
        assertEquals(first.plusDays(2), report.highest)
        assertTrue(report.weeks.isEmpty())
    }

    @Test
    fun `an averaged type's figure is the days' mean`() {
        val values = mapOf(first to 50.0, first.plusDays(1) to 54.0)
        assertEquals(52.0, dailyReport(first, first.plusDays(1), values, averaged = true).overall!!, 1e-9)
    }

    @Test
    fun `a long period is tabled by week from its Monday`() {
        val last = first.plusDays(89)
        val values = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.associateWith { 1.0 }
        val report = dailyReport(first, last, values, averaged = false)

        // 1 September 2026 is a Tuesday: its week starts on 31 August.
        assertEquals(LocalDate.of(2026, 8, 31), report.weeks.first().start)
        assertEquals(6, report.weeks.first().stats.count)
    }

    @Test
    fun `nothing recorded is no figure`() {
        val report = dailyReport(first, first.plusDays(6), emptyMap(), averaged = false)
        assertNull(report.overall)
        assertNull(report.stats)
    }
}
