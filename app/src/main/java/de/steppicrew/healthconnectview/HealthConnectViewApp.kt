package de.steppicrew.healthconnectview

import android.app.Application
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.registry.Units
import de.steppicrew.healthconnectview.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

class HealthConnectViewApp : Application() {

    /** Lives as long as the process; nothing here is tied to a screen. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Early, so what the user owns is known by the time a gated screen asks.
        AppEntitlements.install(this)
        // Read once before anything loads: values are converted as they are read and labelled
        // when drawn, so a first screen loaded before the stored choice arrived would label
        // metric numbers "lb". One small preferences file, read once per process.
        val store = SettingsStore(this)
        val initial = runBlocking { store.settings.first() }
        Units.system = initial.units.resolve(Locale.getDefault())
        Units.glucose = initial.glucose.resolve(Locale.getDefault())
        scope.launch {
            store.settings
                .map { it.units to it.glucose }
                .distinctUntilChanged()
                .collect { (units, glucose) ->
                    Units.system = units.resolve(Locale.getDefault())
                    Units.glucose = glucose.resolve(Locale.getDefault())
                }
        }
    }
}
