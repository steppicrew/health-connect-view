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
    val route: RouteLoad,
    /** Speed through the session as the device recorded it, m/s, or null where none was. */
    val speed: List<Point>?,
    /** Heart rate through the session, or null where none was recorded or it is not allowed. */
    val heartRate: List<Point>?,
    /** True when heart rate is not granted, so a missing curve is a permission, not a gap. */
    val heartRateLocked: Boolean,
    /** The user's heart-rate bands, so a reading is the same colour here as on the tile. */
    val heartRateZones: ValueZones?,
    @param:StringRes val heartRateUnitRes: Int?,
)

/**
 * One session -- a workout, a night, a meditation -- by the id of its record, so every screen
 * that lists sessions opens the same one, and the debug route can open any of them.
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)
    private val dashboardStore = DashboardStore(application)

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
        }
    }

    private suspend fun read(kind: Session.Kind, id: String): UiState<SessionDetail> = coroutineScope {
        val session = repository.sessionById(kind, id) ?: return@coroutineScope UiState.Empty
        val spec = heartRateSpec()
        val granted = repository.grantedPermissions()
        val heartRateLocked = spec == null || spec.permission !in granted

        // Side by side: three independent questions about one window.
        val stats = async { repository.statisticsFor(session) }
        val route = async { repository.routeFor(session) }
        val speed = async { if (session.route == null) null else repository.speedDuring(session) }
        val heartRate = async { if (heartRateLocked) null else repository.heartRateDuring(session) }
        val zones = runCatching { dashboardStore.config.first() }.getOrNull()
            ?.tiles?.firstOrNull { it.typeName == spec?.type?.simpleName }?.effectiveZones
            ?: spec?.tile?.defaultZones

        val detail = SessionDetail(
            session = session,
            stats = stats.await(),
            route = route.await(),
            speed = speed.await(),
            heartRate = heartRate.await(),
            heartRateLocked = heartRateLocked,
            heartRateZones = zones,
            heartRateUnitRes = spec?.displayUnitRes,
        )
        currentCoroutineContext().ensureActive()
        UiState.Data(detail)
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
