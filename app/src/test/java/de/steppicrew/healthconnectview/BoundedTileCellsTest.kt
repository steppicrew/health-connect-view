package de.steppicrew.healthconnectview

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.ui.dashboard.BoundedTileCells
import de.steppicrew.healthconnectview.ui.dashboard.TILE_COLUMNS_MAX
import de.steppicrew.healthconnectview.ui.dashboard.TILE_COLUMNS_MIN
import de.steppicrew.healthconnectview.ui.dashboard.TILE_MIN_WIDTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid's column arithmetic, which is the kind that looks obviously right and is off by a
 * spacing. A wrong count here is not subtle on screen -- it is the reported bug, two tiles
 * filling a landscape phone -- but the off-by-one cases either side of a breakpoint are.
 *
 * Widths are dp at density 1, so the numbers below read as the device widths they represent.
 */
class BoundedTileCellsTest {

    private val density = Density(density = 1f, fontScale = 1f)
    private val spacing = 12
    private val cells = BoundedTileCells(TILE_MIN_WIDTH, TILE_COLUMNS_MIN, TILE_COLUMNS_MAX)

    /** Available width is the screen less the grid's 12dp content padding either side. */
    private fun columnsFor(screenWidth: Int): List<Int> = with(cells) {
        with(density) { calculateCrossAxisCellSizes(screenWidth - 24, spacing) }
    }

    @Test
    fun `a narrow phone keeps two columns`() {
        // 320dp is the narrowest realistic device at minSdk 26. Two 160dp tiles do not fit in
        // 296dp of usable width, so this is the case the lower bound exists for: without it
        // the grid would fall to a single column and be worse than what it replaced.
        assertEquals(2, columnsFor(320).size)
    }

    @Test
    fun `a typical phone keeps two columns`() {
        assertEquals(2, columnsFor(360).size)
        assertEquals(2, columnsFor(412).size)
    }

    @Test
    fun `a phone in landscape gains columns instead of inflating its tiles`() {
        // The reported bug: 2 columns across a landscape phone made each tile half the long
        // edge wide and, being square, just as tall.
        val landscape = columnsFor(732)
        assertTrue("landscape should hold more than two columns", landscape.size > 2)
        assertTrue(
            "a landscape tile should not be far wider than a portrait one",
            landscape.first() < 260,
        )
    }

    @Test
    fun `a tablet in landscape stops at the maximum`() {
        assertEquals(TILE_COLUMNS_MAX, columnsFor(1280).size)
        assertEquals(TILE_COLUMNS_MAX, columnsFor(1920).size)
    }

    /**
     * The columns and the gaps between them must add up to exactly the width handed in, or the
     * grid either overflows its row or leaves a ragged strip down one side.
     */
    @Test
    fun `columns and gaps fill the available width exactly`() {
        listOf(320, 360, 412, 640, 732, 800, 1024, 1280, 1920).forEach { screen ->
            val widths = columnsFor(screen)
            val used = widths.sum() + spacing * (widths.size - 1)
            assertEquals("width not filled at ${screen}dp", screen - 24, used)
        }
    }

    @Test
    fun `column widths differ by at most one pixel`() {
        listOf(320, 360, 412, 640, 732, 800, 1024, 1280).forEach { screen ->
            val widths = columnsFor(screen)
            assertTrue(
                "uneven columns at ${screen}dp: $widths",
                widths.max() - widths.min() <= 1,
            )
        }
    }

    @Test
    fun `the count never leaves the declared bounds`() {
        (200..2000 step 4).forEach { screen ->
            val count = columnsFor(screen).size
            assertTrue(
                "out of bounds at ${screen}dp: $count",
                count in TILE_COLUMNS_MIN..TILE_COLUMNS_MAX,
            )
        }
    }

    @Test
    fun `wider never means fewer columns`() {
        var previous = 0
        (200..2000 step 4).forEach { screen ->
            val count = columnsFor(screen).size
            assertTrue("column count went down at ${screen}dp", count >= previous)
            previous = count
        }
    }

    /** Equal bounds compare equal, so recomposition does not force a relayout. */
    @Test
    fun `cells with the same bounds are equal`() {
        assertEquals(BoundedTileCells(160.dp, 2, 5), BoundedTileCells(160.dp, 2, 5))
        assertEquals(
            BoundedTileCells(160.dp, 2, 5).hashCode(),
            BoundedTileCells(160.dp, 2, 5).hashCode(),
        )
        assertTrue(BoundedTileCells(160.dp, 2, 5) != BoundedTileCells(180.dp, 2, 5))
    }
}
