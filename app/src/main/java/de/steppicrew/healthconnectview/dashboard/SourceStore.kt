package de.steppicrew.healthconnectview.dashboard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.health.connect.client.records.metadata.DataOrigin
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.recordsIn
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
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
    ): String? = when (val chosen = selections[typeName]) {
        ALL_SOURCES -> null
        null -> preferred?.takeIf { it in writers }
        else -> chosen
    }

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

    /**
     * The per-type choice: an app, [ALL_SOURCES] for all of them, or null to drop the choice
     * and follow the preferred app again.
     *
     * "All sources" is a choice of its own rather than the absence of one. Stored as absence,
     * tapping "Alle" showed every source once and the preferred app again on the next visit.
     */
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

    /** Drops every per-type choice, so every type follows the preferred app again. */
    suspend fun clearSelections() {
        context.sourceDataStore.edit { prefs -> prefs.remove(KEY_SOURCES) }
    }

    /** Replaces every choice at once, for restoring a backup. */
    suspend fun restore(selections: Map<String, String>, preferred: String?) {
        context.sourceDataStore.edit { prefs ->
            prefs[KEY_SOURCES] = encode(selections)
            if (preferred == null) prefs.remove(KEY_PREFERRED) else prefs[KEY_PREFERRED] = preferred
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

    companion object {
        /** A per-type choice of every source, overriding the preferred app. */
        const val ALL_SOURCES = "*"

        private val KEY_SOURCES = stringPreferencesKey("selected_sources")
        private val KEY_PREFERRED = stringPreferencesKey("preferred_source")
    }
}

/**
 * The source a type's own screen opens on over [start]..[end], for a view built beside it --
 * a comparison, the insights -- so the same type never shows two different figures: its own
 * choice, or else the preferred app where that app wrote the type in the window, else all.
 * Costs one single-record read, and only where a preferred app is set.
 */
suspend fun SourceStore.openingSource(
    repository: HealthRepository,
    spec: RecordTypeSpec<*>,
    start: Instant,
    end: Instant,
): String? {
    val typeName = spec.type.simpleName.orEmpty()
    val selections = runCatching { selections.first() }.getOrDefault(emptyMap())
    selections[typeName]?.let { return it.takeUnless { chosen -> chosen == SourceStore.ALL_SOURCES } }
    val preferred = runCatching { preferred.first() }.getOrNull() ?: return null
    val wrote = runCatching {
        repository.recordsIn(spec, start, end, setOf(DataOrigin(preferred)), maxRecords = 1).isNotEmpty()
    }.getOrDefault(false)
    return preferred.takeIf { wrote }
}
