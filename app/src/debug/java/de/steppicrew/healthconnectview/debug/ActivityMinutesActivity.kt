package de.steppicrew.healthconnectview.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.observedMaxHeartRate
import de.steppicrew.healthconnectview.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Debug-only. Measures what an estimate of WHO active minutes from all-day heart rate would run
 * into (`docs/FEATURE-IDEAS.md`, "Active minutes against the WHO guideline"): how densely each
 * writer samples outside workouts, how much of the waking day that covers at a few gap caps,
 * how many of the minutes over the threshold are lone minutes or minutes without steps, and how
 * long the read takes.
 *
 * Logs shares, gaps and timings only -- never a reading, a threshold, a minute count or a
 * timestamp. A share of minutes is rounded to whole percent, as the other probes round
 * percentiles.
 *
 *   adb shell am start -n <pkg>/de.steppicrew.healthconnectview.debug.ActivityMinutesActivity \
 *       --ei days 7
 */
class ActivityMinutesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HealthRepository(this)
        val days = intent?.getIntExtra("days", DEFAULT_DAYS)?.coerceIn(1, 28) ?: DEFAULT_DAYS

        // Not finished early: reads fail once the activity backgrounds (CLAUDE.md).
        lifecycleScope.launch {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            val first = today.minusDays(days - 1L)
            val start = first.atStartOfDay(zone).toInstant()
            val end = Instant.now()
            val range = TimeRangeFilter.between(start, end)
            val minutes = Duration.between(start, end).toMinutes().toInt()

            val max = SettingsStore(this@ActivityMinutesActivity).settings.first().maxHeartRate
                ?: repository.observedMaxHeartRate(today)
            val resting = runCatching {
                repository.read(
                    RestingHeartRateRecord::class,
                    TimeRangeFilter.between(today.minusDays(30).atStartOfDay(zone).toInstant(), end),
                ).map { it.beatsPerMinute }.sorted().let { it.getOrNull(it.size / 2) }
            }.getOrNull()
            if (max == null) {
                Log.i(TAG, "no maximum heart rate (settings or a year of data); stopping")
                finish()
                return@launch
            }
            val (moderate, vigorous) = if (resting != null && resting < max) {
                resting + RESERVE_MODERATE * (max - resting) to resting + RESERVE_VIGOROUS * (max - resting)
            } else {
                MAX_MODERATE * max to MAX_VIGOROUS * max
            }
            Log.i(TAG, "days=$days thresholds=${if (resting != null) "heart-rate reserve" else "share of max"}")

            val readStarted = System.nanoTime()
            val samples = mutableMapOf<String, MutableList<Pair<Instant, Long>>>()
            var records = 0
            runCatching {
                repository.forEachPage(HeartRateRecord::class, range) { page ->
                    records += page.size
                    page.forEach { record ->
                        val list = samples.getOrPut(record.metadata.dataOrigin.packageName) { mutableListOf() }
                        record.samples.forEach { if (it.time in start..end) list += it.time to it.beatsPerMinute }
                    }
                }
            }.onFailure { Log.w(TAG, "heart-rate read threw: $it") }
            val readMs = (System.nanoTime() - readStarted) / 1_000_000
            Log.i(TAG, "heart rate: records=$records samples=${samples.values.sumOf { it.size }} readMs=$readMs")

            val inSession = BooleanArray(minutes)
            runCatching { repository.read(ExerciseSessionRecord::class, range) }.getOrDefault(emptyList())
                .forEach { mark(inSession, start, it.startTime, it.endTime) }
            val stepping = BooleanArray(minutes)
            val stepsStarted = System.nanoTime()
            var longStepRecords = 0
            runCatching {
                repository.forEachPage(StepsRecord::class, range) { page ->
                    page.forEach {
                        // A record over a quarter hour says nothing about any one minute in it.
                        if (Duration.between(it.startTime, it.endTime) > STEP_RECORD_LONGEST) {
                            longStepRecords++
                        } else {
                            mark(stepping, start, it.startTime.minusSeconds(60), it.endTime.plusSeconds(60))
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "steps read threw: $it") }
            Log.i(
                TAG,
                "steps readMs=${(System.nanoTime() - stepsStarted) / 1_000_000} " +
                    "minutesWithSteps=${pct(stepping.count { it }, minutes)}% skippedLongRecords=$longStepRecords",
            )

            // Distance in short records: movement without steps (a ride) still leaves it.
            val moving = BooleanArray(minutes)
            runCatching {
                repository.forEachPage(DistanceRecord::class, range) { page ->
                    page.filter { Duration.between(it.startTime, it.endTime) <= STEP_RECORD_LONGEST }
                        .forEach { mark(moving, start, it.startTime, it.endTime) }
                }
            }.onFailure { Log.w(TAG, "distance read threw: $it") }
            val sessionMinutes = inSession.indices.filter { inSession[it] }

            val waking = BooleanArray(minutes) { minute ->
                start.plusSeconds(minute * 60L).atZone(zone).hour in WAKING_HOURS
            }
            samples.forEach { (writer, list) ->
                list.sortBy { it.first }
                val outside = list.filter { !inSession.getOrElse(minuteOf(start, it.first)) { false } }
                val gaps = outside.zipWithNext { a, b -> Duration.between(a.first, b.first).seconds }
                    .filter { it > 0 }.sorted()
                Log.i(
                    TAG,
                    "writer=$writer samples=${list.size} outsideWorkouts=${outside.size} gaps outside " +
                        "workouts: median=${gaps.getOrNull(gaps.size / 2)}s p90=${gaps.getOrNull(gaps.size * 9 / 10)}s",
                )
                CAPS.forEach { cap ->
                    val level = levels(list, start, minutes, cap, moderate, vigorous)
                    val covered = level.indices.count { level[it] >= 0 }
                    val coveredWaking = level.indices.count { level[it] >= 0 && waking[it] }
                    val active = level.indices.filter { level[it] > 0 }
                    val outsideActive = active.filter { !inSession[it] }
                    val lone = outsideActive.count { m -> level.getOrElse(m - 1) { 0 } <= 0 && level.getOrElse(m + 1) { 0 } <= 0 }
                    val still = outsideActive.count { !stepping[it] }
                    Log.i(
                        TAG,
                        "  cap=${cap.seconds}s covered=${pct(covered, minutes)}% " +
                            "wakingCovered=${pct(coveredWaking, waking.count { it })}% " +
                            "activeOfCovered=${pct(active.size, covered)}% " +
                            "vigorousOfActive=${pct(active.count { level[it] == 2 }, active.size)}% " +
                            "inWorkouts=${pct(active.size - outsideActive.size, active.size)}% " +
                            "outsideLone=${pct(lone, outsideActive.size)}% " +
                            "outsideWithoutSteps=${pct(still, outsideActive.size)}%",
                    )
                }
                // The stepless active minutes outside workouts as bouts (a gap of up to two
                // minutes joins them): a long bout with distance is a ride, short scattered
                // ones are a raised pulse at rest. Length and nearness in buckets only.
                val level = levels(list, start, minutes, CAPS.last(), moderate, vigorous)
                val stillMinutes = level.indices.filter { level[it] > 0 && !inSession[it] && !stepping[it] }
                val bouts = mutableListOf<IntRange>()
                stillMinutes.forEach { m ->
                    val last = bouts.lastOrNull()
                    if (last != null && m - last.last <= 3) bouts[bouts.size - 1] = last.first..m else bouts += m..m
                }
                bouts.forEach { bout ->
                    val date = start.plusSeconds(bout.first * 60L).atZone(zone).toLocalDate()
                    val length = bout.last - bout.first + 1
                    val near = sessionMinutes.minOfOrNull { minOf(kotlin.math.abs(it - bout.first), kotlin.math.abs(it - bout.last)) }
                    Log.i(
                        TAG,
                        "  stepless bout date=$date length=${bucket(length, listOf(5, 20, 60))}min " +
                            "fromWorkout=${near?.let { bucket(it, listOf(15, 60, 240)) } ?: "none"}min " +
                            "withDistance=${pct(bout.count { moving[it] }, length)}%",
                    )
                }
            }
            Log.i(TAG, "done")
            finish()
        }
    }

    /**
     * One level per minute: -1 not covered, 0 below moderate, 1 moderate, 2 vigorous. Each
     * reading counts until the next for at most [cap]; a minute takes the level that held for
     * most of its covered seconds, and needs at least half of it covered.
     */
    private fun levels(
        list: List<Pair<Instant, Long>>,
        start: Instant,
        minutes: Int,
        cap: Duration,
        moderate: Double,
        vigorous: Double,
    ): IntArray {
        val seconds = Array(minutes) { IntArray(3) }
        list.zipWithNext { a, b ->
            val level = when {
                a.second >= vigorous -> 2
                a.second >= moderate -> 1
                else -> 0
            }
            var from = Duration.between(start, a.first).seconds
            val until = from + minOf(Duration.between(a.first, b.first).seconds, cap.seconds)
            while (from < until) {
                val minute = (from / 60).toInt()
                if (minute >= minutes) break
                val next = minOf(until, (minute + 1) * 60L)
                seconds[minute][level] += (next - from).toInt()
                from = next
            }
        }
        return IntArray(minutes) { minute ->
            val s = seconds[minute]
            if (s.sum() < 30) -1 else s.indices.maxBy { s[it] }
        }
    }

    private fun mark(minutes: BooleanArray, start: Instant, from: Instant, until: Instant) {
        val first = minuteOf(start, from).coerceAtLeast(0)
        val last = minuteOf(start, until).coerceAtMost(minutes.size - 1)
        for (minute in first..last) minutes[minute] = true
    }

    private fun minuteOf(start: Instant, time: Instant): Int = (Duration.between(start, time).seconds / 60).toInt()

    /** "<5", "5-20", ">60" and so on: which of [edges] a value falls between. */
    private fun bucket(value: Int, edges: List<Int>): String {
        val upper = edges.indexOfFirst { value < it }
        return when (upper) {
            0 -> "<${edges.first()}"
            -1 -> ">=${edges.last()}"
            else -> "${edges[upper - 1]}-${edges[upper]}"
        }
    }

    private fun pct(part: Int, whole: Int): Int = if (whole == 0) 0 else (100.0 * part / whole).roundToInt()

    private companion object {
        const val TAG = "ActivityMinutes"
        const val DEFAULT_DAYS = 7
        const val RESERVE_MODERATE = 0.4
        const val RESERVE_VIGOROUS = 0.6
        const val MAX_MODERATE = 0.64
        const val MAX_VIGOROUS = 0.77
        val WAKING_HOURS = 7..22
        val CAPS: List<Duration> = listOf(Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(5))
        val STEP_RECORD_LONGEST: Duration = Duration.ofMinutes(15)
    }
}
