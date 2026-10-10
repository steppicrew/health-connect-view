package de.steppicrew.healthconnectview.dashboard

import de.steppicrew.healthconnectview.health.Span
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
                    // Only a repeated type's later tiles need one; see [Tile.id].
                    if (tile.id != tile.typeName) put(FIELD_ID, tile.id)
                    put(FIELD_WIDTH, tile.width)
                    put(FIELD_HEIGHT, tile.height)
                    tile.goal?.let { put(FIELD_GOAL, it) }
                    tile.zones?.let { zones ->
                        put(FIELD_ZONES, JSONArray().apply { zones.bounds.forEach { put(it) } })
                    }
                    // Only when chosen, so a layout that never used them reads as it always did.
                    if (tile.span != Span.DAY) put(FIELD_SPAN, tile.span.name)
                    if (tile.face != TileFace.VALUE) put(FIELD_FACE, tile.face.name)
                    if (tile.color != TileColor.DEFAULT) put(FIELD_COLOR, tile.color.name)
                    tile.companion?.let { put(FIELD_COMPANION, it) }
                    if (tile.calendarYear) put(FIELD_CALENDAR_YEAR, true)
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
                span = item.optString(FIELD_SPAN)
                    .let { stored -> Span.entries.firstOrNull { it.name == stored } }
                    ?: Span.DAY,
                face = item.optString(FIELD_FACE)
                    .let { stored -> TileFace.entries.firstOrNull { it.name == stored } }
                    ?: TileFace.VALUE,
                id = item.optString(FIELD_ID).takeIf { it.isNotEmpty() } ?: typeName,
                // A colour this version does not know falls back to the theme's.
                color = item.optString(FIELD_COLOR)
                    .let { stored -> TileColor.entries.firstOrNull { it.name == stored } }
                    ?: TileColor.DEFAULT,
                // A curve this version does not offer for the type is dropped, not drawn.
                companion = item.optString(FIELD_COMPANION).takeIf { it in companionsOf(typeName) },
                calendarYear = item.optBoolean(FIELD_CALENDAR_YEAR, false),
            )
        }
        return DashboardConfig(tiles)
    }

    private const val FIELD_TYPE = "type"
    private const val FIELD_ID = "id"
    private const val FIELD_WIDTH = "w"
    private const val FIELD_HEIGHT = "h"
    private const val FIELD_GOAL = "goal"
    private const val FIELD_ZONES = "zones"
    private const val FIELD_SPAN = "span"
    private const val FIELD_FACE = "face"
    private const val FIELD_COLOR = "color"
    private const val FIELD_COMPANION = "with"
    private const val FIELD_CALENDAR_YEAR = "calendarYear"
}
