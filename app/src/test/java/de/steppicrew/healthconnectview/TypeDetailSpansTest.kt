package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.ui.detail.TypeDetailViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The type detail screen charts from Period-sliced buckets only, so the spans it offers must
 * all have one. Offering [Span.DAY] here would render a single point and read as a broken
 * chart rather than as an unsupported span.
 */
class TypeDetailSpansTest {

    @Test
    fun `every offered span has a period bucket`() {
        TypeDetailViewModel.SPANS.forEach { span ->
            assertNotNull("${span.name} has no period bucket", span.bucket)
        }
    }

    @Test
    fun `the day span is not offered`() {
        assertFalse(Span.DAY in TypeDetailViewModel.SPANS)
    }

    /**
     * The point of the change: this screen could previously only ask for the last N days from
     * now, so nothing older than a year was reachable at all. Offsetting is what removes that
     * wall, and at least one offered span must reach past it.
     */
    @Test
    fun `an offered span steps back beyond a year`() {
        val reaches = TypeDetailViewModel.SPANS.any { span ->
            span.startDate(offset = 3).isBefore(java.time.LocalDate.now().minusDays(365))
        }
        assertTrue("no offered span reaches past the old 365-day ceiling", reaches)
    }
}
