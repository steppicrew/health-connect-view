package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.DayPart
import de.steppicrew.healthconnectview.health.PressureReading
import de.steppicrew.healthconnectview.health.PressureCategory
import de.steppicrew.healthconnectview.health.pressureCategory
import de.steppicrew.healthconnectview.health.dayPartOf
import de.steppicrew.healthconnectview.health.dayPartWindow
import de.steppicrew.healthconnectview.health.splitByDayPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BloodPressureTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 9, 20)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant()

    @Test
    fun `morning runs from four to just before two`() {
        assertEquals(day to DayPart.MORNING, dayPartOf(at(day, 4), zone))
        assertEquals(day to DayPart.MORNING, dayPartOf(at(day, 13, 59), zone))
    }

    @Test
    fun `evening starts at two`() {
        assertEquals(day to DayPart.EVENING, dayPartOf(at(day, 14), zone))
        assertEquals(day to DayPart.EVENING, dayPartOf(at(day, 23, 59), zone))
    }

    @Test
    fun `a reading after midnight belongs to the evening before`() {
        assertEquals(day to DayPart.EVENING, dayPartOf(at(day.plusDays(1), 0, 30), zone))
        assertEquals(day to DayPart.EVENING, dayPartOf(at(day.plusDays(1), 3, 59), zone))
    }

    @Test
    fun `the window reaches past midnight after the last day`() {
        val (start, end) = dayPartWindow(day, day, zone)
        assertEquals(at(day, 4), start)
        assertEquals(at(day.plusDays(1), 4), end)
    }

    @Test
    fun `parts are averaged separately`() {
        val split = splitByDayPart(
            listOf(
                PressureReading(at(day, 7), 130.0, 85.0),
                PressureReading(at(day.plusDays(1), 7), 140.0, 95.0),
                PressureReading(at(day, 21), 120.0, 80.0),
            ),
            zone,
        )
        assertEquals(2, split.morning?.count)
        assertEquals(135.0, split.morning!!.systolic, 0.001)
        assertEquals(90.0, split.morning.diastolic, 0.001)
        assertEquals(1, split.evening?.count)
        assertEquals(120.0, split.evening!!.systolic, 0.001)
    }

    @Test
    fun `the same reading from two apps counts once`() {
        val reading = PressureReading(at(day, 7), 130.0, 85.0)
        val split = splitByDayPart(listOf(reading, reading.copy()), zone)
        assertEquals(1, split.morning?.count)
    }

    @Test
    fun `a part without readings is absent rather than zero`() {
        val split = splitByDayPart(listOf(PressureReading(at(day, 7), 130.0, 85.0)), zone)
        assertNull(split.evening)
    }

    @Test
    fun `the higher of the two values decides the category`() {
        assertEquals(PressureCategory.NORMAL, pressureCategory(125.0, 80.0))
        assertEquals(PressureCategory.HIGH_NORMAL, pressureCategory(135.0, 80.0))
        assertEquals(PressureCategory.GRADE_1, pressureCategory(128.0, 92.0))
        assertEquals(PressureCategory.GRADE_2, pressureCategory(165.0, 85.0))
    }

    @Test
    fun `boundaries belong to the higher band`() {
        assertEquals(PressureCategory.HIGH_NORMAL, pressureCategory(130.0, 70.0))
        assertEquals(PressureCategory.GRADE_1, pressureCategory(140.0, 70.0))
        assertEquals(PressureCategory.GRADE_2, pressureCategory(120.0, 100.0))
    }

    @Test
    fun `low only when nothing is raised`() {
        assertEquals(PressureCategory.LOW, pressureCategory(85.0, 55.0))
        assertEquals(PressureCategory.LOW, pressureCategory(110.0, 58.0))
        assertEquals(PressureCategory.GRADE_1, pressureCategory(150.0, 55.0))
    }
}
