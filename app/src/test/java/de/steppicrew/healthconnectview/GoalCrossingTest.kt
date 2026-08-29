package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.goalCrossing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class GoalCrossingTest {

    private val start: Instant = Instant.parse("2026-08-28T00:00:00Z")

    private fun at(minutes: Long, value: Double) =
        Point(time = start.plusSeconds(minutes * 60), value = value)

    @Test
    fun `the crossing is interpolated, not snapped to the later point`() {
        // A climb from 8 to 12 over fifteen minutes passes 10 exactly halfway.
        val points = listOf(at(0, 0.0), at(330, 8.0), at(345, 12.0))
        val crossing = goalCrossing(points, 10.0)
        assertEquals(start.plusSeconds(337 * 60 + 30), crossing)
    }

    @Test
    fun `a series that never reaches the goal has no crossing`() {
        assertNull(goalCrossing(listOf(at(0, 0.0), at(600, 8.0)), 10.0))
    }

    @Test
    fun `landing exactly on the goal counts as reaching it`() {
        val crossing = goalCrossing(listOf(at(0, 0.0), at(300, 10.0)), 10.0)
        assertEquals(start.plusSeconds(300 * 60), crossing)
    }

    @Test
    fun `only the first crossing is reported`() {
        // A running total never falls, but guard against reporting a later point anyway.
        // 0 to 12 over 100 minutes passes 10 at 83min20s, inside the first segment.
        val points = listOf(at(0, 0.0), at(100, 12.0), at(200, 20.0))
        assertEquals(start.plusSeconds(83 * 60 + 20), goalCrossing(points, 10.0))
    }

    @Test
    fun `a vertical step reports its end rather than dividing by zero`() {
        val jump = listOf(at(0, 0.0), at(300, 0.0), at(300, 12.0))
        assertEquals(start.plusSeconds(300 * 60), goalCrossing(jump, 10.0))
    }

    @Test
    fun `a series already above the goal reports its first point`() {
        val points = listOf(at(0, 15.0), at(300, 20.0))
        assertEquals(start, goalCrossing(points, 10.0))
    }

    @Test
    fun `an absent or nonsensical goal has no crossing`() {
        val points = listOf(at(0, 0.0), at(300, 12.0))
        assertNull(goalCrossing(points, null))
        assertNull(goalCrossing(points, 0.0))
        assertNull(goalCrossing(emptyList(), 10.0))
    }
}
