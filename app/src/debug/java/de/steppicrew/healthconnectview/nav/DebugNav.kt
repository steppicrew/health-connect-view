package de.steppicrew.healthconnectview.nav

import android.content.Intent
import android.util.Log

/**
 * Debug build: opens the app directly on a named screen, for adb-driven inspection.
 *
 * The test phone (HyperOS) refuses `adb shell input tap` without a signed-in Mi account, so a
 * screen two taps deep can only be reached by asking a person to tap -- which makes checking a
 * rendering change a conversation rather than a command. The debug activities already exist
 * for this reason; this extends the same idea to the main UI.
 *
 *     adb shell am start -n de.steppicrew.healthconnectview.debug/de.steppicrew.healthconnectview.MainActivity \
 *         -e route "tile/SleepSessionRecord?date=2026-08-29"
 *
 * The route is a NavHost route string, so anything [de.steppicrew.healthconnectview.ui.nav.Routes]
 * can express is reachable. An unknown route is left to the NavHost, which throws -- loudly, in
 * a debug build, which is the right outcome for a typo in a developer tool.
 */
object DebugNav {

    /** Route named by the launch intent, or null to open on the normal start destination. */
    fun startRoute(intent: Intent?): String? {
        val route = intent?.getStringExtra(EXTRA_ROUTE)?.takeIf { it.isNotBlank() }
        if (route != null) Log.i(TAG, "opening on route: $route")
        return route
    }

    private const val EXTRA_ROUTE = "route"
    private const val TAG = "DebugNav"
}
