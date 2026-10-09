package de.steppicrew.healthconnectview.dashboard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.chartLinesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "chart_lines",
)

/**
 * Which lines a chart with several shows, per chart: the keys of the lines switched on, the one
 * owning the axis first. So a night's chart opens with breath rate beside heart rate once
 * someone has asked for it, rather than starting over on every screen.
 *
 * Line names only -- "heart_rate", "speed" -- never a value.
 */
class ChartLinesStore(private val context: Context) {

    /** The lines shown on [chart], axis owner first, or null where nothing was chosen yet. */
    fun shown(chart: String): Flow<List<String>?> = context.chartLinesDataStore.data.map { prefs ->
        prefs[key(chart)]?.split(SEPARATOR)?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
    }

    suspend fun save(chart: String, lines: List<String>) {
        context.chartLinesDataStore.edit { prefs -> prefs[key(chart)] = lines.joinToString(SEPARATOR) }
    }

    private fun key(chart: String) = stringPreferencesKey("lines_$chart")

    private companion object {
        const val SEPARATOR = ","
    }
}
