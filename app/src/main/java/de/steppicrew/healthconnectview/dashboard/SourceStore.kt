package de.steppicrew.healthconnectview.dashboard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.sourceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sources",
)

/**
 * Remembers which single app's data the user chose to see, per record type.
 *
 * Absent means the default: all sources, deduplicated by Health Connect. That default is the
 * correct answer to "how many floors did I climb" and is deliberately not the same as any one
 * app's figure -- which is exactly why the choice is worth offering, and why it is not the
 * starting point.
 *
 * This is a *view* filter, not a priority setting. Health Connect keeps its own user-ordered
 * app priority list to decide which record wins where two overlap, and that list is not
 * exposed to apps through the Jetpack client. Storing a "primary source" here would invent a
 * ranking the platform does not know about and produce totals matching nothing.
 *
 * Package names only: no health values are persisted.
 */
class SourceStore(private val context: Context) {

    val selections: Flow<Map<String, String>> = context.sourceDataStore.data.map { prefs ->
        val stored = prefs[KEY_SOURCES] ?: return@map emptyMap()
        runCatching { decode(stored) }.getOrDefault(emptyMap())
    }

    /** [packageName] null clears the filter back to all sources. */
    suspend fun select(typeName: String, packageName: String?) {
        context.sourceDataStore.edit { prefs ->
            val current = prefs[KEY_SOURCES]
                ?.let { runCatching { decode(it) }.getOrDefault(emptyMap()) }
                ?: emptyMap()
            val updated = if (packageName == null) {
                current - typeName
            } else {
                current + (typeName to packageName)
            }
            prefs[KEY_SOURCES] = encode(updated)
        }
    }

    private fun encode(selections: Map<String, String>): String =
        JSONObject().apply { selections.forEach { (type, pkg) -> put(type, pkg) } }.toString()

    private fun decode(stored: String): Map<String, String> {
        val json = JSONObject(stored)
        return json.keys().asSequence()
            .mapNotNull { key -> json.optString(key).takeIf { it.isNotEmpty() }?.let { key to it } }
            .toMap()
    }

    private companion object {
        val KEY_SOURCES = stringPreferencesKey("selected_sources")
    }
}
