package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.driftOf
import de.steppicrew.healthconnectview.health.halve
import de.steppicrew.healthconnectview.registry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DriftTest {

    private val base: Instant = Instant.parse("2026-10-09T08:00:00Z")

    private fun at(minutes: Long): Instant = base.plusSeconds(minutes * 60)

    /** A reading every 30 s from [from] to [to] minutes, of [value]. */
    private fun every30s(from: Long, to: Long, value: (Long) -> Double): List<Point> =
        (from * 2 until to * 2).map { half -> Point(base.plusSeconds(half * 30), value(half / 2)) }

    @Test
    fun `halves cut the moving time, not the clock`() {
        // 0-10 moving, 10-30 a break, 30-50 moving: 30 min moving, cut at minute 15 of it.
        val (first, second) = halve(listOf(at(0) to at(10), at(30) to at(50)), Duration.ofMinutes(15))
        assertEquals(listOf(at(0) to at(10), at(30) to at(35)), first)
        assertEquals(listOf(at(35) to at(50)), second)
    }

    @Test
    fun `beats per kilometre side by side, breaks left out`() {
        // 120 bpm at 25 km/h, then 132 bpm at the same speed; the break's resting 60 bpm counts nowhere.
        val heart = every30s(0, 20) { 120.0 } + every30s(20, 30) { 60.0 } + every30s(30, 50) { 132.0 }
        val speed = every30s(0, 50) { 25 / 3.6 }
        val drift = driftOf(heart, speed, listOf(at(0) to at(20), at(30) to at(50)))!!
        assertEquals(120.0, drift.first.heartRate, 0.001)
        assertEquals(132.0, drift.second.heartRate, 0.001)
        // 120 beats a minute over 25/60 km a minute.
        assertEquals(288.0, drift.first.beatsPerKm, 0.001)
        assertEquals(316.8, drift.second.beatsPerKm, 0.001)
    }

    @Test
    fun `too short or standing still gives nothing`() {
        val heart = every30s(0, 60) { 120.0 }
        assertNull(driftOf(heart, every30s(0, 60) { 7.0 }, listOf(at(0) to at(15))))
        assertNull(driftOf(heart, every30s(0, 60) { 0.0 }, listOf(at(0) to at(40))))
        // No speed in the second half.
        assertNull(driftOf(heart, every30s(0, 20) { 7.0 }, listOf(at(0) to at(40))))
    }
}
