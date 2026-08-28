package de.steppicrew.healthconnectview.ui.permissions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.health.Availability
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.resolveAvailability
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionsUiState(
    val availability: Availability = Availability.Available,
    val granted: Set<String> = emptySet(),
    /** Types the user has ticked but not yet requested. */
    val selected: Set<String> = emptySet(),
    val loading: Boolean = true,
) {
    fun isGranted(spec: RecordTypeSpec<*>): Boolean = spec.permission in granted
    fun isSelected(spec: RecordTypeSpec<*>): Boolean = spec.permission in selected

    val grantedCount: Int get() = granted.count { it in RecordRegistry.allReadPermissions }
    val totalCount: Int get() = RecordRegistry.allReadPermissions.size
}

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)

    private val _state = MutableStateFlow(PermissionsUiState())
    val state: StateFlow<PermissionsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Re-reads granted permissions from Health Connect, which is authoritative: access can be
     * revoked in system settings while the app is backgrounded, and the permission-request
     * result itself can be incomplete.
     */
    fun refresh() {
        viewModelScope.launch {
            val availability = resolveAvailability(getApplication())
            if (availability != Availability.Available) {
                _state.update { it.copy(availability = availability, loading = false) }
                return@launch
            }
            val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
            _state.update {
                it.copy(availability = availability, granted = granted, loading = false)
            }
        }
    }

    fun toggle(spec: RecordTypeSpec<*>) {
        _state.update { current ->
            val permission = spec.permission
            val selected = if (permission in current.selected) {
                current.selected - permission
            } else {
                current.selected + permission
            }
            current.copy(selected = selected)
        }
    }

    /** Selects every type not already granted. Opt-in only — never the default. */
    fun selectAll() {
        _state.update { current ->
            current.copy(selected = RecordRegistry.allReadPermissions - current.granted)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    /** Permissions to hand to the launcher: only what the user actually ticked. */
    fun permissionsToRequest(): Set<String> = _state.value.selected

    fun onPermissionResult() {
        clearSelection()
        refresh()
    }
}
