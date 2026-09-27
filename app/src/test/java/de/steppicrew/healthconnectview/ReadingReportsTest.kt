package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.GlucoseBand
import de.steppicrew.healthconnectview.health.GlucoseReading
import de.steppicrew.healthconnectview.health.Reading
import de.steppicrew.healthconnectview.health.glucoseReport
import de.steppicrew.healthconnectview.health.oneWriterPerDay
import de.steppicrew.healthconnectview.health.restingReport
import de.steppicrew.healthconnectview.health.weightReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** What the weight, resting heart rate and glucose reports say, before any of it is drawn. */
class ReadingReportsTest {

    private val zone = ZoneOffset.UTC
    private val sep1 = LocalDate.of(2026, 9, 1) // a Tuesday

    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).toInstant(zone)

    @Test
    fun `a weigh-in copied by a second app is listed once`() {
        val readings = listOf(
            Reading(at(sep1, 7), 82.0, "scale"),
            Reading(at(sep1, 7, 1), 82.0, "sync"),
        )
        assertEquals(1, oneWriterPerDay(readings, zone).size)
    }

    @Test
    fun `weight gives first, last, change and weeks from Monday`() {
        val readings = listOf(
            Reading(at(sep1, 7), 83.0, "scale"),
            Reading(at(sep1.plusDays(6), 7), 82.0, "scale"), // Monday 7 Sept
            Reading(at(sep1.plusDays(8), 7), 81.5, "scale"),
            Reading(at(sep1.plusDays(40), 7), 70.0, "scale"), // outside
        )
        val report = weightReport(readings, sep1, sep1.plusDays(29), zone)
        assertEquals(3, report.readings.size)
        assertEquals(-1.5, report.change!!, 1e-9)
        assertEquals(listOf(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 7)), report.weeks.map { it.start })
        assertEquals(81.75, report.weeks[1].stats.mean, 1e-9)
    }

    @Test
    fun `resting days carry the four-week mean from before the window`() {
        val first = sep1.plusDays(27)
        val readings = (0L..27L).map { Reading(at(sep1.plusDays(it), 6), if (it < 27) 50.0 else 64.0, "watch") }
        val report = restingReport(readings, first, first, zone)
        val day = report.days.single()
        assertEquals(64.0, day.value, 1e-9)
        assertEquals((27 * 50.0 + 64.0) / 28, day.rolling!!, 1e-9)
        assertNull("a four-day window has no first and last month", report.firstMonth)
    }

    @Test
    fun `a long resting window compares its first and last four weeks`() {
        val readings = (0L until 90L).map { Reading(at(sep1.plusDays(it), 6), if (it < 45) 50.0 else 56.0, "watch") }
        val report = restingReport(readings, sep1, sep1.plusDays(89), zone)
        assertEquals(50.0, report.firstMonth!!.mean, 1e-9)
        assertEquals(56.0, report.lastMonth!!.mean, 1e-9)
    }

    @Test
    fun `glucose bands meet in either unit`() {
        assertEquals(GlucoseBand.LOW, GlucoseBand.of(3.8))
        assertEquals(GlucoseBand.LOW, GlucoseBand.of(69 / 18.0))
        assertEquals(GlucoseBand.IN_RANGE, GlucoseBand.of(70 / 18.0))
        assertEquals(GlucoseBand.IN_RANGE, GlucoseBand.of(3.9))
        assertEquals(GlucoseBand.IN_RANGE, GlucoseBand.of(10.0))
        assertEquals(GlucoseBand.HIGH, GlucoseBand.of(181 / 18.0))
        assertEquals(GlucoseBand.HIGH, GlucoseBand.of(13.9))
        assertEquals(GlucoseBand.HIGH, GlucoseBand.of(250 / 18.0))
        assertEquals(GlucoseBand.VERY_HIGH, GlucoseBand.of(251 / 18.0))
        assertEquals(GlucoseBand.LOW, GlucoseBand.of(3.0))
        assertEquals(GlucoseBand.VERY_LOW, GlucoseBand.of(2.9))
    }

    @Test
    fun `fasting and after-meal readings are summarised apart, fasting first`() {
        val readings = listOf(
            GlucoseReading(at(sep1, 12), 8.0, 4, 2, "meter"),
            GlucoseReading(at(sep1, 7), 5.0, 2, 1, "meter"),
            GlucoseReading(at(sep1, 8), 6.0, 2, 1, "meter"),
        )
        val report = glucoseReport(readings, sep1, sep1, zone)
        assertEquals(listOf(2, 4), report.byRelation.map { it.first })
        assertEquals(5.5, report.byRelation[0].second.mean, 1e-9)
        assertEquals(listOf(0, 0, 3, 0, 0), report.bands)
        assertEquals(at(sep1, 7), report.readings.first().time)
        assertNotNull(report.overall)
    }
}
