package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.TallyRecord
import de.steppicrew.healthconnectview.health.atLeast
import de.steppicrew.healthconnectview.health.openTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Today's total must not read below what one writer has already reported.
 *
 * Measured on the phone at 10:32: Garmin's running tally, labelled 00:00-23:59, said 906 kcal
 * while Health Connect's total up to now said 786, because the platform spread the record
 * across the whole day and kept only the elapsed share.
 */
class OpenTallyTest {

    private val now = Instant.parse("2026-09-27T08:32:00Z")
    private val laterToday = Instant.parse("2026-09-27T21:59:00Z")
    private val earlier = Instant.parse("2026-09-27T06:00:00Z")

    @Test
    fun `a record reaching past now sets the floor`() {
        val tally = openTally(listOf(TallyRecord("garmin", laterToday, 906.0)), now)
        assertEquals(906.0, tally!!, 0.0)
        assertEquals(906.0, atLeast(786.0, tally)!!, 0.0)
    }

    @Test
    fun `a day whose records have all ended is left to the platform`() {
        assertNull(openTally(listOf(TallyRecord("garmin", earlier, 906.0)), now))
    }

    @Test
    fun `writers are compared, never added`() {
        val tally = openTally(
            listOf(
                TallyRecord("garmin", laterToday, 906.0),
                TallyRecord("sync", earlier, 500.0),
                TallyRecord("sync", earlier, 300.0),
            ),
            now,
        )
        assertEquals(906.0, tally!!, 0.0)
    }

    @Test
    fun `a higher platform total stands`() {
        assertEquals(1200.0, atLeast(1200.0, 906.0)!!, 0.0)
        assertNull(atLeast(null, null))
    }
}
