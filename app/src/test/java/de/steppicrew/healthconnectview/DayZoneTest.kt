package de.steppicrew.healthconnectview

import java.time.Duration
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import de.steppicrew.healthconnectview.health.ClockChange
import de.steppicrew.healthconnectview.health.clockChanges
import de.steppicrew.healthconnectview.health.clockShifts
import de.steppicrew.healthconnectview.health.zoneOfDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A day is drawn on the clock it was lived on. Measured 07.11.2026: seen from Ecuador, the
 * travel day's walk at 04:56 in Germany sat squashed at 00:00 of a day on Ecuador's clock.
 */
class DayZoneTest {

    private val ecuador: ZoneId = ZoneId.of("America/Guayaquil")
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val germany = ZoneOffset.ofHours(1)
    private val here = ZoneOffset.ofHours(-5)

    private fun at(date: LocalDate, hour: Int, offset: ZoneOffset) =
        OffsetDateTime.of(date.atTime(hour, 0), offset).let { it.toInstant() to offset }

    @Test
    fun `a day at home is drawn on its own clock after the flight`() {
        val day = LocalDate.of(2026, 10, 27)
        val moments = (6..22).map { at(day, it, germany) }
        assertEquals(germany, zoneOfDay(day, moments, ecuador))
    }

    @Test
    fun `a day where the phone is keeps the phone's zone`() {
        val day = LocalDate.of(2026, 11, 2)
        val moments = (6..22).map { at(day, it, here) }
        assertEquals(ecuador, zoneOfDay(day, moments, ecuador))
    }

    @Test
    fun `the travel day goes by the clock most of its records were on`() {
        val day = LocalDate.of(2026, 10, 29)
        val moments = (0..15).map { at(day, it, germany) } + (14..20).map { at(day, it, here) }
        assertEquals(germany, zoneOfDay(day, moments, ecuador))
    }

    @Test
    fun `a clock change at home stays in the phone's zone`() {
        // 25.10.2026: summer time ends in Germany, so the day has +02:00 and +01:00 readings.
        val day = LocalDate.of(2026, 10, 25)
        val moments = (0..1).map { at(day, it, ZoneOffset.ofHours(2)) } + (4..22).map { at(day, it, germany) }
        assertEquals(berlin, zoneOfDay(day, moments, berlin))
    }

    @Test
    fun `without records the phone's zone`() {
        assertEquals(ecuador, zoneOfDay(LocalDate.of(2026, 10, 29), emptyList(), ecuador))
    }

    @Test
    fun `records of neighbouring days do not count`() {
        val day = LocalDate.of(2026, 10, 29)
        val moments = (0..23).map { at(day.minusDays(1), it, germany) } + listOf(at(day, 12, here))
        assertEquals(ecuador, zoneOfDay(day, moments, ecuador))
    }

    @Test
    fun `flying east, the last hours of the day also open the next`() {
        // Back from Ecuador: the 22nd on -05:00, the 23rd on Germany's clock.
        val ecuador = ZoneOffset.ofHours(-5)
        val berlin = ZoneId.of("Europe/Berlin")
        val change = clockChanges(LocalDate.of(2026, 11, 22), ecuador, previous = ecuador, next = berlin).single()
        assertEquals(ClockChange.Kind.DOUBLED_AT_END, change.kind)
        assertEquals(Duration.ofHours(6), change.length)
        assertEquals(LocalDate.of(2026, 11, 23).atStartOfDay(berlin).toInstant(), change.span.start)
        // The 23rd sees the same six hours from its side.
        val next = clockChanges(LocalDate.of(2026, 11, 23), berlin, previous = ecuador, next = berlin).single()
        assertEquals(ClockChange.Kind.DOUBLED_AT_START, next.kind)
        assertEquals(change.span, next.span)
    }

    @Test
    fun `flying west, hours fall between the two days`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val change = clockChanges(LocalDate.of(2026, 11, 7), berlin, previous = berlin, next = ZoneOffset.ofHours(-5)).single()
        assertEquals(ClockChange.Kind.MISSING_AFTER, change.kind)
        assertEquals(Duration.ofHours(6), change.length)
        assertFalse(change.onChart)
    }

    @Test
    fun `winter time marks the hour that came twice, and a plain day nothing`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val change = clockChanges(LocalDate.of(2026, 10, 25), berlin, berlin, berlin).single()
        assertEquals(ClockChange.Kind.SUMMER_TIME, change.kind)
        assertEquals(Duration.ofHours(25), change.dayLength)
        assertEquals(Duration.ofHours(1), change.length)
        assertTrue(clockChanges(LocalDate.of(2026, 11, 24), berlin, berlin, berlin).isEmpty())
        assertTrue(clockChanges(LocalDate.of(2026, 11, 22), ZoneOffset.ofHours(-5), null, null).isEmpty())
    }

    @Test
    fun `the clock changed between the last workout or night on the old clock and the first on the new`() {
        val galapagos = ZoneOffset.ofHours(-6)
        val ecuador = ZoneOffset.ofHours(-5)
        val berlin = ZoneOffset.ofHours(1)
        val day = LocalDate.of(2026, 11, 22)
        fun at(hour: Int, minute: Int, offset: ZoneOffset, date: LocalDate = day) = date.atTime(hour, minute).atOffset(offset).toInstant()
        val moments = listOf(
            at(6, 16, galapagos) to galapagos, // the night on the islands ends
            at(16, 51, ecuador) to ecuador, // the hike near Quito
            at(17, 43, ecuador) to ecuador,
            at(6, 36, berlin, day.plusDays(1)) to berlin, // the night in Berlin begins
        )
        val shown = day.atStartOfDay(ecuador).toInstant()..day.plusDays(1).atStartOfDay(ecuador).toInstant()
        val shifts = clockShifts(moments, shown)
        assertEquals(2, shifts.size)
        assertEquals(galapagos, shifts[0].from)
        assertEquals(at(6, 16, galapagos)..at(16, 51, ecuador), shifts[0].window)
        // The second runs past the day's end, into the 23rd.
        assertEquals(at(17, 43, ecuador)..at(6, 36, berlin, day.plusDays(1)), shifts[1].window)
        // A day the changes do not touch has none.
        val later = day.plusDays(2).atStartOfDay(berlin).toInstant()..day.plusDays(3).atStartOfDay(berlin).toInstant()
        assertTrue(clockShifts(moments, later).isEmpty())
    }
}
