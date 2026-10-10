package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.ui.components.nearestWithin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class NearestWithinTest {

    private val start: Instant = Instant.parse("2026-10-01T00:00:00Z")

    private fun day(n: Long) = start.plusSeconds(n * 86_400)

    @Test
    fun `a day's value is found on the same day of the other chart`() {
        val points = (0L..6L).map { Point(day(it), it.toDouble()) }
        assertEquals(3, nearestWithin(points, day(3)))
        assertEquals(3, nearestWithin(points, day(3).plusSeconds(3_600)))
    }

    @Test
    fun `weigh-ins a week apart say nothing about the days between`() {
        // Usual spacing is two days; a moment four days from both neighbours has no value.
        val points = listOf(Point(day(0), 80.0), Point(day(2), 80.2), Point(day(4), 80.1), Point(day(12), 79.8))
        assertEquals(1, nearestWithin(points, day(2)))
        assertNull(nearestWithin(points, day(8)))
    }

    @Test
    fun `a single reading answers only its own moment`() {
        val points = listOf(Point(day(1), 80.0))
        assertEquals(0, nearestWithin(points, day(1)))
        assertNull(nearestWithin(points, day(2)))
    }
}
