package de.steppicrew.healthconnectview.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.steppicrew.healthconnectview.registry.GlucoseUnit
import de.steppicrew.healthconnectview.registry.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

/** Which colour scheme to use, regardless of the system setting. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Which units to show values in; SYSTEM follows the phone's region. */
enum class UnitChoice {
    SYSTEM,
    METRIC,
    IMPERIAL,
    ;

    fun resolve(locale: Locale): UnitSystem = when (this) {
        SYSTEM -> UnitSystem.forLocale(locale)
        METRIC -> UnitSystem.METRIC
        IMPERIAL -> UnitSystem.IMPERIAL
    }
}

/** Which unit to show blood glucose in; SYSTEM follows the phone's region. */
enum class GlucoseChoice {
    SYSTEM,
    MMOL_PER_L,
    MG_PER_DL,
    ;

    fun resolve(locale: Locale): GlucoseUnit = when (this) {
        SYSTEM -> GlucoseUnit.forLocale(locale)
        MMOL_PER_L -> GlucoseUnit.MMOL_PER_L
        MG_PER_DL -> GlucoseUnit.MG_PER_DL
    }
}

data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /**
     * Material You colours drawn from the wallpaper. On by default because it makes the app
     * feel native, but some people prefer a palette that does not shift.
     */
    val dynamicColor: Boolean = true,
    /**
     * Explanations the user has opened, by key. Every explanation starts closed, so the data is
     * what shows at first glance, and stays open once opened. Per key: wanting the trend
     * explained says nothing about wanting sources explained.
     */
    val expandedExplanations: Set<String> = emptySet(),
    val units: UnitChoice = UnitChoice.SYSTEM,
    val glucose: GlucoseChoice = GlucoseChoice.SYSTEM,
    /** Single nights as dots behind a multi-day HRV line. On until switched off on the chart. */
    val showSingleNights: Boolean = true,
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
            expandedExplanations = prefs[KEY_EXPANDED_EXPLANATIONS] ?: emptySet(),
            units = prefs[KEY_UNITS]
                ?.let { stored -> runCatching { UnitChoice.valueOf(stored) }.getOrNull() }
                ?: UnitChoice.SYSTEM,
            glucose = prefs[KEY_GLUCOSE]
                ?.let { stored -> runCatching { GlucoseChoice.valueOf(stored) }.getOrNull() }
                ?: GlucoseChoice.SYSTEM,
            showSingleNights = prefs[KEY_SINGLE_NIGHTS] ?: true,
        )
    }

    suspend fun setTheme(theme: ThemeChoice) {
        context.settingsDataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setUnits(units: UnitChoice) {
        context.settingsDataStore.edit { it[KEY_UNITS] = units.name }
    }

    suspend fun setGlucose(glucose: GlucoseChoice) {
        context.settingsDataStore.edit { it[KEY_GLUCOSE] = glucose.name }
    }

    suspend fun setShowSingleNights(show: Boolean) {
        context.settingsDataStore.edit { it[KEY_SINGLE_NIGHTS] = show }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setExplanationExpanded(key: String, expanded: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val open = prefs[KEY_EXPANDED_EXPLANATIONS] ?: emptySet()
            prefs[KEY_EXPANDED_EXPLANATIONS] = if (expanded) open + key else open - key
        }
    }

    /** Replaces every setting at once, for restoring a backup. */
    suspend fun restore(settings: Settings) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_THEME] = settings.theme.name
            prefs[KEY_DYNAMIC_COLOR] = settings.dynamicColor
            prefs[KEY_EXPANDED_EXPLANATIONS] = settings.expandedExplanations
            prefs[KEY_UNITS] = settings.units.name
            prefs[KEY_GLUCOSE] = settings.glucose.name
            prefs[KEY_SINGLE_NIGHTS] = settings.showSingleNights
        }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_EXPANDED_EXPLANATIONS = stringSetPreferencesKey("expanded_explanations")
        val KEY_UNITS = stringPreferencesKey("units")
        val KEY_GLUCOSE = stringPreferencesKey("glucose_unit")
        val KEY_SINGLE_NIGHTS = booleanPreferencesKey("show_single_nights")
    }
}
