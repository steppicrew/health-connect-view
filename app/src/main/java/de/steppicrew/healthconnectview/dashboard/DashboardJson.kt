package de.steppicrew.healthconnectview.dashboard

import de.steppicrew.healthconnectview.registry.ValueZones
import org.json.JSONArray
import org.json.JSONObject

/**
 * The dashboard layout as JSON: one object per tile, in order.
 *
 * Shared by the stored preference and the settings backup, so a backup is exactly what the app
 * keeps and cannot drift from it. Decoding is forgiving -- an entry it cannot read is skipped
 * rather than failing the whole layout -- because the input may be hand-edited or from another
 * version of the app.
 */
internal object DashboardJson {

    fun encode(config: DashboardConfig): JSONArray = JSONArray().apply {
        config.tiles.forEach { tile ->
            put(
                JSONObject().apply {
                    put(FIELD_TYPE, tile.typeName)
                    put(FIELD_WIDTH, tile.width)
                    put(FIELD_HEIGHT, tile.height)
                    tile.goal?.let { put(FIELD_GOAL, it) }
                    tile.zones?.let { zones ->
                        put(FIELD_ZONES, JSONArray().apply { zones.bounds.forEach { put(it) } })
                    }
                },
            )
        }
    }

    fun decode(array: JSONArray): DashboardConfig {
        val tiles = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val typeName = item.optString(FIELD_TYPE).takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            Tile(
                typeName = typeName,
                width = item.optInt(FIELD_WIDTH, 1).coerceAtLeast(1),
                height = item.optInt(FIELD_HEIGHT, 1).coerceAtLeast(1),
                goal = if (item.has(FIELD_GOAL)) item.optDouble(FIELD_GOAL).takeIf { it.isFinite() } else null,
                zones = item.optJSONArray(FIELD_ZONES)?.let { stored ->
                    // Sanitised on read as well as on write: a hand-edited or half-written
                    // value must not put unsorted bounds in front of the colour lookup.
                    ValueZones((0 until stored.length()).map { stored.optDouble(it) })
                        .sanitised()
                        .takeIf { it.bounds.isNotEmpty() }
                },
            )
        }
        return DashboardConfig(tiles)
    }

    private const val FIELD_TYPE = "type"
    private const val FIELD_WIDTH = "w"
    private const val FIELD_HEIGHT = "h"
    private const val FIELD_GOAL = "goal"
    private const val FIELD_ZONES = "zones"
}
