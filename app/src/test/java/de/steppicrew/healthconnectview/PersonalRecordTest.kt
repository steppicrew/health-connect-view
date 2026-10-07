package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.bestDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalRecordTest {

    private val day = LocalDate.of(2026, 10, 1)

    @Test
    fun `the highest day wins`() {
        val values = mapOf(day to 8_000.0, day.plusDays(1) to 24_512.0, day.plusDays(2) to 11_000.0)
        assertEquals(day.plusDays(1) to 24_512.0, bestDay(values, lowest = false))
    }

    @Test
    fun `the lowest day wins when lower is better`() {
        val values = mapOf(day to 48.0, day.plusDays(1) to 44.0, day.plusDays(2) to 51.0)
        assertEquals(day.plusDays(1) to 44.0, bestDay(values, lowest = true))
    }

    @Test
    fun `a tie goes to the latest day`() {
        val values = mapOf(day to 44.0, day.plusDays(5) to 44.0, day.plusDays(2) to 47.0)
        assertEquals(day.plusDays(5) to 44.0, bestDay(values, lowest = true))
        assertEquals(day.plusDays(5) to 44.0, bestDay(mapOf(day to 44.0, day.plusDays(5) to 44.0), lowest = false))
    }

    @Test
    fun `an empty day is never the lowest`() {
        val values = mapOf(day to 0.0, day.plusDays(1) to 46.0)
        assertEquals(day.plusDays(1) to 46.0, bestDay(values, lowest = true))
    }

    @Test
    fun `nothing recorded has no record`() {
        assertNull(bestDay(emptyMap(), lowest = false))
        assertNull(bestDay(mapOf(day to 0.0), lowest = true))
    }
}
