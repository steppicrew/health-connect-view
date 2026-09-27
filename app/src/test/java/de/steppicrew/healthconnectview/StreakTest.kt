package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.DailyValues
import de.steppicrew.healthconnectview.health.LongestStreak
import de.steppicrew.healthconnectview.health.STREAK_MAX_GAP_DAYS
import de.steppicrew.healthconnectview.health.Streak
import de.steppicrew.healthconnectview.health.continued
import de.steppicrew.healthconnectview.health.currentStreak
import de.steppicrew.healthconnectview.health.longestStreak
import de.steppicrew.healthconnectview.health.streakStart
import de.steppicrew.healthconnectview.health.streakSummary
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
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

    private val today = LocalDate.of(2026, 9, 27)

    /** A fake day source: [values] by date, null elsewhere, counting the reads it served. */
    private class Days(val values: Map<LocalDate, Double>) {
        var reads = 0
        val daily: DailyValues = { _, _ ->
            reads++
            values::get
        }
    }

    private fun daysBefore(date: LocalDate, vararg values: Double?): Map<LocalDate, Double> =
        values.withIndex().mapNotNull { (i, v) -> v?.let { date.minusDays(i + 1L) to it } }.toMap()

    @Test
    fun `the current streak counts back from the shown day`() = runBlocking {
        val days = Days(daysBefore(today, 11_000.0, null, 12_000.0, 3_000.0, 15_000.0))
        assertEquals(3, currentStreak(goal, today, 10_200.0, days.daily, today))
        assertEquals(1, days.reads)
    }

    @Test
    fun `today short of the goal keeps yesterday's run`() = runBlocking {
        val days = Days(daysBefore(today, 11_000.0, 12_000.0, 3_000.0))
        assertEquals(2, currentStreak(goal, today, 2_000.0, days.daily, today))
    }

    @Test
    fun `a run longer than a chunk reads further back`() = runBlocking {
        val days = Days(daysBefore(today, *Array<Double?>(45) { 12_000.0 } + 1_000.0))
        assertEquals(46, currentStreak(goal, today, 10_000.0, days.daily, today))
        assertEquals(2, days.reads)
    }

    @Test
    fun `the longest run names its first and last day`() {
        val start = LocalDate.of(2026, 5, 1)
        val values = listOf(12_000.0, 1_000.0, 12_000.0, null, 11_000.0, 10_000.0, 2_000.0, 13_000.0)
        val longest = longestStreak(values.mapIndexed { i, v -> start.plusDays(i.toLong()) to v }, goal)
        assertEquals(LongestStreak(3, start.plusDays(2), start.plusDays(5)), longest)
    }

    @Test
    fun `no met day has no longest run`() {
        assertNull(longestStreak(listOf(today to 1_000.0, today.plusDays(1) to null), goal))
    }

    @Test
    fun `the summary takes current and longest from one read`() = runBlocking {
        val older = daysBefore(today.minusDays(10), 12_000.0, 12_000.0, 12_000.0, 12_000.0, 1_000.0)
        val recent = daysBefore(today, 11_000.0, 11_000.0, 500.0)
        val days = Days(older + recent)
        val summary = streakSummary(goal, today, 10_500.0, days.daily, today)
        assertEquals(3, summary.current)
        assertEquals(4, summary.longest?.count)
        assertEquals(today.minusDays(11), summary.longest?.last)
        assertEquals(1, days.reads)
    }

    @Test
    fun `activities count a day without one as a break`() = runBlocking {
        // An activity source reports zero, not null, for an empty day.
        val zeroes = Days(emptyMap())
        val activities: DailyValues = { start, end -> val v = zeroes.daily(start, end); { day -> v(day) ?: 0.0 } }
        assertEquals(1, currentStreak(1.0, today, 2.0, activities, today))
    }
}
