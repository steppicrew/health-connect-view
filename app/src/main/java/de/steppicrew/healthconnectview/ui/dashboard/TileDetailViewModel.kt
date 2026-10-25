package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.ui.SharedDate
import de.steppicrew.healthconnectview.health.countsFromZero
import de.steppicrew.healthconnectview.health.readHeatmap
import java.time.Period
import de.steppicrew.healthconnectview.health.yearHeatmap
import de.steppicrew.healthconnectview.health.YearHeatmap
import de.steppicrew.healthconnectview.billing.Feature
import de.steppicrew.healthconnectview.billing.AppEntitlements
import android.app.Application
import android.util.Log
import de.steppicrew.healthconnectview.export.ExportResult
import android.net.Uri
import de.steppicrew.healthconnectview.export.ExportPeriod
import de.steppicrew.healthconnectview.ui.compare.CandidateCache
import de.steppicrew.healthconnectview.ui.compare.comparableTypes
import de.steppicrew.healthconnectview.ui.components.ExportKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import de.steppicrew.healthconnectview.dashboard.DashboardStore
import de.steppicrew.healthconnectview.dashboard.SourceStore
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.ui.session.MAX_CONCURRENT_READS
import de.steppicrew.healthconnectview.ui.session.SessionCurve
import de.steppicrew.healthconnectview.ui.session.heartRateDuring
import de.steppicrew.healthconnectview.health.movementDuring
import de.steppicrew.healthconnectview.health.recordsIn
import de.steppicrew.healthconnectview.health.Nutrient
import de.steppicrew.healthconnectview.health.nutrientTotals
import de.steppicrew.healthconnectview.health.PressureReading
import de.steppicrew.healthconnectview.health.dayPartWindow
import de.steppicrew.healthconnectview.health.splitByDayPart
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.NutritionRecord
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.ui.UiState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Duration
import androidx.health.connect.client.records.SleepSessionRecord
import de.steppicrew.healthconnectview.health.personalRecord
import de.steppicrew.healthconnectview.health.sourceCoverage
import de.steppicrew.healthconnectview.health.PersonalRecord
import kotlinx.coroutines.delay
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import de.steppicrew.healthconnectview.health.TrainingNights
import de.steppicrew.healthconnectview.health.nightsByMorning
import de.steppicrew.healthconnectview.health.sessionsIn
import de.steppicrew.healthconnectview.health.splitByTraining
import de.steppicrew.healthconnectview.health.trainingDays
import java.time.Instant
import java.time.LocalDate
import de.steppicrew.healthconnectview.util.catching

/**
 * One type, over a span that can be stepped backwards and forwards.
 *
 * Reached from a dashboard tile and from the catalog alike.
 */
class TileDetailViewModel(
    application: Application,
    /**
     * The window on screen, kept across the screen being rebuilt. Without it, every new
     * composition -- a restore after the process was killed in the background, a rotation --
     * called [load] with the route's date again and dropped the days swiped to since.
     */
    private val saved: SavedStateHandle,
) : AndroidViewModel(application) {

    private val base = HealthRepository(application)

    /**
     * [base], or during a load the same with its answers shared, so the chart, its extras, the
     * list and the heatmap read what they have in common once. See [SharedReads].
     */
    private var repository = base
    private val sourceStore = SourceStore(application)
    private val dashboardStore = DashboardStore(application)

    private val _state = MutableStateFlow<UiState<TileDetailData>>(UiState.Loading)
    val state: StateFlow<UiState<TileDetailData>> = _state.asStateFlow()

    /** Outcome of the last export, for a snackbar; one event per export. */
    private val _exportResults = MutableSharedFlow<ExportResult>(extraBufferCapacity = 1)
    val exportResults: SharedFlow<ExportResult> = _exportResults.asSharedFlow()

    private val exports = TileExports(application, sourceStore)

    /** The types a comparison may set beside this one: those with data in the window shown. */
    suspend fun compareCandidates(): List<RecordTypeSpec<*>> = candidateCache.get(_span.value, shownEnd) {
        repository.comparableTypes(_span.value, shownEnd)
    }

    private val candidateCache = CandidateCache()

    /** Whether [kind] over [period] would hold any data; when not, says so and the save dialog stays shut. */
    suspend fun canExport(kind: ExportKind, period: ExportPeriod, combined: Boolean): Boolean {
        val spec = _spec.value ?: return false
        val any = exports.hasData(repository, spec, selectedSource, kind, period, combined)
        if (!any) _exportResults.tryEmit(ExportResult.Empty)
        return any
    }

    /** Writes this type, this source filter and [period] to [uri]. */
    fun export(kind: ExportKind, period: ExportPeriod, combined: Boolean, uri: Uri) {
        val spec = _spec.value ?: return
        val source = selectedSource
        viewModelScope.launch {
            _exportResults.tryEmit(exports.write(repository, spec, source, kind, period, combined, uri))
        }
    }

    /**
     * Whether data older than 30 days is readable, as of the last load; without it an export
     * reaching further back is cut short, and the period dialog says so.
     */
    private val _historyGranted = MutableStateFlow(true)
    val historyGranted: StateFlow<Boolean> = _historyGranted.asStateFlow()

    /** Whether [_historyGranted] has been read yet; until then there is nothing to compare. */
    private var historyKnown = false

    /**
     * Re-reads when access to older data changed while the screen was away -- typically allowed
     * on the permission screen its own button leads to. A window cut to 30 days would
     * otherwise stay cut until the next swipe.
     */
    fun onResume() {
        if (!historyKnown) return
        viewModelScope.launch {
            val granted = catching { repository.grantedPermissions() }.getOrNull() ?: return@launch
            if ((RecordRegistry.HISTORY_PERMISSION in granted) != _historyGranted.value) reload()
        }
    }

    /** The report for [period] as PDF bytes in memory, for the print preview; null if it failed. */
    suspend fun renderReport(period: ExportPeriod, combined: Boolean): ByteArray? {
        val spec = _spec.value ?: return null
        return exports.render(repository, spec, selectedSource, period, combined)
    }

    fun reportNoViewer() {
        _exportResults.tryEmit(ExportResult.NoViewer)
    }

    /**
     * Share of the current load's steps finished, 0 to 1, or null when not loading.
     *
     * Counted in steps -- chart, total, list, the source picker's two reads, sessions where the
     * type has them -- because those are what is known: Health Connect reports no progress
     * within a request. It moves in real jumps rather than a guessed time.
     */
    private val _progress = MutableStateFlow<Float?>(null)

    /**
     * The day of the newest record before an empty window, offered as a way to it; null while
     * the window has data or nothing older exists.
     */
    private val _latestBefore = MutableStateFlow<LocalDate?>(null)
    val latestBefore: StateFlow<LocalDate?> = _latestBefore.asStateFlow()

    /** The personal record to show under an empty window's message; null otherwise. */
    private val _emptyRecord = MutableStateFlow<PersonalRecord?>(null)
    val emptyRecord: StateFlow<PersonalRecord?> = _emptyRecord.asStateFlow()
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    // Opens on the day, matching the tile that was tapped: landing on a week would show a
    // different number from the one just touched, and the point of opening a tile is to see
    // that figure in more detail.
    private val _span = MutableStateFlow(Span.DAY)
    val span: StateFlow<Span> = _span.asStateFlow()

    /**
     * The day the window on screen ends on, set from the app's shared date at each reload, so
     * a load keeps one window however the date moves while it runs. Stepping moves the shared
     * date (see [SharedDate]), which the dashboard and every other window then end on too.
     */
    private var shownEnd: LocalDate = SharedDate.current

    /** The day the windows end on, for the screen's label and stepper. */
    val end: StateFlow<LocalDate> = SharedDate.date

    private var typeName: String? = null
    private var selectedSource: String? = null

    /**
     * The type being shown, known from the moment it is asked for.
     *
     * Separate from the loaded data so the screen can title itself while loading, and -- more
     * to the point -- while empty: a day with nothing recorded was showing a blank title bar,
     * which left no way to tell which type had nothing in it.
     */
    private val _spec = MutableStateFlow<RecordTypeSpec<*>?>(null)
    val spec: StateFlow<RecordTypeSpec<*>?> = _spec.asStateFlow()

    private val _coverage = MutableStateFlow<CoverageLoad?>(null)

    /** The writers compared over the last 30 days, once asked for; null until then. */
    val coverage: StateFlow<CoverageLoad?> = _coverage.asStateFlow()
    private var coverageJob: Job? = null

    /**
     * Counts what each app wrote for this type over the last 30 days, on request only: it
     * reads every record of the type, which for heart rate is tens of thousands.
     */
    fun compareSources() {
        val spec = _spec.value ?: return
        coverageJob?.cancel()
        coverageJob = viewModelScope.launch {
            _coverage.value = CoverageLoad.Loading
            val started = System.nanoTime()
            _coverage.value = try {
                val coverage = repository.sourceCoverage(spec, Instant.now(), lookUpSince = _historyGranted.value)
                // Timing and writer count only, never a reading.
                Log.d(TAG, "compared ${coverage.writers.size} writers of ${spec.type.simpleName} in ${(System.nanoTime() - started) / 1_000_000} ms")
                CoverageLoad.Done(coverage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "source comparison failed: ${e.javaClass.simpleName}")
                CoverageLoad.Failed
            }
        }
    }

    /**
     * Filters every read and aggregate to one app.
     *
     * All sources stay the default: Health Connect's deduplicated total is the correct answer
     * for the metric, and is deliberately not the same as any single app's figure. Selecting a
     * source answers the different question of what one app recorded.
     */
    fun selectSource(packageName: String?) {
        selectedSource = packageName
        val type = typeName ?: return
        viewModelScope.launch {
            // "All" is stored as a choice of its own, so it outlasts the preferred app.
            sourceStore.select(type, packageName ?: SourceStore.ALL_SOURCES)
            refreshSourceDefault(type)
            reload()
        }
    }

    /** Drops this type's own choice, so it follows the preferred app from settings again. */
    fun useDefaultSource() {
        val type = typeName ?: return
        viewModelScope.launch {
            sourceStore.select(type, null)
            selectedSource = resolveSource(type)
            refreshSourceDefault(type)
            reload()
        }
    }

    private val _sourceDefault = MutableStateFlow(SourceDefault(preferred = null, ownChoice = false))

    /** The preferred app from settings, and whether this type has a choice of its own. */
    val sourceDefault: StateFlow<SourceDefault> = _sourceDefault.asStateFlow()

    private suspend fun refreshSourceDefault(type: String) {
        _sourceDefault.value = SourceDefault(
            preferred = catching { sourceStore.preferred.first() }.getOrNull(),
            ownChoice = catching { sourceStore.selections.first() }.getOrDefault(emptyMap()).containsKey(type),
        )
    }

    /**
     * [date] is the day the dashboard was showing, as an ISO string. The offset is derived
     * from it so the detail view opens on the same day the tapped tile described; an empty or
     * unparseable value simply opens on today.
     */
    fun load(typeName: String, date: String = "", span: String = "") {
        // Back from a workout or a record re-runs the screen's effect with the same route. The
        // window and its data are still here; reading them again blanked the list to a
        // spinner and threw the scroll position away with it.
        if (typeName == this.typeName && _state.value is UiState.Data && shownEnd == SharedDate.current) return
        if (typeName != this.typeName) {
            coverageJob?.cancel()
            _coverage.value = null
        }
        this.typeName = typeName
        _spec.update { RecordRegistry.specOrNull(typeName) }
        // The window last shown for this type wins over the route's: the route says where the
        // screen was opened, the saved state where the user has been since.
        val restored = saved.get<String>(KEY_TYPE) == typeName
        val wantedSpan = if (restored) saved.get<String>(KEY_SPAN).orEmpty() else span
        val wantedDate = if (restored) saved.get<String>(KEY_DATE).orEmpty() else date
        Span.entries.firstOrNull { it.name.equals(wantedSpan, ignoreCase = true) }?.let { _span.value = it }
        // The route's date is the screen it came from, which shows the shared date already;
        // a restore after the process was killed brings the date back to the whole app.
        catching { LocalDate.parse(wantedDate) }.getOrNull()?.let(SharedDate::set)
        shownEnd = SharedDate.current
        viewModelScope.launch {
            selectedSource = resolveSource(typeName)
            refreshSourceDefault(typeName)
            reload()
        }
    }

    /**
     * The source this screen opens on: the per-type choice, else the preferred app.
     *
     * The preference is honoured only where that app actually wrote this type, so a type it
     * never writes opens on all sources rather than on an empty chart. Establishing that
     * costs one unfiltered read of the current window -- the same read the picker below is
     * built from, before any filter narrows it.
     */
    private suspend fun resolveSource(typeName: String): String? {
        val selections = catching { sourceStore.selections.first() }.getOrDefault(emptyMap())
        selections[typeName]?.let { return it.takeUnless { chosen -> chosen == SourceStore.ALL_SOURCES } }

        val preferred = catching { sourceStore.preferred.first() }.getOrNull() ?: return null
        val spec = RecordRegistry.specOrNull(typeName) ?: return null
        val writers = catching {
            repository.recordsIn(
                spec,
                windowStart(_span.value, 0, shownEnd),
                windowEnd(_span.value, 0, shownEnd),
            ).map { spec.originOf(it) }
                .toSet()
        }.getOrDefault(emptySet())
        return sourceStore.effective(typeName, selections, preferred, writers)
    }

    /** Opens the window of the current span ending on [date], for the whole app. */
    fun showDate(date: LocalDate) {
        SharedDate.set(date)
        reload()
    }

    /** Opens one day, whatever span is shown: the day a record was set, from its note. */
    fun showDay(date: LocalDate) {
        _span.value = Span.DAY
        SharedDate.set(date)
        reload()
    }

    /**
     * The day of the newest record before [start], in the selected source, or null.
     *
     * One request: reads come newest first, so the first record of a long range ending at the
     * window is the one wanted. A night counts by the morning it ended on, as everywhere else;
     * other records by when they began. Without the history permission Health Connect returns
     * nothing older than 30 days, so the offer reaches back that far only.
     */
    private suspend fun latestBefore(spec: RecordTypeSpec<*>, start: Instant): LocalDate? = catching {
        val origins = selectedSource?.let { setOf(DataOrigin(it)) } ?: emptySet()
        val record = repository.recordsIn(spec, start.minus(LATEST_LOOKBACK), start, origins, maxRecords = 1)
            .firstOrNull() ?: return@catching null
        val time = if (spec.type == SleepSessionRecord::class) spec.endTimeOf(record) ?: spec.timeOf(record) else spec.timeOf(record)
        time.atZone(HealthRepository.DEFAULT_ZONE).toLocalDate()
    }.getOrNull()

    /** A new span ends on the same day: the date is the app's, not the span's. */
    fun setSpan(span: Span) {
        _span.update { span }
        reload()
    }

    /** The window before, ending the day before this one starts. */
    fun stepBack() {
        SharedDate.set(_span.value.previousEnd(shownEnd))
        reload()
    }

    /** Stepping forward past the current window would show an empty future. */
    /** Back to the window holding today. */
    fun stepToNow() {
        if (!canStepForward) return
        SharedDate.set(LocalDate.now())
        reload()
    }

    fun stepForward() {
        if (!canStepForward) return
        SharedDate.set(_span.value.nextEnd(shownEnd))
        reload()
    }

    val canStepForward: Boolean get() = shownEnd.isBefore(LocalDate.now())

    private val _refreshing = MutableStateFlow(false)

    /** True while a reload the user asked for by pulling the page down is running. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /**
     * The same window read again from scratch, curves and records included: the user pulled
     * the page down, most likely after a sync brought something in.
     */
    fun pullRefresh() {
        curveCache.clear()
        personalRecords.clear()
        _refreshing.value = true
        reload()
        val job = loadJob
        viewModelScope.launch {
            job?.join()
            _refreshing.value = false
        }
    }

    /** The load in flight, cancelled when a newer one starts. */
    private var loadJob: Job? = null

    private val _comparing = MutableStateFlow(saved.get<Boolean>(KEY_COMPARE) ?: false)

    /** Whether the chart is set against the same window a year earlier; kept while stepping. */
    val comparing: StateFlow<Boolean> = _comparing.asStateFlow()

    /** The year-earlier read in flight when the comparison was switched on over a shown window. */
    private var compareJob: Job? = null

    fun setComparing(on: Boolean) {
        _comparing.value = on
        saved[KEY_COMPARE] = on
        compareJob?.cancel()
        if (!on) {
            _state.update { current ->
                (current as? UiState.Data)?.let { UiState.Data(it.value.copy(previousYear = null, previousTotal = null, previousSecondaryTotal = null)) } ?: current
            }
            return
        }
        val spec = RecordRegistry.specOrNull(typeName ?: return) ?: return
        val span = _span.value
        compareJob = viewModelScope.launch { showPreviousYear(spec, span, 0) }
    }

    /**
     * The same window a year earlier, read as the chart was -- the same loader, so the two
     * cannot disagree about what a day is -- and moved forward onto this window's days: 52
     * weeks or 13 four-week windows back, so each weekday lies over its own, or one calendar
     * year, whose weekly buckets start on the window's first day as this year's do.
     */
    private suspend fun showPreviousYear(spec: RecordTypeSpec<*>, span: Span, offset: Int) {
        if (!comparesYear(span)) return
        val back = offset + yearSteps(span)
        val capped = span.needsHistoryPermission(back, shownEnd) && !_historyGranted.value
        val started = System.currentTimeMillis()
        val earlier = catching {
            TileChartLoader(repository, dashboardStore, today = shownEnd)
                .chart(spec, span, back, capped, selectedSource, deferExtras = true, withStreak = false)
        }.getOrNull()
        currentCoroutineContext().ensureActive()
        val shift = Duration.between(windowStart(span, back, shownEnd), windowStart(span, offset, shownEnd))
        // Counts and timing only, never a reading.
        Log.d(TAG, "year earlier: ${earlier?.points?.size ?: 0} points in ${System.currentTimeMillis() - started} ms")
        _state.update { current ->
            (current as? UiState.Data)?.let {
                UiState.Data(
                    it.value.copy(
                        previousYear = earlier?.points.orEmpty().map { point -> point.copy(time = point.time.plus(shift)) },
                        previousTotal = earlier?.total,
                        previousSecondaryTotal = earlier?.secondaryTotal,
                    ),
                )
            } ?: current
        }
    }

    /**
     * Reloads, replacing any load still running. With the list read after the chart is shown,
     * swiping on through years while one loads would otherwise let the older load finish last
     * and put its window's list under the newer window's chart.
     */
    private fun reload() {
        val spec = RecordRegistry.specOrNull(typeName ?: return) ?: run {
            _state.update { UiState.Error("Unknown type") }
            return
        }

        typeName?.let { saved[KEY_TYPE] = it }
        saved[KEY_SPAN] = _span.value.name
        shownEnd = SharedDate.current
        saved[KEY_DATE] = shownEnd.toString()

        loadJob?.cancel()
        compareJob?.cancel()
        loadJob = viewModelScope.launch {
            val shared = base.sharing()
            repository = shared
            try {
                loadShown(spec)
            } finally {
                shared.sharedReads?.let { Log.d(TAG, "reads: ${it.issued} sent, ${it.shared} shared") }
                // Unless a newer load has taken over: its reads must stay shared.
                if (repository === shared) repository = base
            }
        }
    }

    private suspend fun loadShown(spec: RecordTypeSpec<*>) = coroutineScope {
        run {
            _state.update { UiState.Loading }
            _latestBefore.value = null

            val granted = try {
                repository.grantedPermissions()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not "not allowed": that screen asks the user to grant what they may well have.
                _state.update { UiState.Error(e.message ?: "Could not read data") }
                return@run
            }
            if (spec.permission !in granted) {
                _state.update { UiState.NoPermission }
                return@run
            }

            _historyGranted.value = RecordRegistry.HISTORY_PERMISSION in granted
            historyKnown = true
            val span = _span.value
            val offset = 0
            val capped = span.needsHistoryPermission(offset, shownEnd) &&
                RecordRegistry.HISTORY_PERMISSION !in granted

            val loadStarted = System.currentTimeMillis()
            val result = catching {
                loadData(spec, span, offset, capped, selectedSource) { chart ->
                    ensureActive()
                    Log.d(TAG, "chart of ${spec.type.simpleName} in ${System.currentTimeMillis() - loadStarted} ms")
                    _progress.value = null
                    // Only when there is something to show: a partial that turns out empty
                    // would flash a chart-less screen before the empty message.
                    if (chart.points.isNotEmpty() || chart.total != null || chart.sessions.isNotEmpty()) {
                        _state.update { UiState.Data(chart) }
                    }
                }
            }
            // Reads inside may swallow a cancellation into defaults, so a superseded load can
            // reach this point with a result. It must not
            // put that result, or an error, over the newer load's screen.
            ensureActive()
            _progress.value = null
            Log.d(TAG, "load of ${spec.type.simpleName} in ${System.currentTimeMillis() - loadStarted} ms")
            result.fold(
                onSuccess = { data ->
                    val empty = data.points.isEmpty() && data.total == null &&
                        data.records.isEmpty() && data.sessions.isEmpty()
                    // An empty window still has a best: VO2 max is measured now and then, and
                    // most single days of it would otherwise never show its record.
                    _emptyRecord.value = data.record.takeIf { empty }
                    _state.update { if (empty) UiState.Empty else UiState.Data(data) }
                    if (empty) _latestBefore.value = latestBefore(spec, windowStart(span, offset, shownEnd))
                    if (!empty && spec.type == RestingHeartRateRecord::class && marksTraining(spec, span)) {
                        val split = trainingNightsFor(span, offset, data.trainingDays)
                        ensureActive()
                        // Found or not, its held place is given up.
                        _state.update { current ->
                            (current as? UiState.Data)?.let {
                                UiState.Data(it.value.copy(trainingNights = split, trainingNightsPending = false))
                            } ?: current
                        }
                    }
                    if (!empty && _comparing.value) showPreviousYear(spec, span, offset)
                    if (!empty && data.heatmapPending) {
                        val heatmap = if (data.dayValues.isNotEmpty()) {
                            yearHeatmap(data.dayValues, span.startDate(offset, shownEnd), span.endDate(offset, shownEnd).minusDays(1), fromZero = countsFromZero(spec))
                        } else {
                            heatmapFor(spec, span, offset, selectedSource)
                        }
                        ensureActive()
                        _state.update { current ->
                            (current as? UiState.Data)?.let {
                                UiState.Data(it.value.copy(heatmap = heatmap, heatmapPending = false))
                            } ?: current
                        }
                    }
                },
                onFailure = { error ->
                    _state.update { UiState.Error(error.message ?: "Could not read data") }
                },
            )
        }
    }


    /**
     * Heart rate through one session's own window, read when its row comes on screen.
     *
     * Reading every session's curve up front made the year view of Trainings wait for all 728
     * of them before showing anything -- about 66 ms each on the phone, so near 48 s -- for rows
     * mostly never scrolled to. Now the list appears at once and each curve is read as its row
     * is shown, at most [MAX_CONCURRENT_READS] at a time, and kept for this screen only: memory,
     * like every other reading here, never disk. Null means no heart rate was recorded then,
     * which the row says in its own words.
     */
    suspend fun curveFor(session: Session): SessionCurve {
        curveCache[session]?.let { return it }
        // The breaks with the curve, so a workout's row shades them as its own screen does.
        val curve = curveGate.withPermit {
            SessionCurve(
                points = repository.heartRateDuring(session),
                // Already read with the list where it was; see TileChartLoader.
                breaks = if (session.moving != null) {
                    session.breaks
                } else {
                    repository.movementDuring(session)?.breaks.orEmpty()
                },
            )
        }
        curveCache[session] = curve
        return curve
    }

    /** Held whole, so a session with no curve is remembered as such, not read again. */
    private val curveCache = java.util.concurrent.ConcurrentHashMap<Session, SessionCurve>()
    private val curveGate = Semaphore(MAX_CONCURRENT_READS)

    /** The whole window's list, kept to restore when the chart is zoomed back out. */
    private var windowList: List<Record>? = null

    /** Personal records already read, by type, source and day; see [loadData]. */
    private val personalRecords = mutableMapOf<String, PersonalRecord?>()
    private var listJob: Job? = null

    /**
     * Lists the records of the stretch the chart shows while zoomed, or the whole window's
     * again for null. Debounced, since a pinch reports every frame: the read starts once the
     * fingers have settled, and a newer range cancels an older read.
     */
    fun showListFor(range: ClosedRange<Instant>?) {
        val current = (_state.value as? UiState.Data)?.value ?: return
        if (current.listRange == range) return
        val spec = current.spec
        val origins = selectedSource?.let { setOf(DataOrigin(it)) } ?: emptySet()
        listJob?.cancel()
        listJob = viewModelScope.launch {
            delay(LIST_DEBOUNCE_MS)
            val records = if (range == null) {
                windowList ?: return@launch
            } else {
                catching {
                    repository.recordsIn(spec, range.start, range.endInclusive, origins, HealthRepository.LIST_RECORDS)
                }.getOrElse { return@launch }
            }
            _state.update { state ->
                val data = (state as? UiState.Data)?.value ?: return@update state
                UiState.Data(
                    data.copy(
                        records = records,
                        truncated = records.size >= HealthRepository.LIST_RECORDS,
                        listRange = range,
                    ),
                )
            }
        }
    }

    private suspend fun loadData(
        spec: RecordTypeSpec<*>,
        span: Span,
        offset: Int,
        historyCapped: Boolean,
        source: String?,
        onChartReady: (TileDetailData) -> Unit = {},
    ): TileDetailData = coroutineScope {
        val metric = spec.aggregate
        val origins = source?.let { setOf(DataOrigin(it)) } ?: emptySet()
        val windowStart = windowStart(span, offset, shownEnd)
        val windowEnd = windowEnd(span, offset, shownEnd)

        val loader = TileChartLoader(repository, dashboardStore, today = shownEnd)
        val chart = loader
            .chart(spec, span, offset, historyCapped, source, deferExtras = true) { _progress.value = it }

        // The chart first, then the list and the picker.
        //
        // Health Connect serves one app's requests largely in turn, so reading these alongside
        // the chart only queued the chart behind them: a year of heart rate showed nothing for
        // ~13.5 s. Handing the chart over first puts it on screen in about half that, while the
        // picker keeps its place and the list says it is loading.
        onChartReady(chart)

        val recordsRead = async {
            catching {
                repository.recordsIn(spec, windowStart, windowEnd, origins, HealthRepository.LIST_RECORDS)
            }.getOrDefault(emptyList())
        }
        // Only to name the other writers when one is selected, so one page is enough: the
        // aggregate's origins below already name every writer that reaches a total, and this
        // catches the ones that do not. Reading the full 5000 again took 7 s for a year.
        val otherWritersRead = if (origins.isEmpty()) {
            null
        } else {
            async {
                catching {
                    repository.recordsIn(spec, windowStart, windowEnd, maxRecords = HealthRepository.PAGE_SIZE)
                        .map { spec.originOf(it) }
                        .toSet()
                }.getOrDefault(emptySet())
            }
        }
        // From the chart's buckets where it had them: one aggregate over a year of total
        // calories, only to name its apps, took 20 s on the phone.
        val aggregateOriginsRead = chart.aggregateOrigins?.let { known -> async { known } } ?: metric?.let { aggregate ->
            async {
                catching { repository.contributingApps(aggregate, span.localFilter(offset, shownEnd)) }
                    .getOrDefault(emptySet())
            }
        }

        // Its own read rather than the list's: a reading at 01:00 counts for the evening before,
        // so the window runs 04:00 to 04:00 and differs from the calendar one at both ends.
        val dayPartsRead = if (spec.type == BloodPressureRecord::class) {
            async {
                val (from, to) = dayPartWindow(
                    span.startDate(offset, shownEnd),
                    span.endDate(offset, shownEnd).minusDays(1),
                    HealthRepository.DEFAULT_ZONE,
                )
                catching {
                    val readings = repository.recordsIn(spec, from, to, origins)
                        .filterIsInstance<BloodPressureRecord>()
                        .map {
                            PressureReading(
                                it.time,
                                it.systolic.inMillimetersOfMercury,
                                it.diastolic.inMillimetersOfMercury,
                            )
                        }
                    splitByDayPart(readings, HealthRepository.DEFAULT_ZONE)
                }.getOrNull()
            }
        } else {
            null
        }

        // One request for every nutrient, a bucket per day: the deduplicated totals, and the
        // days each was recorded on for its mean. Never summed from the records, which overlap
        // where two apps log the same meal.
        val nutrientsRead = if (spec.type == NutritionRecord::class) {
            async {
                catching {
                    val first = Nutrient.entries.first()
                    repository.bucketedTotals(
                        first.metric,
                        span.localFilter(offset, shownEnd),
                        Period.ofDays(1),
                        origins,
                        also = Nutrient.metrics - first.metric,
                    ).map { bucket ->
                        Nutrient.entries.mapNotNull { nutrient ->
                            bucket.result[nutrient.metric]?.let { nutrient to nutrient.of(it) }
                        }.toMap()
                    }.let(::nutrientTotals)
                }.getOrDefault(emptyList())
            }
        } else {
            null
        }

        // Newest first, matching how the other list reads.
        val records = recordsRead.await()

        // Deliberately unfiltered: this drives the source picker, so it must list every app
        // that wrote into the window. Scoping it to the current selection would collapse the
        // picker to that one app and strand the user there with no way back. So the records
        // above are reused only when nothing is filtered, which is the common case.
        //
        // The writers are taken from the records and unioned with the aggregate's origins,
        // never from the origins alone. The two legitimately disagree: a writer whose records
        // do not reach the aggregate is absent from the origins while still plainly present
        // in the list below. The whole-day-summary case is exactly that -- a record as wide
        // as its bucket aggregates to nothing (see CLAUDE.md) -- and with one contributor
        // left the picker hid itself, so the user saw two writers listed and no way to choose
        // between them. Origins still contribute because some types aggregate without storing
        // records at all, where the records alone would name nobody.
        // A capped list names too few: the newest 500 readings of a respiratory rate are ten
        // hours, and a writer from the morning would drop out of the picker. Then one page
        // of the window names them, as the unfiltered list of 5,000 used to.
        val writers = otherWritersRead?.await()
            ?: if (records.size >= HealthRepository.LIST_RECORDS) {
                catching {
                    repository.recordsIn(spec, windowStart, windowEnd, maxRecords = HealthRepository.PAGE_SIZE)
                        .map { spec.originOf(it) }
                        .toSet()
                }.getOrDefault(emptySet()) + records.map { spec.originOf(it) }
            } else {
                records.map { spec.originOf(it) }.toSet()
            }
        val contributors = writers + (aggregateOriginsRead?.await() ?: emptySet())

        windowList = records
        val listed = chart.copy(
            records = records,
            truncated = records.size >= HealthRepository.LIST_RECORDS,
            contributingApps = contributors,
            dayParts = dayPartsRead?.await()?.takeIf { it.morning != null || it.evening != null },
            nutrients = nutrientsRead?.await().orEmpty(),
            listPending = false,
        )
        // The list is on screen before the extras: a streak reaching back a year took 12.5 s
        // on the phone, and holding the list behind it left the screen half empty for that long.
        onChartReady(listed)
        // Off the main thread: only the Health Connect calls themselves leave it, and building
        // and counting the sessions behind a 48-day activity streak then ran on it while the
        // screen was already in use -- dragging a route's marker turned visibly laggy.
        val extras = withContext(Dispatchers.Default) { loader.extras() }
        // Last, and once per type and source: it does not depend on the window on screen, and
        // reading a year again on every swipe would queue behind the next window's chart.
        // With the history permission in the key: allowing older data changes the answer.
        val recordKey = "${spec.type.simpleName}|$source|${LocalDate.now()}|${_historyGranted.value}"
        // containsKey, not getOrPut: "no record" is an answer too, and must not be read again.
        val record = if (personalRecords.containsKey(recordKey)) {
            personalRecords[recordKey]
        } else {
            // Only an answer is kept: a cancelled or failed read is tried again next time.
            try {
                withContext(Dispatchers.Default) { repository.personalRecord(spec, origins, LocalDate.now()) }
                    .also { personalRecords[recordKey] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        val training = if (marksTraining(spec, span)) {
            catching {
                // From the day before the window: its first morning's night follows that day.
                val sessions = repository.sessionsIn(windowStart.minus(Duration.ofDays(1)), windowEnd, setOf(Session.Kind.EXERCISE))
                trainingDays(sessions, HealthRepository.DEFAULT_ZONE)
            }.getOrDefault(emptySet())
        } else {
            emptySet()
        }
        listed.copy(
            baseline = extras.baseline,
            usualBand = extras.usualBand,
            trend = extras.trend,
            streak = extras.streak,
            record = record,
            trainingDays = training,
            extrasPending = false,
            trainingNightsPending = spec.type == RestingHeartRateRecord::class && marksTraining(spec, span),
            heatmapOffered = showsHeatmap(spec, span, listed.dayValues),
            heatmapPending = showsHeatmap(spec, span, listed.dayValues) &&
                AppEntitlements.current.pro.value.allows(Feature.YEAR_HEATMAP),
        )
    }

    /**
     * Each night's lowest heart rate in the window, split by the day before it, read last
     * because a year is 365 nights. The lowest of the fullest writer's readings through each
     * night, as the night's curve is drawn: a minimum aggregate per night took 27 s for 28
     * nights on the phone, about a second each. Null where there are too few nights, or no
     * access to sleep or heart rate -- the section then stays away.
     */
    /**
     * The window's days as a calendar: one daily aggregate per day, deduplicated as every
     * total is, read in quarters so a slow answer for one does not hold the rest. Null where
     * no day has a value or the read failed.
     */
    private suspend fun heatmapFor(spec: RecordTypeSpec<*>, span: Span, offset: Int, source: String?): YearHeatmap? =
        repository.readHeatmap(
            spec,
            span.startDate(offset, shownEnd),
            span.endDate(offset, shownEnd).minusDays(1),
            source?.let { setOf(DataOrigin(it)) } ?: emptySet(),
        )

    private suspend fun trainingNightsFor(span: Span, offset: Int, training: Set<LocalDate>): TrainingNights? {
        val zone = HealthRepository.DEFAULT_ZONE
        val started = System.currentTimeMillis()
        val nights = nightsByMorning(
            catching {
                repository.sessionsIn(windowStart(span, offset, shownEnd), windowEnd(span, offset, shownEnd), setOf(Session.Kind.SLEEP))
            }.getOrDefault(emptyList()),
            zone,
        )
        val gate = Semaphore(MAX_NIGHT_READS)
        val lows = coroutineScope {
            nights.map { (morning, night) ->
                async {
                    gate.withPermit {
                        try {
                            repository.heartRateDuring(night)?.minOf { it.value }?.let { morning to it }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        // Counts and timing only, never a reading.
        Log.d(TAG, "training nights: ${lows.size}/${nights.size} nights in ${System.currentTimeMillis() - started} ms")
        return splitByTraining(lows, training)
    }

    private companion object {
        /** Night aggregates in flight at once; Health Connect serves them largely in turn. */
        const val MAX_NIGHT_READS = 4
        const val TAG = "TileDetail"
        const val KEY_TYPE = "shownType"
        const val KEY_SPAN = "shownSpan"
        const val KEY_DATE = "shownDate"
        const val KEY_COMPARE = "comparing"

        /** How far back [latestBefore] looks: far enough for a reading taken once a year. */
        val LATEST_LOOKBACK: Duration = Duration.ofDays(3650)
        /** How long a zoom must settle before the list is re-read for it. */
        const val LIST_DEBOUNCE_MS = 400L
    }

}

/** Resting heart rate and HRV across days mark their training days; nothing else does. */
internal fun marksTraining(spec: RecordTypeSpec<*>, span: Span): Boolean =
    span != Span.DAY && (spec.type == RestingHeartRateRecord::class || spec.type == HeartRateVariabilityRmssdRecord::class)

/**
 * A type's year as a calendar: where a daily aggregate can be read, or the chart's own read
 * gave daily values -- nights, workouts, daily means. Not for the cycle types, which have no
 * figure to shade.
 */
/** A window that has a year earlier to set beside it: across days, not a single day's hours. */
internal fun comparesYear(span: Span): Boolean = span != Span.DAY

/** How many windows back a year is: whole weeks for the week spans, so weekdays line up. */
internal fun yearSteps(span: Span): Int = when (span) {
    Span.DAY -> WEEKS_PER_YEAR * DAYS_PER_WEEK
    Span.WEEK -> WEEKS_PER_YEAR
    Span.MONTH -> WEEKS_PER_YEAR / WEEKS_PER_MONTH
    Span.YEAR -> 1
}

private const val WEEKS_PER_YEAR = 52
private const val DAYS_PER_WEEK = 7
private const val WEEKS_PER_MONTH = 4

internal fun showsHeatmap(spec: RecordTypeSpec<*>, span: Span, dayValues: Map<LocalDate, Double>): Boolean =
    span == Span.YEAR && (dayValues.isNotEmpty() || spec.aggregate != null && spec.tile.form != TileSpec.Form.SESSIONS)
