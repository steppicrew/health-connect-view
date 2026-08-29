package de.steppicrew.healthconnectview.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

/** Which colour scheme to use, regardless of the system setting. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /**
     * Material You colours drawn from the wallpaper. On by default because it makes the app
     * feel native, but some people prefer a palette that does not shift.
     */
    val dynamicColor: Boolean = true,
)

/**
 * App preferences. Display choices only -- no health data, and nothing that changes what is
 * read from Health Connect.
 *
 * Language is deliberately absent: on Android 13+ it belongs to the platform's per-app
 * language setting, which the settings screen opens rather than duplicating. A private
 * override would disagree with what Android's own settings show.
 */
class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        Settings(
            theme = prefs[KEY_THEME]
                ?.let { stored -> runCatching { ThemeChoice.valueOf(stored) }.getOrNull() }
                ?: ThemeChoice.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
        )
    }

    suspend fun setTheme(theme: ThemeChoice) {
        context.settingsDataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
