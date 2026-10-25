package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.dedupeSessions
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** What a workout carries beside its numbers: notes, segments and the plan it followed. */
class WorkoutDetailsTest {

    private val t0: Instant = Instant.parse("2026-10-25T08:00:00Z")
    private val end: Instant = t0.plusSeconds(3600)

    @Test
    fun `the named copy of a workout keeps the other copy's note`() {
        val watch = Session(t0, end, null, Session.Kind.EXERCISE, "watch", notes = "Knee fine")
        val machine = Session(t0.plusSeconds(60), end, "Indoor bike", Session.Kind.EXERCISE, "machine")
        val kept = dedupeSessions(listOf(watch, machine)).single()
        assertEquals("Indoor bike", kept.title)
        assertEquals("Knee fine", kept.notes)
    }
}
