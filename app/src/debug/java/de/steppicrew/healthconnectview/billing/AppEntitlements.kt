package de.steppicrew.healthconnectview.billing

import android.content.Context

/** Debug build: every feature unlocked, so premium ones are testable without a Play account. */
object AppEntitlements {
    var current: Entitlements = DebugEntitlements()
        private set

    // Nothing to connect to: debug owns everything from the start.
    fun install(context: Context) = Unit
}
