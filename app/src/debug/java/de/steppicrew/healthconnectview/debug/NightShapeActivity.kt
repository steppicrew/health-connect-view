package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.sessionsIn
import de.steppicrew.healthconnectview.registry.RecordRegistry
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug-only. Reports which readings are taken during each recent night, to decide which
 * lines a night's chart offers: a chip for a type the phone never holds at night is a dead
 * button.
 *
 * Per night and type it logs, per writing app, how many readings fall inside the night and the
 * median and longest gap between them. Never a value, a timestamp or a record id, matching the
 * other probes: a gap says when a device measured, not what.
 *
 * Keep it short (see CLAUDE.md, reads require the foreground): a week of nights by default.
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.NightShapeActivity [-e nights 7]
 */
class NightShapeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HealthRepository(this)
        val nights = intent?.getStringExtra("nights")?.toIntOrNull() ?: 7

        lifecycleScope.launch {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            val sleep = repository.sessionsIn(
                today.minusDays(nights.toLong()).atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant(),
                setOf(Session.Kind.SLEEP),
            )
            val granted = repository.grantedPermissions()
            Log.i(TAG, "nights=${sleep.size}")

            sleep.forEach { night ->
                Log.i(
                    TAG,
                    "night length=${Duration.between(night.start, night.end).toMinutes()}m " +
                        "stages=${night.stages.size} writer=${night.origin}",
                )
                TYPES.mapNotNull(RecordRegistry::specOrNull).forEach { spec ->
                    if (spec.permission !in granted) {
                        Log.i(TAG, "  ${spec.type.simpleName} not granted")
                        return@forEach
                    }
                    val records = runCatching {
                        repository.readForChart(spec.type, TimeRangeFilter.between(night.start, night.end))
                    }.getOrElse {
                        Log.i(TAG, "  ${spec.type.simpleName} read failed: ${it.javaClass.simpleName}")
                        return@forEach
                    }
                    if (records.isEmpty()) Log.i(TAG, "  ${spec.type.simpleName} none")
                    records.groupBy { spec.originOf(it) }.forEach { (writer, group) ->
                        val times = group.flatMap { record -> spec.pointsOf(record).map { it.time } }
                            .filter { it >= night.start && it <= night.end }
                            .sorted()
                        val gaps = times.zipWithNext { a, b -> Duration.between(a, b).seconds }.sorted()
                        Log.i(
                            TAG,
                            "  ${spec.type.simpleName} writer=$writer readings=${times.size} " +
                                "gapMedian=${gaps.getOrNull(gaps.size / 2)}s gapMax=${gaps.lastOrNull()}s",
                        )
                    }
                }
            }
            Log.i(TAG, "done")
            finish()
        }
    }

    private companion object {
        const val TAG = "NightShape"
        val TYPES = listOf(
            "HeartRateRecord",
            "RespiratoryRateRecord",
            "OxygenSaturationRecord",
            "HeartRateVariabilityRmssdRecord",
            "SkinTemperatureRecord",
            "BodyTemperatureRecord",
        )
    }
}
