package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Break
import de.steppicrew.healthconnectview.health.heartZones
import de.steppicrew.healthconnectview.health.loadOf
import de.steppicrew.healthconnectview.health.zoneOf
import de.steppicrew.healthconnectview.registry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

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
        val zones = heartZones(points, emptyList(), 200, maxFromSettings = false)!!
        assertEquals(Duration.ofMinutes(10), zones.times[2])
    }

    @Test
    fun `a dropout counts no longer than a minute`() {
        val points = listOf(Point(at(0), 150.0), Point(at(1800), 150.0))
        val zones = heartZones(points, emptyList(), 200, maxFromSettings = false)!!
        assertEquals(Duration.ofMinutes(1), zones.times[2])
    }

    @Test
    fun `readings in a break count in no zone`() {
        val points = (0L..1200 step 10).map { Point(at(it), 150.0) }
        val zones = heartZones(points, listOf(Break(at(300), at(900))), 200, maxFromSettings = false)!!
        assertEquals(Duration.ofMinutes(10), zones.times[2])
    }

    @Test
    fun `load is minutes times zone number, added up`() {
        val times = listOf(10L, 0, 20, 0, 5).map(Duration::ofMinutes)
        assertEquals(10 + 60 + 25, loadOf(times))
    }

    @Test
    fun `no maximum, no zones`() {
        assertNull(heartZones(listOf(Point(at(0), 150.0), Point(at(10), 150.0)), emptyList(), null, false))
    }
}
