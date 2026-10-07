package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Trend
import de.steppicrew.healthconnectview.health.trendOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightRankTest {

    @Test
    fun `a small move on a steady series outranks a large one on a restless series`() {
        // Weight: steady around 80, the last week 2.4 kg (3 %) up.
        val weight = trendOf(List(23) { 80.0 + (it % 2) * 0.4 } + List(7) { 82.4 })!!
        // Steps: swinging 4000-12000, the last week 10 % up on average.
        val steps = trendOf(List(23) { if (it % 2 == 0) 4000.0 else 12000.0 } + List(7) { 9000.0 })!!
        assertEquals(Trend.UP, weight.direction)
        assertTrue(weight.weight > steps.weight)
        assertTrue(weight.percent!! < steps.percent!!)
    }

    @Test
    fun `no percentage against a zero baseline`() {
        assertNull(trendOf(List(30) { 0.0 })!!.percent)
    }
}
