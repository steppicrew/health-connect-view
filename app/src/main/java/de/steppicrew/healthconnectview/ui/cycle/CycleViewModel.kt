package de.steppicrew.healthconnectview.ui.cycle

import de.steppicrew.healthconnectview.ui.SharedDate
import android.app.Application
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.health.Cycle
import de.steppicrew.healthconnectview.health.CycleRecords
import de.steppicrew.healthconnectview.health.CycleStats
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.health.buildCycles
import de.steppicrew.healthconnectview.health.cycleStats
import de.steppicrew.healthconnectview.health.mergeCycleDays
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

data class CycleData(
    /** Oldest first, so the rows read down the page the way time runs. */
    val cycles: List<Cycle>,
    val stats: CycleStats?,
    /** Types whose layer is missing because access was not granted, as display names. */
    val notGranted: List<Int>,
    /** Same meaning as on the type detail screen: older data exists but may not be read. */
    val historyCapped: Boolean,
)

class CycleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)

    private val _state = MutableStateFlow<UiState<CycleData>>(UiState.Loading)
    val state: StateFlow<UiState<CycleData>> = _state.asStateFlow()

    /** The day the year ends on: the app's shared date, which stepping here moves too. */
    val end: StateFlow<LocalDate> = SharedDate.date

    /** Draw [CycleFixture]'s synthetic cycles instead of reading; debug builds only. */
    private var fixture = false

    fun load(fixture: Boolean = false) {
        this.fixture = fixture
        reload()
    }

    fun stepBack() {
        SharedDate.set(SPAN.previousEnd(SharedDate.current))
        reload()
    }

    /** Back to the year ending today. */
    fun stepToNow() {
        if (!SharedDate.current.isBefore(LocalDate.now())) return
        SharedDate.set(LocalDate.now())
        reload()
    }

    fun stepForward() {
        if (!SharedDate.current.isBefore(LocalDate.now())) return
        SharedDate.set(SPAN.nextEnd(SharedDate.current))
        reload()
    }

    /** The load in flight, cancelled when a newer one starts: every resume starts one. */
    private var loadJob: Job? = null

    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { UiState.Loading }
            if (fixture) {
                val records = CycleFixture.records(ZoneId.systemDefault())
                if (records != null) {
                    _state.update { UiState.Data(present(records, SharedDate.current, notGranted = emptyList(), capped = false)) }
                    return@launch
                }
            }
            // A refused permission read is a failure, not "nothing granted".
            val granted = runCatching { repository.grantedPermissions() }.getOrElse { error ->
                ensureActive()
                _state.update { UiState.Error(error.message ?: "Could not read data") }
                return@launch
            }
            // Periods and flow share READ_MENSTRUATION. Without it there is no day 1 to align
            // anything to, so the other layers alone cannot make a single row.
            if (permissionOf(MenstruationPeriodRecord::class) !in granted) {
                _state.update { UiState.NoPermission }
                return@launch
            }
            val last = SharedDate.current
            val result = runCatching { loadData(granted, last) }
            // runCatching also catches the cancellation; a superseded load must not publish.
            ensureActive()
            _state.update {
                result.fold(
                    onSuccess = { data -> if (data.cycles.isEmpty()) UiState.Empty else UiState.Data(data) },
                    onFailure = { error -> UiState.Error(error.message ?: "Could not read data") },
                )
            }
        }
    }

    private suspend fun loadData(granted: Set<String>, last: LocalDate): CycleData {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = SPAN.startDate(0, last)
        val end = SPAN.endDate(0, last)

        // Widened both ways and then selected by start date, never trimmed. Earlier: a period
        // that began just before the window would otherwise put a false day 1 on its first
        // day inside it. Later: the last cycle to start in the window ends after it, and cut
        // off at the boundary it would read as a short cycle.
        val readFrom = start.minusDays(MARGIN_DAYS).atStartOfDay(zone).toInstant()
        val readTo = minOf(end.plusDays(MARGIN_DAYS), today.plusDays(1)).atStartOfDay(zone).toInstant()
        val range = TimeRangeFilter.between(readFrom, readTo)

        suspend fun <T : Record> readIfGranted(type: KClass<T>): List<T> =
            if (permissionOf(type) in granted) repository.read(type, range) else emptyList()

        val records = CycleRecords(
            periods = readIfGranted(MenstruationPeriodRecord::class),
            flows = readIfGranted(MenstruationFlowRecord::class),
            spotting = readIfGranted(IntermenstrualBleedingRecord::class),
            ovulationTests = readIfGranted(OvulationTestRecord::class),
            mucus = readIfGranted(CervicalMucusRecord::class),
            temperatures = readIfGranted(BasalBodyTemperatureRecord::class),
        )
        return present(
            records,
            last,
            notGranted = LAYERS.filter { permissionOf(it) !in granted }
                .map { RecordRegistry.spec(it).displayNameRes },
            capped = RecordRegistry.HISTORY_PERMISSION !in granted,
        )
    }

    private fun present(records: CycleRecords, last: LocalDate, notGranted: List<Int>, capped: Boolean): CycleData {
        val zone = ZoneId.systemDefault()
        // The real today: no day after it has happened, wherever the window ends.
        val today = LocalDate.now(zone)
        val start = SPAN.startDate(0, last)
        val end = SPAN.endDate(0, last)
        val cycles = buildCycles(
            days = mergeCycleDays(records, zone),
            window = start..end.minusDays(1),
            lastDay = minOf(end.plusDays(MARGIN_DAYS), today),
        )
        return CycleData(cycles = cycles, stats = cycleStats(cycles), notGranted = notGranted, historyCapped = capped)
    }

    private fun permissionOf(type: KClass<out Record>): String = RecordRegistry.spec(type).permission

    private companion object {
        /** A year of cycles is a dozen rows: enough to see a pattern, few enough to read. */
        val SPAN = Span.YEAR

        /** Longer than any cycle the view is meant to show whole. */
        const val MARGIN_DAYS = 60L

        /** Optional layers, listed when missing so an absent marker is not read as "none". */
        val LAYERS = listOf(
            IntermenstrualBleedingRecord::class,
            OvulationTestRecord::class,
            CervicalMucusRecord::class,
            BasalBodyTemperatureRecord::class,
        )
    }
}
