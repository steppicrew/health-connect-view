package de.steppicrew.healthconnectview.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.settings.Settings
import de.steppicrew.healthconnectview.settings.SettingsStore
import de.steppicrew.healthconnectview.settings.ThemeChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)
    private val repository = HealthRepository(application)

    val settings: StateFlow<Settings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Settings())

    private val _grantedCount = MutableStateFlow(0)
    val grantedCount: StateFlow<Int> = _grantedCount.asStateFlow()

    init {
        refresh()
    }

    /** Granted permissions are re-read rather than cached; they change outside this app. */
    fun refresh() {
        viewModelScope.launch {
            _grantedCount.value = runCatching { repository.grantedPermissions().size }
                .getOrDefault(0)
        }
    }

    fun setTheme(theme: ThemeChoice) {
        viewModelScope.launch { store.setTheme(theme) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { store.setDynamicColor(enabled) }
    }

    /**
     * Withdraws every granted permission.
     *
     * Nothing is deleted: this app stores no health data, so revoking only removes its ability
     * to read. The wording in the UI says exactly that, because "withdraw access" invites the
     * fear that it also erases the underlying records.
     */
    fun revokeAll(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.revokeAll() }
            refresh()
            onDone()
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
