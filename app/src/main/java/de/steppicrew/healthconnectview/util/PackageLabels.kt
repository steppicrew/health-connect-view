package de.steppicrew.healthconnectview.util

import android.content.Context
import de.steppicrew.healthconnectview.R

/**
 * Health Connect identifies a record's writer only by package name, so a human-readable label
 * has to come from the package manager. Falls back to the raw package name when the writing
 * app is no longer installed or is not visible to this app.
 */
fun Context.appLabelFor(packageName: String): String {
    // Health Connect records its own sensor readings under a synthetic origin with a hashed
    // suffix. No such package is installed, so the package manager can never name it, and the
    // raw id -- com.android.healthconnect.phone.jf653... -- is meaningless to a reader. It is
    // the phone's own step counter, so it is named as such.
    if (packageName.startsWith(PHONE_SENSOR_PREFIX)) {
        return getString(R.string.source_phone_sensors)
    }
    return runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}

private const val PHONE_SENSOR_PREFIX = "com.android.healthconnect.phone"
