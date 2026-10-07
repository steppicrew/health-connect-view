package de.steppicrew.healthconnectview.ui.insights

import android.app.Application
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.dashboard.SourceStore
import de.steppicrew.healthconnectview.dashboard.openingSource
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.TREND_DAYS
import de.steppicrew.healthconnectview.health.Trend
import de.steppicrew.healthconnectview.health.TrendResult
import de.steppicrew.healthconnectview.health.trendBefore
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.ui.session.MAX_CONCURRENT_READS
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

/** One type's last week against its month, from the same numbers as its trend arrow. */
data class Insight(val spec: RecordTypeSpec<*>, val trend: TrendResult)

data class InsightsState(
    /** Types still being read, out of [total]; the list fills in as they arrive. */
    val pending: Int = 0,
    val total: Int = 0,
    /** Granted access to no type with a daily figure at all. */
    val noAccess: Boolean = false,
    val insights: List<Insight> = emptyList(),
) {
    val loading: Boolean get() = pending > 0

    /** Above or below, the most unusual first. */
    val notable: List<Insight>
        get() = insights.filter { it.trend.direction != Trend.FLAT }.sortedByDescending { it.trend.weight }

    val level: List<Insight>
        get() = insights.filter { it.trend.direction == Trend.FLAT }
}

/**
 * Every type's trend at once: which moved this week, ranked by how unusual the move is for
 * that type, and which held level.
 *
 * Built on the trend arrows' own computation, so a change named here is the one the type's
 * screen explains. Worded as a position -- "above the 30-day average" -- never as a verdict:
 * a rising resting heart rate and rising steps point the same way and mean opposite things,
 * and whether either is good depends on the person.
 *
 * Only types with a daily aggregate: a sum or mean Health Connect deduplicates. Sessions are
 * left out, as on the arrows -- the platform's sleep total cuts nights at midnight -- and so is
 * basal metabolic rate, derived from weight and height and so only ever an echo of them.
 */
class InsightsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)
    private val sourceStore = SourceStore(application)

    private val _state = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        reload()
    }

    /** Back from the permission screen, or after a day turned over, everything is read again. */
    fun onResume() {
        if (started && !_state.value.loading) reload()
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
            val specs = RecordRegistry.all.filter { spec ->
                spec.aggregate != null &&
                    spec.tile.form != TileSpec.Form.SESSIONS &&
                    spec.type != BasalMetabolicRateRecord::class &&
                    spec.permission in granted
            }
            _state.value = InsightsState(pending = specs.size, total = specs.size, noAccess = specs.isEmpty())
            val today = LocalDate.now()
            val zone = HealthRepository.DEFAULT_ZONE
            val start = today.minusDays(TREND_DAYS.toLong()).atStartOfDay(zone).toInstant()
            val end = today.atStartOfDay(zone).toInstant()
            val gate = Semaphore(MAX_CONCURRENT_READS)
            specs.map { spec ->
                async {
                    val trend = gate.withPermit {
                        runCatching {
                            val source = sourceStore.openingSource(repository, spec, start, end)
                            val origins = source?.let { setOf(DataOrigin(it)) } ?: emptySet()
                            repository.trendBefore(requireNotNull(spec.aggregate), today, origins)
                        }.getOrNull()
                    }
                    _state.update { current ->
                        current.copy(
                            pending = current.pending - 1,
                            insights = if (trend != null) current.insights + Insight(spec, trend) else current.insights,
                        )
                    }
                }
            }.awaitAll()
        }
    }
}
