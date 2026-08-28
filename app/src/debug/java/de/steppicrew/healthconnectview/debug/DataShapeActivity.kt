package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.TimeRange
import de.steppicrew.healthconnectview.registry.RecordRegistry
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * Debug-only. Reports the *shape* of the data on this device so a realistic synthetic
 * dataset can be built for the emulator, without any real health data leaving the phone.
 *
 * What it logs, per type: how many records exist, how many writing apps, how many records a
 * day, the typical gap between them, and the rounded 10th/50th/90th percentile of the values.
 * Percentiles are rounded to two significant figures, so they describe a plausible range
 * rather than any actual reading.
 *
 * What it never logs: individual values, timestamps, or record ids.
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.DataShapeActivity
 */
class DataShapeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HealthRepository(this)

        lifecycleScope.launch {
            val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
            val range = TimeRange.MONTH
            val zone = ZoneId.systemDefault()

            RecordRegistry.all
                .filter { it.permission in granted }
                .forEach { spec ->
                    val name = spec.type.simpleName ?: return@forEach
                    val records = runCatching { repository.read(spec.type, range.filter()) }
                        .getOrDefault(emptyList())
                    if (records.isEmpty()) return@forEach

                    val writers = records.map { it.metadata.dataOrigin.packageName }.distinct()
                    val perDay = records
                        .groupBy { spec.timeOf(it).atZone(zone).toLocalDate() }
                        .map { (_, sameDay) -> sameDay.size }

                    val values = records.flatMap { spec.pointsOf(it) }.map { it.value }.sorted()
                    val gaps = records
                        .map { spec.timeOf(it) }
                        .sorted()
                        .zipWithNext { a, b -> Duration.between(a, b).toMinutes() }
                        .filter { it > 0 }

                    Log.i(
                        TAG,
                        "$name writers=${writers.size} records=${records.size} " +
                            "daysCovered=${perDay.size} recordsPerDay=${perDay.median()} " +
                            "gapMinutes=${gaps.median()} " +
                            "valueP10=${values.percentile(0.10).round2sf()} " +
                            "valueP50=${values.percentile(0.50).round2sf()} " +
                            "valueP90=${values.percentile(0.90).round2sf()} " +
                            "samplesPerRecord=${(values.size.toDouble() / records.size).round2sf()}",
                    )
                }
            Log.i(TAG, "done")
            finish()
        }
    }

    private fun List<Int>.median(): Int =
        if (isEmpty()) 0 else sorted()[size / 2]

    @JvmName("medianLong")
    private fun List<Long>.median(): Long =
        if (isEmpty()) 0L else sorted()[size / 2]

    private fun List<Double>.percentile(p: Double): Double =
        if (isEmpty()) 0.0 else this[((size - 1) * p).roundToLong().toInt()]

    /** Two significant figures, so the number describes a range rather than a real reading. */
    private fun Double.round2sf(): Double {
        if (this == 0.0 || !isFinite()) return 0.0
        val magnitude = Math.pow(10.0, Math.floor(Math.log10(Math.abs(this))) - 1)
        return Math.round(this / magnitude) * magnitude
    }

    private companion object {
        const val TAG = "DataShape"
    }
}
