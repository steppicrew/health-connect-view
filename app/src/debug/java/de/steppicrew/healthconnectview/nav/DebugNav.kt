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
 * can express is reachable. An unknown route logs a warning under this tag and stays on the
 * dashboard: crashing on it also crashed the app whenever a build without that route had been
 * installed over this one, which read as a broken screen rather than a missing one.
 *
 * `-e expand <text>` also opens full screen the first chart whose title contains the text,
 * since a double tap cannot be sent either.
 */
object DebugNav {

    /** Route named by the launch intent, or null to open on the normal start destination. */
    fun startRoute(intent: Intent?): String? {
        val route = intent?.getStringExtra(EXTRA_ROUTE)?.takeIf { it.isNotBlank() }
        if (route != null) Log.i(TAG, "opening on route: $route")
        return route
    }

    /** Title text of a chart to open full screen, or null. */
    fun expandChart(intent: Intent?): String? {
        val text = intent?.getStringExtra(EXTRA_EXPAND)?.takeIf { it.isNotBlank() }
        if (text != null) Log.i(TAG, "expanding chart: $text")
        return text
    }

    private const val EXTRA_ROUTE = "route"
    private const val EXTRA_EXPAND = "expand"
    private const val TAG = "DebugNav"
}
