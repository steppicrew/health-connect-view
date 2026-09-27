package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.rollingMean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The four-week reference line drawn behind a resting heart rate. */
class RollingMeanTest {

    private val day = LocalDate.of(2026, 9, 27)

    @Test
    fun `each date averages the four weeks ending on it`() {
        // 28 days of 50 and, before them, a week of 60 that must not count.
        val daily = (0L until 28L).associate { day.minusDays(it) to 50.0 } +
            (28L until 35L).associate { day.minusDays(it) to 60.0 }
        val (date, mean) = rollingMean(daily, day, day).single()
        assertEquals(day, date)
        assertEquals(50.0, mean, 1e-9)
    }

    @Test
    fun `the day itself counts`() {
        val daily = (1L until 28L).associate { day.minusDays(it) to 50.0 } + (day to 77.0)
        assertEquals((27 * 50.0 + 77.0) / 28, rollingMean(daily, day, day).single().second, 1e-9)
    }

    @Test
    fun `missing days are skipped, not counted as zero`() {
        val daily = (0L until 28L step 2).associate { day.minusDays(it) to 50.0 }
        assertEquals(50.0, rollingMean(daily, day, day).single().second, 1e-9)
    }

    @Test
    fun `too few days give no point rather than a guess`() {
        val daily = (0L until 5L).associate { day.minusDays(it) to 50.0 }
        assertTrue(rollingMean(daily, day, day).isEmpty())
    }

    @Test
    fun `one point per date across the window`() {
        val daily = (0L until 60L).associate { day.minusDays(it) to 50.0 }
        assertEquals(7, rollingMean(daily, day.minusDays(6), day).size)
    }
}
