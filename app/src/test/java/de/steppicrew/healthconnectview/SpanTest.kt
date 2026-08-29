package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Span arithmetic decides which data is reachable at all, and a silent off-by-one shows up as
 * missing history rather than as an error -- the same failure mode that hid a year of data
 * before. Fixed "today" values keep these deterministic.
 */
class SpanTest {

    private val today = LocalDate.of(2026, 8, 29)

    @Test
    fun `offset zero includes today`() {
        Span.entries.forEach { span ->
            val end = span.endDate(0, today)
            assertTrue("${span.name} excludes today", end.isAfter(today))
        }
    }

    @Test
    fun `a week span covers seven days`() {
        assertEquals(LocalDate.of(2026, 8, 23), Span.WEEK.startDate(0, today))
        assertEquals(LocalDate.of(2026, 8, 30), Span.WEEK.endDate(0, today))
    }

    @Test
    fun `stepping back moves by exactly one span`() {
        assertEquals(LocalDate.of(2026, 8, 23), Span.WEEK.endDate(1, today))
        assertEquals(LocalDate.of(2026, 8, 16), Span.WEEK.startDate(1, today))
    }

    @Test
    fun `windows tile without gaps or overlap`() {
        Span.entries.forEach { span ->
            (0..5).forEach { offset ->
                assertEquals(
                    "${span.name} leaves a gap at offset $offset",
                    span.startDate(offset, today),
                    span.endDate(offset + 1, today),
                )
            }
        }
    }

    @Test
    fun `a year step lands on the same calendar date`() {
        assertEquals(LocalDate.of(2025, 8, 30), Span.YEAR.endDate(1, today))
        assertEquals(LocalDate.of(2024, 8, 30), Span.YEAR.endDate(2, today))
    }

    @Test
    fun `a year span survives a leap day`() {
        val leap = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDate.of(2023, 3, 1), Span.YEAR.startDate(0, leap))
    }

    @Test
    fun `stepping back far enough reaches data from over a year ago`() {
        // The bug this exists to prevent: TimeRange topped out at 365 days, so April 2025 was
        // unreachable from August 2026 no matter which range was selected.
        val april2025 = LocalDate.of(2025, 4, 15)
        val reached = (0..20).any { offset ->
            val start = Span.MONTH.startDate(offset, today)
            val end = Span.MONTH.endDate(offset, today)
            !april2025.isBefore(start) && april2025.isBefore(end)
        }
        assertTrue("April 2025 is unreachable by stepping back", reached)
    }

    @Test
    fun `only short recent windows avoid needing history permission`() {
        assertFalse(Span.DAY.needsHistoryPermission(0, today))
        assertFalse(Span.WEEK.needsHistoryPermission(0, today))
        assertTrue(Span.YEAR.needsHistoryPermission(0, today))
        // Stepping a short span far enough back also crosses the 30-day line.
        assertTrue(Span.WEEK.needsHistoryPermission(6, today))
    }

    @Test
    fun `every span has a bucket no wider than the span itself`() {
        Span.entries.forEach { span ->
            val start = span.startDate(0, today)
            val end = span.endDate(0, today)
            val bucketEnd = start.plus(span.bucket)
            assertTrue(
                "${span.name} bucket is wider than the span",
                !bucketEnd.isAfter(end),
            )
        }
    }
}
