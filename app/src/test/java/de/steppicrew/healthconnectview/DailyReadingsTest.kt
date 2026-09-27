package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.DailyReadings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Respiratory rate and oxygen saturation across days: a mean per bucket from thousands of
 * readings, which Health Connect cannot aggregate for these types.
 */
class DailyReadingsTest {

    private val zone = ZoneOffset.UTC
    private val first = LocalDate.of(2026, 9, 21)

    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).toInstant(zone)

    @Test
    fun `a day is the mean of its readings with their spread`() {
        val daily = DailyReadings(zone, first)
        listOf(12.0, 14.0, 16.0).forEachIndexed { i, v -> daily.add(at(first, i), v, "sync") }
        val bucket = daily.buckets().single()
        assertEquals(first, bucket.start)
        assertEquals(14.0, bucket.mean, 1e-9)
        assertEquals(12.0, bucket.low, 1e-9)
        assertEquals(16.0, bucket.high, 1e-9)
    }

    @Test
    fun `a day copied by two writers counts once`() {
        val daily = DailyReadings(zone, first)
        listOf(12.0, 14.0, 16.0).forEachIndexed { i, v -> daily.add(at(first, i), v, "sync") }
        daily.add(at(first, 1), 40.0, "other")
        assertEquals(14.0, daily.buckets().single().mean, 1e-9)
        assertEquals(16.0, daily.buckets().single().high, 1e-9)
    }

    @Test
    fun `the writer is chosen per day`() {
        val daily = DailyReadings(zone, first)
        daily.add(at(first, 1), 12.0, "a")
        daily.add(at(first.plusDays(1), 1), 20.0, "b")
        daily.add(at(first.plusDays(1), 2), 22.0, "b")
        daily.add(at(first.plusDays(1), 3), 99.0, "a")
        assertEquals(listOf(12.0, 21.0), daily.buckets().map { it.mean })
    }

    @Test
    fun `a year's buckets are weeks from the window's first day, weighted by readings`() {
        val daily = DailyReadings(zone, first, bucketDays = 7)
        daily.add(at(first, 1), 10.0, "sync")
        daily.add(at(first.plusDays(6), 1), 20.0, "sync")
        daily.add(at(first.plusDays(6), 2), 20.0, "sync")
        daily.add(at(first.plusDays(7), 1), 30.0, "sync")
        val buckets = daily.buckets()
        assertEquals(listOf(first, first.plusDays(7)), buckets.map { it.start })
        assertEquals(50.0 / 3, buckets[0].mean, 1e-9)
        assertEquals(10.0, buckets[0].low, 1e-9)
    }

    @Test
    fun `days before the window feed the daily means but not the buckets`() {
        val daily = DailyReadings(zone, first)
        daily.add(at(first.minusDays(3), 1), 10.0, "sync")
        daily.add(at(first, 1), 20.0, "sync")
        assertEquals(1, daily.buckets().size)
        assertEquals(2, daily.dailyMeans().size)
        assertEquals(20.0, daily.overallMean()!!, 1e-9)
    }

    @Test
    fun `no readings give no mean`() {
        assertNull(DailyReadings(zone, first).overallMean())
    }
}
