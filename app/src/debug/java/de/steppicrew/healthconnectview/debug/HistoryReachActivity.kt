package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.registry.RecordRegistry
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Debug-only. Reports how far back each granted type can actually be read.
 *
 * Without READ_HEALTH_DATA_HISTORY, Health Connect silently truncates every read to the last
 * 30 days and reports no error, which is indistinguishable from having no older data. This
 * asks for a deliberately over-long window and logs the oldest record that comes back, so the
 * cap becomes visible as a date rather than a guess.
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.HistoryReachActivity
 *
 * Logs dates and counts only, never a health value.
 */
class HistoryReachActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HealthRepository(this)

        lifecycleScope.launch {
            val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
            val historyGranted = RecordRegistry.HISTORY_PERMISSION in granted
            val now = Instant.now()
            val cutoff = now.minus(CAP_DAYS, ChronoUnit.DAYS)

            Log.i(TAG, "historyPermissionGranted=$historyGranted grantedTypes=${granted.size}")
            Log.i(TAG, "asking back to ${date(now.minus(WINDOW_DAYS, ChronoUnit.DAYS))}")

            // Ascending, page size 1: the first record returned is the oldest reachable one.
            val filter = TimeRangeFilter.between(
                now.minus(WINDOW_DAYS, ChronoUnit.DAYS),
                now,
            )

            RecordRegistry.all
                .filter { it.permission in granted }
                .forEach { spec ->
                    val name = spec.type.simpleName
                    val oldest = runCatching { oldestRecordTime(repository, spec, filter) }
                        .getOrElse { error ->
                            Log.w(TAG, "$name: read threw: $error")
                            return@forEach
                        } ?: return@forEach

                    // A floor at ~30 days is the fingerprint of the missing permission; older
                    // than that proves history is actually being served.
                    val beyondCap = oldest.isBefore(cutoff)
                    Log.i(
                        TAG,
                        "$name: oldest=${date(oldest)} daysBack=${daysBack(oldest, now)} " +
                            "beyond30d=$beyondCap",
                    )
                }
            Log.i(TAG, "done")
            finish()
        }
    }

    private suspend fun oldestRecordTime(
        repository: HealthRepository,
        spec: de.steppicrew.healthconnectview.registry.RecordTypeSpec<*>,
        filter: TimeRangeFilter,
    ): Instant? {
        val records = repository.read(spec.type, filter, maxRecords = HealthRepository.MAX_RECORDS)
        return records.minOfOrNull { spec.timeOf(it) }
    }

    private fun date(instant: Instant): LocalDate =
        instant.atZone(ZoneId.systemDefault()).toLocalDate()

    private fun daysBack(oldest: Instant, now: Instant): Long =
        ChronoUnit.DAYS.between(oldest, now)

    private companion object {
        const val TAG = "HistoryReach"

        /** Deliberately longer than any range the UI offers, to find the real floor. */
        const val WINDOW_DAYS = 1000L

        /** Health Connect's default limit for apps without the history permission. */
        const val CAP_DAYS = 31L
    }
}
