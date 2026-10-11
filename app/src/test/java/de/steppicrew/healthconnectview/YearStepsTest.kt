package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.ui.dashboard.comparesYear
import de.steppicrew.healthconnectview.ui.dashboard.yearSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class YearStepsTest {

    private val today = LocalDate.of(2026, 10, 11)

    private fun back(span: Span, offset: Int) =
        ChronoUnit.DAYS.between(span.startDate(offset + yearSteps(span), today), span.startDate(offset, today))

    @Test
    fun `a week and four weeks go back 364 days, so weekdays line up`() {
        assertEquals(364, back(Span.WEEK, 0))
        assertEquals(364, back(Span.WEEK, 3))
        assertEquals(364, back(Span.MONTH, 0))
        assertEquals(364, back(Span.MONTH, 5))
    }

    @Test
    fun `a year goes back one calendar year`() {
        assertEquals(LocalDate.of(2024, 10, 12), Span.YEAR.startDate(yearSteps(Span.YEAR), today))
    }

    @Test
    fun `a single day has no year to compare`() {
        assertFalse(comparesYear(Span.DAY))
    }
}
