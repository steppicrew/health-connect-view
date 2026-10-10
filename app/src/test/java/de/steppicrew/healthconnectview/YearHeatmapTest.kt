package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.YearHeatmap
import de.steppicrew.healthconnectview.health.yearHeatmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class YearHeatmapTest {

    private val first = LocalDate.of(2025, 10, 11)
    private val last = LocalDate.of(2026, 10, 10)

    private fun days(vararg values: Double) = values.mapIndexed { i, v -> first.plusDays(i.toLong()) to v }.toMap()

    @Test
    fun `no values, no heatmap`() {
        assertNull(yearHeatmap(emptyMap(), first, last, fromZero = true))
    }

    @Test
    fun `one huge day does not leave every other day pale`() {
        // 99 ordinary days and one marathon: the darkest step starts at the 95th percentile.
        val values = days(*(DoubleArray(99) { 5000.0 + it * 50 } + 60000.0))
        val map = yearHeatmap(values, first, last, fromZero = true)!!
        assertEquals(9700.0, map.high, 0.0)
        assertEquals(YearHeatmap.STEPS - 1, map.step(60000.0))
        assertEquals(YearHeatmap.STEPS - 1, map.step(9700.0))
        assertEquals(2, map.step(5000.0))
    }

    @Test
    fun `a count starts at zero, a level at its own lowest`() {
        val values = days(48.0, 50.0, 52.0, 55.0)
        assertEquals(0.0, yearHeatmap(values, first, last, fromZero = true)!!.low, 0.0)
        val level = yearHeatmap(values, first, last, fromZero = false)!!
        assertEquals(48.0, level.low, 0.0)
        assertEquals(0, level.step(48.0))
    }

    @Test
    fun `a year of one value sits in the middle`() {
        val map = yearHeatmap(days(81.9, 81.9), first, last, fromZero = false)!!
        assertEquals(YearHeatmap.STEPS / 2, map.step(81.9))
    }

    @Test
    fun `days outside the year are left out`() {
        val map = yearHeatmap(mapOf(first.minusDays(1) to 3.0, first to 4.0), first, last, fromZero = true)!!
        assertEquals(setOf(first), map.values.keys)
    }

    @Test
    fun `total calories start at the basal rate, not zero`() {
        val values = days(2100.0, 2400.0, 2900.0, 3300.0)
        val map = yearHeatmap(values, first, last, fromZero = true, floor = 1800.0)!!
        assertEquals(1800.0, map.low, 0.0)
        assertEquals(0, map.step(1900.0))
        assertEquals(1, map.step(2100.0))
    }

    @Test
    fun `a floor above the top is ignored`() {
        val map = yearHeatmap(days(900.0, 1000.0), first, last, fromZero = true, floor = 1800.0)!!
        assertEquals(0.0, map.low, 0.0)
    }
}
