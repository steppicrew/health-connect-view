package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.HrvDay
import de.steppicrew.healthconnectview.health.HrvNight
import de.steppicrew.healthconnectview.health.HrvReading
import de.steppicrew.healthconnectview.health.HrvStanding
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.hrvDays
import de.steppicrew.healthconnectview.health.nightlyHrv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The nightly HRV value and the week-against-baseline comparison built on it.
 *
 * Health Connect has no aggregate for HRV, so every number here is computed by the app; a
 * wrong one would colour a normal week as unusual with nothing on screen to contradict it.
 */
class HrvStatusTest {

    private val zone = ZoneOffset.UTC
    private val morning = LocalDate.of(2026, 9, 27)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) =
        date.atTime(hour, minute).toInstant(zone)

    private fun night(ending: LocalDate) = Session(
        start = at(ending.minusDays(1), 23),
        end = at(ending, 7),
        title = null,
        kind = Session.Kind.SLEEP,
        origin = "com.garmin",
    )

    @Test
    fun `only readings taken asleep count towards the night`() {
        val readings = listOf(
            HrvReading(at(morning, 2), 40.0, "sync"),
            HrvReading(at(morning, 4), 60.0, "sync"),
            // After waking: the same writer keeps sampling, and a morning is a different state.
            HrvReading(at(morning, 10), 20.0, "sync"),
        )
        val nights = nightlyHrv(readings, listOf(night(morning)), zone)
        assertEquals(listOf(HrvNight(morning, 50.0)), nights)
    }

    @Test
    fun `a night copied by two writers counts once`() {
        val readings = listOf(
            HrvReading(at(morning, 1), 40.0, "sync"),
            HrvReading(at(morning, 2), 60.0, "sync"),
            HrvReading(at(morning, 3), 50.0, "sync"),
            HrvReading(at(morning, 2), 90.0, "other"),
        )
        assertEquals(50.0, nightlyHrv(readings, listOf(night(morning)), zone).single().mean, 1e-9)
    }

    @Test
    fun `a night is credited to the morning it ends on`() {
        val readings = listOf(HrvReading(at(morning.minusDays(1), 23, 30), 50.0, "sync"))
        assertEquals(morning, nightlyHrv(readings, listOf(night(morning)), zone).single().date)
    }

    @Test
    fun `the week is judged against the four weeks before it, not itself`() {
        // 28 nights of 50 to 57 before the week, then a week of 40.
        val baseline = (7L until 35L).map { HrvNight(morning.minusDays(it), 50.0 + it % 8) }
        val week = (0L until 7L).map { HrvNight(morning.minusDays(it), 40.0) }
        val day = hrvDays(baseline + week, listOf(morning)).single()

        assertEquals(40.0, day.weekMean!!, 1e-9)
        // Had the low week counted, the range would reach down towards it.
        assertEquals(51.0, day.usualLow!!, 1e-9)
        assertEquals(55.25, day.usualHigh!!, 1e-9)
        assertEquals(HrvStanding.BELOW, day.standing)
    }

    @Test
    fun `too few nights give no verdict rather than a guess`() {
        val sparse = listOf(HrvNight(morning, 50.0), HrvNight(morning.minusDays(1), 52.0))
        val day = hrvDays(sparse, listOf(morning)).single()
        assertNull(day.weekMean)
        assertNull(day.usualLow)
        assertNull(day.standing)
    }

    @Test
    fun `a week inside the range is within it`() {
        val day = HrvDay(morning, weekMean = 52.0, usualLow = 48.0, usualHigh = 56.0)
        assertEquals(HrvStanding.WITHIN, day.standing)
        assertEquals(HrvStanding.ABOVE, day.copy(weekMean = 60.0).standing)
    }
}
