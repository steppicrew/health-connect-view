package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.health.hrvWindow
import de.steppicrew.healthconnectview.health.HrvStanding
import de.steppicrew.healthconnectview.billing.Feature
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.dashboard.TileColor
import de.steppicrew.healthconnectview.dashboard.TileFace
import de.steppicrew.healthconnectview.health.Span
import android.app.Application
import android.util.Log
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
import de.steppicrew.healthconnectview.health.Trend
import de.steppicrew.healthconnectview.health.trendBefore
import de.steppicrew.healthconnectview.health.currentStreak
import de.steppicrew.healthconnectview.health.dailyActivities
import de.steppicrew.healthconnectview.health.dailyTotalsOf
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.atLeast
import de.steppicrew.healthconnectview.health.dayTotalFilter
import de.steppicrew.healthconnectview.health.dayInstants
import de.steppicrew.healthconnectview.health.openTally
import de.steppicrew.healthconnectview.health.resolveAvailability
import de.steppicrew.healthconnectview.health.sessionsIn
import de.steppicrew.healthconnectview.health.totalDuration
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.registry.ValueZones
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import de.steppicrew.healthconnectview.registry.GlucoseUnit
import de.steppicrew.healthconnectview.registry.UnitSystem
import de.steppicrew.healthconnectview.registry.Units
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
    /** The type's second value where it has one -- blood pressure's diastolic. */
    val secondaryValue: Double? = null,
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
    /**
     * The week before the shown day against the 30 days before it, or null where the type has
     * no aggregate or too few recorded days to say.
     */
    val trend: Trend? = null,
    /**
     * The day [value] was measured on, when it is carried from before the shown day; null when
     * it belongs to the shown day itself. See [TileSpec.carryLastReading].
     */
    val valueDate: LocalDate? = null,
    /**
     * The window [value] describes: the tile's own span where it is drawn large, the day
     * otherwise. A single cell always shows the day, whatever its stored span.
     */
    val shownSpan: Span = Span.DAY,
    /** The window's chart, for a large tile showing one; null for every other tile. */
    val chart: TileDetailData? = null,
    /**
     * For a type judged night by night: where the week's mean in [value] sits against the
     * usual range. Null for every other type, and where there are too few nights to say.
     */
    val standing: HrvStanding? = null,
    /**
     * Days in a row up to the shown day: of the goal met, for a ring showing a day, or with an
     * activity, for the activities tile. Zero where there is no run. See [currentStreak].
     */
    val streak: Int = 0,
) {
    /** Everything the day's sessions covered, for the subtitle under a session count. */
    val sessionDuration: Duration get() = sessions.totalDuration()

    /** Fraction of the goal, for a ring. Null when there is no goal or nothing to show. */
    val progress: Float?
        get() {
            // A goal is per day: a week's steps against a daily 5,000 would always read full.
            if (shownSpan != Span.DAY) return null
            // Goals are stored metric; the value is in the shown unit.
            val goal = tile.effectiveGoal?.let(spec::display) ?: return null
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

/** A type the add picker offers, and whether a tile of it is already pinned. */
data class AddCandidate(val spec: RecordTypeSpec<*>, val pinned: Boolean)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)
    private val store = DashboardStore(application)
    private val sourceStore = SourceStore(application)

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var config: DashboardConfig = DashboardConfig.DEFAULT

    /** Per-type source filter, shared with the full-screen view so the two agree. */
    private var sources: Map<String, String> = emptyMap()

    /**
     * The app to show where a tile has no per-type choice, or null for all sources.
     *
     * Applied per tile in [load], which falls back to all sources when the preferred app
     * wrote nothing for that type -- so preferring one app cannot empty a tile that has data.
     */
    private var preferred: String? = null

    /**
     * What the tiles currently on screen were loaded for, and when.
     *
     * Returning from a tile re-ran every read, so the dashboard blanked to placeholders and
     * refilled a second later -- on data that could not have changed in the time it takes to
     * look at one chart and press Back. The values are re-read whenever anything they depend
     * on differs, so this only ever suppresses a load that would produce the same answer.
     *
     * Deliberately in memory only: health values are never written to disk (see the app's
     * non-negotiables), and a cache that outlives the process would be exactly that.
     */
    private var cache: CacheKey? = null

    /**
     * Everything a tile's value depends on. Permissions are part of it, so a grant made in
     * settings takes effect on return rather than waiting out the TTL.
     */
    private data class CacheKey(
        val date: LocalDate,
        val tiles: List<Tile>,
        val sources: Map<String, String>,
        val preferred: String?,
        val granted: Set<String>,
        val loadedAt: Long,
        /** Values are converted as they are read, so a change of units needs a fresh read. */
        val units: UnitSystem = Units.system,
        val glucose: GlucoseUnit = Units.glucose,
    ) {
        fun isFresh(now: Long, other: CacheKey): Boolean =
            date == other.date &&
                tiles == other.tiles &&
                sources == other.sources &&
                preferred == other.preferred &&
                units == other.units &&
                glucose == other.glucose &&
                granted == other.granted &&
                now - loadedAt < CACHE_TTL_MS
    }

    init {
        refresh()
    }

    /** The load in flight, cancelled when a newer one starts. */
    private var loadJob: Job? = null

    /**
     * Reloads, replacing any load still running.
     *
     * `init` and the screen's first ON_RESUME both call this, a few milliseconds apart and
     * before either has filled the cache, so every cold start ran every read twice -- seen in
     * the load log as two identical lines. Cancelling the older load rather than skipping the
     * newer one means the latest inputs always win: a resume after a grant in settings must
     * not be swallowed by a load that started before it.
     */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val availability = resolveAvailability(getApplication())
            if (availability != Availability.Available) {
                _state.update { it.copy(availability = availability, loading = false) }
                return@launch
            }

            config = store.config.first()
            sources = runCatching { sourceStore.selections.first() }.getOrDefault(emptyMap())
            preferred = runCatching { sourceStore.preferred.first() }.getOrNull()
            loadTiles()
        }
    }

    /**
     * Reads the tiles again, cancelling a read still running, as [refresh] does: two loads
     * racing publish in the order they finish, not the order they started.
     */
    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadTiles() }
    }

    /**
     * Changes a tile's goal and persists it. A goal of zero or less would make the ring
     * meaningless, so it clears the override rather than storing an unusable value.
     */
    fun setGoal(typeName: String, goal: Double?) {
        val sanitised = goal?.takeIf { it > 0.0 }
        config = config.withGoal(typeName, sanitised)
        viewModelScope.launch { store.save(config) }
        reload()
    }

    /**
     * Changes a tile's colour zones and persists them. Null restores the type's defaults.
     */
    fun setZones(typeName: String, zones: ValueZones?) {
        config = config.withZones(typeName, zones?.takeIf { it.bounds.isNotEmpty() })
        viewModelScope.launch { store.save(config) }
        reload()
    }

    /** Sets a large tile's window and face, and persists them. */
    fun setOptions(id: String, span: Span, face: TileFace) {
        config = config.withOptions(id, span, face)
        viewModelScope.launch { store.save(config) }
        reload()
    }

    /**
     * Sets a tile's colour and persists it. In place, like a resize: nothing read changes, so
     * a reload would only blank the tiles for the length of a read.
     */
    fun setColor(id: String, color: TileColor) {
        config = config.withColor(id, color)
        _state.update { it.copy(tiles = withCurrentLayout(it.tiles)) }
        viewModelScope.launch { store.save(config) }
    }

    /** Removes a tile and persists the layout. */
    fun removeTile(id: String) {
        config = config.without(id)
        viewModelScope.launch { store.save(config) }
        reload()
    }

    /**
     * Pins a type. A type already on the dashboard gets another tile only with Pro; the picker
     * offers it locked otherwise, so this refuses rather than trusting the caller.
     */
    fun addTile(typeName: String) {
        if (config.has(typeName) && !AppEntitlements.current.pro.value.allows(Feature.TILE_REPEAT)) return
        config = config.adding(typeName)
        viewModelScope.launch { store.save(config) }
        reload()
    }

    /**
     * Moves a tile one place: the accessibility actions' way to reorder, where a drag is not
     * available.
     */
    fun moveTile(id: String, forward: Boolean) {
        val from = config.tiles.indexOfFirst { it.id == id }
        if (from < 0) return
        val moved = config.moved(from, if (forward) from + 1 else from - 1)
        if (moved !== config) reorder(moved.tiles.map { it.id })
    }

    /**
     * Puts the tiles in the order of [ids], as a drag leaves them, and persists it.
     *
     * In place, like a resize, and with no reload: the data does not change. Each move used
     * to save and reload, and a reload publishes the order it started with, so tapping "up"
     * a few times had a slow earlier reload land after a later one and send the tile back.
     */
    fun reorder(ids: List<String>) {
        val byId = config.tiles.associateBy { it.id }
        val ordered = ids.mapNotNull(byId::get)
        // Anything the caller did not name keeps its place at the end rather than vanishing.
        val reordered = DashboardConfig(ordered + config.tiles.filterNot { it.id in ids })
        if (reordered == config) return
        config = reordered
        _state.update { it.copy(tiles = withCurrentLayout(it.tiles)) }
        viewModelScope.launch { store.save(config) }
    }

    /**
     * Steps a tile to its next size. The shown tiles are updated in place rather than
     * reloaded: nothing about the data changes, and a reload would blank every tile to a dash
     * for the length of a read just to make one of them bigger.
     */
    fun resizeTile(id: String) {
        val before = config.tiles.firstOrNull { it.id == id }
        config = config.resized(id)
        val after = config.tiles.firstOrNull { it.id == id }
        _state.update { it.copy(tiles = withCurrentLayout(it.tiles)) }
        viewModelScope.launch { store.save(config) }
        // Except where the size decides *what* is shown: a tile growing into its stored week,
        // or shrinking back to the day, needs that window read.
        if (before != null && after != null && showsWindow(before) != showsWindow(after)) reload()
    }

    /**
     * [tiles] with the sizes, colours and order the config holds now. A load reads the config
     * when it starts, so a tile resized, coloured or moved while it runs would otherwise snap
     * back when the load publishes.
     */
    private fun withCurrentLayout(tiles: List<TileData>): List<TileData> {
        val position = config.tiles.withIndex().associate { (index, tile) -> tile.id to index }
        val current = config.tiles.associateBy { it.id }
        return tiles.map { data ->
            val tile = current[data.tile.id] ?: return@map data
            data.copy(tile = data.tile.copy(width = tile.width, height = tile.height, color = tile.color))
        }.sortedBy { position[it.tile.id] ?: Int.MAX_VALUE }
    }

    /**
     * Every pinnable type for the add picker, each marked whether it is on the dashboard
     * already: then it is offered again, as a further tile for a different window or face.
     */
    fun addableTypes(): List<AddCandidate> = RecordRegistry.all
        .filter { it.isPinnable }
        .sortedBy { it.type.simpleName }
        .map { AddCandidate(it, pinned = config.has(it.type.simpleName.orEmpty())) }

    fun showPreviousDay() {
        _state.update { it.copy(date = it.date.minusDays(1)) }
        reload()
    }

    fun showNextDay() {
        if (!_state.value.canStepForward) return
        _state.update { it.copy(date = it.date.plusDays(1)) }
        reload()
    }

    /**
     * Loads every tile for the selected day.
     *
     * Concurrency is capped for the same reason the catalog probe caps it: a full dashboard
     * would otherwise fire a tile's worth of IPC calls at Health Connect simultaneously.
     */
    private suspend fun loadTiles() {
        val date = _state.value.date
        // Always re-read: permissions are authoritative from Health Connect and can be
        // revoked while backgrounded, so they are never taken from the cache.
        val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())

        val now = System.currentTimeMillis()
        // Colour left out: it changes nothing read, and a new colour must not cost a reload.
        val key = CacheKey(date, config.tiles.map { it.copy(color = TileColor.DEFAULT) }, sources, preferred, granted, now)
        val loadedTiles = _state.value.tiles.takeIf { it.none(TileData::loading) }
        if (loadedTiles != null && cache?.isFresh(now, key) == true) {
            // Nothing the values depend on has changed and they are still fresh, so the reads
            // would return what is already on screen. Skipping them is what keeps the
            // dashboard from blanking on the way back from a tile.
            return
        }

        val previous = _state.value.tiles.associateBy { it.tile.id }
        val placeholders = config.tiles.mapNotNull { tile ->
            val spec = tile.spec ?: return@mapNotNull null
            TileData(
                tile = tile,
                spec = spec,
                granted = spec.permission in granted,
                // The preference is provisional here: load() drops it for a type the
                // preferred app never wrote, and reports back what it actually used.
                source = sources[tile.typeName] ?: preferred,
            )
        }

        // Only the first load has nothing to show. Afterwards the previous values stay on
        // screen while the re-read runs: replacing them with empty placeholders is what made
        // the dashboard blank and refill on the way back from a tile, and the cache above
        // only hides that for as long as its TTL lasts. A stale number for a moment is a
        // better answer than no number, since it is what the tile showed a second ago.
        val shown = placeholders.map { placeholder ->
            val carried = previous[placeholder.tile.id] ?: return@map placeholder
            // Source included: a value read under one filter must not be shown against
            // another, or changing the preferred app leaves the old app's number on screen
            // under the new app's name until the read returns.
            if (carried.loading ||
                carried.granted != placeholder.granted ||
                carried.source != placeholder.source
            ) {
                placeholder
            } else {
                placeholder.copy(
                    value = carried.value,
                    secondaryValue = carried.secondaryValue,
                    valueDate = carried.valueDate,
                    curve = carried.curve,
                    sessions = carried.sessions,
                    trend = carried.trend,
                    streak = carried.streak.takeIf { carried.tile.effectiveGoal == placeholder.tile.effectiveGoal } ?: 0,
                    shownSpan = carried.shownSpan,
                    standing = carried.standing,
                    chart = carried.chart,
                    loading = false,
                )
            }
        }
        _state.update { it.copy(tiles = shown, loading = false) }

        val started = System.currentTimeMillis()
        val gate = Semaphore(MAX_CONCURRENT_TILES)
        val loaded = coroutineScope {
            placeholders.map { placeholder ->
                async {
                    if (!placeholder.granted) {
                        placeholder.copy(loading = false)
                    } else {
                        gate.withPermit {
                            load(placeholder, date, RecordRegistry.HISTORY_PERMISSION in granted)
                        }
                    }
                }
            }.awaitAll()
        }
        // Keep the previous arrows and streaks until the new ones arrive, as with the values
        // above -- the streak only while the goal is the same, since a new goal is a new count.
        val withCarried = loaded.map { tile ->
            val carried = previous[tile.tile.id]
            if (carried != null && carried.source == tile.source) {
                tile.copy(
                    trend = carried.trend,
                    streak = carried.streak.takeIf { carried.tile.effectiveGoal == tile.tile.effectiveGoal } ?: 0,
                )
            } else {
                tile
            }
        }
        _state.update { it.copy(tiles = withCurrentLayout(withCarried)) }
        // Timing and count only, never a value: how long the dashboard takes to fill is the
        // cost every per-tile read adds to, so it is worth being able to measure from adb.
        Log.i(TAG, "loaded ${loaded.size} tiles in ${System.currentTimeMillis() - started} ms")

        val trended = withCurrentLayout(loadTrends(loaded, date, gate))
        // Only if nothing has replaced these tiles in the meantime -- a day step or a source
        // change starts a new load, and its tiles must not receive this load's arrows.
        _state.update { state ->
            if (state.date == date && state.tiles.map { it.tile } == trended.map { it.tile }) {
                state.copy(tiles = trended)
            } else {
                state
            }
        }
        Log.i(TAG, "trends in ${System.currentTimeMillis() - started} ms")
        cache = key.copy(loadedAt = System.currentTimeMillis())
    }

    /**
     * A tile's number always comes from aggregation, never from summing records: a day total
     * is exactly where several apps writing the same metric would double-count.
     *
     * Types with no aggregate metric cannot show a total at all. They fall back to the latest
     * reading of the day, which is a different statement -- a weight, not a sum -- and is the
     * only honest number available for them.
     */
    private suspend fun load(placeholder: TileData, date: LocalDate, historyGranted: Boolean): TileData {
        val tile = placeholder.tile
        if (!showsWindow(tile)) return loadDay(placeholder, date)

        // The day's own path still supplies a day's number -- a carried weight, a ring, the
        // floor under a running tally -- and the chart comes from the detail screen's loader,
        // so the tile draws exactly the chart its tap opens.
        val day = if (tile.span == Span.DAY) loadDay(placeholder, date) else null
        val offset = tile.span.offsetOf(date)
        val source = day?.source ?: resolveSource(
            placeholder,
            TimeRangeFilter.between(windowStart(tile.span, offset), windowEnd(tile.span, offset)),
        )
        val capped = tile.span.needsHistoryPermission(offset) && !historyGranted
        val chart = runCatching {
            TileChartLoader(repository, store).chart(placeholder.spec, tile.span, offset, capped, source)
        }.getOrNull()

        return (day ?: placeholder.copy(source = source)).copy(
            value = day?.value ?: chart?.total,
            secondaryValue = day?.secondaryValue ?: chart?.secondaryTotal,
            sessions = day?.sessions ?: chart?.sessions.orEmpty(),
            standing = day?.standing ?: chart?.hrv?.day?.standing,
            shownSpan = tile.span,
            chart = chart,
            loading = false,
        )
    }

    /**
     * Whether [tile] is drawn with its own window or face, rather than as the day's value.
     *
     * Large tiles only, and so Pro only: without it every tile is drawn as one cell, and a
     * single cell has room for neither a chart nor a label saying which window it covers.
     */
    private fun showsWindow(tile: Tile): Boolean =
        AppEntitlements.current.pro.value.allows(Feature.TILE_SIZES) &&
            tile.isLarge &&
            (tile.span != Span.DAY || tile.face != TileFace.VALUE)

    /**
     * The per-type choice, else the preferred app where it wrote into [range].
     *
     * A per-type choice stands even when it comes up empty -- it was made deliberately for
     * this type. A global preference is only a default, so a type the preferred app never
     * writes falls back to all sources rather than showing an empty tile, which would read as
     * missing data instead of as a filter matching nothing.
     */
    private suspend fun resolveSource(placeholder: TileData, range: TimeRangeFilter): String? {
        val spec = placeholder.spec
        return sources[spec.type.simpleName] ?: placeholder.source?.takeIf { pkg ->
            runCatching {
                repository.read(spec.type, range, maxRecords = LATEST_ONLY, origins = setOf(DataOrigin(pkg)))
                    .isNotEmpty()
            }.getOrDefault(false)
        }
    }

    /** The tile for the dashboard's day, as every single cell shows it. */
    private suspend fun loadDay(placeholder: TileData, date: LocalDate): TileData {
        val spec = placeholder.spec

        // A session tile counts spans rather than measuring a metric, so neither branch below
        // describes it: its face is how many activities there were, not how much of anything.
        spec.tile.sessionKind?.takeIf { spec.tile.form == TileSpec.Form.SESSIONS }?.let { kind ->
            val sessions = runCatching { daySessions(date, kind) }.getOrDefault(emptyList())
            return placeholder.copy(sessions = sessions, loading = false)
        }

        val metric = spec.aggregate

        val effective = resolveSource(placeholder, dayInstants(date))
        val tile = placeholder.copy(source = effective)
        val origins = effective?.let { setOf(DataOrigin(it)) } ?: emptySet()

        // HRV's tile number is the week's mean of nightly values, coloured against the usual
        // range -- the latest five-minute reading said nothing about how the week went.
        if (spec.tile.nightlyStatus) {
            val day = runCatching { repository.hrvWindow(date, date, origins).days.singleOrNull() }.getOrNull()
            return tile.copy(value = day?.weekMean, standing = day?.standing, loading = false)
        }

        val value = if (metric != null) {
            val total = runCatching { repository.total(metric, dayTotalFilter(date), origins) }.getOrNull()
                // Aggregation returns nothing for an interval as wide as its own bucket -- an
                // app posting one whole-day summary record. Summing that one app's records is
                // safe because a single writer cannot overlap itself; never for the combined
                // view, where resolving overlap is the whole point.
                ?: tile.source?.let { sumOwnRecords(spec, date, origins) }
            // Today, a running tally labelled as the whole day is apportioned by the platform
            // and reads low; the writer's own figure is the floor. See openTally.
            if (date == LocalDate.now()) {
                atLeast(total, runCatching { repository.openTally(spec, origins) }.getOrNull())
            } else {
                total
            }
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

        // Only beside a first value from the same day: a diastolic without its systolic, or one
        // paired with a carried reading from another day, would be half of two readings.
        val secondaryValue = if (value != null) {
            spec.secondaryAggregate?.let { second ->
                runCatching { repository.total(second, dayTotalFilter(date), origins) }.getOrNull()
            }
        } else {
            null
        }

        val curve = if (spec.tile.form == TileSpec.Form.CURVE) {
            runCatching { recentPoints(spec, date, origins) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        // Only where the day itself has nothing: a reading on the shown day always wins over
        // an older one, however recent.
        val carried = if (value == null && spec.tile.carryLastReading) {
            runCatching { lastReadingBefore(spec, date, origins) }.getOrNull()
        } else {
            null
        }

        return tile.copy(
            value = value ?: carried?.second,
            secondaryValue = secondaryValue,
            valueDate = carried?.first,
            curve = curve,
            loading = false,
        )
    }

    /**
     * The newest reading before [date], with the day it was taken.
     *
     * A raw reading rather than an aggregate, which is safe for these types because each is a
     * measurement at a moment: two writers' weights are two readings, not an overlapping sum.
     * Looks back a year; past 30 days that needs the history permission, and without it the
     * platform quietly returns only what is newer, so an older weight simply is not found.
     */
    private suspend fun lastReadingBefore(
        spec: RecordTypeSpec<*>,
        date: LocalDate,
        origins: Set<DataOrigin>,
    ): Pair<LocalDate, Double>? {
        val zone = HealthRepository.DEFAULT_ZONE
        val latest = repository.read(
            spec.type,
            TimeRangeFilter.between(
                date.minusDays(CARRY_DAYS).atStartOfDay(zone).toInstant(),
                date.atStartOfDay(zone).toInstant(),
            ),
            maxRecords = LATEST_ONLY,
            origins = origins,
        ).firstOrNull() ?: return null
        val reading = spec.pointsOf(latest).lastOrNull() ?: return null
        return reading.time.atZone(zone).toLocalDate() to reading.value
    }

    /**
     * The trend arrows and goal streaks, fetched after the values are on screen.
     *
     * A month of daily buckets per tile nearly doubled the time to fill the dashboard when it
     * was read alongside each value -- measured on the phone, about 650 ms to 1200 ms for
     * eight tiles -- because the tiles are published together once the slowest finishes. The
     * arrow is secondary to the number, so the number no longer waits for it; the streak, which
     * may read back a year, even less so.
     */
    private suspend fun loadTrends(tiles: List<TileData>, date: LocalDate, gate: Semaphore): List<TileData> =
        coroutineScope {
            tiles.map { data ->
                async {
                    // The arrow compares a day with the weeks before it, so it says nothing
                    // about a tile showing a week or a year; nor does a streak.
                    if (!data.granted || data.shownSpan != Span.DAY) return@async data
                    val origins = data.source?.let { setOf(DataOrigin(it)) } ?: emptySet()
                    val metric = data.spec.aggregate?.takeIf { data.spec.tile.form != TileSpec.Form.SESSIONS }
                    val trend = metric?.let {
                        gate.withPermit {
                            runCatching { repository.trendBefore(it, date, origins)?.direction }.getOrNull()
                        }
                    }
                    val streak = gate.withPermit { runCatching { streakOf(data, date, origins) }.getOrNull() } ?: 0
                    data.copy(trend = trend, streak = streak)
                }
            }.awaitAll()
        }

    /**
     * The tile's current run: for a ring, of days its goal was met, against the value the
     * ring shows; for the activities tile, of days with an activity. Zero for every other tile.
     */
    private suspend fun streakOf(data: TileData, date: LocalDate, origins: Set<DataOrigin>): Int {
        val tile = data.spec.tile
        val metric = data.spec.aggregate
        val goal = data.tile.effectiveGoal?.let(data.spec::display)?.takeIf { it > 0.0 }
        return when {
            tile.form == TileSpec.Form.RING && metric != null && goal != null ->
                currentStreak(goal, date, data.value, repository.dailyTotalsOf(metric, origins))
            tile.form == TileSpec.Form.SESSIONS && tile.sessionKind == Session.Kind.EXERCISE ->
                currentStreak(1.0, date, data.sessions.size.toDouble(), repository.dailyActivities())
            else -> 0
        }
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

    /** One app's own records for the day, combined. Only valid for a single-source filter. */
    private suspend fun sumOwnRecords(
        spec: RecordTypeSpec<*>,
        date: LocalDate,
        origins: Set<DataOrigin>,
    ): Double? = runCatching {
        repository.read(spec.type, dayInstants(date), origins = origins)
            .flatMap { spec.pointsOf(it) }
            .let { points -> spec.combine(points.map { it.value }) }
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
        const val TAG = "Dashboard"
        const val MAX_CONCURRENT_TILES = 4

        /**
         * How long loaded values stay good. Long enough to cover leaving the dashboard and
         * coming back, short enough that a genuinely new reading appears without the user
         * wondering why it has not. Anything the values depend on invalidates them regardless.
         */
        const val CACHE_TTL_MS = 30_000L

        /** How far back a carried reading may come from. */
        const val CARRY_DAYS = 365L

        /** Trailing window for a curve tile. */
        const val CURVE_HOURS = 4L

        /** read() returns newest-first, so one record is the latest reading. */
        const val LATEST_ONLY = 1
    }
}
