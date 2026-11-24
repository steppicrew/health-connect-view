package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Break
import de.steppicrew.healthconnectview.health.MaxSource
import de.steppicrew.healthconnectview.health.MaxRule
import de.steppicrew.healthconnectview.health.maxHeartRate
import de.steppicrew.healthconnectview.health.predictedMaxHeartRate
import de.steppicrew.healthconnectview.health.heartZones
import de.steppicrew.healthconnectview.health.loadOf
import de.steppicrew.healthconnectview.health.zoneOf
import de.steppicrew.healthconnectview.registry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class HeartZonesTest {

    private val base: Instant = Instant.parse("2026-10-09T06:00:00Z")

    private fun at(seconds: Long) = base.plusSeconds(seconds)

    @Test
    fun `zones are tenths of the maximum from half of it`() {
        assertNull(zoneOf(99.0, 200))
        assertEquals(0, zoneOf(100.0, 200))
        assertEquals(2, zoneOf(150.0, 200))
        assertEquals(4, zoneOf(199.0, 200))
        // Above the maximum is still the top zone, not none.
        assertEquals(4, zoneOf(210.0, 200))
    }

    @Test
    fun `each reading counts until the next`() {
        // A reading every 10 s for ten minutes at 150: zone 3 of a maximum of 200.
        val points = (0L..600 step 10).map { Point(at(it), 150.0) }
        val zones = heartZones(points, emptyList(), 200, maxSource = MaxSource.DATA)!!
        assertEquals(Duration.ofMinutes(10), zones.times[2])
    }

    @Test
    fun `a dropout counts no longer than a minute`() {
        val points = listOf(Point(at(0), 150.0), Point(at(1800), 150.0))
        val zones = heartZones(points, emptyList(), 200, maxSource = MaxSource.DATA)!!
        assertEquals(Duration.ofMinutes(1), zones.times[2])
    }

    @Test
    fun `readings in a break count in no zone`() {
        val points = (0L..1200 step 10).map { Point(at(it), 150.0) }
        val zones = heartZones(points, listOf(Break(at(300), at(900))), 200, maxSource = MaxSource.DATA)!!
        assertEquals(Duration.ofMinutes(10), zones.times[2])
    }

    @Test
    fun `load is minutes times zone number, added up`() {
        val times = listOf(10L, 0, 20, 0, 5).map(Duration::ofMinutes)
        assertEquals(10 + 60 + 25, loadOf(times))
    }

    @Test
    fun `load counts the whole minutes the zone rows show`() {
        // 8m 59s reads "8m" in its row, so it counts 8 times 1, not 9.
        val times = listOf(Duration.ofSeconds(8 * 60 + 59), Duration.ofSeconds(40), Duration.ZERO, Duration.ZERO, Duration.ZERO)
        assertEquals(8, loadOf(times))
    }

    @Test
    fun `no maximum, no zones`() {
        assertNull(heartZones(listOf(Point(at(0), 150.0), Point(at(10), 150.0)), emptyList(), null, MaxSource.DATA))
    }

    @Test
    fun `the formula gives 170 at 54`() {
        assertEquals(170, predictedMaxHeartRate(1972, LocalDate.of(2026, 11, 24)))
    }

    @Test
    fun `each rule finds its maximum`() = runBlocking {
        val today = LocalDate.of(2026, 11, 24)
        suspend fun max(rule: MaxRule, observed: Int?, birthYear: Int? = 1972) =
            maxHeartRate(rule, 185, birthYear, { observed }, today)
        assertEquals(185 to MaxSource.SET, max(MaxRule.SET, 201))
        assertEquals(170 to MaxSource.AGE, max(MaxRule.AGE, 201))
        assertEquals(201 to MaxSource.DATA, max(MaxRule.DATA, 201))
        // A peak above the formula is taken, one below it is not.
        assertEquals(201 to MaxSource.DATA, max(MaxRule.HIGHER, 201))
        assertEquals(170 to MaxSource.AGE, max(MaxRule.HIGHER, 155))
        assertEquals(155 to MaxSource.DATA, max(MaxRule.HIGHER, 155, birthYear = null))
        assertNull(max(MaxRule.AGE, 201, birthYear = null))
        assertNull(max(MaxRule.DATA, null))
    }
}
