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

    /** Counts record types only; history is depth, not a type, and is reported separately. */
    val grantedCount: Int get() = granted.count { it in RecordRegistry.allReadPermissions }
    val totalCount: Int get() = RecordRegistry.allReadPermissions.size

    val historyGranted: Boolean get() = RecordRegistry.HISTORY_PERMISSION in granted
    val historySelected: Boolean get() = RecordRegistry.HISTORY_PERMISSION in selected
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

    /** Selects every type not already granted, plus history. Opt-in only — never the default. */
    fun selectAll() {
        _state.update { current ->
            val everything = RecordRegistry.allReadPermissions + RecordRegistry.HISTORY_PERMISSION
            current.copy(selected = everything - current.granted)
        }
    }

    /**
     * History is not a record type, so it has no spec and cannot go through [toggle].
     *
     * Without it Health Connect caps every read at the last 30 days and reports no error, so
     * long ranges silently return a month of data and look like missing history.
     */
    fun toggleHistory() {
        _state.update { current ->
            val permission = RecordRegistry.HISTORY_PERMISSION
            val selected = if (permission in current.selected) {
                current.selected - permission
            } else {
                current.selected + permission
            }
            current.copy(selected = selected)
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
