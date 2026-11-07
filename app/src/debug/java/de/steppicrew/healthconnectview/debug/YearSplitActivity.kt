package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

/**
 * Debug-only. Times a year of totals read as one aggregateGroupByPeriod against the same year
 * through [HealthRepository.bucketedTotals], which asks for it in about monthly pieces
 * ([de.steppicrew.healthconnectview.health.aggregationPieces]), and checks that both give the
 * same buckets.
 *
 * On the owner's phone (07.11.2026) a year of daily distance took 19-23 s as one request, as
 * twelve monthly ones 5.4 s, and every other call waited behind the single request. Steps took
 * about a second either way.
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.YearSplitActivity
 *
 * Logs times, bucket counts, dates and relative differences -- never a value. Stays open until
 * done: Health Connect refuses aggregates once the activity is in the background.
 */
class YearSplitActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HealthRepository(this)
        val client = HealthConnectClient.getOrCreate(this)

        lifecycleScope.launch {
            val end = LocalDate.now()
            val start = end.minusDays(YEAR_DAYS)
            val range = TimeRangeFilter.between(start.atStartOfDay(), end.atStartOfDay())
            val cases = listOf(
                Triple("distance daily", DistanceRecord.DISTANCE_TOTAL, Period.ofDays(1)),
                Triple("distance weekly", DistanceRecord.DISTANCE_TOTAL, Period.ofWeeks(1)),
                Triple("steps daily", StepsRecord.COUNT_TOTAL, Period.ofDays(1)),
            )
            for ((name, metric, bucket) in cases) {
                val whole = timed("$name one request") {
                    client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(metric), range, bucket))
                }
                val split = timed("$name in pieces") { repository.bucketedTotals(metric, range, bucket) }
                Log.i(TAG, "$name buckets one=${whole.size} pieces=${split.size} ${agree(whole, split, metric)}")
            }
            Log.i(TAG, "done")
            finish()
        }
    }

    private suspend fun <T> timed(label: String, block: suspend () -> T): T {
        val started = System.currentTimeMillis()
        return block().also { Log.i(TAG, "$label ${System.currentTimeMillis() - started} ms") }
    }

    /**
     * How the two ways differ, day by day: dates, counts and relative differences only, so no
     * value is logged.
     */
    private fun agree(
        a: List<AggregationResultGroupedByPeriod>,
        b: List<AggregationResultGroupedByPeriod>,
        metric: AggregateMetric<*>,
    ): String {
        fun byDay(list: List<AggregationResultGroupedByPeriod>) =
            list.associate { it.startTime.toLocalDate() to it.result[metric]?.let(::number) }
        val left = byDay(a)
        val right = byDay(b)
        val days = (left.keys + right.keys).sorted()
        val onlyOne = days.filter { (left[it] == null) != (right[it] == null) }
        val differing = days.filter { left[it] != null && right[it] != null && left[it] != right[it] }
        val worst = differing.maxOfOrNull { day ->
            val x = left.getValue(day)!!
            val y = right.getValue(day)!!
            kotlin.math.abs(x - y) / maxOf(kotlin.math.abs(x), kotlin.math.abs(y))
        }
        return "days=${days.size} keysEqual=${left.keys == right.keys} onlyOneSide=${onlyOne.size} " +
            "firstOnlyOne=${onlyOne.take(3)} differing=${differing.size} firstDiffering=${differing.take(3)} " +
            "worstRelative=${worst?.let { "%.4f".format(it) }} startTimes=${a.firstOrNull()?.startTime?.toLocalTime()}/" +
            "${b.firstOrNull()?.startTime?.toLocalTime()}"
    }

    private fun number(value: Any): Double = when (value) {
        is Long -> value.toDouble()
        is androidx.health.connect.client.units.Length -> value.inMeters
        else -> Double.NaN
    }

    private companion object {
        const val TAG = "YearSplit"
        const val YEAR_DAYS = 365L
    }
}
