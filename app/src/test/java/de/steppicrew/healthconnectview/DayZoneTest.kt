package de.steppicrew.healthconnectview

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
}
