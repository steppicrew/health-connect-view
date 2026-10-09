package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * The grid's measured geometry in pixels: column starts and widths, the square cell's side,
 * the gap and the top padding. Enough to say where any placement lands without laying it out.
 */
internal data class GridMetrics(val starts: List<Int>, val widths: List<Int>, val cell: Int, val gap: Int, val top: Int) {
    val columns: Int get() = widths.size

    fun rectOf(at: TilePlacement): Rect {
        val w = (at.column until at.column + at.width).sumOf { widths[it] } + gap * (at.width - 1)
        val h = cell * at.height + gap * (at.height - 1)
        val x = starts[at.column].toFloat()
        val y = (top + at.row * (cell + gap)).toFloat()
        return Rect(x, y, x + w, y + h)
    }
}

/** Where one tile sits, in grid cells. */
internal data class TilePlacement(val column: Int, val row: Int, val width: Int, val height: Int)

/**
 * Places tiles of [sizes] (width to height, in cells) on a grid [columns] wide, in list order,
 * each at the first free spot that holds it.
 *
 * First fit rather than a cursor that only moves forward: with two columns, a 1x1 followed by
 * a 2x2 would otherwise leave a permanent hole beside the 1x1, and every wide tile would open
 * another. Filling holes can put a later small tile beside an earlier one, but list order
 * still decides who gets a spot first, so moving a tile up never moves it down the screen.
 *
 * A tile wider than the grid is narrowed to fit; the stored size is left alone, so it widens
 * again on a screen with room.
 */
internal fun placeTiles(sizes: List<Pair<Int, Int>>, columns: Int): List<TilePlacement> {
    require(columns > 0) { "columns must be positive" }
    val taken = mutableListOf<BooleanArray>()
    fun free(column: Int, row: Int): Boolean = row >= taken.size || !taken[row][column]

    return sizes.map { (rawWidth, rawHeight) ->
        val width = rawWidth.coerceIn(1, columns)
        val height = rawHeight.coerceAtLeast(1)
        // Rows past the last taken one are empty, so the search always ends there at the latest.
        val at = generateSequence(0) { it + 1 }.firstNotNullOf { row ->
            (0..columns - width).firstOrNull { column ->
                (row until row + height).all { r -> (column until column + width).all { free(it, r) } }
            }?.let { column -> TilePlacement(column, row, width, height) }
        }
        while (taken.size < at.row + height) taken.add(BooleanArray(columns))
        for (r in at.row until at.row + height) {
            for (c in at.column until at.column + width) taken[r][c] = true
        }
        at
    }
}

/**
 * The dashboard grid: square cells from [BoundedTileCells], tiles spanning one or two of them
 * in either direction.
 *
 * Not a `LazyVerticalGrid`: its items can span columns but never rows, so a 2x2 tile would
 * stretch its row and leave the cells beside it half empty. A dashboard holds a few dozen
 * tiles at most, all loaded up front by the view model, so laying them all out costs nothing
 * that laziness would save.
 */
@Composable
internal fun TileGrid(
    sizes: List<Pair<Int, Int>>,
    spacing: Dp,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Passed in where a drag has to scroll the grid as it nears an edge. */
    scrollState: ScrollState = rememberScrollState(),
    /** Told the geometry on every measure; a plain callback, not state, since it runs in layout. */
    onMetrics: (GridMetrics) -> Unit = {},
    /**
     * Full width above the tiles, scrolling with them: the "What's new" card, which pinned
     * above the grid took the screen's top until put away (the owner, 09.10.2026).
     */
    header: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val cells = remember { BoundedTileCells(TILE_MIN_WIDTH, TILE_COLUMNS_MIN, TILE_COLUMNS_MAX) }
    Layout(
        contents = listOf(header, content),
        modifier = modifier.verticalScroll(scrollState),
    ) { (headerMeasurables, measurables), constraints ->
        val gap = spacing.roundToPx()
        val left = contentPadding.calculateLeftPadding(LayoutDirection.Ltr).roundToPx()
        val right = contentPadding.calculateRightPadding(LayoutDirection.Ltr).roundToPx()
        val padTop = contentPadding.calculateTopPadding().roundToPx()
        val bottom = contentPadding.calculateBottomPadding().roundToPx()

        val headers = headerMeasurables.map {
            it.measure(Constraints(maxWidth = (constraints.maxWidth - left - right).coerceAtLeast(0)))
        }
        // The tiles start below the header and a gap, and the metrics say so: a drag reads
        // the grid's geometry from them.
        val headerHeight = headers.sumOf { it.height }.let { if (it > 0) it + gap else 0 }
        val top = padTop + headerHeight
        val widths = with(cells) { calculateCrossAxisCellSizes(constraints.maxWidth - left - right, gap) }
        val starts = widths.runningFold(left) { x, w -> x + w + gap }
        // Square cells; widths differ by at most a pixel, and the narrowest keeps rows even.
        val cell = widths.min()

        val metrics = GridMetrics(starts, widths, cell, gap, top)
        onMetrics(metrics)
        val placements = placeTiles(sizes, widths.size)
        val rects = placements.map(metrics::rectOf)
        val placeables = measurables.zip(rects) { measurable, rect ->
            measurable.measure(Constraints.fixed(rect.width.toInt(), rect.height.toInt()))
        }
        val rows = placements.maxOfOrNull { it.row + it.height } ?: 0
        val height = top + bottom + rows * cell + (rows - 1).coerceAtLeast(0) * gap

        // At least the minimum: a grid shorter than the screen is otherwise centred in it
        // rather than starting at the top.
        layout(constraints.maxWidth, height.coerceAtLeast(constraints.minHeight)) {
            var y = top - headerHeight
            headers.forEach { placeable ->
                placeable.placeRelative(left, y)
                y += placeable.height
            }
            placeables.zip(rects) { placeable, rect ->
                placeable.placeRelative(rect.left.toInt(), rect.top.toInt())
            }
        }
    }
}
