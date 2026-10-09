package de.steppicrew.healthconnectview.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.whatsNewDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "whats_new",
)

/**
 * Which "What's new" card was last put away, by the version it belongs to. Kept apart from
 * [SettingsStore] so a settings backup carried to a new phone does not bring it along: that
 * phone's install is a fresh one, with nothing new to tell.
 *
 * A version number only -- nothing about health data.
 */
class WhatsNewStore(private val context: Context) {

    /**
     * Which of [versions] are due: newer than the last card put away, so an update that skipped
     * a release or two still names everything it brings. A fresh install marks all of them seen
     * at once, since to a new user everything is new.
     */
    suspend fun due(versions: List<Int>): List<Int> {
        val latest = versions.maxOrNull() ?: return emptyList()
        val seen = context.whatsNewDataStore.data.first()[SEEN] ?: 0
        if (seen >= latest) return emptyList()
        if (freshInstall()) {
            dismiss(latest)
            return emptyList()
        }
        return versions.filter { it > seen }
    }

    suspend fun dismiss(version: Int) {
        context.whatsNewDataStore.edit { it[SEEN] = version }
    }

    /** Installed and never updated since: the two times are the same until the first update. */
    private fun freshInstall(): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    private companion object {
        val SEEN = intPreferencesKey("seen_version")
    }
}
