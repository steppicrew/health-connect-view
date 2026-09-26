package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Trend
import de.steppicrew.healthconnectview.health.trendOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendTest {

    /** 23 days alternating around [base], then 7 days at [recent]. */
    private fun month(base: Double, noise: Double, recent: Double): List<Double?> =
        List(23) { if (it % 2 == 0) base + noise else base - noise } + List(7) { recent }

    @Test
    fun `a clear rise points up`() {
        assertEquals(Trend.UP, trendOf(month(8000.0, 500.0, 11000.0)))
    }

    @Test
    fun `a clear fall points down`() {
        assertEquals(Trend.DOWN, trendOf(month(8000.0, 500.0, 5000.0)))
    }

    @Test
    fun `a change within day-to-day noise is flat`() {
        assertEquals(Trend.FLAT, trendOf(month(8000.0, 3000.0, 8600.0)))
    }

    @Test
    fun `a small shift on a steady metric still shows`() {
        // Weight barely moves day to day, so a kilo is well outside its usual spread.
        assertEquals(Trend.DOWN, trendOf(month(80.0, 0.2, 79.0)))
    }

    @Test
    fun `a constant series is flat, not undefined`() {
        assertEquals(Trend.FLAT, trendOf(List(30) { 60.0 }))
    }

    @Test
    fun `missing days are left out, not read as zero`() {
        // Every other recent day unrecorded: counting them as zero would halve the week.
        val daily = List(23) { 8000.0 } + listOf(8000.0, null, 8000.0, null, 8000.0, null, 8000.0)
        assertEquals(Trend.FLAT, trendOf(daily))
    }

    @Test
    fun `too few recent days give no trend`() {
        val daily = List(23) { 8000.0 } + listOf(12000.0, null, null, 12000.0, null, 12000.0, null)
        assertNull(trendOf(daily))
    }

    @Test
    fun `too little history gives no trend`() {
        assertNull(trendOf(List(20) { null } + List(10) { 8000.0 }))
    }
}
