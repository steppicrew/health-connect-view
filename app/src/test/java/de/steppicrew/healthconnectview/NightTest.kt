package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.night
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class NightTest {

    private fun session(from: String, to: String, kind: Session.Kind = Session.Kind.SLEEP) =
        Session(Instant.parse(from), Instant.parse(to), title = null, kind = kind, origin = "app")

    @Test
    fun `an afternoon nap does not take the night's place`() {
        val night = session("2026-10-09T21:30:00Z", "2026-10-10T05:15:00Z")
        val nap = session("2026-10-10T12:40:00Z", "2026-10-10T13:20:00Z")
        assertEquals(night, listOf(night, nap).night())
    }

    @Test
    fun `a workout is never the night, and no sleep gives none`() {
        val ride = session("2026-10-10T08:00:00Z", "2026-10-10T16:00:00Z", Session.Kind.EXERCISE)
        val nap = session("2026-10-10T12:40:00Z", "2026-10-10T13:20:00Z")
        assertEquals(nap, listOf(ride, nap).night())
        assertNull(listOf(ride).night())
    }
}
