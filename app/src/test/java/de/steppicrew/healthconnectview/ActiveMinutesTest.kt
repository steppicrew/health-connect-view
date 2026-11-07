package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.ActiveLimits
import de.steppicrew.healthconnectview.health.activeLimits
import de.steppicrew.healthconnectview.health.activeMinutes
import de.steppicrew.healthconnectview.health.chooseWriter
import de.steppicrew.healthconnectview.health.sampleCap
import de.steppicrew.healthconnectview.registry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ActiveMinutesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val day: LocalDate = LocalDate.of(2026, 10, 20)
    private val noon: Instant = day.atTime(12, 0).atZone(zone).toInstant()
    private val limits = ActiveLimits(moderate = 110.0, vigorous = 140.0, fromReserve = true)

    /** A reading every [every] seconds for [minutes] minutes from [from], all at [bpm]. */
    private fun readings(from: Instant, minutes: Int, bpm: Double, every: Long = 15): List<Point> =
        (0L..minutes * 60L step every).map { Point(from.plusSeconds(it), bpm) }

    private fun run(points: List<Point>, workouts: List<Pair<Instant, Instant>> = emptyList(), movement: List<Pair<Instant, Instant>> = emptyList()) =
        activeMinutes(points.sortedBy { it.time }, day, day, zone, limits, workouts, movement, "writer")

    @Test
    fun `limits come from the reserve where a resting rate is known`() {
        val reserve = activeLimits(max = 190, resting = 50)
        assertEquals(106.0, reserve.moderate, 0.01)
        assertEquals(134.0, reserve.vigorous, 0.01)
        assertTrue(reserve.fromReserve)
        val share = activeLimits(max = 200, resting = null)
        assertEquals(128.0, share.moderate, 0.01)
        assertEquals(154.0, share.vigorous, 0.01)
        assertFalse(share.fromReserve)
    }

    @Test
    fun `the cap follows the writer's interval`() {
        assertEquals(Duration.ofMinutes(1), sampleCap(readings(noon, 10, 80.0, every = 15)))
        assertEquals(Duration.ofMinutes(3), sampleCap(readings(noon, 60, 80.0, every = 120)))
        assertEquals(Duration.ofMinutes(5), sampleCap(readings(noon, 120, 80.0, every = 600)))
    }

    @Test
    fun `minutes in a workout count by level`() {
        val points = readings(noon, 20, 120.0) + readings(noon.plusSeconds(20 * 60 + 15), 10, 150.0)
        val result = run(points, workouts = listOf(noon to noon.plusSeconds(30 * 60)))
        assertEquals(20, result.days.single().moderate)
        assertEquals(10, result.days.single().vigorous)
        assertEquals(40, result.weighted)
        assertEquals(0, result.leftOut)
    }

    @Test
    fun `a raised pulse without movement outside a workout is left out`() {
        val result = run(readings(noon, 30, 120.0))
        assertEquals(0, result.weighted)
        assertEquals(30, result.leftOut)
    }

    @Test
    fun `steps within a minute make a minute outside a workout count`() {
        // Steps for the first ten minutes; the minute after still counts, the rest not.
        val result = run(readings(noon, 30, 120.0), movement = listOf(noon to noon.plusSeconds(600)))
        assertEquals(11, result.days.single().moderate)
        assertEquals(19, result.leftOut)
    }

    @Test
    fun `a sparse writer covers its intervals`() {
        // A reading every two minutes: each one counts for two, under the three-minute cap.
        val points = readings(noon, 60, 120.0, every = 120)
        val result = run(points, workouts = listOf(noon to noon.plusSeconds(3600)))
        assertEquals(60, result.days.single().moderate)
    }

    @Test
    fun `a dropout counts only up to the cap`() {
        // Every 15 s for ten minutes, then nothing for an hour: the last reading counts for one.
        val points = readings(noon, 10, 120.0) + Point(noon.plusSeconds(4200), 120.0)
        val result = run(points, workouts = listOf(noon to noon.plusSeconds(4200)))
        assertEquals(11, result.days.single().moderate)
    }

    @Test
    fun `resting minutes count as covered but not active`() {
        val result = run(readings(noon, 60, 70.0))
        assertEquals(0, result.weighted)
        assertEquals(0, result.leftOut)
        assertEquals(60.0 / 1440, result.coverage, 0.002)
    }

    @Test
    fun `the sparser of two writers that cover alike is chosen`() {
        val dense = readings(noon, 600, 80.0, every = 15).map { it.time }
        val sparse = readings(noon, 600, 80.0, every = 120).map { it.time }
        val short = readings(noon, 60, 80.0, every = 120).map { it.time }
        assertEquals("sparse", chooseWriter(mapOf("dense" to dense, "sparse" to sparse, "short" to short)))
        assertEquals("dense", chooseWriter(mapOf("dense" to dense, "short" to short)))
    }

    @Test
    fun `a day across the clock change has its own minutes`() {
        // 25.10.2026 has 25 hours in Berlin; a reading late that evening still lands on it.
        val change = LocalDate.of(2026, 10, 25)
        val late = change.atTime(23, 0).atZone(zone).toInstant()
        val result = activeMinutes(
            readings(late, 30, 120.0), change.minusDays(1), change, zone, limits,
            listOf(late to late.plusSeconds(1800)), emptyList(), "writer",
        )
        assertEquals(listOf(0, 30), result.days.map { it.moderate })
    }
}
