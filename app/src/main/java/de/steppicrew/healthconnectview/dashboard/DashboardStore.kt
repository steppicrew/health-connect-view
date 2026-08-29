package de.steppicrew.healthconnectview.dashboard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import de.steppicrew.healthconnectview.registry.ValueZones
import org.json.JSONArray
import org.json.JSONObject

private val Context.dashboardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dashboard",
)

/**
 * Persists the dashboard layout.
 *
 * Layout only -- which types are pinned, in what order, at what size, against what goal. No
 * health value is ever written here; the tiles' numbers are read fresh from Health Connect on
 * every load and held only for the current screen.
 *
 * Stored as JSON in a single preference rather than one key per field: the config is a list
 * whose length changes, which preference keys model badly, and it has to be read and written
 * as a unit anyway.
 */
class DashboardStore(private val context: Context) {

    val config: Flow<DashboardConfig> = context.dashboardDataStore.data.map { prefs ->
        // A malformed or half-written value must not brick the start screen, so anything
        // unparseable falls back to the default layout rather than propagating.
        val stored = prefs[KEY_TILES] ?: return@map DashboardConfig.DEFAULT
        runCatching { decode(stored) }.getOrDefault(DashboardConfig.DEFAULT).sanitised()
    }

    suspend fun save(config: DashboardConfig) {
        context.dashboardDataStore.edit { prefs ->
            prefs[KEY_TILES] = encode(config)
        }
    }

    private fun encode(config: DashboardConfig): String {
        val array = JSONArray()
        config.tiles.forEach { tile ->
            array.put(
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
        return array.toString()
    }

    private fun decode(stored: String): DashboardConfig {
        val array = JSONArray(stored)
        val tiles = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val typeName = item.optString(FIELD_TYPE).takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            Tile(
                typeName = typeName,
                width = item.optInt(FIELD_WIDTH, 1).coerceAtLeast(1),
                height = item.optInt(FIELD_HEIGHT, 1).coerceAtLeast(1),
                goal = if (item.has(FIELD_GOAL)) item.optDouble(FIELD_GOAL) else null,
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

    private companion object {
        val KEY_TILES = stringPreferencesKey("tiles")
        const val FIELD_TYPE = "type"
        const val FIELD_WIDTH = "w"
        const val FIELD_HEIGHT = "h"
        const val FIELD_GOAL = "goal"
        const val FIELD_ZONES = "zones"
    }
}
