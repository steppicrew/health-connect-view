package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.AdaptiveGoalRule
import de.steppicrew.healthconnectview.health.DISTANCE_GOAL_RULE
import de.steppicrew.healthconnectview.health.STEPS_GOAL_RULE
import de.steppicrew.healthconnectview.health.adaptiveGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AdaptiveGoalTest {
    private val start = LocalDate.of(2025, 8, 1)
    private val rule = STEPS_GOAL_RULE

    private fun goals(steps: List<Double?>, seed: Double, rule: AdaptiveGoalRule = this.rule): List<Double> {
        val last = start.plusDays(steps.size - 1L)
        val byDay = steps.withIndex().associate { (i, v) -> start.plusDays(i.toLong()) to v }
        val map = adaptiveGoals(seed, rule, { byDay[it] }, start, start, last)
        return steps.indices.map { map.getValue(start.plusDays(it.toLong())) }
    }

    @Test
    fun `the hiking holiday the rule was read from`() {
        // Days 12 to 21 of the owner's record: the watch's goals after its unexplained reset.
        val steps = listOf(9_695.0, 22_455.0, 23_162.0, 28_428.0, 27_231.0, 17_272.0, 22_329.0, 16_172.0, 25_601.0, 36_343.0)
        val watch = listOf(5_000.0, 5_940.0, 7_940.0, 9_940.0, 11_940.0, 13_940.0, 14_610.0, 16_160.0, 16_170.0, 18_060.0)
        // A lead-in day that rises to the watch's 5,000, so the first change here is a second one.
        val replayed = goals(listOf(9_500.0) + steps, seed = 4_500.0).drop(1)

        replayed.zip(watch).forEach { (ours, theirs) -> assertEquals(theirs, ours, 10.0) }
    }

    @Test
    fun `the first change in a direction is a tenth of the gap, the next a fifth`() {
        val replayed = goals(listOf(5_000.0, 5_000.0, 15_000.0, 15_000.0, 0.0), seed = 10_000.0)

        assertEquals(listOf(10_000.0, 9_500.0, 8_600.0, 9_240.0, 10_400.0), replayed)
    }

    @Test
    fun `rises stop at the cap, falls do not`() {
        val replayed = goals(listOf(100_000.0, 100_000.0, 0.0, 0.0, 0.0), seed = 10_000.0)

        assertEquals(12_000.0, replayed[1], 0.0)
        assertEquals(14_000.0, replayed[2], 0.0)
        assertTrue(replayed[4] < replayed[3] - 2_000)
    }

    @Test
    fun `a day without data changes nothing and keeps the direction`() {
        val replayed = goals(listOf(5_000.0, null, 5_000.0), seed = 10_000.0)

        // Second fall in a row despite the empty day between: a fifth, not a tenth.
        assertEquals(listOf(10_000.0, 9_500.0, 9_500.0), replayed)
        assertEquals(8_600.0, goals(listOf(5_000.0, null, 5_000.0, 0.0), seed = 10_000.0)[3], 0.0)
    }

    @Test
    fun `the floor holds the goal up`() {
        val floored = goals(List(30) { 0.0 }, seed = 10_000.0, rule.copy(floor = 6_000.0))

        assertEquals(6_000.0, floored.last(), 0.0)
        assertTrue(goals(List(30) { 0.0 }, seed = 10_000.0).last() < 6_000.0)
    }

    @Test
    fun `a distance goal moves in hundredths of a kilometre`() {
        val replayed = goals(listOf(2.0, 2.0, 8.0, 8.04, 0.0), seed = 5.0, DISTANCE_GOAL_RULE)

        // A tenth of 3 km down, a fifth of 2.7, a tenth of 3.84 up, a fifth of 3.49, each rounded up.
        listOf(5.0, 4.7, 4.16, 4.55, 5.25).zip(replayed).forEach { (expected, ours) -> assertEquals(expected, ours, 1e-9) }
    }

    @Test
    fun `a distance goal falls on a day a little short of it`() {
        // 300 m short: a tenth of a kilometre as the step would round the fall up to nothing.
        assertEquals(4.97, goals(listOf(4.7, 4.7), seed = 5.0, DISTANCE_GOAL_RULE)[1], 1e-9)
    }

    @Test
    fun `a long ride raises a low distance goal by a kilometre and no more`() {
        val replayed = goals(listOf(80.0, 80.0, 0.0), seed = 4.0, DISTANCE_GOAL_RULE)

        listOf(4.0, 5.0, 6.0).zip(replayed).forEach { (expected, ours) -> assertEquals(expected, ours, 1e-9) }
    }

    @Test
    fun `a higher distance goal may rise by a fifth of itself`() {
        val replayed = goals(listOf(80.0, 80.0, 0.0), seed = 20.0, DISTANCE_GOAL_RULE)

        listOf(20.0, 24.0, 28.8).zip(replayed).forEach { (expected, ours) -> assertEquals(expected, ours, 1e-9) }
    }

    @Test
    fun `a distance goal keeps up with weekdays on the bike`() {
        // A week like the owner's: under a flat kilometre a day the goal sat at 13 to 17 km.
        val week = listOf(41.0, 16.0, 33.0, 33.0, 26.0, 1.0, 1.0)
        val replayed = goals(List(20) { week }.flatten(), seed = 5.0, DISTANCE_GOAL_RULE).takeLast(7)

        assertTrue(replayed.min() > 17.0)
        assertTrue(replayed.max() < 26.0)
    }

    @Test
    fun `a distance goal never falls to nothing`() {
        val idle = List(200) { 0.0 }

        assertTrue(goals(idle, seed = 5.0, DISTANCE_GOAL_RULE).last() > 0.0)
        assertEquals(3.0, goals(idle, seed = 5.0, DISTANCE_GOAL_RULE.copy(floor = 3.0)).last(), 1e-9)
    }

    @Test
    fun `the seed is forgotten after the warm-up`() {
        val steps = List(70) { if (it % 3 == 0) 14_000.0 else 8_000.0 }

        assertEquals(goals(steps, seed = 4_000.0).last(), goals(steps, seed = 20_000.0).last(), 50.0)
    }
}
