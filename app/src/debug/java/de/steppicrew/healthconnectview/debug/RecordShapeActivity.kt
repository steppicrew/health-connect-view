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

            // Does a record sit just outside the local-midnight window? A climb logged late
            // in the evening but stamped after midnight, or one whose interval straddles the
            // boundary, would be excluded from the day while the source app still counts it.
            val wide = runCatching {
                repository.read(
                    FloorsClimbedRecord::class,
                    androidx.health.connect.client.time.TimeRangeFilter.between(
                        day.minusDays(1).atStartOfDay(zone).toInstant(),
                        day.plusDays(2).atStartOfDay(zone).toInstant(),
                    ),
                )
            }.getOrDefault(emptyList())

            wide.sortedBy { it.startTime }.forEach { record ->
                val startDay = record.startTime.atZone(zone).toLocalDate()
                val endDay = record.endTime.atZone(zone).toLocalDate()
                val straddles = startDay != endDay
                Log.i(
                    TAG,
                    "WIDE startDay=$startDay endDay=$endDay straddles=$straddles " +
                        "start=${record.startTime.atZone(zone).toLocalTime()} " +
                        "floors=${record.floors} " +
                        "origin=${record.metadata.dataOrigin.packageName}",
                )
            }

            // Per-origin totals over the same day, to see where the difference sits.
            listOf(
                "com.garmin.android.apps.connectmobile",
                "nl.appyhapps.healthsync",
            ).forEach { pkg ->
                val scoped = runCatching {
                    repository.total(
                        FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
                        de.steppicrew.healthconnectview.health.dayFilter(day),
                        setOf(androidx.health.connect.client.records.metadata.DataOrigin(pkg)),
                    )
                }.getOrNull()
                val scopedRaw = wide
                    .filter { it.metadata.dataOrigin.packageName == pkg }
                    .filter { it.startTime.atZone(zone).toLocalDate() == day }
                    .sumOf { it.floors }
                Log.i(TAG, "ORIGIN $pkg aggregate=$scoped rawSumStartingThatDay=$scopedRaw")
            }

            // Hourly buckets, the exact call the day chart makes, to see whether the
            // whole-day summary record is being spread across every hour.
            val hourly = runCatching {
                repository.intradayTotals(
                    FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
                    dayInstants(day, zone),
                    java.time.Duration.ofHours(1),
                )
            }.getOrDefault(emptyList())
            var running = 0.0
            hourly.forEach { bucket ->
                val v = bucket.result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]
                if (v != null) {
                    running += v
                    Log.i(
                        TAG,
                        "HOUR ${bucket.startTime.atZone(zone).toLocalTime()} value=$v " +
                            "running=$running",
                    )
                }
            }
            Log.i(TAG, "HOURLY buckets=${hourly.size} sumOfBuckets=$running")

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
