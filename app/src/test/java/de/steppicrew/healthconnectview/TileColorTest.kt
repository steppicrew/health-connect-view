package de.steppicrew.healthconnectview

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.steppicrew.healthconnectview.dashboard.DashboardConfig
import de.steppicrew.healthconnectview.dashboard.DashboardJson
import de.steppicrew.healthconnectview.dashboard.Tile
import de.steppicrew.healthconnectview.dashboard.TileColor
import de.steppicrew.healthconnectview.dashboard.muted
import de.steppicrew.healthconnectview.registry.ValueZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A tile colour must never cost readability or meaning: its text must read in both themes,
 * and the value-zone colours drawn on it must stay clearly apart from the background.
 */
class TileColorTest {

    private val pairs = TileColor.entries.flatMap { color ->
        listOfNotNull(color.pair(dark = false)?.let { "$color light" to it }, color.pair(dark = true)?.let { "$color dark" to it })
    }

    @Test
    fun `every colour but the theme's has a pair for each theme`() {
        TileColor.entries.filter { it != TileColor.DEFAULT }.forEach { color ->
            assertTrue("$color light", color.pair(dark = false) != null)
            assertTrue("$color dark", color.pair(dark = true) != null)
        }
    }

    @Test
    fun `text reads on every pair`() {
        pairs.forEach { (name, pair) ->
            // WCAG AA for body text; the muted text is small labels, so the same bar.
            assertTrue("$name text ${contrast(pair.content, pair.container)}", contrast(pair.content, pair.container) >= 7.0)
            assertTrue("$name muted ${contrast(pair.muted, pair.container)}", contrast(pair.muted, pair.container) >= 4.5)
        }
    }

    @Test
    fun `zone colours stay apart from every background`() {
        pairs.forEach { (name, pair) ->
            ValueZones.ZONE_COLORS.forEach { zone ->
                val distance = deltaE(zone, pair.container)
                assertTrue("$name vs $zone: $distance", distance >= MIN_ZONE_DISTANCE)
            }
        }
    }

    @Test
    fun `a colour survives the stored json, the theme's is not written`() {
        val config = DashboardConfig(listOf(Tile("StepsRecord", color = TileColor.CORAL), Tile("WeightRecord")))
        assertEquals(config, DashboardJson.decode(DashboardJson.encode(config)))
        assertTrue(!DashboardJson.encode(config).getJSONObject(1).has("color"))
    }

    @Test
    fun `an unknown stored colour falls back to the theme's`() {
        val stored = org.json.JSONArray("""[{"type":"StepsRecord","color":"CHARTREUSE"}]""")
        assertEquals(TileColor.DEFAULT, DashboardJson.decode(stored).tiles.single().color)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    /** CIE76 colour difference; above about 10 two colours are plainly different. */
    private fun deltaE(a: Color, b: Color): Double {
        val (l1, a1, b1) = lab(a)
        val (l2, a2, b2) = lab(b)
        return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }

    private fun lab(color: Color): Triple<Double, Double, Double> {
        fun linear(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        val r = linear(color.red)
        val g = linear(color.green)
        val b = linear(color.blue)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
        return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    private companion object {
        /** The nearest pair measured is 35 (the yellow zone on amber); the theme's own sits above 70. */
        const val MIN_ZONE_DISTANCE = 30.0
    }
}
