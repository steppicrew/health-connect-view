package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.export.ExportPeriod
import de.steppicrew.healthconnectview.export.ExportPeriod.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The period an export covers, chosen when exporting: "a report for August 2026". */
class ExportPeriodTest {

    private val today = LocalDate.of(2026, 9, 27)
    private val shown = ExportPeriod(LocalDate.of(2026, 8, 31), today)

    @Test
    fun `last month is the whole calendar month before`() {
        assertEquals(ExportPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)), ExportPeriod.of(Preset.LAST_MONTH, shown, today))
    }

    @Test
    fun `last month in January is December of the year before`() {
        val jan = LocalDate.of(2027, 1, 10)
        assertEquals(ExportPeriod(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)), ExportPeriod.of(Preset.LAST_MONTH, shown, jan))
    }

    @Test
    fun `this month and this year end today`() {
        assertEquals(ExportPeriod(LocalDate.of(2026, 9, 1), today), ExportPeriod.of(Preset.THIS_MONTH, shown, today))
        assertEquals(ExportPeriod(LocalDate.of(2026, 1, 1), today), ExportPeriod.of(Preset.THIS_YEAR, shown, today))
    }

    @Test
    fun `last year is the whole calendar year`() {
        assertEquals(ExportPeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)), ExportPeriod.of(Preset.LAST_YEAR, shown, today))
    }

    @Test
    fun `the file tag names a month, a year, a day or both ends`() {
        assertEquals("2026-08", ExportPeriod.of(Preset.LAST_MONTH, shown, today).fileTag)
        assertEquals("2025", ExportPeriod.of(Preset.LAST_YEAR, shown, today).fileTag)
        assertEquals("2026-09-27", ExportPeriod(today, today).fileTag)
        assertEquals("2026-08-31_2026-09-27", shown.fileTag)
        assertEquals("2026-09-01_2026-09-27", ExportPeriod.of(Preset.THIS_MONTH, shown, today).fileTag)
    }

    @Test
    fun `a leap February is a whole month`() {
        assertEquals("2028-02", ExportPeriod(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)).fileTag)
    }

    @Test
    fun `only a period reaching past 30 days needs the history permission`() {
        assertFalse(ExportPeriod(today.minusDays(30), today).needsHistory(today))
        assertTrue(ExportPeriod(today.minusDays(31), today).needsHistory(today))
    }
}
