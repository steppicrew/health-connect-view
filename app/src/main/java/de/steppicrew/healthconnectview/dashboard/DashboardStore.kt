package de.steppicrew.healthconnectview.dashboard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

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
        runCatching { DashboardJson.decode(JSONArray(stored)) }.getOrDefault(DashboardConfig.DEFAULT).sanitised()
    }

    suspend fun save(config: DashboardConfig) {
        context.dashboardDataStore.edit { prefs ->
            prefs[KEY_TILES] = DashboardJson.encode(config).toString()
        }
    }

    private companion object {
        val KEY_TILES = stringPreferencesKey("tiles")
    }
}
