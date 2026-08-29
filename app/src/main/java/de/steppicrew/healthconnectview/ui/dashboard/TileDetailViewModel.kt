package de.steppicrew.healthconnectview.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.records.Record
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.health.numericAggregate
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TileDetailData(
    val spec: RecordTypeSpec<*>,
    val points: List<Point>,
    val total: Double?,
    /** True when [points] came from Health Connect's deduplicating aggregation. */
    val aggregated: Boolean,
    /** True when a bucket is wider than a day, so the caption must not say "daily". */
    val weeklyBuckets: Boolean,
    /** True when the window reaches past 30 days without the history permission. */
    val historyCapped: Boolean,
    /** Apps that wrote into this window, so every number on screen names its source. */
    val contributingApps: Set<String>,
    val start: LocalDate,
    val end: LocalDate,
    /**
     * The raw records behind the chart. Inspecting exactly what each app stored is the point
     * of the app, and it is the only place a whole-day summary record can be told apart from
     * an itemised one.
     */
    val records: List<Record>,
    val truncated: Boolean,
)

/**
 * One type, over a span that can be stepped backwards and forwards.
 *
 * Separate from TypeDetailViewModel, which shows a fixed trailing range plus the raw record
 * list. This is the chart-first view reached from a dashboard tile, and it is the only place
 * that can reach data older than a year.
 */
class TileDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)

    private val _state = MutableStateFlow<UiState<TileDetailData>>(UiState.Loading)
    val state: StateFlow<UiState<TileDetailData>> = _state.asStateFlow()

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

    val canStepForward: Boolean get() = _offset.value > 0

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
                    _state.update {
                        if (data.points.isEmpty() && data.total == null && data.records.isEmpty()) {
                            UiState.Empty
                        } else {
                            UiState.Data(data)
                        }
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
    ): TileDetailData {
        val metric = spec.aggregate

        // Totals and bucketed series both come from aggregation wherever the type supports
        // it: several apps can write the same metric, so summing raw records double-counts.
        val points = if (metric != null) {
            repository.bucketedTotals(metric, span.localFilter(offset), span.bucket)
                .mapNotNull { bucket ->
                    val value = bucket.result[metric]?.let(::numericAggregate)
                        ?: return@mapNotNull null
                    Point(
                        time = bucket.startTime.atZone(HealthRepository.DEFAULT_ZONE).toInstant(),
                        value = value,
                    )
                }
        } else {
            // No aggregate metric: chart the readings themselves, via the path that spans the
            // whole window rather than stopping at the newest records.
            repository.readForChart(spec.type, span.instantFilter(offset))
                .flatMap { spec.pointsOf(it) }
                .sortedBy { it.time }
        }

        val total = if (metric != null) {
            runCatching { repository.total(metric, span.localFilter(offset)) }.getOrNull()
        } else {
            null
        }

        // Aggregation reports its origins directly; for the raw-record path the writers have
        // to be collected from the records themselves.
        val contributors = if (metric != null) {
            runCatching { repository.contributingApps(metric, span.localFilter(offset)) }
                .getOrDefault(emptySet())
        } else {
            runCatching {
                repository.read(spec.type, span.instantFilter(offset))
                    .map { spec.originOf(it) }
                    .toSet()
            }.getOrDefault(emptySet())
        }

        // Newest first, matching how the other list reads.
        val records = runCatching {
            repository.read(spec.type, span.instantFilter(offset))
        }.getOrDefault(emptyList())

        return TileDetailData(
            spec = spec,
            points = points,
            total = total,
            aggregated = metric != null,
            contributingApps = contributors,
            weeklyBuckets = span.bucket.days > 1,
            historyCapped = historyCapped,
            start = span.startDate(offset),
            end = span.endDate(offset).minusDays(1),
            records = records,
            truncated = records.size >= HealthRepository.MAX_RECORDS,
        )
    }
}
