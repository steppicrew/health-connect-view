package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.dayInstants
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug-only. Dumps the interval shape of one day's records for the types where overlapping
 * writers are suspected, so a whole-day record can be told apart from a short one.
 *
 *   adb shell am start -n <pkg>/...debug.RecordShapeActivity
 *
 * Logs times, durations, and writing app -- never a health value beyond the count needed to
 * identify the record, which for these types is the metric itself and unavoidable here.
 */
class RecordShapeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HealthRepository(this)

        lifecycleScope.launch {
            val zone = ZoneId.systemDefault()
            val day = LocalDate.now().minusDays(1)
            Log.i(TAG, "day=$day zone=$zone")

            val floors = runCatching {
                repository.read(FloorsClimbedRecord::class, dayInstants(day, zone))
            }.getOrElse {
                Log.w(TAG, "floors read failed: $it")
                emptyList()
            }

            floors.sortedBy { it.startTime }.forEach { record ->
                val duration = Duration.between(record.startTime, record.endTime)
                Log.i(
                    TAG,
                    "FLOORS start=${record.startTime.atZone(zone).toLocalTime()} " +
                        "end=${record.endTime.atZone(zone).toLocalTime()} " +
                        "durationMin=${duration.toMinutes()} floors=${record.floors} " +
                        "origin=${record.metadata.dataOrigin.packageName}",
                )
            }

            val total = runCatching {
                repository.total(
                    FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
                    de.steppicrew.healthconnectview.health.dayFilter(day),
                )
            }.getOrNull()
            Log.i(TAG, "FLOORS rawSum=${floors.sumOf { it.floors }} aggregateTotal=$total")

            val steps = runCatching {
                repository.read(StepsRecord::class, dayInstants(day, zone))
            }.getOrDefault(emptyList())
            val longSteps = steps.filter {
                Duration.between(it.startTime, it.endTime).toHours() >= 12
            }
            Log.i(TAG, "STEPS records=${steps.size} spanningHalfDayOrMore=${longSteps.size}")
            longSteps.forEach { record ->
                Log.i(
                    TAG,
                    "STEPS-LONG start=${record.startTime.atZone(zone).toLocalTime()} " +
                        "end=${record.endTime.atZone(zone).toLocalTime()} " +
                        "count=${record.count} " +
                        "origin=${record.metadata.dataOrigin.packageName}",
                )
            }

            Log.i(TAG, "done")
            finish()
        }
    }

    private companion object {
        const val TAG = "RecordShape"
    }
}
