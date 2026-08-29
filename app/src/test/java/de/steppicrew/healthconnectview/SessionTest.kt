package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.dedupeSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

/**
 * The same workout arrives from several apps and they disagree about what it was. Measured on
 * a real device, one indoor bike session was written by three: a watch called it outdoor
 * biking, the machine's own app called it stationary and named it "Heimtrainer".
 */
class SessionTest {

    private val base: Instant = Instant.parse("2026-08-28T06:03:00Z")

    private fun session(
        startMin: Long,
        durationMin: Long,
        title: String?,
        origin: String,
        kind: Session.Kind = Session.Kind.EXERCISE,
    ) = Session(
        start = base.plusSeconds(startMin * 60),
        end = base.plusSeconds((startMin + durationMin) * 60),
        title = title,
        kind = kind,
        origin = origin,
    )

    @Test
    fun `three writers of one workout collapse to a single session`() {
        val sessions = listOf(
            session(0, 53, null, "com.garmin.android.apps.connectmobile"),
            session(0, 53, null, "nl.appyhapps.healthsync"),
            session(0, 53, "Heimtrainer", "com.lifefitness.connect"),
        )
        assertEquals(1, dedupeSessions(sessions).size)
    }

    @Test
    fun `the writer that named the activity wins`() {
        val sessions = listOf(
            session(0, 53, null, "com.garmin.android.apps.connectmobile"),
            session(0, 53, "Heimtrainer", "com.lifefitness.connect"),
        )
        assertEquals("Heimtrainer", dedupeSessions(sessions).single().title)
    }

    @Test
    fun `a named session is not replaced by an unnamed one arriving later`() {
        val sessions = listOf(
            session(0, 53, "Heimtrainer", "com.lifefitness.connect"),
            session(1, 52, null, "com.garmin.android.apps.connectmobile"),
        )
        assertEquals("Heimtrainer", dedupeSessions(sessions).single().title)
    }

    @Test
    fun `sessions that do not overlap are all kept`() {
        val sessions = listOf(
            session(0, 53, "Heimtrainer", "a"),
            session(120, 23, "Stärke deinen Rücken", "a"),
            session(300, 34, "Berlin Mountainbiken", "a"),
        )
        assertEquals(3, dedupeSessions(sessions).size)
    }

    @Test
    fun `sleep and exercise overlapping do not collapse into each other`() {
        val sessions = listOf(
            session(0, 60, "Nap", "a", Session.Kind.SLEEP),
            session(10, 30, "Strength", "b", Session.Kind.EXERCISE),
        )
        assertEquals(2, dedupeSessions(sessions).size)
    }

    @Test
    fun `results are ordered by start time`() {
        val sessions = listOf(
            session(300, 34, "late", "a"),
            session(0, 53, "early", "a"),
        )
        val result = dedupeSessions(sessions)
        assertEquals("early", result.first().title)
        assertEquals("late", result.last().title)
    }

    @Test
    fun `an untitled session still survives so the band is drawn`() {
        val result = dedupeSessions(listOf(session(0, 53, null, "a")))
        assertEquals(1, result.size)
        assertNotNull(result.single().start)
    }

    @Test
    fun `an empty list yields no sessions`() {
        assertEquals(emptyList<Session>(), dedupeSessions(emptyList()))
    }
}
