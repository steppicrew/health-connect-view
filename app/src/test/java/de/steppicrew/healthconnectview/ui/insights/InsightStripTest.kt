package de.steppicrew.healthconnectview.ui.insights

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightStripTest {
    @Test
    fun levelIsGreenAndTheEdgeRed() {
        assertEquals(Color(0xFF0CA30C), colourAt(0.0))
        assertEquals(Color(0xFF0CA30C), colourAt(-0.5))
        assertEquals(Color(0xFFD03B3B), colourAt(STRIP_SPREADS))
        assertEquals(Color(0xFFD03B3B), colourAt(-10.0))
    }

    @Test
    fun beyondLevelRunsFromYellowTowardsRed() {
        val near = colourAt(0.6)
        val far = colourAt(2.0)
        assertNotEquals(Color(0xFF0CA30C), near)
        // Redder further out: less green in the mix.
        assertTrue(far.green < near.green)
        assertEquals(colourAt(1.2), colourAt(-1.2))
    }
}
