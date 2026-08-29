package de.steppicrew.healthconnectview.ui.detail

import android.app.Application
import android.util.Log
import androidx.health.connect.client.records.Record
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.numericAggregate
import de.steppicrew.healthconnectview.health.TimeRange
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TypeDetailData(
    val spec: RecordTypeSpec<*>,
    val records: List<Record>,
    /** Points to chart: aggregated daily buckets when available, raw readings otherwise. */
    val points: List<Point>,
    /** True when [points] came from Health Connect's deduplicating aggregation. */
    val pointsAreAggregated: Boolean,
    /**
     * True when the charted series was thinned to keep it spanning the whole range. The
     * chart's extent is right; its resolution is reduced.
     */
    val pointsAreSampled: Boolean,
    /** Apps that wrote into this range; more than one means totals differ from any single app. */
    val contributingApps: Set<String>,
    val truncated: Boolean,
    /**
     * True when the range asks for more than 30 days but READ_HEALTH_DATA_HISTORY is not
     * granted. Health Connect silently returns only the last 30 days in that case, which is
     * indistinguishable from simply having no older data -- so the UI has to say so.
     */
    val historyCapped: Boolean,
)

class TypeDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)

    private val _state = MutableStateFlow<UiState<TypeDetailData>>(UiState.Loading)
    val state: StateFlow<UiState<TypeDetailData>> = _state.asStateFlow()

    private val _range = MutableStateFlow(TimeRange.WEEK)
    val range: StateFlow<TimeRange> = _range.asStateFlow()

    private var typeName: String? = null

    fun load(typeName: String) {
        this.typeName = typeName
        reload()
    }

    fun setRange(range: TimeRange) {
        _range.update { range }
        reload()
    }

    private fun reload() {
        val spec = RecordRegistry.specOrNull(typeName ?: return) ?: run {
            _state.update { UiState.Error("Unknown type") }
            return
        }

        viewModelScope.launch {
            _state.update { UiState.Loading }

            val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
            if (spec.permission !in granted) {
                _state.update { UiState.NoPermission }
                return@launch
            }

            val range = _range.value
            val capped = range.needsHistoryPermission &&
                RecordRegistry.HISTORY_PERMISSION !in granted
            val result = runCatching { loadData(spec, range, capped) }
            result.fold(
                onSuccess = { data ->
                    // Some types have no stored records but still aggregate to a value:
                    // Health Connect derives basal metabolic rate from height and weight, for
                    // instance. Treating "no raw records" as empty would hide a real chart.
                    val hasSomething = data.records.isNotEmpty() || data.points.isNotEmpty()
                    _state.update {
                        if (hasSomething) UiState.Data(data) else UiState.Empty
                    }
                },
                onFailure = { error ->
                    _state.update { UiState.Error(error.message ?: "Could not read data") }
                },
            )
        }
    }

    private suspend fun loadData(
        spec: RecordTypeSpec<*>,
        range: TimeRange,
        historyCapped: Boolean,
    ): TypeDetailData {
        val records = repository.read(spec.type, range.filter())

        // Totals must never be computed by summing raw records: several apps can write the
        // same metric, so their records overlap and adding them double-counts. Health
        // Connect's aggregation applies data-origin priority and deduplicates. Raw points are
        // charted only for types with no aggregate metric, where each record is a discrete
        // reading rather than an accumulating quantity.
        val metric = spec.aggregate

        // The record list shows the newest records and stops at MAX_RECORDS. A chart must not
        // be built from that same slice: on a high-frequency type the cap is hit within days,
        // so the chart would cover the last few days of the range and read as missing history.
        // Types with an aggregate metric chart from daily buckets and never need this.
        val chartRecords = if (metric == null) {
            runCatching { repository.readForChart(spec.type, range.filter()) }
                .onFailure { Log.w(TAG, "chart read failed for ${spec.type.simpleName}", it) }
                .getOrDefault(records)
        } else {
            emptyList()
        }
        val aggregated = if (metric != null) {
            runCatching { aggregatePoints(spec, range) }
                .onFailure { Log.w(TAG, "aggregation failed for ${spec.type.simpleName}", it) }
                .onSuccess { pts ->
                    Log.i(TAG, "AGG ${spec.type.simpleName}: ${pts.size} aggregated points")
                }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val contributors = if (metric != null) {
            runCatching { repository.contributingApps(metric, range.localFilter()) }
                .getOrDefault(emptySet())
        } else {
            emptySet()
        }

        return TypeDetailData(
            spec = spec,
            records = records,
            // Records arrive newest-first for the list; a chart has to read left to right.
            points = aggregated.ifEmpty {
                chartRecords.flatMap { spec.pointsOf(it) }.sortedBy { it.time }
            },
            pointsAreAggregated = aggregated.isNotEmpty(),
            pointsAreSampled = aggregated.isEmpty() &&
                chartRecords.size >= HealthRepository.CHART_POINTS,
            contributingApps = contributors,
            truncated = records.size >= HealthRepository.MAX_RECORDS,
            historyCapped = historyCapped,
        )
    }

    private suspend fun aggregatePoints(spec: RecordTypeSpec<*>, range: TimeRange): List<Point> {
        val metric = spec.aggregate ?: return emptyList()
        return repository.dailyTotals(metric, range.localFilter())
            .mapNotNull { bucket ->
                val value = bucket.result[metric]?.let(::numericAggregate) ?: return@mapNotNull null
                Point(
                    time = bucket.startTime.atZone(HealthRepository.DEFAULT_ZONE).toInstant(),
                    value = value,
                )
            }
    }

    private companion object {
        const val TAG = "TypeDetail"
    }

}
