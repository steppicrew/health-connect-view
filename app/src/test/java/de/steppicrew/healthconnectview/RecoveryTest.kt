package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.recoveryOf
import de.steppicrew.healthconnectview.registry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RecoveryTest {

    private val end: Instant = Instant.parse("2026-10-09T06:56:00Z")

    private fun at(seconds: Long, bpm: Double) = Point(end.plusSeconds(seconds), bpm)

    @Test
    fun `the change after one and two minutes, from readings every 15 s`() {
        val points = listOf(at(-6, 150.0), at(9, 148.0), at(54, 131.0), at(69, 128.0), at(114, 118.0), at(129, 116.0))
        val recovery = recoveryOf(points, end)!!
        // Nearest to 60 s is 54 s, to 120 s is 114 s.
        assertEquals(-19.0, recovery.afterOne!!, 0.001)
        assertEquals(-32.0, recovery.afterTwo!!, 0.001)
    }

    @Test
    fun `a reading two minutes apart does not stand for one minute`() {
        // One all-day reading every two minutes: the one at 110 s is no "after one minute",
        // though close enough to stand for two.
        val recovery = recoveryOf(listOf(at(0, 150.0), at(110, 120.0), at(230, 110.0)), end)!!
        assertNull(recovery.afterOne)
        assertEquals(-30.0, recovery.afterTwo!!, 0.001)
        // Readings at 85 s and 205 s stand for neither minute.
        assertNull(recoveryOf(listOf(at(0, 150.0), at(85, 125.0), at(205, 112.0)), end))
    }

    @Test
    fun `no reading at the end gives nothing`() {
        assertNull(recoveryOf(listOf(at(30, 140.0), at(60, 130.0), at(120, 120.0)), end))
    }

    @Test
    fun `one minute alone where the second is not read`() {
        val recovery = recoveryOf(listOf(at(2, 150.0), at(61, 135.0)), end)!!
        assertEquals(-15.0, recovery.afterOne!!, 0.001)
        assertNull(recovery.afterTwo)
    }
}
