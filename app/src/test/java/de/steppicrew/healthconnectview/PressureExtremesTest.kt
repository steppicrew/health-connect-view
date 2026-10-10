package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.PressureReading
import de.steppicrew.healthconnectview.health.pressureExtremes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PressureExtremesTest {

    private fun reading(minute: Long, systolic: Double, diastolic: Double) =
        PressureReading(Instant.parse("2026-10-01T07:00:00Z").plusSeconds(minute * 60), systolic, diastolic)

    @Test
    fun `lowest and highest are whole readings, never a pair made up of two`() {
        // The lowest diastolic (68) belongs to the highest systolic: printing "112/68" would
        // be a reading nobody took.
        val readings = listOf(reading(0, 112.0, 74.0), reading(1, 128.0, 80.0), reading(2, 141.0, 68.0))
        val (low, high) = pressureExtremes(readings)!!
        assertEquals(112.0 to 74.0, low.systolic to low.diastolic)
        assertEquals(141.0 to 68.0, high.systolic to high.diastolic)
    }

    @Test
    fun `no readings, no extremes`() {
        assertNull(pressureExtremes(emptyList()))
    }
}
