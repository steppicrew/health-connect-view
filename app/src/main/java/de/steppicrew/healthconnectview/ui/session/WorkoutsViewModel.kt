package de.steppicrew.healthconnectview.ui.session

import android.app.Application
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.billing.Feature
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.WorkoutPlan
import de.steppicrew.healthconnectview.health.WorkoutTrendPoint
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.health.familyOf
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.Duration
import java.time.Instant
import de.steppicrew.healthconnectview.util.catching

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
        trend(family)
    }

    private val _upcoming = MutableStateFlow<List<WorkoutPlan>>(emptyList())

    /** Planned workouts of the coming week, read after the list; empty where there are none. */
    val upcoming: StateFlow<List<WorkoutPlan>> = _upcoming.asStateFlow()

    private val _trend = MutableStateFlow<WorkoutTrend?>(null)

    /** The chosen kind's workouts with their figures, while one is chosen; see [WorkoutTrend]. */
    val trend: StateFlow<WorkoutTrend?> = _trend.asStateFlow()

    /** Read once per kind and kept: switching back to a kind should not read it again. */
    private val trends = mutableMapOf<WorkoutFamily, List<WorkoutTrendPoint>>()
    private var trendJob: Job? = null

    /**
     * Reads [family]'s workouts' distance and heart rate, one aggregate request per workout,
     * a few at a time: a year of rides is a hundred requests, a few seconds. Pro only, and
     * only once asked for by choosing a kind.
     */
    private fun trend(family: WorkoutFamily?) {
        trendJob?.cancel()
        val sessions = (_state.value as? UiState.Data)?.value
        if (family == null || sessions == null) {
            _trend.value = null
            return
        }
        val chosen = sessions.filter { familyOf(it.exerciseType) == family }.sortedBy { it.start }
        // Without Pro nothing is read; the section offers the purchase in its place.
        if (!AppEntitlements.current.pro.value.allows(Feature.WORKOUT_TRENDS)) {
            _trend.value = WorkoutTrend(family, chosen, points = null)
            return
        }
        trends[family]?.let { _trend.value = WorkoutTrend(family, chosen, it); return }
        _trend.value = WorkoutTrend(family, chosen, points = null)
        trendJob = viewModelScope.launch {
            val gate = Semaphore(TREND_READS)
            val points = coroutineScope {
                chosen.map { session ->
                    async {
                        gate.withPermit {
                            val totals = catching {
                                repository.totals(
                                    setOf(DistanceRecord.DISTANCE_TOTAL, HeartRateRecord.BPM_AVG),
                                    TimeRangeFilter.between(session.start, session.end),
                                )
                            }.getOrDefault(emptyMap())
                            WorkoutTrendPoint(
                                start = session.start,
                                exerciseType = session.exerciseType,
                                duration = session.duration,
                                metres = totals[DistanceRecord.DISTANCE_TOTAL]?.times(METRES_PER_KM),
                                heartRate = totals[HeartRateRecord.BPM_AVG],
                            )
                        }
                    }
                }.awaitAll()
            }
            currentCoroutineContext().ensureActive()
            trends[family] = points
            if (_family.value == family) _trend.value = WorkoutTrend(family, chosen, points)
        }
    }

    /** Back from the permission screen with older data allowed, the year is read again. */
    fun onResume() {
        if (!started || _state.value is UiState.Loading) return
        viewModelScope.launch {
            val granted = catching { repository.grantedPermissions() }.getOrNull() ?: return@launch
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
                    trends.clear()
                    if (sessions.isEmpty()) UiState.Empty else UiState.Data(sessions)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.message ?: e.javaClass.simpleName)
            }
            // A kind the route opened on has its course read as soon as there is a list.
            trend(_family.value)
            // From the start of today: a plan for this evening is still to come at noon.
            _upcoming.value = repository.upcomingPlans(
                LocalDate.now(HealthRepository.DEFAULT_ZONE).atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant(),
            )
        }
    }

    private companion object {
        const val EXERCISE = "ExerciseSessionRecord"

        /** Requests at once; Health Connect serves an app largely in turn. */
        const val TREND_READS = 4

        /** Distance aggregates in km here (`numericAggregate`), the points keep metres. */
        const val METRES_PER_KM = 1000.0
    }
}

/** A kind's workouts over the year, oldest first, and their figures once read (null until then). */
data class WorkoutTrend(
    val family: WorkoutFamily,
    val sessions: List<Session>,
    val points: List<WorkoutTrendPoint>?,
)
