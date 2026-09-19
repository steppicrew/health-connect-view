package de.steppicrew.healthconnectview.ui.detail

import android.app.Application
import android.util.Log
import androidx.health.connect.client.records.Record
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.numericAggregate
import de.steppicrew.healthconnectview.health.Span
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

    private val _span = MutableStateFlow(Span.WEEK)
    val span: StateFlow<Span> = _span.asStateFlow()

    /** Steps back from the present; 0 is the current window. Never negative. */
    private val _offset = MutableStateFlow(0)
    val offset: StateFlow<Int> = _offset.asStateFlow()

    private var typeName: String? = null

    fun load(typeName: String) {
        this.typeName = typeName
        reload()
    }

    /** Changing span resets the offset: "three weeks ago" has no meaning as "three years ago". */
    fun setSpan(span: Span) {
        _span.update { span }
        _offset.update { 0 }
        reload()
    }

    fun stepBack() {
        _offset.update { it + 1 }
        reload()
    }

    /** Stepping forward past the current window would show an empty future. */
    fun stepForward() {
        if (_offset.value == 0) return
        _offset.update { (it - 1).coerceAtLeast(0) }
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

            val span = _span.value
            val offset = _offset.value
            val capped = span.needsHistoryPermission(offset) &&
                RecordRegistry.HISTORY_PERMISSION !in granted
            val result = runCatching { loadData(spec, span, offset, capped) }
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
        span: Span,
        offset: Int,
        historyCapped: Boolean,
    ): TypeDetailData {
        val records = repository.read(spec.type, span.instantFilter(offset))

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
            runCatching { repository.readForChart(spec.type, span.instantFilter(offset)) }
                .onFailure { Log.w(TAG, "chart read failed for ${spec.type.simpleName}", it) }
                .getOrDefault(records)
        } else {
            emptyList()
        }
        val aggregated = if (metric != null) {
            runCatching { aggregatePoints(spec, span, offset) }
                .onFailure { Log.w(TAG, "aggregation failed for ${spec.type.simpleName}", it) }
                .onSuccess { pts ->
                    Log.i(TAG, "AGG ${spec.type.simpleName}: ${pts.size} aggregated points")
                }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val contributors = if (metric != null) {
            runCatching { repository.contributingApps(metric, span.localFilter(offset)) }
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

    /**
     * Aggregated buckets for the window, sliced by the span's own bucket.
     *
     * The slicer comes from [Span.bucket] rather than being fixed at a day: a year window
     * buckets by week, and asking for 365 daily buckets there would chart a year of noise.
     * [Span.DAY] has no Period-expressible bucket, so it is not offered by this screen.
     */
    private suspend fun aggregatePoints(spec: RecordTypeSpec<*>, span: Span, offset: Int): List<Point> {
        val metric = spec.aggregate ?: return emptyList()
        val bucket = span.bucket ?: return emptyList()
        return repository.bucketedTotals(metric, span.localFilter(offset), bucket)
            .mapNotNull { bucket ->
                val value = bucket.result[metric]?.let(::numericAggregate) ?: return@mapNotNull null
                Point(
                    time = bucket.startTime.atZone(HealthRepository.DEFAULT_ZONE).toInstant(),
                    value = value,
                )
            }
    }

    companion object {
        private const val TAG = "TypeDetail"

        /**
         * The spans this screen offers.
         *
         * [Span.DAY] is excluded: this screen charts from Period-sliced buckets, and a single
         * day sliced by a day-wide bucket is one point rather than a chart. The tile's
         * full-screen view offers the day because it has the intraday, Duration-sliced path.
         */
        val SPANS: List<Span> = listOf(Span.WEEK, Span.MONTH, Span.YEAR)
    }

}
