package de.steppicrew.healthconnectview.util

import android.content.Context

/**
 * Health Connect identifies a record's writer only by package name, so a human-readable label
 * has to come from the package manager. Falls back to the raw package name when the writing
 * app is no longer installed or is not visible to this app.
 */
fun Context.appLabelFor(packageName: String): String =
    runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
