package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.PressureReading
import de.steppicrew.healthconnectview.health.pressureReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class PressureReportTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val first = LocalDate.of(2026, 9, 1)
    private val last = LocalDate.of(2026, 9, 3)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun reading(date: LocalDate, hour: Int, sys: Double = 130.0, dia: Double = 80.0) =
        PressureReading(at(date, hour), sys, dia)

    @Test
    fun `only days with a reading get a line`() {
        val report = pressureReport(
            listOf(reading(first, 7), reading(last, 21)),
            first, last, zone,
        )
        assertEquals(listOf(first, last), report.days.map { it.date })
    }

    @Test
    fun `a reading after midnight counts for the evening before, at both ends`() {
        val report = pressureReport(
            listOf(
                // The evening before the first day: out.
                PressureReading(at(first, 1), 150.0, 95.0),
                // The last day's evening, measured after midnight: in.
                PressureReading(at(last.plusDays(1), 1), 140.0, 90.0),
            ),
            first, last, zone,
        )
        assertEquals(1, report.readings.size)
        val day = report.days.single()
        assertEquals(last, day.date)
        assertNull(day.morning)
        assertEquals(140.0, day.evening!!.systolic, 0.001)
    }

    @Test
    fun `each day averages its parts separately`() {
        val report = pressureReport(
            listOf(
                reading(first, 7, 120.0, 80.0),
                reading(first, 8, 140.0, 90.0),
                reading(first, 21, 150.0, 95.0),
            ),
            first, last, zone,
        )
        val day = report.days.single()
        assertEquals(2, day.morning!!.count)
        assertEquals(130.0, day.morning.systolic, 0.001)
        assertEquals(1, day.evening!!.count)
        assertEquals(3, report.overall!!.count)
    }

    @Test
    fun `copies from a second app count once and readings come oldest first`() {
        val late = reading(last, 21)
        val early = reading(first, 7)
        val report = pressureReport(listOf(late, early, late.copy()), first, last, zone)
        assertEquals(listOf(early, late), report.readings)
    }

    @Test
    fun `an empty window has no averages rather than zeros`() {
        val report = pressureReport(emptyList(), first, last, zone)
        assertNull(report.overall)
        assertNull(report.parts.morning)
        assertEquals(0, report.days.size)
    }
}
