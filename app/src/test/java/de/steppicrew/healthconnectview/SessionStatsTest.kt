package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.ui.session.heartRateStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SessionStatsTest {

    private val spec = RecordRegistry.specOrNull("HeartRateRecord")!!
    private val base: Instant = Instant.parse("2026-10-09T06:00:00Z")

    private fun at(minutes: Long) = base.plusSeconds(minutes * 60)

    @Test
    fun `heart rate is the mean and the extremes of the readings while moving`() {
        val points = listOf(Point(at(0), 120.0), Point(at(10), 160.0), Point(at(20), 60.0), Point(at(30), 140.0))
        // A break from minute 15 to 25: the resting reading at 20 counts in nothing.
        val pieces = listOf(at(0) to at(15), at(25) to at(40))
        val stat = heartRateStat(spec, points, pieces)!!
        assertEquals(140.0, stat.value, 0.001)
        assertEquals(120.0, stat.low!!, 0.001)
        assertEquals(160.0, stat.high!!, 0.001)
    }

    @Test
    fun `no reading inside the session gives no figure`() {
        assertNull(heartRateStat(spec, listOf(Point(at(50), 100.0)), listOf(at(0) to at(40))))
    }
}
