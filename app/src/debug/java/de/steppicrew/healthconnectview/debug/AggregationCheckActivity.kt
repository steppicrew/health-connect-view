package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.TimeRange
import de.steppicrew.healthconnectview.registry.RecordRegistry
import kotlinx.coroutines.launch

/**
 * Debug-only. Reports, for each aggregatable type, how the raw record count compares with
 * Health Connect's deduplicated total -- the check that matters when several apps write the
 * same metric. Startable from adb, so it needs no UI interaction:
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.AggregationCheckActivity
 *
 * Logs counts and which apps contributed. It never logs a health value.
 */
class AggregationCheckActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HealthRepository(this)

        lifecycleScope.launch {
            val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
            val range = TimeRange.MONTH

            RecordRegistry.all
                .filter { it.aggregate != null && it.permission in granted }
                .forEach { spec ->
                    val metric = spec.aggregate ?: return@forEach
                    val name = spec.type.simpleName

                    val rawCount = runCatching { repository.read(spec.type, range.filter()).size }
                        .getOrElse { -1 }

                    val buckets = runCatching { repository.dailyTotals(metric, range.localFilter()) }
                        .getOrElse { error ->
                            Log.w(TAG, "$name: aggregation threw: $error")
                            emptyList()
                        }
                    val withValue = buckets.count { it.result[metric] != null }

                    val origins = runCatching {
                        repository.contributingApps(metric, range.localFilter())
                    }.getOrDefault(emptySet())

                    Log.i(
                        TAG,
                        "$name: rawRecords=$rawCount buckets=${buckets.size} " +
                            "bucketsWithValue=$withValue writers=${origins.size} $origins",
                    )
                }
            Log.i(TAG, "done")
            finish()
        }
    }

    private companion object {
        const val TAG = "AggCheck"
    }
}
