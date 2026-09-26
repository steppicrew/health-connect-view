package de.steppicrew.healthconnectview

import android.app.Application
import de.steppicrew.healthconnectview.billing.AppEntitlements

class HealthConnectViewApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Early, so what the user owns is known by the time a gated screen asks.
        AppEntitlements.install(this)
    }
}
