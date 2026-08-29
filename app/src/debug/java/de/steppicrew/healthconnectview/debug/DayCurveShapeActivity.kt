package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug-only. Reports how many heart-rate samples a day actually holds, and how many the
 * chart ends up drawing, so the two can be compared.
 *
 * Logs counts and gap statistics only -- never a reading, a timestamp or a record id, matching
 * the other probes. A gap in seconds describes the recording schedule, not the measurement.
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.DayCurveShapeActivity \
 *       -e date 2026-08-28
 */
class DayCurveShapeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HealthRepository(this)
        val date = intent?.getStringExtra("date")
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()

        lifecycleScope.launch {
            val zone = ZoneId.systemDefault()
            val range = TimeRangeFilter.between(
                date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant(),
            )

            // What readRecords returns unthinned, versus what readForChart keeps.
            val all = runCatching { repository.read(HeartRateRecord::class, range) }
                .getOrDefault(emptyList())
            val thinned = runCatching { repository.readForChart(HeartRateRecord::class, range) }
                .getOrDefault(emptyList())

            Log.i(TAG, "date=$date records: all=${all.size} afterThinning=${thinned.size}")

            all.groupBy { it.metadata.dataOrigin.packageName }.forEach { (app, group) ->
                val times = group.flatMap { r -> r.samples.map { it.time } }.sorted()
                val gaps = times.zipWithNext { a, b -> Duration.between(a, b).seconds }
                    .filter { it > 0 }
                Log.i(
                    TAG,
                    "  writer=$app records=${group.size} samples=${times.size} " +
                        "gaps: median=${gaps.sorted().getOrNull(gaps.size / 2)}s " +
                        "p90=${gaps.sorted().getOrNull(gaps.size * 9 / 10)}s " +
                        "max=${gaps.maxOrNull()}s",
                )
            }

            val drawnSamples = thinned.sumOf { it.samples.size }
            Log.i(TAG, "samples the chart would draw: $drawnSamples")
            Log.i(TAG, "done")
            finish()
        }
    }

    private companion object {
        const val TAG = "DayCurve"
    }
}
