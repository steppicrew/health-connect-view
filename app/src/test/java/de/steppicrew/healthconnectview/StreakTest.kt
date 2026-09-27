package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.STREAK_MAX_GAP_DAYS
import de.steppicrew.healthconnectview.health.Streak
import de.steppicrew.healthconnectview.health.continued
import de.steppicrew.healthconnectview.health.streakStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakTest {

    private val goal = 10_000.0

    @Test
    fun `met days count until one falls short`() {
        val streak = Streak(0, broken = false).continued(listOf(12_000.0, 10_000.0, 11_000.0, 9_999.0, 15_000.0), goal)
        assertEquals(3, streak.count)
        assertTrue(streak.broken)
    }

    @Test
    fun `a day without data neither counts nor breaks`() {
        val streak = Streak(0, broken = false).continued(listOf(12_000.0, null, null, 11_000.0), goal)
        assertEquals(2, streak.count)
        assertFalse(streak.broken)
    }

    @Test
    fun `a gap longer than a month ends the run`() {
        val days = listOf(12_000.0) + List(STREAK_MAX_GAP_DAYS + 1) { null } + listOf(11_000.0)
        val streak = Streak(0, broken = false).continued(days, goal)
        assertEquals(1, streak.count)
        assertTrue(streak.broken)
    }

    @Test
    fun `a gap is carried across chunks`() {
        val first = Streak(0, broken = false).continued(listOf(12_000.0) + List(20) { null }, goal)
        val second = first.continued(List(STREAK_MAX_GAP_DAYS - 20 + 1) { null } + listOf(11_000.0), goal)
        assertEquals(1, second.count)
        assertTrue(second.broken)
    }

    @Test
    fun `a broken streak takes no more days`() {
        val broken = Streak(4, broken = true)
        assertEquals(broken, broken.continued(listOf(12_000.0), goal))
    }

    @Test
    fun `today short of the goal breaks nothing yet`() {
        assertEquals(Streak(0, broken = false), streakStart(4_000.0, goal, running = true))
    }

    @Test
    fun `a finished day short of the goal has no streak`() {
        assertNull(streakStart(4_000.0, goal, running = false))
    }

    @Test
    fun `a day that met the goal starts the count`() {
        assertEquals(Streak(1, broken = false), streakStart(10_500.0, goal, running = false))
    }

    @Test
    fun `a shown day without data is bridged like any other`() {
        assertEquals(Streak(0, broken = false, gap = 1), streakStart(null, goal, running = false))
    }
}
