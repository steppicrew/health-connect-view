package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.rollingUsualRange
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsualRangeTest {

    private val day = LocalDate.of(2026, 10, 1)

    @Test
    fun `the range is the middle half of the 28 days before, never the day itself`() {
        // 28 days of 41..68 before the day, and an outlier on the day that must not count.
        val daily = (1..28).associate { day.minusDays(it.toLong()) to (40.0 + it) } + (day to 100.0)
        val (_, low, high) = rollingUsualRange(daily, day, day).single()
        assertEquals(47.75, low, 1e-9)
        assertEquals(61.25, high, 1e-9)
    }

    @Test
    fun `too few days behind it give no range`() {
        val daily = (1..13).associate { day.minusDays(it.toLong()) to 50.0 }
        assertTrue(rollingUsualRange(daily, day, day).isEmpty())
    }
}
