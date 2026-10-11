package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.weekdayMeans
import de.steppicrew.healthconnectview.health.yearHeatmap
import de.steppicrew.healthconnectview.registry.RecordRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekdayMeansTest {

    // A Monday.
    private val first = LocalDate.of(2026, 1, 5)
    private val last = LocalDate.of(2026, 12, 31)
    private val today = first.plusWeeks(8).minusDays(1)

    private fun heatmap(values: Map<LocalDate, Double>) = yearHeatmap(values, first, last, fromZero = true)!!

    private fun spec(name: String) = RecordRegistry.specOrNull(name)!!

    @Test
    fun `each weekday is the mean of its own days`() {
        // Eight weeks: Sundays 9 h, every other night 7 h, one Monday 8 h.
        val nights = (0L until 56L).associate { i ->
            val day = first.plusDays(i)
            day to when {
                day.dayOfWeek == DayOfWeek.SUNDAY -> 9.0
                i == 0L -> 8.0
                else -> 7.0
            }
        }
        val means = weekdayMeans(spec("SleepSessionRecord"), heatmap(nights), today)
        assertEquals(9.0, means.getValue(DayOfWeek.SUNDAY), 0.0)
        assertEquals(7.125, means.getValue(DayOfWeek.MONDAY), 1e-9)
        assertEquals(7.0, means.getValue(DayOfWeek.WEDNESDAY), 0.0)
    }

    @Test
    fun `a day without a workout trained for none`() {
        // A ride every Sunday for eight weeks, and nothing else.
        val rides = (0L until 8L).associate { first.plusDays(6 + it * 7) to 2.0 }
        val means = weekdayMeans(spec("ExerciseSessionRecord"), heatmap(rides), today)
        assertEquals(2.0, means.getValue(DayOfWeek.SUNDAY), 0.0)
        assertEquals(0.0, means.getValue(DayOfWeek.TUESDAY), 0.0)
    }

    @Test
    fun `steps keep a missing day out rather than counting it zero`() {
        val days = (0L until 56L).filter { first.plusDays(it).dayOfWeek != DayOfWeek.SATURDAY }
            .associate { first.plusDays(it) to 8000.0 }
        val means = weekdayMeans(spec("StepsRecord"), heatmap(days), today)
        assertEquals(6, means.size)
        assertEquals(8000.0, means.getValue(DayOfWeek.FRIDAY), 0.0)
    }

    @Test
    fun `fewer than four weeks shows no rhythm`() {
        val days = (0L until 20L).associate { first.plusDays(it) to 8000.0 }
        assertTrue(weekdayMeans(spec("StepsRecord"), heatmap(days), today).isEmpty())
    }

    @Test
    fun `days after today are left out`() {
        val days = (0L until 56L).associate { first.plusDays(it) to 1.0 } + (first.plusDays(70) to 100.0)
        assertTrue(weekdayMeans(spec("StepsRecord"), heatmap(days), today).values.all { it == 1.0 })
    }
}
