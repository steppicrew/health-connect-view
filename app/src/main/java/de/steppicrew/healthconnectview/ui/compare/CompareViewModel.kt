package de.steppicrew.healthconnectview.ui.compare

import android.app.Application
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.dashboard.DashboardStore
import de.steppicrew.healthconnectview.dashboard.SourceStore
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.health.recordsIn
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.dashboard.TileChartLoader
import de.steppicrew.healthconnectview.ui.dashboard.TileDetailData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Two types' charts over one window, and the time axis both are drawn on. */
data class Compared(
    val first: TileDetailData,
    val second: TileDetailData,
    val extent: ClosedRange<Instant>,
)

/**
 * Two types over the same window, for a comparison by eye: two charts on one time axis, never
 * two scales on one chart. Each is built by the same loader as its own screen, so a chart here
 * is the one the user already knows -- bars stay bars, a daily mean keeps its spread -- and
 * follows the same source choice.
 */
class CompareViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)
    private val dashboardStore = DashboardStore(application)
    private val sourceStore = SourceStore(application)

    private val _state = MutableStateFlow<UiState<Compared>>(UiState.Loading)
    val state: StateFlow<UiState<Compared>> = _state.asStateFlow()

    private val _types = MutableStateFlow<Pair<String, String>?>(null)
    val types: StateFlow<Pair<String, String>?> = _types.asStateFlow()

    private val _span = MutableStateFlow(Span.MONTH)
    val span: StateFlow<Span> = _span.asStateFlow()

    private val _offset = MutableStateFlow(0)
    val offset: StateFlow<Int> = _offset.asStateFlow()

    private val _historyGranted = MutableStateFlow(true)
    val historyGranted: StateFlow<Boolean> = _historyGranted.asStateFlow()

    private var loadJob: Job? = null

    /** Opens on the window the first type's screen was showing. Once; a rotation keeps the rest. */
    fun start(first: String, second: String, span: String, date: String) {
        if (_types.value != null) return
        _types.value = first to second
        Span.entries.firstOrNull { it.name.equals(span, ignoreCase = true) }?.let { _span.value = it }
        runCatching { LocalDate.parse(date) }.getOrNull()?.let { _offset.value = _span.value.offsetOf(it) }
        reload()
    }

    fun replaceSecond(type: String) {
        val current = _types.value ?: return
        if (type == current.first) return
        _types.value = current.first to type
        reload()
    }

    /** The other way up: which one is on top is all it changes. */
    fun swap() {
        val current = _types.value ?: return
        _types.value = current.second to current.first
        (_state.value as? UiState.Data)?.value?.let { _state.value = UiState.Data(it.copy(first = it.second, second = it.first)) }
    }

    fun onResume() {
        if (_types.value != null && _state.value !is UiState.Loading) reload()
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

    suspend fun candidates(): List<RecordTypeSpec<*>> = repository.comparableTypes()

    private fun reload() {
        val (firstName, secondName) = _types.value ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                load(firstName, secondName, _span.value, _offset.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun load(firstName: String, secondName: String, span: Span, offset: Int): UiState<Compared> {
        val first = RecordRegistry.specOrNull(firstName) ?: return UiState.Error("Unknown type")
        val second = RecordRegistry.specOrNull(secondName) ?: return UiState.Error("Unknown type")
        val granted = repository.grantedPermissions()
        if (first.permission !in granted || second.permission !in granted) return UiState.NoPermission
        _historyGranted.value = RecordRegistry.HISTORY_PERMISSION in granted
        val capped = span.needsHistoryPermission(offset) && RecordRegistry.HISTORY_PERMISSION !in granted

        // One after the other: Health Connect serves an app's requests largely in turn, so
        // reading both at once only made the first wait for the second.
        val loader = TileChartLoader(repository, dashboardStore)
        val firstData = loader.chart(first, span, offset, capped, sourceFor(first, span, offset), deferExtras = true)
        val secondData = loader.chart(second, span, offset, capped, sourceFor(second, span, offset), deferExtras = true)
        if (firstData.isBlank() && secondData.isBlank()) return UiState.Empty
        return UiState.Data(Compared(firstData, secondData, sharedExtent(span, offset, firstData, secondData)))
    }

    /** The source the type's own screen would open on: its own choice, or the preferred app where it wrote this type. */
    private suspend fun sourceFor(spec: RecordTypeSpec<*>, span: Span, offset: Int): String? {
        val typeName = spec.type.simpleName.orEmpty()
        val selections = runCatching { sourceStore.selections.first() }.getOrDefault(emptyMap())
        selections[typeName]?.let { return it.takeUnless { chosen -> chosen == SourceStore.ALL_SOURCES } }
        val preferred = runCatching { sourceStore.preferred.first() }.getOrNull() ?: return null
        val zone = HealthRepository.DEFAULT_ZONE
        val wrote = runCatching {
            repository.recordsIn(
                spec,
                span.startDate(offset).atStartOfDay(zone).toInstant(),
                span.endDate(offset).atStartOfDay(zone).toInstant(),
                setOf(DataOrigin(preferred)),
                maxRecords = 1,
            ).isNotEmpty()
        }.getOrDefault(false)
        return preferred.takeIf { wrote }
    }
}

private fun TileDetailData.isBlank(): Boolean = points.isEmpty() && total == null && sessions.isEmpty()

/**
 * One time axis for both charts, so a day sits at the same x in each.
 *
 * Within a day the window itself, widened to whatever either chart widened to (a night that
 * began the evening before). Across days each bucket is drawn centred on its own slot -- a bar
 * is, and a line's points must sit above the bars beneath them -- so the axis runs half a
 * bucket early: a point stamped at a day's start lands in the middle of that day's slot.
 */
internal fun sharedExtent(span: Span, offset: Int, first: TileDetailData, second: TileDetailData): ClosedRange<Instant> {
    val zone = HealthRepository.DEFAULT_ZONE
    val start = span.startDate(offset).atStartOfDay(zone).toInstant()
    val end = span.endDate(offset).atStartOfDay(zone).toInstant()
    val bucket = span.bucket ?: return listOfNotNull(first.extent, second.extent, start..end).let { ranges ->
        ranges.minOf { it.start }..ranges.maxOf { it.endInclusive }
    }
    val half = Duration.ofDays(bucket.days.toLong()).dividedBy(2)
    return start.minus(half)..end.minus(half)
}

/** Every type with a chart and access granted, the choice for a comparison's second chart. */
suspend fun HealthRepository.comparableTypes(): List<RecordTypeSpec<*>> {
    val granted = runCatching { grantedPermissions() }.getOrDefault(emptySet())
    return RecordRegistry.all.filter { it.isPinnable && it.permission in granted }
}
