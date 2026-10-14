package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.dedupeSessions
import de.steppicrew.healthconnectview.health.widenToSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import androidx.health.connect.client.records.ExerciseSessionRecord
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
        type: Int? = null,
    ) = Session(
        start = base.plusSeconds(startMin * 60),
        end = base.plusSeconds((startMin + durationMin) * 60),
        title = title,
        kind = kind,
        origin = origin,
        exerciseType = type,
    )

    /** A session at clock seconds after [base], for the measured day below. */
    private fun at(startSec: Long, endSec: Long, origin: String, type: Int, title: String? = null) = Session(
        start = base.plusSeconds(startSec),
        end = base.plusSeconds(endSec),
        title = title,
        kind = Session.Kind.EXERCISE,
        origin = origin,
        exerciseType = type,
    )

    private val bike = ExerciseSessionRecord.EXERCISE_TYPE_BIKING
    private val stationary = ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
    private val strength = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
    private val garmin = "com.garmin.android.apps.connectmobile"
    private val sync = "nl.appyhapps.healthsync"
    private val machine = "com.lifefitness.connect"

    /**
     * 14.10.2026 on the phone, in seconds after 05:00: the machine's app wrote its 53-minute
     * ride twice, once 53 minutes early, and that copy hid the strength session it overlapped.
     */
    private val morning = listOf(
        at(12 * 60 + 2, 65 * 60 + 2, machine, stationary, "Heimtrainer"),
        at(39 * 60 + 9, 62 * 60 + 52, garmin, strength),
        at(39 * 60 + 9, 62 * 60 + 52, sync, strength),
        at(64 * 60 + 57, 117 * 60 + 57, garmin, bike),
        at(64 * 60 + 57, 117 * 60 + 57, sync, bike),
        at(65 * 60 + 3, 118 * 60 + 3, machine, stationary, "Heimtrainer"),
    )

    @Test
    fun `a stray copy of a ride neither hides the strength session nor shows itself`() {
        val kept = dedupeSessions(morning)

        assertEquals(listOf(strength, stationary), kept.map { it.exerciseType })
        assertEquals(base.plusSeconds(39 * 60 + 9), kept.first().start)
        assertEquals("Heimtrainer", kept.last().title)
    }

    @Test
    fun `rides by two writers that only touch are both kept`() {
        val kept = dedupeSessions(listOf(session(0, 30, null, "a", type = bike), session(29, 30, null, "b", type = bike)))

        assertEquals(2, kept.size)
    }

    @Test
    fun `different workouts overlapping are both kept`() {
        val kept = dedupeSessions(listOf(session(0, 60, null, "a", type = bike), session(10, 30, null, "b", type = strength)))

        assertEquals(2, kept.size)
    }

    @Test
    fun `a writer's two equal workouts back to back are both kept without a witness against one`() {
        // Two real intervals of one length, nothing else written: no copy is confirmed, so
        // neither can be the echo of the other.
        val kept = dedupeSessions(listOf(session(0, 20, null, machine, type = stationary), session(20, 20, null, machine, type = stationary)))

        assertEquals(2, kept.size)
    }

    @Test
    fun `two types outside every family merge only when they are the same`() {
        val golf = ExerciseSessionRecord.EXERCISE_TYPE_GOLF
        val dance = ExerciseSessionRecord.EXERCISE_TYPE_DANCING

        assertEquals(2, dedupeSessions(listOf(session(0, 60, null, "a", type = golf), session(0, 60, null, "b", type = dance))).size)
        assertEquals(1, dedupeSessions(listOf(session(0, 60, null, "a", type = golf), session(0, 60, null, "b", type = golf))).size)
    }

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

    /**
     * The day's plot range grows backwards for a night that began the evening before.
     *
     * Measured on the phone: a night of 22:18 to 08:58 on a midnight-pinned axis was drawn
     * 00:00-08:58, because the chart clamps anything outside its extent onto the plot edge.
     * The headline above it read the true 10h 40m, so one screen gave two answers to how long
     * the night was.
     */
    @Test
    fun `a night starting before midnight widens the day backwards`() {
        val midnight = Instant.parse("2026-09-19T00:00:00Z")
        val nextMidnight = midnight.plusSeconds(24 * 3600)
        val night = Session(
            start = midnight.minusSeconds(102 * 60), // 22:18 the previous evening
            end = midnight.plusSeconds((8 * 60 + 58) * 60),
            title = null,
            kind = Session.Kind.SLEEP,
            origin = "com.garmin.android.apps.connectmobile",
        )

        val widened = widenToSessions(midnight..nextMidnight, listOf(night))

        assertEquals(night.start, widened.start)
        assertEquals(nextMidnight, widened.endInclusive)
        assertTrue("the whole night must fit inside", night.end <= widened.endInclusive)
    }

    /** A day whose sessions sit inside it keeps the fixed midnight-to-midnight axis. */
    @Test
    fun `a day containing its sessions is left alone`() {
        val midnight = Instant.parse("2026-09-19T00:00:00Z")
        val day = midnight..midnight.plusSeconds(24 * 3600)
        val nap = Session(
            start = midnight.plusSeconds(13 * 3600),
            end = midnight.plusSeconds(14 * 3600),
            title = null,
            kind = Session.Kind.SLEEP,
            origin = "com.garmin.android.apps.connectmobile",
        )

        assertSame(day, widenToSessions(day, listOf(nap)))
        assertSame(day, widenToSessions(day, emptyList()))
    }

    /**
     * Never forwards. A session running past the end belongs to the next day -- sleep is
     * selected by its end so it cannot occur, and an exercise crossing midnight is shown on
     * the day it began. Widening forward would pull tomorrow's evening onto today's axis.
     */
    @Test
    fun `a session running past the end does not widen the day forwards`() {
        val midnight = Instant.parse("2026-09-19T00:00:00Z")
        val nextMidnight = midnight.plusSeconds(24 * 3600)
        val lateRun = Session(
            start = midnight.plusSeconds(23 * 3600),
            end = nextMidnight.plusSeconds(3600),
            title = null,
            kind = Session.Kind.EXERCISE,
            origin = "com.garmin.android.apps.connectmobile",
        )

        val widened = widenToSessions(midnight..nextMidnight, listOf(lateRun))
        assertEquals(nextMidnight, widened.endInclusive)
    }
}
