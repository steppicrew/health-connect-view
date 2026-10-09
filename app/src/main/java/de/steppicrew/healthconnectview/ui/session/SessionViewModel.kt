package de.steppicrew.healthconnectview.ui.session

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.StringRes
import androidx.health.connect.client.records.ExerciseRoute
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.dashboard.DashboardStore
import de.steppicrew.healthconnectview.export.ExportResult
import de.steppicrew.healthconnectview.export.Gpx
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Movement
import de.steppicrew.healthconnectview.health.HeartZones
import de.steppicrew.healthconnectview.health.Recovery
import de.steppicrew.healthconnectview.health.recoveryOf
import de.steppicrew.healthconnectview.health.coversDistance
import de.steppicrew.healthconnectview.health.heartZones
import de.steppicrew.healthconnectview.health.observedMaxHeartRate
import de.steppicrew.healthconnectview.settings.SettingsStore
import de.steppicrew.healthconnectview.health.movementDuring
import de.steppicrew.healthconnectview.ui.components.SessionLine
import de.steppicrew.healthconnectview.health.RoutePoint
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.sessionById
import de.steppicrew.healthconnectview.health.toPoints
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.dashboard.heartRateSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the session screen shows, read together so it appears at once. */
data class SessionDetail(
    val session: Session,
    val stats: List<SessionStat>,
    /**
     * A workout's moving time and breaks, inferred from missing movement readings; null where
     * there were none to read.
     */
    val movement: Movement?,
    val route: RouteLoad,
    /** Speed through the session as the device recorded it, m/s, or null where none was. */
    val speed: List<Point>?,
    /** Heart rate through the session, or null where none was recorded or it is not allowed. */
    val heartRate: List<Point>?,
    /**
     * The session's other readings, by type name, each where granted and recorded: a night's
     * breath rate, oxygen and HRV; a workout's breath rate, which the phone held for every
     * workout, about once a minute (`SessionCurveShapeActivity`, 09.10.2026).
     */
    val nightLines: Map<String, List<Point>> = emptyMap(),
    /** True when heart rate is not granted, so a missing curve is a permission, not a gap. */
    val heartRateLocked: Boolean,
    /** The user's heart-rate bands, so a reading is the same colour here as on the tile. */
    val heartRateZones: ValueZones?,
    @param:StringRes val heartRateUnitRes: Int?,
    /** A workout's time in heart-rate zones and its load; null without heart rate or a maximum. */
    val heartZones: HeartZones? = null,
    /** A workout's heart rate in the minutes after it, drawn on past its end; null for a night. */
    val heartRateAfter: List<Point>? = null,
    val recovery: Recovery? = null,
)

/**
 * One session -- a workout, a night, a meditation -- by the id of its record, so every screen
 * that lists sessions opens the same one, and the debug route can open any of them.
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)
    private val dashboardStore = DashboardStore(application)
    private val settingsStore = SettingsStore(application)

    private val _state = MutableStateFlow<UiState<SessionDetail>>(UiState.Loading)
    val state: StateFlow<UiState<SessionDetail>> = _state.asStateFlow()

    private val _exportResults = MutableSharedFlow<ExportResult>(extraBufferCapacity = 1)
    val exportResults: SharedFlow<ExportResult> = _exportResults.asSharedFlow()

    private var loaded: Pair<Session.Kind, String>? = null
    private var loadJob: Job? = null

    fun load(kind: Session.Kind, id: String) {
        if (loaded == kind to id) return
        loaded = kind to id
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                read(kind, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A retry on the next load, not a remembered failure.
                loaded = null
                UiState.Error(e.message ?: e.javaClass.simpleName)
            }
            val zones = observedZones((_state.value as? UiState.Data)?.value) ?: return@launch
            // Onto whatever is on screen by now, which a granted route may have changed.
            _state.update { state ->
                (state as? UiState.Data)?.let { UiState.Data(it.value.copy(heartZones = zones)) } ?: state
            }
        }
    }

    private val _refreshing = MutableStateFlow(false)

    /** True while a reload the user asked for by pulling the page down is running. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Reads the open session again: the user pulled the page down. */
    fun pullRefresh() {
        val (kind, id) = loaded ?: return
        loaded = null
        _refreshing.value = true
        load(kind, id)
        val job = loadJob
        viewModelScope.launch {
            job?.join()
            _refreshing.value = false
        }
    }

    private suspend fun read(kind: Session.Kind, id: String): UiState<SessionDetail> = coroutineScope {
        val session = repository.sessionById(kind, id) ?: return@coroutineScope UiState.Empty
        val spec = heartRateSpec()
        val granted = repository.grantedPermissions()
        val heartRateLocked = spec == null || spec.permission !in granted

        // Side by side: independent questions about one window. The statistics wait for the
        // breaks, since they leave them out, and for the heart rate, whose readings give its
        // figures.
        val movement = async { repository.movementDuring(session) }
        val route = async { repository.routeFor(session) }
        // Wherever a workout goes somewhere, route or none: the watch's own copy of a ride
        // carries no route but does carry its speed.
        val speed = async { if (coversDistance(session.exerciseType)) repository.speedDuring(session) else null }
        val heartRate = async { if (heartRateLocked) null else repository.heartRateDuring(session) }
        val after = async {
            if (heartRateLocked || session.kind != Session.Kind.EXERCISE) null else repository.heartRateAfter(session)
        }
        val stats = async { repository.statisticsFor(session, movement.await()?.breaks.orEmpty(), heartRate.await()) }
        val nightLines = async {
            when (session.kind) {
                Session.Kind.SLEEP -> SessionLine.entries.filter { it != SessionLine.HEART_RATE }
                Session.Kind.EXERCISE -> listOf(SessionLine.BREATH)
                else -> emptyList()
            }
                .mapNotNull { line -> repository.readingsDuring(session, line.typeName)?.let { line.typeName to it } }
                .toMap()
        }
        val zones = runCatching { dashboardStore.config.first() }.getOrNull()
            ?.tiles?.firstOrNull { it.typeName == spec?.type?.simpleName }?.effectiveZones
            ?: spec?.tile?.defaultZones

        // A workout's zones, where the maximum is set in settings; the one the data shows
        // takes seconds to read and follows in [observedZones].
        val heartPoints = heartRate.await()
        val heartZones = if (session.kind == Session.Kind.EXERCISE && heartPoints != null) {
            runCatching { settingsStore.settings.first().maxHeartRate }.getOrNull()?.let { chosen ->
                heartZones(heartPoints, movement.await()?.breaks.orEmpty(), chosen, maxFromSettings = true)
            }
        } else {
            null
        }

        val detail = SessionDetail(
            session = session,
            stats = stats.await(),
            movement = movement.await(),
            route = route.await(),
            speed = speed.await(),
            heartRate = heartRate.await(),
            nightLines = nightLines.await(),
            heartRateLocked = heartRateLocked,
            heartRateZones = zones,
            heartRateUnitRes = spec?.displayUnitRes,
            heartZones = heartZones,
            heartRateAfter = after.await(),
            recovery = after.await()?.let { recoveryOf(it, session.end) },
        )
        currentCoroutineContext().ensureActive()
        UiState.Data(detail)
    }

    /**
     * A workout's zones by the maximum its data shows, added to a page already on screen: a
     * year of heart rate takes Health Connect seconds to search, and everything else on the
     * page is ready long before.
     */
    private suspend fun observedZones(detail: SessionDetail?): HeartZones? {
        val points = detail?.heartRate ?: return null
        if (detail.session.kind != Session.Kind.EXERCISE || detail.heartZones != null) return null
        return heartZones(
            points = points,
            breaks = detail.movement?.breaks.orEmpty(),
            max = repository.observedMaxHeartRate(),
            maxFromSettings = false,
        )
    }

    /** The system's consent dialog handed this one route back: show it without a second read. */
    fun routeGranted(route: ExerciseRoute) {
        _state.update { state ->
            val data = (state as? UiState.Data)?.value ?: return@update state
            UiState.Data(data.copy(route = RouteLoad.Shown(route.toPoints())))
        }
    }

    /**
     * Writes the route on screen to [uri] as GPX. The points are the ones already shown, so
     * nothing is read again; like every export, a failure removes the file.
     */
    fun exportRoute(points: List<RoutePoint>, name: String, uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requireNotNull(resolver.openOutputStream(uri)) { "cannot open $uri" }.use { out ->
                        Gpx.write(points, name, out.bufferedWriter(Charsets.UTF_8))
                    }
                }
                ExportResult.Written(points.size, uri, GPX_MIME)
            }
            result.onFailure {
                Log.w(TAG, "route export failed: ${it.javaClass.simpleName}")
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            }
            _exportResults.tryEmit(result.getOrDefault(ExportResult.Failed))
        }
    }

    private companion object {
        const val TAG = "Session"
    }
}
