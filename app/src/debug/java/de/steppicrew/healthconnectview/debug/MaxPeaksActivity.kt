package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.ui.theme.HealthConnectViewTheme
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Debug-only. Shows where the past year's monthly heart-rate maxima were reached, the ones
 * observedMaxHeartRate picks the third-highest of, so the owner can judge whether a peak was a
 * real effort or a sensor's spike: per month, the moment, the writer, the readings either side
 * of it, and any workout running then.
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.MaxPeaksActivity --ei months 6
 *
 * On screen only, never in the log: these are readings. Stays open, since Health Connect
 * refuses reads once the activity is in the background.
 */
class MaxPeaksActivity : ComponentActivity() {

    private val lines = mutableStateOf(listOf("Reading…"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val months = intent.getIntExtra(EXTRA_MONTHS, DEFAULT_MONTHS)
        setContent {
            HealthConnectViewTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .safeDrawingPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        lines.value.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp)) }
                    }
                }
            }
        }
        val repository = HealthRepository(this)
        lifecycleScope.launch {
            lines.value = runCatching { repository.peaks(months) }.getOrElse { listOf("Failed: ${it::class.simpleName}") }
        }
    }

    private suspend fun HealthRepository.peaks(months: Int): List<String> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val start = today.minusYears(1).withDayOfMonth(1).atStartOfDay()
        val monthly = bucketedTotals(HeartRateRecord.BPM_MAX, TimeRangeFilter.between(start, today.plusDays(1).atStartOfDay()), Period.ofMonths(1))
            .mapNotNull { group -> group.result[HeartRateRecord.BPM_MAX]?.let { group.startTime to it } }
            .sortedByDescending { it.second }
        val out = mutableListOf(
            "Monthly maxima, highest first: " + monthly.joinToString { (month, bpm) -> "${month.format(MONTH)} $bpm" },
            "The app takes the third-highest.",
        )
        monthly.take(months).forEachIndexed { rank, (month, bpm) ->
            val day = dayOfPeak(month, bpm) ?: run {
                out += "#${rank + 1} ${month.format(MONTH)}: $bpm bpm, day not found"
                return@forEachIndexed
            }
            val from = day.atStartOfDay(zone).toInstant()
            val until = day.plusDays(1).atStartOfDay(zone).toInstant()
            val samples = mutableListOf<Triple<Instant, Long, String>>()
            forEachPage(HeartRateRecord::class, TimeRangeFilter.between(from, until)) { page ->
                page.forEach { record ->
                    record.samples.forEach { samples += Triple(it.time, it.beatsPerMinute, record.metadata.dataOrigin.packageName) }
                }
            }
            val peak = samples.filter { it.second == bpm }.minByOrNull { it.first }
            if (peak == null) {
                out += "#${rank + 1} $day: $bpm bpm, reading not found"
                return@forEachIndexed
            }
            // The same writer's readings either side: a spike stands alone, an effort climbs.
            val own = samples.filter { it.third == peak.third }.sortedBy { it.first }
            val at = own.indexOf(peak)
            val around = own.subList(maxOf(0, at - AROUND), minOf(own.size, at + AROUND + 1))
                .joinToString(" ") { if (it === peak) "[${it.second}]" else "${it.second}" }
            val workouts = read(ExerciseSessionRecord::class, TimeRangeFilter.between(from.minus(Duration.ofHours(12)), until))
                .filter { !it.startTime.isAfter(peak.first) && it.endTime.isAfter(peak.first) }
                .joinToString("; ") { w ->
                    "type ${w.exerciseType}${w.title?.let { " \"$it\"" }.orEmpty()} " +
                        "${w.startTime.atZone(zone).format(TIME)}–${w.endTime.atZone(zone).format(TIME)} (${w.metadata.dataOrigin.packageName})"
                }
            out += "#${rank + 1} $day ${peak.first.atZone(zone).format(SECONDS)}: $bpm bpm by ${peak.third}\n" +
                "around: $around\n" +
                "workout: ${workouts.ifEmpty { "none" }}"
        }
        return out
    }

    /** The day in [month] whose maximum is [bpm]. */
    private suspend fun HealthRepository.dayOfPeak(month: LocalDateTime, bpm: Long): LocalDate? =
        bucketedTotals(HeartRateRecord.BPM_MAX, TimeRangeFilter.between(month, month.plusMonths(1)), Period.ofDays(1))
            .firstOrNull { it.result[HeartRateRecord.BPM_MAX] == bpm }
            ?.startTime?.toLocalDate()

    private companion object {
        const val EXTRA_MONTHS = "months"
        const val DEFAULT_MONTHS = 5
        const val AROUND = 4
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
