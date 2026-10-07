package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.dashboard.DashboardConfig
import de.steppicrew.healthconnectview.dashboard.DashboardJson
import de.steppicrew.healthconnectview.dashboard.Tile
import de.steppicrew.healthconnectview.dashboard.TileFace
import de.steppicrew.healthconnectview.health.Span
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
    fun `default layout pins only pinnable types`() {
        DashboardConfig.DEFAULT.tiles.forEach { tile ->
            val spec = tile.spec ?: return@forEach
            assertTrue("${tile.typeName} has nothing to show", spec.isPinnable)
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
    fun `adding a pinned type again gives the new tile an id of its own`() {
        val twice = config.adding("StepsRecord").adding("StepsRecord")
        assertEquals(listOf("StepsRecord", "HeartRateRecord", "WeightRecord", "StepsRecord#2", "StepsRecord#3"),
            twice.tiles.map { it.id })
        assertEquals(3, twice.tiles.count { it.typeName == "StepsRecord" })
    }

    @Test
    fun `a new tile of a pinned type takes the type's goal`() {
        val added = config.withGoal("StepsRecord", 7_500.0).adding("StepsRecord")
        assertEquals(7_500.0, added.tiles.last().goal!!, 0.0)
    }

    @Test
    fun `a goal applies to every tile of its type`() {
        val updated = config.adding("StepsRecord").withGoal("StepsRecord", 6_000.0)
        assertEquals(listOf(6_000.0, null, null, 6_000.0), updated.tiles.map { it.goal })
    }

    @Test
    fun `options, size and removal address one tile of a repeated type`() {
        val two = config.adding("StepsRecord")
        val changed = two.withOptions("StepsRecord#2", Span.YEAR, TileFace.CHART).resized("StepsRecord#2")
        assertEquals(Span.DAY, changed.tiles.first().span)
        assertEquals(1, changed.tiles.first().width)
        assertEquals(Span.YEAR, changed.tiles.last().span)
        assertEquals(2, changed.tiles.last().width)
        assertEquals(listOf("StepsRecord", "HeartRateRecord", "WeightRecord"),
            changed.without("StepsRecord#2").tiles.map { it.id })
    }

    @Test
    fun `a freed id is reused`() {
        val back = config.adding("StepsRecord").without("StepsRecord").adding("StepsRecord")
        assertEquals(setOf("StepsRecord", "StepsRecord#2"), back.tiles.filter { it.typeName == "StepsRecord" }.map { it.id }.toSet())
    }

    @Test
    fun `sanitising repairs repeated ids and differing goals`() {
        val broken = DashboardConfig(
            listOf(Tile("StepsRecord", goal = 5_000.0), Tile("StepsRecord", goal = 9_000.0)),
        ).sanitised()
        assertEquals(listOf("StepsRecord", "StepsRecord#2"), broken.tiles.map { it.id })
        assertEquals(listOf(5_000.0, 5_000.0), broken.tiles.map { it.goal })
    }

    @Test
    fun `ids survive the stored json, and a layout without them reads as before`() {
        val two = config.adding("StepsRecord").withOptions("StepsRecord#2", Span.WEEK, TileFace.BOTH)
        assertEquals(two, DashboardJson.decode(DashboardJson.encode(two)))
        val old = org.json.JSONArray("""[{"type":"StepsRecord","w":1,"h":1}]""")
        assertEquals("StepsRecord", DashboardJson.decode(old).tiles.single().id)
        assertTrue("first tile needs no stored id", !DashboardJson.encode(config).getJSONObject(0).has("id"))
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
    fun `setting a goal overrides only that type`() {
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
    fun `options change only their own tile`() {
        val config = DashboardConfig(listOf(Tile("StepsRecord"), Tile("WeightRecord")))
        val updated = config.withOptions("WeightRecord", Span.MONTH, TileFace.CHART)
        assertEquals(Span.DAY, updated.tiles[0].span)
        assertEquals(TileFace.VALUE, updated.tiles[0].face)
        assertEquals(Span.MONTH, updated.tiles[1].span)
        assertEquals(TileFace.CHART, updated.tiles[1].face)
    }

    @Test
    fun `resizing keeps the options`() {
        val tile = Tile("StepsRecord", width = 2, span = Span.WEEK, face = TileFace.BOTH)
        val single = tile.nextSize().nextSize()
        assertEquals(1 to 1, single.width to single.height)
        assertEquals(Span.WEEK, single.span)
        assertEquals(TileFace.BOTH, single.face)
    }

    @Test
    fun `a type with no ring has no goal`() {
        assertNull(Tile("HeartRateRecord").effectiveGoal)
    }

    @Test
    fun `every tile form declares what its renderer needs`() {
        de.steppicrew.healthconnectview.registry.RecordRegistry.all.forEach { spec ->
            when (spec.tile.form) {
                TileSpec.Form.RING -> assertTrue(
                    "${spec.type.simpleName} is a ring with nothing to fill",
                    spec.tile.defaultGoal != null && spec.aggregate != null,
                )
                TileSpec.Form.CURVE -> assertTrue(
                    "${spec.type.simpleName} is a curve with no colour zones",
                    spec.tile.defaultZones != null,
                )
                // A session tile counts spans of one kind. Without a kind it has nothing to
                // count and would render a permanent zero.
                TileSpec.Form.SESSIONS -> assertTrue(
                    "${spec.type.simpleName} counts sessions but names no kind",
                    spec.tile.sessionKind != null,
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

    @Test
    fun `the insights tile survives sanitising, once and as one cell`() {
        val stored = DashboardConfig(
            listOf(
                Tile("StepsRecord"),
                Tile(Tile.INSIGHTS, width = 2, height = 2),
                Tile(Tile.INSIGHTS, id = "Insights#2"),
                Tile("NoSuchRecord"),
            ),
        )
        val clean = stored.sanitised()
        assertEquals(listOf("StepsRecord", Tile.INSIGHTS), clean.tiles.map { it.typeName })
        val insights = clean.tiles.single { it.isInsights }
        assertEquals(1 to 1, insights.width to insights.height)
    }

    @Test
    fun `the insights tile round-trips through the stored form`() {
        val stored = DashboardConfig(listOf(Tile(Tile.INSIGHTS), Tile("StepsRecord")))
        assertEquals(stored, DashboardJson.decode(DashboardJson.encode(stored)).sanitised())
    }
}
