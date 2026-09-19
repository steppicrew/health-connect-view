package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Columns sized to fit, between [TILE_COLUMNS_MIN] and [TILE_COLUMNS_MAX].
 *
 * `GridCells.Fixed(2)` made a landscape phone show two enormous tiles: each is half the *long*
 * edge wide and, through the square aspect ratio, just as tall. `GridCells.Adaptive` flows
 * instead, but neither bound can be expressed with it, and both are real:
 *
 * - Below the minimum, a narrow phone falls to a single column. Any tile minimum wide enough
 *   to keep a tablet sensible (150dp and up) drops a 320dp device to one column, which is
 *   worse than what it replaces.
 * - Above the maximum, a tablet in landscape reaches eight columns and the tiles become too
 *   small to read a number off, which is the one thing a tile exists to do.
 *
 * Within the bounds the column count is whatever [TILE_MIN_WIDTH] allows, so the tiles keep a
 * roughly constant size and the grid gains columns rather than inflating. Widths are returned
 * in whole pixels, remainder spread one per column from the left, matching how the built-in
 * cells divide a row that does not split evenly.
 *
 * Lives in its own file rather than beside the screen so the arithmetic can be tested: it is
 * the kind that looks obviously right and is off by a spacing.
 */
internal class BoundedTileCells(
    private val minWidth: Dp,
    private val min: Int,
    private val max: Int,
) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(
        availableSize: Int,
        spacing: Int,
    ): List<Int> {
        // The width one column needs, spacing included, so the division below counts whole
        // slots rather than leaving the gaps unaccounted for. A row of n columns holds n-1
        // gaps, which is what the added `spacing` on the left corrects for.
        val slot = (minWidth.roundToPx() + spacing).coerceAtLeast(1)
        val fits = (availableSize + spacing) / slot
        val count = fits.coerceIn(min, max)

        val usable = availableSize - spacing * (count - 1)
        val each = usable / count
        val remainder = usable % count
        return List(count) { index -> each + if (index < remainder) 1 else 0 }
    }

    // Value semantics, so recomposition with equal bounds does not relayout the grid.
    override fun hashCode(): Int = (minWidth.hashCode() * 31 + min) * 31 + max

    override fun equals(other: Any?): Boolean = other is BoundedTileCells &&
        other.minWidth == minWidth && other.min == min && other.max == max
}

/** Two columns stay readable on the narrowest phone this app supports. */
internal const val TILE_COLUMNS_MIN = 2

/**
 * Past this the tiles are small enough that the face stops being readable at arm's length,
 * and a dashboard of forty tiny squares is a list, not a glance.
 */
internal const val TILE_COLUMNS_MAX = 5

/**
 * Roughly the width a tile has on a typical phone today, so portrait is unchanged and the
 * extra room in landscape turns into more columns rather than bigger tiles.
 */
internal val TILE_MIN_WIDTH = 160.dp
