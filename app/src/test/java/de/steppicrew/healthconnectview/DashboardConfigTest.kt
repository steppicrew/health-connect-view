package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.dashboard.DashboardConfig
import de.steppicrew.healthconnectview.dashboard.Tile
import de.steppicrew.healthconnectview.registry.TileSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard layout is user-visible state that survives restarts, so its edit operations
 * have to be exactly right: a reorder that loses a tile silently discards configuration the
 * user cannot get back except by rebuilding it.
 */
class DashboardConfigTest {

    private val config = DashboardConfig(
        listOf(Tile("StepsRecord"), Tile("HeartRateRecord"), Tile("WeightRecord")),
    )

    @Test
    fun `default layout names only real types`() {
        DashboardConfig.DEFAULT.tiles.forEach { tile ->
            assertTrue("unknown type in default layout: ${tile.typeName}", tile.spec != null)
        }
    }

    @Test
    fun `default layout pins only chartable types`() {
        DashboardConfig.DEFAULT.tiles.forEach { tile ->
            val spec = tile.spec ?: return@forEach
            assertTrue("${tile.typeName} has nothing to show", spec.isChartable)
        }
    }

    @Test
    fun `moving a tile preserves every tile`() {
        val moved = config.moved(0, 2)
        assertEquals(listOf("HeartRateRecord", "WeightRecord", "StepsRecord"),
            moved.tiles.map { it.typeName })
        assertEquals(config.tiles.size, moved.tiles.size)
    }

    @Test
    fun `moving backwards preserves every tile`() {
        val moved = config.moved(2, 0)
        assertEquals(listOf("WeightRecord", "StepsRecord", "HeartRateRecord"),
            moved.tiles.map { it.typeName })
    }

    @Test
    fun `an out-of-range or no-op move changes nothing`() {
        assertSame(config, config.moved(0, 0))
        assertSame(config, config.moved(-1, 1))
        assertSame(config, config.moved(0, 99))
    }

    @Test
    fun `adding the same type twice is ignored`() {
        val once = config.plus(Tile("SleepSessionRecord"))
        val twice = once.plus(Tile("SleepSessionRecord"))
        assertEquals(4, once.tiles.size)
        assertEquals(4, twice.tiles.size)
    }

    @Test
    fun `removing drops exactly one tile`() {
        val without = config.without("HeartRateRecord")
        assertEquals(listOf("StepsRecord", "WeightRecord"), without.tiles.map { it.typeName })
    }

    @Test
    fun `sanitising drops tiles whose type no longer exists`() {
        val stale = DashboardConfig(listOf(Tile("StepsRecord"), Tile("RemovedRecord")))
        assertEquals(listOf("StepsRecord"), stale.sanitised().tiles.map { it.typeName })
    }

    @Test
    fun `a goal override wins over the type default`() {
        val steps = Tile("StepsRecord")
        assertEquals(10_000.0, steps.effectiveGoal!!, 0.0)
        assertEquals(5_000.0, steps.copy(goal = 5_000.0).effectiveGoal!!, 0.0)
    }

    @Test
    fun `setting a goal overrides only that tile`() {
        val updated = config.withGoal("StepsRecord", 7_500.0)
        assertEquals(7_500.0, updated.tiles.first().effectiveGoal!!, 0.0)
        assertNull(updated.tiles[1].goal)
        assertEquals(config.tiles.size, updated.tiles.size)
    }

    @Test
    fun `clearing a goal falls back to the type default`() {
        val overridden = config.withGoal("StepsRecord", 7_500.0)
        val cleared = overridden.withGoal("StepsRecord", null)
        assertEquals(10_000.0, cleared.tiles.first().effectiveGoal!!, 0.0)
    }

    @Test
    fun `a goal for an unpinned type changes nothing`() {
        val updated = config.withGoal("SleepSessionRecord", 8.0)
        assertEquals(config.tiles, updated.tiles)
    }

    @Test
    fun `a type with no ring has no goal`() {
        assertNull(Tile("HeartRateRecord").effectiveGoal)
    }

    @Test
    fun `ring tiles declare a default goal and curve tiles a colour scale`() {
        de.steppicrew.healthconnectview.registry.RecordRegistry.all.forEach { spec ->
            when (spec.tile.form) {
                TileSpec.Form.RING -> assertTrue(
                    "${spec.type.simpleName} is a ring with nothing to fill",
                    spec.tile.defaultGoal != null && spec.aggregate != null,
                )
                TileSpec.Form.CURVE -> assertTrue(
                    "${spec.type.simpleName} is a curve with no colour scale",
                    spec.tile.colorScale != null,
                )
                TileSpec.Form.NUMBER -> Unit
            }
        }
    }

    /**
     * Cumulative charts must only be declared for quantities that add up. A running total of
     * weight or heart rate is meaningless, and the chart would state it confidently.
     */
    @Test
    fun `only additive types accumulate through the day`() {
        de.steppicrew.healthconnectview.registry.RecordRegistry.all
            .filter { it.tile.cumulativeIntraday }
            .forEach { spec ->
                assertTrue(
                    "${spec.type.simpleName} accumulates but has no total to accumulate",
                    spec.aggregate != null,
                )
                assertEquals(
                    "${spec.type.simpleName} accumulates but is not an interval quantity",
                    de.steppicrew.healthconnectview.registry.RecordTypeSpec.Shape.INTERVAL,
                    spec.shape,
                )
            }
    }
}
