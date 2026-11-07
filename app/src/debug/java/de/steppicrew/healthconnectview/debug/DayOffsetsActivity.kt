package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Debug-only. Counts, per day, type and writer, the records written with each UTC offset --
 * which writers stamp a record with the offset where it was measured and which with the
 * phone's zone at the time they synced, for choosing the clock a day is drawn on (`dayZone`).
 *
 * The day is the record's own, by its own offset. Logs dates, offsets, counts and package
 * names only, never a reading or a timestamp.
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.DayOffsetsActivity \
 *       -e from 2026-10-25 -e to 2026-11-01
 */
class DayOffsetsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HealthRepository(this)
        val from = intent?.getStringExtra("from")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now().minusDays(14)
        val to = intent?.getStringExtra("to")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()

        // Not finished early: reads fail once the activity backgrounds (CLAUDE.md).
        lifecycleScope.launch {
            val range = TimeRangeFilter.between(
                from.atStartOfDay(ZoneOffset.ofHours(14)).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneOffset.ofHours(-12)).toInstant(),
            )
            // type -> writer -> day -> offset -> count
            val counts = sortedMapOf<String, MutableMap<String, MutableMap<LocalDate, MutableMap<ZoneOffset, Int>>>>()
            fun count(type: String, writer: String, time: Instant, offset: ZoneOffset?) {
                offset ?: return
                val day = time.atOffset(offset).toLocalDate()
                if (day < from || day > to) return
                counts.getOrPut(type) { sortedMapOf() }.getOrPut(writer) { sortedMapOf() }
                    .getOrPut(day) { mutableMapOf() }.merge(offset, 1, Int::plus)
            }
            runCatching {
                repository.forEachPage(HeartRateRecord::class, range) { page ->
                    page.forEach { count("heart", it.metadata.dataOrigin.packageName, it.startTime, it.startZoneOffset) }
                }
            }.onFailure { Log.w(TAG, "heart rate read threw: $it") }
            runCatching {
                repository.forEachPage(StepsRecord::class, range) { page ->
                    page.forEach { count("steps", it.metadata.dataOrigin.packageName, it.startTime, it.startZoneOffset) }
                }
            }.onFailure { Log.w(TAG, "steps read threw: $it") }
            runCatching {
                repository.read(ExerciseSessionRecord::class, range).forEach {
                    count("workout", it.metadata.dataOrigin.packageName, it.endTime, it.endZoneOffset)
                }
                repository.read(SleepSessionRecord::class, range).forEach {
                    count("sleep", it.metadata.dataOrigin.packageName, it.endTime, it.endZoneOffset)
                }
            }.onFailure { Log.w(TAG, "session read threw: $it") }
            counts.forEach { (type, writers) ->
                writers.forEach { (writer, days) ->
                    days.forEach { (day, offsets) ->
                        Log.i(TAG, "$type $writer $day ${offsets.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }}")
                    }
                }
            }
            Log.i(TAG, "done")
            finish()
        }
    }

    private companion object {
        const val TAG = "DayOffsets"
    }
}
