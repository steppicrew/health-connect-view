package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.calendarHeadline
import de.steppicrew.healthconnectview.health.yearHeatmap
import de.steppicrew.healthconnectview.registry.RecordRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CalendarHeadlineTest {

    private val first = LocalDate.of(2026, 1, 1)
    private val last = LocalDate.of(2026, 12, 31)

    private fun heatmap(vararg values: Double, second: List<Double> = emptyList()) = yearHeatmap(
        values.mapIndexed { i, v -> first.plusDays(i.toLong()) to v }.toMap(),
        first,
        last,
        fromZero = true,
    )!!.copy(secondValues = second.mapIndexed { i, v -> first.plusDays(i.toLong()) to v }.toMap())

    private fun spec(name: String) = RecordRegistry.specOrNull(name)!!

    @Test
    fun `steps add up over the year`() {
        assertEquals(30000.0, calendarHeadline(spec("StepsRecord"), heatmap(8000.0, 10000.0, 12000.0))!!.first, 0.0)
    }

    @Test
    fun `a level is its mean`() {
        assertEquals(50.0, calendarHeadline(spec("RestingHeartRateRecord"), heatmap(48.0, 50.0, 52.0))!!.first, 1e-9)
    }

    @Test
    fun `sleep is a night, workouts the hours trained`() {
        assertEquals(7.0, calendarHeadline(spec("SleepSessionRecord"), heatmap(6.0, 8.0))!!.first, 0.0)
        assertEquals(3.5, calendarHeadline(spec("ExerciseSessionRecord"), heatmap(1.0, 2.5))!!.first, 0.0)
    }

    @Test
    fun `blood pressure keeps both numbers`() {
        val (systolic, diastolic) = calendarHeadline(
            spec("BloodPressureRecord"),
            heatmap(120.0, 130.0, second = listOf(80.0, 90.0)),
        )!!
        assertEquals(125.0, systolic, 1e-9)
        assertEquals(85.0, diastolic!!, 1e-9)
    }

    @Test
    fun `steps have no second number`() {
        assertNull(calendarHeadline(spec("StepsRecord"), heatmap(1.0))!!.second)
    }
}
