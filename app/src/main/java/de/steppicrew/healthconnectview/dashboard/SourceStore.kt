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

    /**
     * The app to prefer where no per-type choice has been made, or null for all sources.
     *
     * A convenience over the per-type filter, not a second mechanism: it supplies the default
     * that [selections] overrides. It cannot change which record wins where two overlap --
     * that is Health Connect's own app-priority list, which this app cannot write -- so a
     * tile showing one app's data is showing *that app's* figure, not a reprioritised total.
     */
    val preferred: Flow<String?> = context.sourceDataStore.data.map { prefs ->
        prefs[KEY_PREFERRED]?.takeIf { it.isNotEmpty() }
    }

    /**
     * The source to show for [typeName]: the per-type choice if there is one, otherwise the
     * preferred app, and null (all sources) if neither applies.
     *
     * [writers] is who actually wrote this type in the window being shown. The preference is
     * only honoured when it appears there: an app that wrote nothing for a type would
     * otherwise filter the tile down to an empty chart, which reads as missing data rather
     * than as a filter matching nothing. A per-type choice is *not* filtered this way -- it
     * was made deliberately for this type, so it stands even when it comes up empty.
     */
    fun effective(
        typeName: String,
        selections: Map<String, String>,
        preferred: String?,
        writers: Set<String>,
    ): String? = selections[typeName] ?: preferred?.takeIf { it in writers }

    /** [packageName] null clears the preference back to all sources. */
    suspend fun preferSource(packageName: String?) {
        context.sourceDataStore.edit { prefs ->
            if (packageName == null) {
                prefs.remove(KEY_PREFERRED)
            } else {
                prefs[KEY_PREFERRED] = packageName
            }
        }
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
        val KEY_PREFERRED = stringPreferencesKey("preferred_source")
    }
}
