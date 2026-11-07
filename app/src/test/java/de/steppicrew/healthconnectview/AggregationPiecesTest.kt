package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.aggregationPieces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Period

/**
 * How a long bucketed aggregate is split into requests. A wrong split shifts or loses buckets
 * in every year chart, goal and streak, with nothing on screen to show it.
 */
class AggregationPiecesTest {

    private val end = LocalDate.of(2026, 11, 7).atStartOfDay()
    private val start = end.minusDays(365)

    @Test
    fun `a year of days becomes about monthly pieces that cover it without gaps`() {
        val pieces = aggregationPieces(start, end, Period.ofDays(1))!!
        assertEquals(12, pieces.size)
        assertEquals(start, pieces.first().keepFrom)
        assertEquals(end, pieces.last().to)
        pieces.zipWithNext { a, b -> assertEquals(a.to, b.keepFrom) }
    }

    @Test
    fun `every piece but the first asks one bucket early, so no kept bucket is a first one`() {
        val pieces = aggregationPieces(start, end, Period.ofDays(1))!!
        assertEquals(pieces.first().keepFrom, pieces.first().from)
        pieces.drop(1).forEach { assertEquals(it.keepFrom.minusDays(1), it.from) }
    }

    @Test
    fun `week buckets keep their edges on the week grid of the whole range`() {
        val pieces = aggregationPieces(start, end, Period.ofWeeks(1))!!
        pieces.forEach { piece ->
            assertEquals(0, java.time.Duration.between(start, piece.keepFrom).toDays() % 7)
            assertEquals(0, java.time.Duration.between(start, piece.from).toDays() % 7)
        }
        assertTrue(pieces.size > 1)
    }

    @Test
    fun `month buckets and short ranges stay one request`() {
        assertNull(aggregationPieces(start, end, Period.ofMonths(1)))
        assertNull(aggregationPieces(end.minusDays(30), end, Period.ofDays(1)))
        assertNull(aggregationPieces(end.minusDays(62), end, Period.ofDays(1)))
    }
}
