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

            // Steps per writer for the same day, and what the ordering looks like: the
            // cumulative chart builds from records, so overlapping or out-of-order intervals
            // between writers are what would send the line backwards.
            val stepRecords = runCatching {
                repository.read(StepsRecord::class, dayInstants(day, zone))
            }.getOrDefault(emptyList())

            stepRecords.groupBy { it.metadata.dataOrigin.packageName }.forEach { (pkg, recs) ->
                val sorted = recs.sortedBy { it.startTime }
                var overlaps = 0
                sorted.zipWithNext().forEach { (a, b) ->
                    if (b.startTime < a.endTime) overlaps++
                }
                Log.i(
                    TAG,
                    "STEPS-ORIGIN $pkg records=${recs.size} sum=${recs.sumOf { it.count }} " +
                        "internalOverlaps=$overlaps " +
                        "first=${sorted.first().startTime.atZone(zone).toLocalTime()} " +
                        "last=${sorted.last().endTime.atZone(zone).toLocalTime()}",
                )
                val scoped = runCatching {
                    repository.total(
                        StepsRecord.COUNT_TOTAL,
                        de.steppicrew.healthconnectview.health.dayFilter(day),
                        setOf(androidx.health.connect.client.records.metadata.DataOrigin(pkg)),
                    )
                }.getOrNull()
                Log.i(TAG, "STEPS-ORIGIN $pkg aggregate=$scoped")
            }

            // Cross-writer overlap on the merged set: this is what the all-sources chart sees.
            val allSorted = stepRecords.sortedBy { it.startTime }
            var crossOverlaps = 0
            allSorted.zipWithNext().forEach { (a, b) ->
                if (b.startTime < a.endTime) crossOverlaps++
            }
            val combined = runCatching {
                repository.total(
                    StepsRecord.COUNT_TOTAL,
                    de.steppicrew.healthconnectview.health.dayFilter(day),
                )
            }.getOrNull()
            Log.i(
                TAG,
                "STEPS-ALL records=${stepRecords.size} rawSum=${stepRecords.sumOf { it.count }} " +
                    "aggregate=$combined crossWriterOverlaps=$crossOverlaps",
            )

            // Are exercise sessions and the readings taken during them connected? Health
            // Connect stores them as separate record types with no foreign key, so the only
            // available link is the time range. This measures whether that link is usable:
            // how many heart-rate samples fall inside each session's window.
            val sessions = runCatching {
                repository.read(
                    androidx.health.connect.client.records.ExerciseSessionRecord::class,
                    androidx.health.connect.client.time.TimeRangeFilter.between(
                        day.minusDays(7).atStartOfDay(zone).toInstant(),
                        day.plusDays(1).atStartOfDay(zone).toInstant(),
                    ),
                )
            }.getOrDefault(emptyList())

            val hr = runCatching {
                repository.read(
                    androidx.health.connect.client.records.HeartRateRecord::class,
                    androidx.health.connect.client.time.TimeRangeFilter.between(
                        day.minusDays(7).atStartOfDay(zone).toInstant(),
                        day.plusDays(1).atStartOfDay(zone).toInstant(),
                    ),
                )
            }.getOrDefault(emptyList())

            Log.i(TAG, "SESSIONS count=${sessions.size} hrRecords=${hr.size}")
            sessions.sortedBy { it.startTime }.forEach { session ->
                val inWindow = hr.filter {
                    it.startTime < session.endTime && it.endTime > session.startTime
                }
                val samples = inWindow.sumOf { it.samples.size }
                Log.i(
                    TAG,
                    "SESSION type=${session.exerciseType} title=${session.title} " +
                        "start=${session.startTime.atZone(zone).toLocalDateTime()} " +
                        "durationMin=${Duration.between(session.startTime, session.endTime).toMinutes()} " +
                        "origin=${session.metadata.dataOrigin.packageName} " +
                        "hrRecordsInWindow=${inWindow.size} hrSamplesInWindow=$samples",
                )
            }

            // Sleep spans midnight, so a session belonging to "last night" starts on the
            // previous calendar day and a day-bounded read can miss it entirely.
            val sleepWide = runCatching {
                repository.read(
                    androidx.health.connect.client.records.SleepSessionRecord::class,
                    androidx.health.connect.client.time.TimeRangeFilter.between(
                        day.minusDays(2).atStartOfDay(zone).toInstant(),
                        day.plusDays(2).atStartOfDay(zone).toInstant(),
                    ),
                )
            }.getOrDefault(emptyList())

            val dayStart = day.atStartOfDay(zone).toInstant()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
            Log.i(TAG, "SLEEP wideCount=${sleepWide.size}")
            sleepWide.sortedBy { it.startTime }.forEach { rec ->
                val overlapsDay = rec.startTime < dayEnd && rec.endTime > dayStart
                val startsInDay = rec.startTime >= dayStart && rec.startTime < dayEnd
                Log.i(
                    TAG,
                    "SLEEP start=${rec.startTime.atZone(zone).toLocalDateTime()} " +
                        "end=${rec.endTime.atZone(zone).toLocalDateTime()} " +
                        "overlapsDay=$overlapsDay startsInDay=$startsInDay " +
                        "origin=${rec.metadata.dataOrigin.packageName}",
                )
            }

            // ExerciseSessionRecord carries no distance, power or calories -- only type,
            // title, notes, segments, laps and route. Those metrics are separate record types
            // written over the same window, so a session's statistics have to be assembled by
            // overlapping them. This measures what is actually there for one session.
            val bikeSession = sessions
                .filter { it.metadata.dataOrigin.packageName == "com.lifefitness.connect" }
                .maxByOrNull { it.startTime }
            if (bikeSession != null) {
                val from = bikeSession.startTime
                val to = bikeSession.endTime
                val window = androidx.health.connect.client.time.TimeRangeFilter.between(from, to)
                Log.i(
                    TAG,
                    "SESSION-STATS for ${bikeSession.title} " +
                        "${from.atZone(zone).toLocalDateTime()} .. ${to.atZone(zone).toLocalTime()} " +
                        "segments=${bikeSession.segments.size} laps=${bikeSession.laps.size} " +
                        "notes=${bikeSession.notes}",
                )

                val grantedNow = runCatching { repository.grantedPermissions() }
                    .getOrDefault(emptySet())
                de.steppicrew.healthconnectview.registry.RecordRegistry.all
                    .filter { it.permission in grantedNow }
                    .forEach { spec ->
                        val n = runCatching { repository.read(spec.type, window).size }
                            .getOrDefault(0)
                        if (n > 0) {
                            val agg = spec.aggregate?.let { metric ->
                                runCatching { repository.total(metric, window) }.getOrNull()
                            }
                            Log.i(TAG, "SESSION-STATS   ${spec.type.simpleName}: records=$n total=$agg")
                        }
                    }
            }

            // Does a day-bounded read return a session that started the previous evening?
            // Sleep always straddles midnight, so if the filter only returns records fully
            // inside the window, a night's sleep is invisible on the day it ended.
            val sleepDayFiltered = runCatching {
                repository.read(
                    androidx.health.connect.client.records.SleepSessionRecord::class,
                    dayInstants(day, zone),
                )
            }.getOrDefault(emptyList())
            Log.i(
                TAG,
                "SLEEP-DAYFILTER returned=${sleepDayFiltered.size} " +
                    "starts=${sleepDayFiltered.map { it.startTime.atZone(zone).toLocalDateTime().toString() }}",
            )

            Log.i(TAG, "done")
            finish()
        }
    }

    private companion object {
        const val TAG = "RecordShape"
    }
}
