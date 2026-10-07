package de.steppicrew.healthconnectview.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.RECORD_DAYS
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.WorkoutFamily
import de.steppicrew.healthconnectview.health.sessionsIn
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.ui.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * The past year's workouts, newest first, for a list filtered by kind.
 *
 * Read once and filtered in memory: a year held 728 sessions on the phone, a few hundred
 * kilobytes, and switching kinds should not wait on Health Connect.
 */
class WorkoutsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)

    private val _state = MutableStateFlow<UiState<List<Session>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Session>>> = _state.asStateFlow()

    /** The kind shown, or null for all of them. */
    private val _family = MutableStateFlow<WorkoutFamily?>(null)
    val family: StateFlow<WorkoutFamily?> = _family.asStateFlow()

    private val _historyGranted = MutableStateFlow(true)
    val historyGranted: StateFlow<Boolean> = _historyGranted.asStateFlow()

    private var started = false
    private var loadJob: Job? = null

    /** Starts the read once; [family] is the kind to open on, from the route. */
    fun start(family: WorkoutFamily?) {
        if (started) return
        started = true
        _family.value = family
        load()
    }

    fun select(family: WorkoutFamily?) {
        _family.value = family
    }

    /** Back from the permission screen with older data allowed, the year is read again. */
    fun onResume() {
        if (!started || _state.value is UiState.Loading) return
        viewModelScope.launch {
            val granted = runCatching { repository.grantedPermissions() }.getOrNull() ?: return@launch
            if ((RecordRegistry.HISTORY_PERMISSION in granted) != _historyGranted.value) load()
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                val granted = repository.grantedPermissions()
                val permission = RecordRegistry.specOrNull(EXERCISE)?.permission
                _historyGranted.value = RecordRegistry.HISTORY_PERMISSION in granted
                if (permission == null || permission !in granted) {
                    UiState.NoPermission
                } else {
                    val now = Instant.now()
                    val sessions = repository
                        .sessionsIn(now.minus(Duration.ofDays(RECORD_DAYS)), now, setOf(Session.Kind.EXERCISE))
                        .sortedByDescending { it.start }
                    currentCoroutineContext().ensureActive()
                    if (sessions.isEmpty()) UiState.Empty else UiState.Data(sessions)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private companion object {
        const val EXERCISE = "ExerciseSessionRecord"
    }
}
