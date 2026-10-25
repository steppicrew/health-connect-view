package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Debug-only. Logs the record id of the newest workout of one type that has segments, so a
 * script can open its screen: the session route needs the id Health Connect gave the record,
 * which changes with every seeding. The store screenshots use it for the strength session.
 *
 *   adb shell am start -W -n <pkg>/de.steppicrew.healthconnectview.debug.LatestSessionActivity --ei type 70
 *   adb logcat -d -s LatestSession:I     # "id=<record id>"
 *
 * Logs an id and nothing of the workout.
 */
class LatestSessionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getIntExtra(EXTRA_TYPE, ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING)
        val repository = HealthRepository(this)
        lifecycleScope.launch {
            val now = Instant.now()
            val newest = runCatching {
                repository.read(ExerciseSessionRecord::class, TimeRangeFilter.between(now.minus(LOOKBACK), now))
            }.getOrDefault(emptyList())
                .filter { it.exerciseType == type && it.segments.isNotEmpty() }
                .maxByOrNull { it.startTime }
            Log.i(TAG, newest?.let { "id=${it.metadata.id}" } ?: "none")
            finish()
        }
    }

    private companion object {
        const val TAG = "LatestSession"
        const val EXTRA_TYPE = "type"
        val LOOKBACK: Duration = Duration.ofDays(30)
    }
}
