package de.steppicrew.healthconnectview.ui.catalog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.health.Availability
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.TimeRange
import de.steppicrew.healthconnectview.health.resolveAvailability
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Per-type state in the catalog list.
 *
 * [CHECKING] and [FAILED] are kept apart from [NO_DATA]: a single "unknown" drawn like an
 * empty type made every row read as empty while the probes were still running.
 */
enum class TypeStatus { HAS_DATA, NO_DATA, NOT_GRANTED, CHECKING, FAILED }

data class CatalogUiState(
    val availability: Availability = Availability.Available,
    val granted: Set<String> = emptySet(),
    val status: Map<String, TypeStatus> = emptyMap(),
    val loading: Boolean = true,
) {
    fun statusOf(spec: RecordTypeSpec<*>): TypeStatus =
        when {
            spec.permission !in granted -> TypeStatus.NOT_GRANTED
            else -> status[spec.type.simpleName] ?: TypeStatus.CHECKING
        }
}

class CatalogViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** The refresh in flight, cancelled when a newer one starts. */
    private var loadJob: Job? = null

    /**
     * Re-reads permissions and probes again, replacing a refresh still running: `init` and the
     * first ON_RESUME both start one, and two probes racing publish in the order they finish.
     */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val availability = resolveAvailability(getApplication())
            if (availability != Availability.Available) {
                _state.update { it.copy(availability = availability, loading = false) }
                return@launch
            }

            val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
            _state.update { it.copy(availability = availability, granted = granted, loading = false) }
            probeForData(granted)
        }
    }

    /**
     * Marks which granted types actually hold data, so the catalog can distinguish "nothing
     * recorded" from "not allowed to look". Each probe reads a single record; concurrency is
     * capped so that granting everything does not fire forty simultaneous IPC calls.
     */
    private suspend fun probeForData(granted: Set<String>) {
        val gate = Semaphore(MAX_CONCURRENT_PROBES)
        val range = TimeRange.YEAR.filter()
        val localRange = TimeRange.YEAR.localFilter()

        coroutineScope {
            RecordRegistry.all
                .filter { it.permission in granted }
                .map { spec ->
                    async {
                        gate.withPermit {
                            // Not runCatching: that would turn a cancelled probe into a failed one.
                            val status = try {
                                val has = repository.hasData(
                                    type = spec.type,
                                    range = range,
                                    aggregateRange = localRange,
                                    metric = spec.aggregate,
                                )
                                if (has) TypeStatus.HAS_DATA else TypeStatus.NO_DATA
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                TypeStatus.FAILED
                            }
                            spec.type.simpleName.orEmpty() to status
                        }
                    }
                }
                .awaitAll()
                .let { results ->
                    _state.update { it.copy(status = it.status + results.toMap()) }
                }
        }
    }

    private companion object {
        const val MAX_CONCURRENT_PROBES = 6
    }
}
