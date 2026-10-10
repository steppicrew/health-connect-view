package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/**
 * A tile being dragged to a new place in edit mode, by its handle.
 *
 * The order changes while the finger moves, so the other tiles make room as it passes them
 * and the grid shows the layout a drop would leave; nothing is stored until the drop. The
 * dragged tile is drawn where the finger has taken it, whatever slot the grid has given it
 * meanwhile.
 *
 * The tile goes to the place whose slot would lie nearest to where it is held, trying every
 * place with the grid's own first-fit rule. Swapping with whichever tile its centre entered
 * made mixed sizes hard to aim: a wide tile under a 2x2 one reshuffled the rows on each swap,
 * and no spot near it put it back where it came from.
 */
@Stable
internal class TileDragState(private val scroll: ScrollState) {

    /** Where each tile sits in the grid's content, by id; the tiles report it themselves. */
    val bounds = mutableStateMapOf<String, Rect>()

    /** The grid's visible height, for scrolling when the tile nears an edge. */
    var viewport by mutableIntStateOf(0)

    /** The order while dragging, null otherwise. */
    var order by mutableStateOf<List<String>?>(null)
        private set

    var dragging by mutableStateOf<String?>(null)
        private set

    /** The grid's geometry, as last measured; written from layout, so not state. */
    var metrics: GridMetrics? = null

    private var start = Rect.Zero
    private var moved by mutableStateOf(Offset.Zero)
    private var sizes: Map<String, Pair<Int, Int>> = emptyMap()

    /** The first of [ids] whose top is in view, or null where all are scrolled past. */
    fun firstInView(ids: List<String>): String? =
        ids.firstOrNull { id -> bounds[id]?.let { it.top >= scroll.value } == true }

    /** [ids] in their shown order, with the size each is drawn at. */
    fun start(id: String, ids: List<String>, sizes: Map<String, Pair<Int, Int>>) {
        start = bounds[id] ?: return
        this.sizes = sizes
        moved = Offset.Zero
        order = ids
        dragging = id
    }

    fun drag(delta: Offset) {
        moved += delta
        retarget()
    }

    /** Ends the drag and returns the order it leaves, or null if there was none. */
    fun end(): List<String>? {
        val result = order?.takeIf { dragging != null }
        dragging = null
        order = null
        return result
    }

    /** How far to draw [id] from its slot: the finger's way for the dragged tile, else none. */
    fun translation(id: String): Offset {
        if (id != dragging) return Offset.Zero
        val slot = bounds[id] ?: return Offset.Zero
        return start.topLeft + moved - slot.topLeft
    }

    /**
     * Scrolls while the finger is near the top or bottom, until the drop. The finger, not the
     * tile's centre: a wide tile low on the screen had its centre in the edge band while the
     * finger was still well inside, and the grid scrolled away under the aim. The grip sits
     * [grip] below the tile's top edge.
     */
    suspend fun autoScroll(edge: Float, maxStep: Float, grip: Float) {
        while (dragging != null) {
            withFrameNanos { }
            val y = start.top + grip + moved.y - scroll.value
            val step = when {
                y < edge -> -maxStep * ((edge - y) / edge).coerceAtMost(1f)
                y > viewport - edge -> maxStep * ((y - viewport + edge) / edge).coerceAtMost(1f)
                else -> 0f
            }
            if (step == 0f) continue
            val used = scroll.scrollBy(step)
            if (used != 0f) {
                // The content moved under a finger that did not, so the tile moves with it.
                moved += Offset(0f, used)
                retarget()
            }
        }
    }

    private fun retarget() {
        val id = dragging ?: return
        val ids = order ?: return
        val grid = metrics ?: return
        val centre = start.center + moved
        val others = ids - id
        fun distance(place: Int): Float {
            val candidate = others.toMutableList().apply { add(place, id) }
            val at = placeTiles(candidate.map { sizes[it] ?: (1 to 1) }, grid.columns)[place]
            return (grid.rectOf(at).center - centre).getDistance()
        }
        val here = ids.indexOf(id)
        // Of places giving the same slot -- a wide tile before a small one still lands below
        // it, the small one filling the gap first -- the one nearest the current order.
        val best = (0..others.size).minWith(compareBy<Int>(::distance).thenBy { abs(it - here) })
        // Only for a clearly nearer slot: two slots about equally near would otherwise swap
        // back and forth under a finger that hardly moves.
        if (best != here && distance(best) + grid.cell * HYSTERESIS < distance(here)) {
            order = others.toMutableList().apply { add(best, id) }
        }
    }
}

/** How much nearer, as a share of a cell, a new slot must be before the tile moves there. */
private const val HYSTERESIS = 0.2f
