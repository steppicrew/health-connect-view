package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug-only. Reports the *structure* of the heart-rate samples inside each exercise session
 * on a given day, to explain why a session curve looks the way it does.
 *
 * What it logs: per writing app, how many records and samples fall in the window, and the
 * distribution of gaps between consecutive samples. Gaps are what decide whether a curve
 * reads as continuous or as spikes, and they are a property of the recording schedule rather
 * than of the readings.
 *
 * What it never logs, matching the other probes: individual values, exact timestamps, or
 * record ids. A gap in seconds says when the watch sampled, not what it measured.
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.SessionCurveShapeActivity \
 *       -e date 2026-08-27
 */
class SessionCurveShapeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HealthRepository(this)
        val date = intent?.getStringExtra("date")
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()

        lifecycleScope.launch {
            val zone = ZoneId.systemDefault()
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

            val sessions = runCatching {
                repository.read(
                    ExerciseSessionRecord::class,
                    TimeRangeFilter.between(dayStart, dayEnd),
                )
            }.getOrDefault(emptyList())

            Log.i(TAG, "date=$date sessions=${sessions.size}")

            sessions.forEach { session ->
                val length = Duration.between(session.startTime, session.endTime)
                Log.i(TAG, "session type=${session.exerciseType} length=${length.toMinutes()}m")

                val records = runCatching {
                    repository.read(
                        HeartRateRecord::class,
                        TimeRangeFilter.between(session.startTime, session.endTime),
                    )
                }.getOrDefault(emptyList())

                records.groupBy { it.metadata.dataOrigin.packageName }.forEach { (app, group) ->
                    val times = group.flatMap { record -> record.samples.map { it.time } }.sorted()
                    val gaps = times.zipWithNext { a, b -> Duration.between(a, b).seconds }
                    Log.i(
                        TAG,
                        "  writer=$app records=${group.size} samples=${times.size} " +
                            "gaps: min=${gaps.minOrNull()}s median=${gaps.median()}s " +
                            "max=${gaps.maxOrNull()}s over1min=${gaps.count { it > 60 }}",
                    )
                }
            }
            Log.i(TAG, "done")
            finish()
        }
    }

    private fun List<Long>.median(): Long? =
        sorted().takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }

    private companion object {
        const val TAG = "SessionCurve"
    }
}
