package de.steppicrew.healthconnectview.nav

import android.content.Intent

/**
 * Release build: there is no navigation backdoor.
 *
 * The debug source set provides an implementation that reads a start destination from the
 * launch intent, so an adb-driven screenshot can open any screen directly. This variant makes
 * the feature *absent* from release rather than merely disabled -- the same reasoning that
 * puts the seeder's WRITE permissions in the debug manifest instead of behind a flag. A
 * backdoor that ships and is guarded by a boolean is still a backdoor that shipped.
 */
object DebugNav {
    /** Always null in release: the app opens on its own start destination. */
    fun startRoute(intent: Intent?): String? = null
}
