package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.singleStream
import de.steppicrew.healthconnectview.registry.Point
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SingleStreamTest {

    private val base: Instant = Instant.parse("2026-10-09T05:37:00Z")

    private fun at(seconds: Long, bpm: Double) = Point(base.plusSeconds(seconds), bpm)

    @Test
    fun `a sparse record inside a dense one adds nothing there`() {
        // A workout recorded every second, and an all-day record every 15 s through it and on.
        val workout = (0L..120L).map { at(it, 110.0) }
        val allDay = (-60L..240L step 15).map { at(it, 80.0) }
        val stream = singleStream(listOf(allDay, workout))
        // Inside the workout's span only its own samples; the all-day ones before and after stay.
        assertEquals(121, stream.count { it.value == 110.0 })
        assertEquals(stream.filter { it.value == 80.0 }.map { it.time },
            allDay.filter { it.time < base || it.time > base.plusSeconds(120) }.map { it.time })
        assertEquals(stream.sortedBy { it.time }, stream)
    }

    @Test
    fun `back-to-back records join whole`() {
        val first = (0L..55L step 5).map { at(it, 100.0) }
        val second = (60L..115L step 5).map { at(it, 101.0) }
        assertEquals(first.size + second.size, singleStream(listOf(second, first)).size)
    }
}
