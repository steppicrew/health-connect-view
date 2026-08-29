package de.steppicrew.healthconnectview.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.dashboard.DashboardConfig
import de.steppicrew.healthconnectview.dashboard.DashboardStore
import de.steppicrew.healthconnectview.dashboard.Tile
import de.steppicrew.healthconnectview.health.Availability
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.dayFilter
import de.steppicrew.healthconnectview.health.dayInstants
import de.steppicrew.healthconnectview.health.resolveAvailability
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

/**
 * What one tile shows.
 *
 * [value] is null when the day holds nothing, which is deliberately distinct from a value of
 * zero: "no steps recorded" and "zero steps" mean different things, and rendering the first as
 * "0" is the misreading the roadmap calls out.
 */
data class TileData(
    val tile: Tile,
    val spec: RecordTypeSpec<*>,
    val value: Double? = null,
    val granted: Boolean = true,
    val loading: Boolean = true,
) {
    /** Fraction of the goal, for a ring. Null when there is no goal or nothing to show. */
    val progress: Float?
        get() {
            val goal = tile.effectiveGoal ?: return null
            val current = value ?: return null
            if (goal <= 0.0) return null
            return (current / goal).toFloat().coerceIn(0f, 1f)
        }
}

data class DashboardUiState(
    val availability: Availability = Availability.Available,
    val date: LocalDate = LocalDate.now(),
    val tiles: List<TileData> = emptyList(),
    val loading: Boolean = true,
) {
    /** Today is the newest day with data; stepping forward past it is meaningless. */
    val canStepForward: Boolean get() = date.isBefore(LocalDate.now())
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)
    private val store = DashboardStore(application)

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var config: DashboardConfig = DashboardConfig.DEFAULT

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val availability = resolveAvailability(getApplication())
            if (availability != Availability.Available) {
                _state.update { it.copy(availability = availability, loading = false) }
                return@launch
            }

            config = store.config.first()
            loadTiles()
        }
    }

    fun showPreviousDay() {
        _state.update { it.copy(date = it.date.minusDays(1)) }
        viewModelScope.launch { loadTiles() }
    }

    fun showNextDay() {
        if (!_state.value.canStepForward) return
        _state.update { it.copy(date = it.date.plusDays(1)) }
        viewModelScope.launch { loadTiles() }
    }

    /**
     * Loads every tile for the selected day.
     *
     * Concurrency is capped for the same reason the catalog probe caps it: a full dashboard
     * would otherwise fire a tile's worth of IPC calls at Health Connect simultaneously.
     */
    private suspend fun loadTiles() {
        val date = _state.value.date
        val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())

        val placeholders = config.tiles.mapNotNull { tile ->
            val spec = tile.spec ?: return@mapNotNull null
            TileData(tile = tile, spec = spec, granted = spec.permission in granted)
        }
        _state.update { it.copy(tiles = placeholders, loading = false) }

        val gate = Semaphore(MAX_CONCURRENT_TILES)
        val loaded = coroutineScope {
            placeholders.map { placeholder ->
                async {
                    if (!placeholder.granted) {
                        placeholder.copy(loading = false)
                    } else {
                        gate.withPermit { load(placeholder, date) }
                    }
                }
            }.awaitAll()
        }
        _state.update { it.copy(tiles = loaded) }
    }

    /**
     * A tile's number always comes from aggregation, never from summing records: a day total
     * is exactly where several apps writing the same metric would double-count.
     *
     * Types with no aggregate metric cannot show a total at all. They fall back to the latest
     * reading of the day, which is a different statement -- a weight, not a sum -- and is the
     * only honest number available for them.
     */
    private suspend fun load(placeholder: TileData, date: LocalDate): TileData {
        val spec = placeholder.spec
        val metric = spec.aggregate

        val value = if (metric != null) {
            runCatching { repository.total(metric, dayFilter(date)) }.getOrNull()
        } else {
            runCatching {
                repository.read(spec.type, dayInstants(date), maxRecords = LATEST_ONLY)
                    .firstOrNull()
                    ?.let { spec.pointsOf(it).lastOrNull()?.value }
            }.getOrNull()
        }

        return placeholder.copy(value = value, loading = false)
    }

    private companion object {
        const val MAX_CONCURRENT_TILES = 4

        /** read() returns newest-first, so one record is the latest reading. */
        const val LATEST_ONLY = 1
    }
}
