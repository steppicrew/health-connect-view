package de.steppicrew.healthconnectview.ui.dashboard

import android.app.Application
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.dashboard.DashboardConfig
import androidx.health.connect.client.records.metadata.DataOrigin
import de.steppicrew.healthconnectview.dashboard.DashboardStore
import de.steppicrew.healthconnectview.dashboard.SourceStore
import de.steppicrew.healthconnectview.dashboard.Tile
import de.steppicrew.healthconnectview.health.Availability
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.dayFilter
import de.steppicrew.healthconnectview.health.dayInstants
import de.steppicrew.healthconnectview.health.resolveAvailability
import de.steppicrew.healthconnectview.health.sessionsIn
import de.steppicrew.healthconnectview.health.totalDuration
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.TileSpec
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    /** Recent readings for a curve tile; empty for every other form. */
    val curve: List<Point> = emptyList(),
    /**
     * The day's sessions for a [TileSpec.Form.SESSIONS] tile; empty for every other form.
     *
     * The tile's face is the count of these and its subtitle their total duration, so the
     * list itself is what the tile is showing rather than a derived number: "three
     * activities, 1h 40m" cannot be recovered from a summed duration alone.
     */
    val sessions: List<Session> = emptyList(),
    val granted: Boolean = true,
    val loading: Boolean = true,
    /**
     * The single app this tile is filtered to, or null for the combined deduplicated view.
     * Shown on the tile, because a filtered number differs from the one the same tile shows
     * unfiltered and the difference would otherwise be unexplained.
     */
    val source: String? = null,
) {
    /** Everything the day's sessions covered, for the subtitle under a session count. */
    val sessionDuration: Duration get() = sessions.totalDuration()

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
    private val sourceStore = SourceStore(application)

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var config: DashboardConfig = DashboardConfig.DEFAULT

    /** Per-type source filter, shared with the full-screen view so the two agree. */
    private var sources: Map<String, String> = emptyMap()

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
            sources = runCatching { sourceStore.selections.first() }.getOrDefault(emptyMap())
            loadTiles()
        }
    }

    /**
     * Changes a tile's goal and persists it. A goal of zero or less would make the ring
     * meaningless, so it clears the override rather than storing an unusable value.
     */
    fun setGoal(typeName: String, goal: Double?) {
        val sanitised = goal?.takeIf { it > 0.0 }
        config = config.withGoal(typeName, sanitised)
        viewModelScope.launch {
            store.save(config)
            loadTiles()
        }
    }

    /** Removes a tile and persists the layout. */
    fun removeTile(typeName: String) {
        config = config.without(typeName)
        viewModelScope.launch {
            store.save(config)
            loadTiles()
        }
    }

    /** Pins a type. Ignored if already present, so double-adding cannot duplicate a tile. */
    fun addTile(typeName: String) {
        config = config.plus(Tile(typeName))
        viewModelScope.launch {
            store.save(config)
            loadTiles()
        }
    }

    /**
     * Moves a tile one place. Reordering by single steps rather than drag-and-drop: it is
     * reachable without a gesture the user has to discover, and it cannot drop a tile in an
     * unintended slot.
     */
    fun moveTile(typeName: String, forward: Boolean) {
        val from = config.tiles.indexOfFirst { it.typeName == typeName }
        if (from < 0) return
        val to = if (forward) from + 1 else from - 1
        val moved = config.moved(from, to)
        if (moved === config) return
        config = moved
        viewModelScope.launch {
            store.save(config)
            loadTiles()
        }
    }

    /** Types that may be pinned but are not yet: the add picker's contents. */
    fun addableTypes(): List<RecordTypeSpec<*>> {
        val pinned = config.tiles.map { it.typeName }.toSet()
        return RecordRegistry.all
            .filter { it.isPinnable && it.type.simpleName !in pinned }
            .sortedBy { it.type.simpleName }
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
            TileData(
                tile = tile,
                spec = spec,
                granted = spec.permission in granted,
                source = sources[tile.typeName],
            )
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

        // A session tile counts spans rather than measuring a metric, so neither branch below
        // describes it: its face is how many activities there were, not how much of anything.
        spec.tile.sessionKind?.takeIf { spec.tile.form == TileSpec.Form.SESSIONS }?.let { kind ->
            val sessions = runCatching { daySessions(date, kind) }.getOrDefault(emptyList())
            return placeholder.copy(sessions = sessions, loading = false)
        }

        val metric = spec.aggregate
        val origins = placeholder.source?.let { setOf(DataOrigin(it)) } ?: emptySet()

        val value = if (metric != null) {
            runCatching { repository.total(metric, dayFilter(date), origins) }.getOrNull()
                // Aggregation returns nothing for an interval as wide as its own bucket -- an
                // app posting one whole-day summary record. Summing that one app's records is
                // safe because a single writer cannot overlap itself; never for the combined
                // view, where resolving overlap is the whole point.
                ?: placeholder.source?.let { sumOwnRecords(spec, date, origins) }
        } else {
            runCatching {
                repository.read(
                    spec.type,
                    dayInstants(date),
                    maxRecords = LATEST_ONLY,
                    origins = origins,
                )
                    .firstOrNull()
                    ?.let { spec.pointsOf(it).lastOrNull()?.value }
            }.getOrNull()
        }

        val curve = if (spec.tile.form == TileSpec.Form.CURVE) {
            runCatching { recentPoints(spec, date, origins) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        return placeholder.copy(value = value, curve = curve, loading = false)
    }

    /**
     * The day's sessions of one kind.
     *
     * Read unfiltered by source, matching the chart's bands: a session written by any app is
     * still a fact about what the user was doing, while the source filter is about which
     * app's *measurements* to trust. Overlapping duplicates are collapsed by [sessionsIn]
     * preferring the writer that named the activity, so a workout recorded by both a watch
     * and a machine counts once.
     */
    private suspend fun daySessions(date: LocalDate, kind: Session.Kind): List<Session> {
        val zone = HealthRepository.DEFAULT_ZONE
        return repository.sessionsIn(
            start = date.atStartOfDay(zone).toInstant(),
            end = date.plusDays(1).atStartOfDay(zone).toInstant(),
            kinds = setOf(kind),
        )
    }

    /** One app's own records for the day, summed. Only valid for a single-source filter. */
    private suspend fun sumOwnRecords(
        spec: RecordTypeSpec<*>,
        date: LocalDate,
        origins: Set<DataOrigin>,
    ): Double? = runCatching {
        repository.read(spec.type, dayInstants(date), origins = origins)
            .flatMap { spec.pointsOf(it) }
            .takeIf { it.isNotEmpty() }
            ?.sumOf { it.value }
    }.getOrNull()

    /**
     * The trailing few hours of readings, for a curve tile.
     *
     * Raw points are safe here only because curve types are instantaneous -- a heart rate
     * sample is a reading at a moment, so overlapping writers duplicate points rather than
     * inflating a total. An interval type charted this way would double-count and would need
     * aggregation instead; TileSpec should not put one on a curve.
     *
     * On the current day the window ends now; on an earlier day it ends at that day's close,
     * so stepping back shows the same span rather than an empty slice.
     */
    private suspend fun recentPoints(
        spec: RecordTypeSpec<*>,
        date: LocalDate,
        origins: Set<DataOrigin>,
    ): List<Point> {
        val zone = HealthRepository.DEFAULT_ZONE
        val today = LocalDate.now()
        val end = if (date == today) Instant.now() else date.plusDays(1).atStartOfDay(zone).toInstant()
        val start = end.minus(CURVE_HOURS, ChronoUnit.HOURS)
            .coerceAtLeast(date.atStartOfDay(zone).toInstant())

        return repository.read(spec.type, TimeRangeFilter.between(start, end), origins = origins)
            .flatMap { spec.pointsOf(it) }
            .sortedBy { it.time }
    }

    private companion object {
        const val MAX_CONCURRENT_TILES = 4

        /** Trailing window for a curve tile. */
        const val CURVE_HOURS = 4L

        /** read() returns newest-first, so one record is the latest reading. */
        const val LATEST_ONLY = 1
    }
}
