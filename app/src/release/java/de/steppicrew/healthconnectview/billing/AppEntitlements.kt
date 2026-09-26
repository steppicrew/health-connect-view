package de.steppicrew.healthconnectview.billing

import android.content.Context

/** Release build: what Play Billing says. Installed once, from the Application. */
object AppEntitlements {
    lateinit var current: Entitlements
        private set

    fun install(context: Context) {
        current = BillingEntitlements(context)
    }
}
