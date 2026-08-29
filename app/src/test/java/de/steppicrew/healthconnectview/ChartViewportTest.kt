package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.ui.components.clampPan
import de.steppicrew.healthconnectview.ui.components.visibleFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The zoom viewport, which every drawn element shares.
 *
 * Worth pinning without a device because a discrepancy here is invisible until someone
 * pinches: the line, the session bands, the axis icons and the touch handler all convert
 * through these two functions, and if they disagree a band drifts away from the stretch of
 * line it explains.
 */
class ChartViewportTest {

    @Test
    fun `unzoomed the viewport is the identity`() {
        assertEquals(0f, visibleFraction(0f, zoom = 1f, pan = 0f), 0.0001f)
        assertEquals(0.5f, visibleFraction(0.5f, zoom = 1f, pan = 0f), 0.0001f)
        assertEquals(1f, visibleFraction(1f, zoom = 1f, pan = 0f), 0.0001f)
    }

    @Test
    fun `zooming twofold from the left shows the first half across the width`() {
        assertEquals(0f, visibleFraction(0f, zoom = 2f, pan = 0f), 0.0001f)
        assertEquals(1f, visibleFraction(0.5f, zoom = 2f, pan = 0f), 0.0001f)
    }

    @Test
    fun `panning moves the window without changing its width`() {
        // Second half of the series, at twofold zoom.
        val left = visibleFraction(0.5f, zoom = 2f, pan = 0.5f)
        val right = visibleFraction(1f, zoom = 2f, pan = 0.5f)
        assertEquals(0f, left, 0.0001f)
        assertEquals(1f, right, 0.0001f)
    }

    /** At zoom 1 the whole series is on screen, so there is nowhere to pan to. */
    @Test
    fun `unzoomed pan is pinned to zero`() {
        assertEquals(0f, clampPan(0.4f, 1f), 0.0001f)
        assertEquals(0f, clampPan(-0.4f, 1f), 0.0001f)
    }

    /** Panning must never run past the end and reveal empty space beyond the data. */
    @Test
    fun `pan cannot leave the data`() {
        assertEquals(0.5f, clampPan(0.9f, 2f), 0.0001f)
        assertEquals(0f, clampPan(-0.2f, 2f), 0.0001f)

        val scale = 4f
        val maxPan = clampPan(Float.MAX_VALUE, scale)
        // The right edge of the viewport lands exactly on the end of the series.
        assertEquals(1f, visibleFraction(1f, scale, maxPan), 0.0001f)
    }

    @Test
    fun `a point outside the window falls outside the unit range`() {
        assertTrue(visibleFraction(0.9f, zoom = 4f, pan = 0f) > 1f)
        assertTrue(visibleFraction(0.1f, zoom = 4f, pan = 0.5f) < 0f)
    }
}
