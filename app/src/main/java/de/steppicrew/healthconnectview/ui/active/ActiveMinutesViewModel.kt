package de.steppicrew.healthconnectview.ui.active

import android.app.Application
import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.dashboard.SourceStore
import de.steppicrew.healthconnectview.health.ActiveMinutes
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.health.observedMaxHeartRate
import de.steppicrew.healthconnectview.health.readActiveMinutes
import de.steppicrew.healthconnectview.settings.SettingsStore
import de.steppicrew.healthconnectview.util.catching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What a read of active minutes came to; the tile and the screen say each differently. */
sealed interface ActiveRead {
    /** Not allowed to read heart rate, which every minute starts from. */
    data object NoAccess : ActiveRead

    /** No maximum heart rate set and too little of a year to read one from. */
    data object NoMax : ActiveRead

    /** No heart rate in the window. */
    data object Empty : ActiveRead

    data object Failed : ActiveRead

    data class Data(val minutes: ActiveMinutes) : ActiveRead
}

/**
 * Active minutes from [first] to [last], from the writer the heart-rate screen would open on
 * where one is chosen, against the maximum set in settings or else read from the data.
 */
suspend fun readActive(
    repository: HealthRepository,
    sourceStore: SourceStore,
    settingsStore: SettingsStore,
    granted: Set<String>,
    first: LocalDate,
    last: LocalDate,
): ActiveRead {
    if (HEART_RATE_PERMISSION !in granted) return ActiveRead.NoAccess
    val started = System.currentTimeMillis()
    return catching {
        val max = settingsStore.settings.first().maxHeartRate ?: repository.observedMaxHeartRate(last)
            ?: return ActiveRead.NoMax
        Log.i(TAG, "maximum in ${System.currentTimeMillis() - started} ms")
        val typeName = HeartRateRecord::class.simpleName.orEmpty()
        val preferred = when (val chosen = sourceStore.selections.first()[typeName]) {
            SourceStore.ALL_SOURCES -> null
            null -> sourceStore.preferred.first()
            else -> chosen
        }
        repository.readActiveMinutes(first, last, max, preferred)
    }.fold(
        onSuccess = { read -> read?.let(ActiveRead::Data) ?: ActiveRead.Empty },
        onFailure = { ActiveRead.Failed },
    ).also {
        // Timing and outcome only, never a minute.
        Log.i(TAG, "active minutes ${it::class.simpleName} for ${first..last} in ${System.currentTimeMillis() - started} ms")
    }
}

private val HEART_RATE_PERMISSION = HealthPermission.getReadPermission(HeartRateRecord::class)
private const val TAG = "ActiveMinutes"

data class ActiveMinutesState(
    val span: Span = Span.WEEK,
    val offset: Int = 0,
    /** The day the windows are counted back from: the dashboard's when opened from it. */
    val anchor: LocalDate = LocalDate.now(),
    /** Null while reading. */
    val read: ActiveRead? = null,
) {
    val first: LocalDate get() = span.startDate(offset, anchor)
    val last: LocalDate get() = span.endDate(offset, anchor).minusDays(1)
    val canStepForward: Boolean get() = last.isBefore(LocalDate.now())
}

/** A week or four weeks of active minutes, stepped back and forward like a type's screen. */
class ActiveMinutesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)
    private val sourceStore = SourceStore(application)
    private val settingsStore = SettingsStore(application)

    private val _state = MutableStateFlow(ActiveMinutesState())
    val state: StateFlow<ActiveMinutesState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var started = false

    fun start(date: LocalDate?) {
        if (started) return
        started = true
        date?.let { day -> _state.update { it.copy(anchor = day) } }
        reload()
    }

    /** Back from the permission screen, or from settings with a new maximum. */
    fun onResume() {
        if (started) reload()
    }

    fun select(span: Span) {
        if (span == _state.value.span) return
        _state.update { it.copy(span = span, offset = 0) }
        reload()
    }

    fun step(back: Boolean) {
        val state = _state.value
        if (!back && !state.canStepForward) return
        _state.update { it.copy(offset = it.offset + if (back) 1 else -1) }
        reload()
    }

    fun now() {
        _state.update { it.copy(offset = 0, anchor = LocalDate.now()) }
        reload()
    }

    private fun reload() {
        loadJob?.cancel()
        _state.update { it.copy(read = null) }
        loadJob = viewModelScope.launch {
            val state = _state.value
            val granted = catching { repository.grantedPermissions() }.getOrDefault(emptySet())
            val read = readActive(repository, sourceStore, settingsStore, granted, state.first, state.last)
            _state.update { current -> if (current.first == state.first && current.last == state.last) current.copy(read = read) else current }
        }
    }
}
