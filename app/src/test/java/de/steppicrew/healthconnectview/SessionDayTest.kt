package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.endDay
import de.steppicrew.healthconnectview.health.localEnd
import de.steppicrew.healthconnectview.health.startDay
import de.steppicrew.healthconnectview.health.startZone
import de.steppicrew.healthconnectview.registry.Formatting
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

/**
 * A session's day is where it happened, not where the phone is now. Measured 07.11.2026: seen
 * from Ecuador, a walk early in the morning in Germany on the travel day fell on the day
 * before, and the workout streak broke over a day that had its workout.
 */
class SessionDayTest {

    private val ecuador: ZoneId = ZoneId.of("America/Guayaquil")
    private val germany = ZoneOffset.ofHours(1)

    private fun walk(start: OffsetDateTime, minutes: Long, offsets: Boolean = true) = Session(
        start = start.toInstant(),
        end = start.plusMinutes(minutes).toInstant(),
        title = null,
        kind = Session.Kind.EXERCISE,
        origin = "watch",
        startOffset = if (offsets) start.offset else null,
        endOffset = if (offsets) start.offset else null,
    )

    @Test
    fun `a session keeps the day it happened on after a flight`() {
        // 29.10 03:30 in Germany is still the 28th in Ecuador.
        val early = walk(OffsetDateTime.of(2026, 10, 29, 3, 30, 0, 0, germany), 15)
        assertEquals(LocalDate.of(2026, 10, 29), early.startDay(ecuador))
        assertEquals(LocalDate.of(2026, 10, 29), early.endDay(ecuador))
    }

    @Test
    fun `without a recorded offset the phone's zone decides`() {
        val early = walk(OffsetDateTime.of(2026, 10, 29, 3, 30, 0, 0, germany), 15, offsets = false)
        assertEquals(LocalDate.of(2026, 10, 28), early.endDay(ecuador))
    }

    @Test
    fun `the local clock places a session in its own day's window`() {
        val early = walk(OffsetDateTime.of(2026, 10, 29, 3, 30, 0, 0, germany), 15)
        val dayStart = LocalDate.of(2026, 10, 29).atStartOfDay(ecuador).toInstant()
        val dayEnd = LocalDate.of(2026, 10, 30).atStartOfDay(ecuador).toInstant()
        val end = early.localEnd(ecuador)
        assertEquals(true, end > dayStart && end <= dayEnd)
        // 03:45 on the clock where it happened.
        assertEquals(3, end.atZone(ecuador).hour)
    }

    @Test
    fun `a time is shown where it happened, marked where the phone's clock differs`() {
        val zone = TimeZone.getDefault()
        val locale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(ecuador))
            Locale.setDefault(Locale.GERMANY)
            val early = walk(OffsetDateTime.of(2026, 10, 29, 3, 30, 0, 0, germany), 15)
            assertEquals("03:30", Formatting.time(early.start, early.startZone))
            assertEquals(" GMT+1", Formatting.zoneMark(early.start, early.startZone))
            val here = walk(OffsetDateTime.of(2026, 11, 2, 9, 0, 0, 0, ZoneOffset.ofHours(-5)), 60)
            assertEquals("", Formatting.zoneMark(here.start, here.startZone))
        } finally {
            TimeZone.setDefault(zone)
            Locale.setDefault(locale)
        }
    }
}
