package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.clockSpread
import de.steppicrew.healthconnectview.health.sleepRegularity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class SleepRegularityTest {

    private val zone = ZoneOffset.UTC

    private fun at(hours: Int, minutes: Int = 0) = hours * 60 + minutes

    @Test
    fun `bedtimes either side of midnight are close, not a day apart`() {
        val spread = clockSpread(listOf(at(23, 30), at(23, 45), at(0, 0), at(0, 15), at(0, 30)))
        assertEquals(at(0), spread.usual)
        assertEquals(at(23, 45), spread.early)
        assertEquals(at(0, 15), spread.late)
    }

    @Test
    fun `a middle half lies between the quarter marks`() {
        val spread = clockSpread(listOf(at(6), at(6, 30), at(7), at(7, 30), at(8)))
        assertEquals(at(7), spread.usual)
        assertEquals(at(6, 30), spread.early)
        assertEquals(at(7, 30), spread.late)
    }

    @Test
    fun `a night worker's mornings are as regular as anyone's evenings`() {
        val spread = clockSpread(listOf(at(8, 50), at(9), at(9, 10), at(9), at(9)))
        assertEquals(at(9), spread.usual)
    }

    @Test
    fun `nights give their starts and ends`() {
        val first = LocalDate.of(2026, 10, 1)
        val nights = (0L until 7L).associate { i ->
            val morning = first.plusDays(i)
            val start = LocalDateTime.of(morning.minusDays(1), java.time.LocalTime.of(23, 0)).plusMinutes(i * 10)
            val end = LocalDateTime.of(morning, java.time.LocalTime.of(6, 30))
            morning to Session(start.toInstant(zone), end.toInstant(zone), null, Session.Kind.SLEEP, "watch")
        }
        val regularity = sleepRegularity(nights, zone)!!
        assertEquals(7, regularity.nights)
        assertEquals(at(23, 30), regularity.start.usual)
        assertEquals(at(6, 30), regularity.end.usual)
        assertEquals(at(6, 30), regularity.end.early)
    }

    @Test
    fun `four nights are too few`() {
        val nights = (0L until 4L).associate { i ->
            val morning = LocalDate.of(2026, 10, 1).plusDays(i)
            morning to Session(
                morning.atStartOfDay().minusHours(1).toInstant(zone),
                morning.atTime(7, 0).toInstant(zone),
                null,
                Session.Kind.SLEEP,
                "watch",
            )
        }
        assertNull(sleepRegularity(nights, zone))
    }
}
