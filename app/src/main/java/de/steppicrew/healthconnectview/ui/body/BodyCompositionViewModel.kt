package de.steppicrew.healthconnectview.ui.body

import android.app.Application
import androidx.annotation.StringRes
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.dashboard.SourceStore
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.Quantity
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.ui.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/** One part of the body's weight over the window, in the shown unit. */
data class BodyPart(
    @param:StringRes val labelRes: Int,
    @param:StringRes val unitRes: Int,
    /** One point per day, or per week in a year: the mean of that bucket's readings. */
    val points: List<Point>,
    /** Fat in kilograms is worked out, not measured; the screen says from what. */
    val derived: Boolean = false,
)

data class BodyComposition(
    val parts: List<BodyPart>,
    val extent: ClosedRange<Instant>,
    val weekly: Boolean,
)

/**
 * Weight and what it is made of -- fat, lean mass, water, bone -- over one window, so a change
 * in weight can be read against the parts that moved with it.
 *
 * Each part is read as readings and averaged per day: none of the four composition types has an
 * aggregate in Health Connect. Several writers would otherwise be averaged together, so each
 * type follows the source chosen for its own tile, or the preferred one where that app wrote
 * it, as the tiles do. Fat in kilograms is the day's fat percentage times the same day's
 * weight; a day missing either has none.
 */
class BodyCompositionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)
    private val sourceStore = SourceStore(application)

    private val _state = MutableStateFlow<UiState<BodyComposition>>(UiState.Loading)
    val state: StateFlow<UiState<BodyComposition>> = _state.asStateFlow()

    private val _span = MutableStateFlow(Span.MONTH)
    val span: StateFlow<Span> = _span.asStateFlow()

    private val _offset = MutableStateFlow(0)
    val offset: StateFlow<Int> = _offset.asStateFlow()

    private val _historyGranted = MutableStateFlow(true)
    val historyGranted: StateFlow<Boolean> = _historyGranted.asStateFlow()

    private var started = false
    private var loadJob: Job? = null

    fun start() {
        if (started) return
        started = true
        reload()
    }

    /** Back from the permission screen, the window is read again. */
    fun onResume() {
        if (started && _state.value !is UiState.Loading) reload()
    }

    fun setSpan(span: Span) {
        _span.value = span
        _offset.value = 0
        reload()
    }

    fun stepBack() {
        _offset.value += 1
        reload()
    }

    fun stepForward() {
        if (_offset.value == 0) return
        _offset.value -= 1
        reload()
    }

    fun stepToNow() {
        if (_offset.value == 0) return
        _offset.value = 0
        reload()
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                load(_span.value, _offset.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun load(span: Span, offset: Int): UiState<BodyComposition> {
        val granted = repository.grantedPermissions()
        _historyGranted.value = RecordRegistry.HISTORY_PERMISSION in granted
        val specs = TYPES.mapNotNull { RecordRegistry.specOrNull(it.simpleName.orEmpty()) }
            .filter { it.permission in granted }
        if (specs.none { it.type == WeightRecord::class }) return UiState.NoPermission

        val selections = runCatching { sourceStore.selections.first() }.getOrDefault(emptyMap())
        val preferred = runCatching { sourceStore.preferred.first() }.getOrNull()
        val first = span.startDate(offset)
        val weekly = span == Span.YEAR

        // In parallel: five paged reads one after another kept the screen waiting ten seconds.
        val daily = coroutineScope {
            specs.map { spec -> async { spec.type to dailyMeans(spec, span, offset, selections, preferred) } }
                .awaitAll()
                .toMap()
        }
        val weight = daily[WeightRecord::class].orEmpty()
        val fatPercent = daily[BodyFatRecord::class].orEmpty()
        val fatMass = fatPercent.mapNotNull { (day, percent) ->
            weight[day]?.let { day to percent * it / 100.0 }
        }.toMap()

        val massUnit = Quantity.MASS.unitRes()
        fun part(@StringRes label: Int, unit: Int, days: Map<LocalDate, Double>, derived: Boolean = false) =
            BodyPart(label, unit, buckets(days, first, weekly), derived).takeIf { it.points.isNotEmpty() }

        val parts = listOfNotNull(
            part(R.string.type_weight, massUnit, weight),
            part(R.string.body_fat_mass, massUnit, fatMass, derived = true),
            part(R.string.type_lean_body_mass, massUnit, daily[LeanBodyMassRecord::class].orEmpty()),
            part(R.string.type_body_water_mass, massUnit, daily[BodyWaterMassRecord::class].orEmpty()),
            part(R.string.type_bone_mass, massUnit, daily[BoneMassRecord::class].orEmpty()),
            part(R.string.type_body_fat, R.string.unit_percent, fatPercent),
        )
        if (parts.isEmpty()) return UiState.Empty
        val zone = HealthRepository.DEFAULT_ZONE
        val extent = first.atStartOfDay(zone).toInstant()..span.endDate(offset).atStartOfDay(zone).toInstant()
        return UiState.Data(BodyComposition(parts, extent, weekly))
    }

    /** The mean of each day's readings in the shown unit, from the source this type follows. */
    private suspend fun dailyMeans(
        spec: RecordTypeSpec<*>,
        span: Span,
        offset: Int,
        selections: Map<String, String>,
        preferred: String?,
    ): Map<LocalDate, Double> {
        val records: List<Record> = repository.readForChart(spec.type, span.instantFilter(offset))
        val typeName = spec.type.simpleName.orEmpty()
        val writers = records.map { spec.originOf(it) }.toSet()
        val source = sourceStore.effective(typeName, selections, preferred, writers)
        val zone = HealthRepository.DEFAULT_ZONE
        return records
            .filter { source == null || spec.originOf(it) == source }
            .flatMap { spec.pointsOf(it) }
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, points) -> points.map { it.value }.average() }
    }

    private companion object {
        val TYPES: List<KClass<out Record>> = listOf(
            WeightRecord::class,
            BodyFatRecord::class,
            LeanBodyMassRecord::class,
            BodyWaterMassRecord::class,
            BoneMassRecord::class,
        )
    }
}

/**
 * Daily means as points, or weekly means of them from [first] where [weekly]: a year of daily
 * dots is 365 of them, the rule for a reading taken now and then is a mean per week.
 * A week's mean is of its days, not its readings, so a day weighed three times counts once.
 */
internal fun buckets(days: Map<LocalDate, Double>, first: LocalDate, weekly: Boolean): List<Point> {
    val zone = HealthRepository.DEFAULT_ZONE
    if (!weekly) {
        return days.entries.sortedBy { it.key }.map { Point(it.key.atStartOfDay(zone).toInstant(), it.value) }
    }
    return days.entries
        .groupBy { (day, _) -> first.plusDays(Math.floorDiv(ChronoUnit.DAYS.between(first, day), 7L) * 7) }
        .map { (start, entries) -> Point(start.atStartOfDay(zone).toInstant(), entries.map { it.value }.average()) }
        .sortedBy { it.time }
}
