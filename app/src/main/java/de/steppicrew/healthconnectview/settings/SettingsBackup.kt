package de.steppicrew.healthconnectview.settings

import de.steppicrew.healthconnectview.dashboard.DashboardConfig
import de.steppicrew.healthconnectview.dashboard.DashboardJson
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/**
 * Everything a phone switch would otherwise lose: the dashboard, the source choices and the
 * display settings. No health data -- none of these hold any -- so a backup file is safe to
 * keep anywhere and needs no premium gate.
 */
data class SettingsBackup(
    val dashboard: DashboardConfig,
    /** Per-type source filter, type name to package name. */
    val sourceSelections: Map<String, String>,
    val preferredSource: String?,
    val settings: Settings,
)

/** Why a file could not be restored; the message to show follows from which. */
sealed class BackupError(message: String) : Exception(message) {
    /** Not JSON, or JSON that is not this app's backup. */
    class NotABackup(cause: Throwable? = null) : BackupError("not a settings backup") {
        init { cause?.let(::initCause) }
    }

    /** Written by a newer version of the app, whose format this one cannot vouch for. */
    class NewerFormat(val format: Int) : BackupError("backup format $format is newer than ${SettingsBackupCodec.FORMAT}")
}

/**
 * The backup file: a small, readable JSON document, versioned so a later app can still read
 * an old file and an old app refuses a newer one rather than half-applying it.
 */
object SettingsBackupCodec {

    fun encode(backup: SettingsBackup, exportedOn: LocalDate): String = JSONObject().apply {
        put(FIELD_KIND, KIND)
        put(FIELD_FORMAT, FORMAT)
        put(FIELD_EXPORTED, exportedOn.toString())
        put(FIELD_DASHBOARD, DashboardJson.encode(backup.dashboard))
        put(
            FIELD_SOURCES,
            JSONObject().apply { backup.sourceSelections.forEach { (type, pkg) -> put(type, pkg) } },
        )
        backup.preferredSource?.let { put(FIELD_PREFERRED, it) }
        put(
            FIELD_SETTINGS,
            JSONObject().apply {
                put(FIELD_THEME, backup.settings.theme.name)
                put(FIELD_DYNAMIC, backup.settings.dynamicColor)
                put(FIELD_EXPLANATIONS, JSONArray().apply { backup.settings.expandedExplanations.sorted().forEach { put(it) } })
            },
        )
    }.toString(2)

    /**
     * Reads a backup. Rejects anything that is not one outright; within a valid backup a field
     * it cannot read falls back to its default, so one odd value does not cost the rest.
     */
    fun decode(text: String): SettingsBackup {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupError.NotABackup(e)
        }
        if (root.optString(FIELD_KIND) != KIND) throw BackupError.NotABackup()
        val format = root.optInt(FIELD_FORMAT, -1)
        if (format < 1) throw BackupError.NotABackup()
        if (format > FORMAT) throw BackupError.NewerFormat(format)

        val sources = root.optJSONObject(FIELD_SOURCES)
        val settings = root.optJSONObject(FIELD_SETTINGS)
        val explanations = settings?.optJSONArray(FIELD_EXPLANATIONS)
        return SettingsBackup(
            dashboard = root.optJSONArray(FIELD_DASHBOARD)?.let(DashboardJson::decode)?.sanitised()
                ?: DashboardConfig.DEFAULT,
            sourceSelections = sources?.keys()?.asSequence()
                ?.mapNotNull { type -> sources.optString(type).takeIf { it.isNotEmpty() }?.let { type to it } }
                ?.toMap()
                .orEmpty(),
            preferredSource = root.optString(FIELD_PREFERRED).takeIf { it.isNotEmpty() },
            settings = Settings(
                theme = settings?.optString(FIELD_THEME)
                    ?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() }
                    ?: ThemeChoice.SYSTEM,
                dynamicColor = settings?.optBoolean(FIELD_DYNAMIC, true) ?: true,
                expandedExplanations = explanations
                    ?.let { list -> (0 until list.length()).mapNotNull { list.optString(it).takeIf(String::isNotEmpty) } }
                    ?.toSet()
                    .orEmpty(),
            ),
        )
    }

    /** Marks the file as this app's, so an unrelated JSON file is refused, not misread. */
    const val KIND = "health-connect-view-settings"
    const val FORMAT = 1

    private const val FIELD_KIND = "kind"
    private const val FIELD_FORMAT = "format"
    private const val FIELD_EXPORTED = "exported"
    private const val FIELD_DASHBOARD = "dashboard"
    private const val FIELD_SOURCES = "sources"
    private const val FIELD_PREFERRED = "preferredSource"
    private const val FIELD_SETTINGS = "settings"
    private const val FIELD_THEME = "theme"
    private const val FIELD_DYNAMIC = "dynamicColor"
    private const val FIELD_EXPLANATIONS = "openExplanations"
}
