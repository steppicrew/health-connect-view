package de.steppicrew.healthconnectview.ui.detail

import android.app.Application
import android.util.Log
import androidx.health.connect.client.records.Record
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.TimeRange
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.Velocity
import androidx.health.connect.client.units.Volume
import de.steppicrew.healthconnectview.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration

data class TypeDetailData(
    val spec: RecordTypeSpec<*>,
    val records: List<Record>,
    /** Points to chart: aggregated daily buckets when available, raw readings otherwise. */
    val points: List<Point>,
    /** True when [points] came from Health Connect's deduplicating aggregation. */
    val pointsAreAggregated: Boolean,
    /** Apps that wrote into this range; more than one means totals differ from any single app. */
    val contributingApps: Set<String>,
    val truncated: Boolean,
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
            val result = runCatching { loadData(spec, range) }
            result.fold(
                onSuccess = { data ->
                    _state.update {
                        if (data.records.isEmpty()) UiState.Empty else UiState.Data(data)
                    }
                },
                onFailure = { error ->
                    _state.update { UiState.Error(error.message ?: "Could not read data") }
                },
            )
        }
    }

    private suspend fun loadData(spec: RecordTypeSpec<*>, range: TimeRange): TypeDetailData {
        val records = repository.read(spec.type, range.filter())

        // Totals must never be computed by summing raw records: several apps can write the
        // same metric, so their records overlap and adding them double-counts. Health
        // Connect's aggregation applies data-origin priority and deduplicates. Raw points are
        // charted only for types with no aggregate metric, where each record is a discrete
        // reading rather than an accumulating quantity.
        val metric = spec.aggregate
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
                records.flatMap { spec.pointsOf(it) }.sortedBy { it.time }
            },
            pointsAreAggregated = aggregated.isNotEmpty(),
            contributingApps = contributors,
            truncated = records.size >= HealthRepository.MAX_RECORDS,
        )
    }

    private suspend fun aggregatePoints(spec: RecordTypeSpec<*>, range: TimeRange): List<Point> {
        val metric = spec.aggregate ?: return emptyList()
        return repository.dailyTotals(metric, range.localFilter())
            .mapNotNull { bucket ->
                val value = bucket.result[metric]?.let(::numericValue) ?: return@mapNotNull null
                Point(
                    time = bucket.startTime.atZone(HealthRepository.DEFAULT_ZONE).toInstant(),
                    value = value,
                )
            }
    }

    private companion object {
        const val TAG = "TypeDetail"
    }

    /**
     * Aggregates come back as a plain number, a Duration, or one of the library's unit types.
     * Each unit is converted to the same scale the corresponding spec displays, so the chart
     * axis matches the raw rows beneath it.
     */
    private fun numericValue(value: Any): Double? = when (value) {
        is Long -> value.toDouble()
        is Double -> value
        is Duration -> value.toMinutes() / 60.0
        is Mass -> value.inKilograms
        is Length -> value.inKilometers
        is Energy -> value.inKilocalories
        is Volume -> value.inLiters
        is Power -> value.inWatts
        is Velocity -> value.inKilometersPerHour
        is Percentage -> value.value
        is Pressure -> value.inMillimetersOfMercury
        is Temperature -> value.inCelsius
        else -> null
    }
}
