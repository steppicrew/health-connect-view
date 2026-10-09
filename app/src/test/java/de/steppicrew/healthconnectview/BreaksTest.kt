package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Break
import de.steppicrew.healthconnectview.health.activePieces
import de.steppicrew.healthconnectview.health.breaksIn
import de.steppicrew.healthconnectview.health.combinePieces
import de.steppicrew.healthconnectview.health.movementIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Measured on the phone, 18.09.2026: a ride from 09:37 with a break from minute 64 to 405,
 * shown only by the movement readings stopping. Times here are minutes from the start.
 */
class BreaksTest {

    private val base: Instant = Instant.parse("2026-09-18T07:37:00Z")

    private fun at(minute: Long): Instant = base.plusSeconds(minute * 60)

    private fun everyMinute(from: Long, to: Long) = (from..to).map(::at)

    @Test
    fun `a long hole in the movement readings is a break`() {
        val times = everyMinute(0, 64) + everyMinute(405, 469)
        assertEquals(listOf(Break(at(64), at(405))), breaksIn(times))
    }

    @Test
    fun `a short stop is not a break`() {
        val times = everyMinute(0, 30) + everyMinute(34, 60)
        assertTrue(breaksIn(times).isEmpty())
    }

    @Test
    fun `readings in any order give the same breaks`() {
        val times = everyMinute(405, 469) + everyMinute(0, 64)
        assertEquals(listOf(Break(at(64), at(405))), breaksIn(times))
    }

    @Test
    fun `missing edges are not breaks`() {
        // Recording began 20 minutes late and ended 30 minutes early; nothing in between.
        assertTrue(breaksIn(everyMinute(20, 439)).isEmpty())
    }

    @Test
    fun `halts count against moving time but are not breaks`() {
        // Readings every 10 s, a red light of 90 s at minute 20 and the long break.
        val seconds = (0L..64 * 60 step 10).filter { it !in 1201L..1289L } + (405L * 60..469 * 60 step 10)
        val movement = movementIn(at(0), at(469), seconds.map { base.plusSeconds(it) })!!
        assertEquals(listOf(Break(at(64), at(405))), movement.breaks)
        assertEquals(Duration.ofMinutes(128).minusSeconds(90), movement.moving)
    }

    @Test
    fun `a writer recording every 30 s is not stopped between readings`() {
        val seconds = (0L..60 * 60 step 30)
        val movement = movementIn(at(0), at(60), seconds.map { base.plusSeconds(it) })!!
        assertEquals(Duration.ofMinutes(60), movement.moving)
    }

    @Test
    fun `too few readings say nothing about stops`() {
        assertNull(movementIn(at(0), at(60), listOf(at(1))))
    }

    @Test
    fun `active pieces are the window less its breaks`() {
        val pieces = activePieces(at(0), at(469), listOf(Break(at(64), at(405))))
        assertEquals(listOf(at(0) to at(64), at(405) to at(469)), pieces)
    }

    @Test
    fun `no breaks leave the whole window`() {
        assertEquals(listOf(at(0) to at(60)), activePieces(at(0), at(60), emptyList()))
    }

    @Test
    fun `a break reaching past the window is cut at its edge`() {
        val pieces = activePieces(at(0), at(60), listOf(Break(at(50), at(90))))
        assertEquals(listOf(at(0) to at(50)), pieces)
    }

    @Test
    fun `totals add up and means are weighted by time`() {
        val hour = Duration.ofHours(1)
        assertEquals(30.0, combinePieces(listOf(10.0 to hour, 20.0 to hour), averaged = false)!!, 1e-9)
        // An hour at 140 and twenty minutes at 100: not the plain mean of 120.
        assertEquals(130.0, combinePieces(listOf(140.0 to hour, 100.0 to Duration.ofMinutes(20)), averaged = true)!!, 1e-9)
        assertNull(combinePieces(emptyList(), averaged = true))
    }
}
