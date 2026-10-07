package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Suggestion
import de.steppicrew.healthconnectview.health.WriterCoverage
import de.steppicrew.healthconnectview.health.suggestWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SourceCoverageTest {

    @Test
    fun `the writer on most days is named, however few entries it writes`() {
        val watch = WriterCoverage("watch", days = 29, records = 29 * 1400)
        val scale = WriterCoverage("phone", days = 30, records = 30 * 3)
        assertEquals(Suggestion("phone", Suggestion.Reason.MOST_DAYS), suggestWriter(listOf(watch, scale)))
    }

    @Test
    fun `equal days are decided by entries a day`() {
        val sparse = WriterCoverage("sync", days = 30, records = 300)
        val dense = WriterCoverage("watch", days = 30, records = 30_000)
        assertEquals(Suggestion("watch", Suggestion.Reason.MOST_ENTRIES), suggestWriter(listOf(sparse, dense)))
    }

    @Test
    fun `equal in days and density, the longer history is named`() {
        val watch = WriterCoverage("watch", days = 30, records = 30, since = LocalDate.of(2025, 7, 13))
        val sync = WriterCoverage("sync", days = 30, records = 30, since = LocalDate.of(2025, 7, 24))
        assertEquals(Suggestion("watch", Suggestion.Reason.LONGEST_HISTORY), suggestWriter(listOf(sync, watch)))
    }

    @Test
    fun `two equal writers and a single writer name nobody`() {
        val a = WriterCoverage("a", days = 30, records = 600)
        val b = WriterCoverage("b", days = 30, records = 600)
        assertNull(suggestWriter(listOf(a, b)))
        assertNull(suggestWriter(listOf(a)))
    }
}
