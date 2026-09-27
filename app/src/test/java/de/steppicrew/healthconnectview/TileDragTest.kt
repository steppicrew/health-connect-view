package de.steppicrew.healthconnectview

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Offset
import de.steppicrew.healthconnectview.ui.dashboard.GridMetrics
import de.steppicrew.healthconnectview.ui.dashboard.TileDragState
import de.steppicrew.healthconnectview.ui.dashboard.placeTiles
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The owner's case: a wide tile right under a 2x2 one, with two single tiles after it, was
 * hard to put back where it came from -- it went above the 2x2 or below the two after it.
 */
class TileDragTest {

    private val grid = GridMetrics(starts = listOf(0, 110, 220), widths = listOf(100, 100), cell = 100, gap = 10, top = 0)
    private val ids = listOf("a", "b", "big", "wide", "c", "d")
    private val sizes = mapOf("a" to (1 to 1), "b" to (1 to 1), "big" to (2 to 2), "wide" to (2 to 1), "c" to (1 to 1), "d" to (1 to 1))

    private fun dragState(order: List<String> = ids): TileDragState = TileDragState(ScrollState(0)).apply {
        metrics = grid
        placeTiles(order.map { sizes.getValue(it) }, grid.columns).forEachIndexed { i, at ->
            bounds[order[i]] = grid.rectOf(at)
        }
    }

    /** Where each tile lands, by id: what the user sees, whatever the order behind it. */
    private fun layout(order: List<String>) =
        order.zip(placeTiles(order.map { sizes.getValue(it) }, grid.columns)).toMap()

    @Test
    fun `a small move and back leaves the wide tile where it was`() {
        val drag = dragState()
        drag.start("wide", ids, sizes)
        listOf(Offset(20f, 30f), Offset(10f, 25f), Offset(-15f, -35f), Offset(-15f, -20f)).forEach(drag::drag)
        assertEquals(ids, drag.end())
    }

    @Test
    fun `half a cell either way does not move it`() {
        listOf(Offset(0f, -50f), Offset(0f, 50f)).forEach { delta ->
            val drag = dragState()
            drag.start("wide", ids, sizes)
            drag.drag(delta)
            assertEquals(ids, drag.end())
        }
    }

    @Test
    fun `dragged over the big tile it goes above it`() {
        val drag = dragState()
        drag.start("wide", ids, sizes)
        drag.drag(Offset(0f, -170f))
        assertEquals(listOf("a", "b", "wide", "big", "c", "d"), drag.end())
    }

    @Test
    fun `dragged a row down it goes below the next two`() {
        val drag = dragState()
        drag.start("wide", ids, sizes)
        drag.drag(Offset(0f, 110f))
        // Placed before "d" or after it, the wide tile lands in the row below both.
        assertEquals(layout(listOf("a", "b", "big", "c", "d", "wide")), layout(drag.end()!!))
    }
}
