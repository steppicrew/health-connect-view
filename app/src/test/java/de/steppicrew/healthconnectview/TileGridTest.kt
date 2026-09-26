package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.dashboard.SIZES
import de.steppicrew.healthconnectview.dashboard.Tile
import de.steppicrew.healthconnectview.ui.dashboard.TilePlacement
import de.steppicrew.healthconnectview.ui.dashboard.placeTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileGridTest {

    @Test
    fun `square tiles fill rows left to right`() {
        assertEquals(
            listOf(TilePlacement(0, 0, 1, 1), TilePlacement(1, 0, 1, 1), TilePlacement(0, 1, 1, 1)),
            placeTiles(List(3) { 1 to 1 }, columns = 2),
        )
    }

    @Test
    fun `a small tile after a large one fills the hole before it`() {
        // 1x1, 2x2, 1x1 on two columns: the last tile goes beside the first, not below the big one.
        assertEquals(
            listOf(TilePlacement(0, 0, 1, 1), TilePlacement(0, 1, 2, 2), TilePlacement(1, 0, 1, 1)),
            placeTiles(listOf(1 to 1, 2 to 2, 1 to 1), columns = 2),
        )
    }

    @Test
    fun `tiles beside a tall one stack in its rows`() {
        val placed = placeTiles(listOf(2 to 2, 1 to 1, 1 to 1), columns = 3)
        assertEquals(TilePlacement(2, 0, 1, 1), placed[1])
        assertEquals(TilePlacement(2, 1, 1, 1), placed[2])
    }

    @Test
    fun `a tile wider than the grid is narrowed`() {
        assertEquals(TilePlacement(0, 0, 1, 1), placeTiles(listOf(2 to 1), columns = 1).single())
    }

    @Test
    fun `no two tiles overlap`() {
        val sizes = List(40) { SIZES[(it * 7) % SIZES.size] }
        for (columns in 2..5) {
            val cells = placeTiles(sizes, columns).flatMap { at ->
                (at.row until at.row + at.height).flatMap { r ->
                    (at.column until at.column + at.width).map { c -> r to c }
                }
            }
            assertEquals("overlap at $columns columns", cells.size, cells.toSet().size)
            assertTrue(cells.all { (_, c) -> c in 0 until columns })
        }
    }

    @Test
    fun `sizes cycle and wrap`() {
        val tile = Tile("StepsRecord")
        assertEquals(2 to 1, tile.nextSize().let { it.width to it.height })
        assertEquals(2 to 2, tile.nextSize().nextSize().let { it.width to it.height })
        assertEquals(1 to 1, tile.nextSize().nextSize().nextSize().let { it.width to it.height })
        assertEquals(1 to 1, tile.copy(width = 3, height = 1).nextSize().let { it.width to it.height })
    }
}
