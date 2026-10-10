package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.nightsByMorning
import de.steppicrew.healthconnectview.health.splitByTraining
import de.steppicrew.healthconnectview.health.trainingDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TrainingNightsTest {

    private val zone = ZoneOffset.UTC

    private fun session(from: String, to: String, kind: Session.Kind) =
        Session(Instant.parse(from), Instant.parse(to), title = null, kind = kind, origin = "app")

    private fun day(n: Int): LocalDate = LocalDate.of(2026, 10, n)

    @Test
    fun `a short walk does not make a training day`() {
        val ride = session("2026-10-03T09:00:00Z", "2026-10-03T10:30:00Z", Session.Kind.EXERCISE)
        val walk = session("2026-10-04T12:00:00Z", "2026-10-04T12:10:00Z", Session.Kind.EXERCISE)
        val nap = session("2026-10-05T13:00:00Z", "2026-10-05T14:00:00Z", Session.Kind.SLEEP)
        assertEquals(setOf(day(3)), trainingDays(listOf(ride, walk, nap), zone))
    }

    @Test
    fun `a night is the longest sleep ending on its morning`() {
        val night = session("2026-10-04T22:00:00Z", "2026-10-05T06:00:00Z", Session.Kind.SLEEP)
        val nap = session("2026-10-05T13:00:00Z", "2026-10-05T14:00:00Z", Session.Kind.SLEEP)
        assertEquals(mapOf(day(5) to night), nightsByMorning(listOf(night, nap), zone))
    }

    @Test
    fun `nights are split by the day before their morning`() {
        // Training on the 1st, 3rd and 5th: the mornings of the 2nd, 4th and 6th follow them.
        val lows = mapOf(day(2) to 52.0, day(4) to 54.0, day(6) to 53.0, day(3) to 48.0, day(5) to 50.0, day(7) to 49.0)
        val split = splitByTraining(lows, setOf(day(1), day(3), day(5)))!!
        assertEquals(53.0, split.afterTraining!!.meanLow, 0.001)
        assertEquals(3, split.afterTraining!!.nights)
        assertEquals(49.0, split.afterRest!!.meanLow, 0.001)
        assertEquals(3, split.afterRest!!.nights)
    }

    @Test
    fun `too few nights in a group give it no mean`() {
        val lows = mapOf(day(2) to 52.0, day(3) to 48.0, day(4) to 49.0, day(5) to 50.0)
        val split = splitByTraining(lows, setOf(day(1)))!!
        assertNull(split.afterTraining)
        assertEquals(3, split.afterRest!!.nights)
        assertNull(splitByTraining(mapOf(day(2) to 52.0), setOf(day(1))))
    }
}
