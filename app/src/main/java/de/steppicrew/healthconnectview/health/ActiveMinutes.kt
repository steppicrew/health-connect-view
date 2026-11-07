package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.util.catching
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Minutes of moderate and vigorous activity a day, estimated from heart rate, against the WHO
 * guideline of 150 moderate minutes a week, a vigorous minute counting twice. Health Connect
 * has no type for them, so no writer can pass them on (`docs/FEATURE-IDEAS.md`).
 *
 * Measured on the phone, 07.11.2026, before any of it was built: one writer's readings are
 * enough, and the sparse one agrees with the dense one; and a pulse raised while sitting -- a
 * travel day -- put a dozen bouts over the threshold with no step taken. So a minute outside a
 * workout counts only where steps or distance were recorded within a minute of it.
 */
data class ActiveMinutes(
    /** One per day of the window, oldest first. */
    val days: List<ActiveDay>,
    val limits: ActiveLimits,
    /** The app whose readings were used. */
    val writer: String,
    /** Minutes over the threshold outside a workout with no movement recorded near them. */
    val leftOut: Int,
    /** Share of the window's minutes the readings cover, 0 to 1. */
    val coverage: Double,
) {
    val moderate: Int get() = days.sumOf { it.moderate }
    val vigorous: Int get() = days.sumOf { it.vigorous }

    /** What is set against [WHO_WEEKLY]: moderate minutes plus twice the vigorous ones. */
    val weighted: Int get() = days.sumOf { it.weighted }
}

data class ActiveDay(val date: LocalDate, val moderate: Int, val vigorous: Int) {
    val weighted: Int get() = moderate + 2 * vigorous
}

/**
 * The pulse from which a minute counts as moderate and as vigorous. From the heart-rate
 * reserve where a resting rate is known -- 40 % and 60 % of the span from resting to maximum
 * -- otherwise from shares of the maximum alone, 64 % and 77 %.
 */
data class ActiveLimits(val moderate: Double, val vigorous: Double, val fromReserve: Boolean)

fun activeLimits(max: Int, resting: Int?): ActiveLimits =
    if (resting != null && resting in 1 until max) {
        val reserve = max - resting
        ActiveLimits(resting + RESERVE_MODERATE * reserve, resting + RESERVE_VIGOROUS * reserve, fromReserve = true)
    } else {
        ActiveLimits(MAX_MODERATE * max, MAX_VIGOROUS * max, fromReserve = false)
    }

/**
 * How long one reading counts for: half again the writer's usual interval, between one and
 * five minutes. One constant would not do -- at a one-minute cap a writer posting every two
 * minutes covered half the day, every active minute standing alone.
 */
fun sampleCap(points: List<Point>): Duration {
    val gaps = points.zipWithNext { a, b -> Duration.between(a.time, b.time).seconds }.filter { it > 0 }.sorted()
    val median = gaps.getOrNull(gaps.size / 2) ?: return SHORTEST_CAP
    return Duration.ofSeconds(median * 3 / 2).coerceIn(SHORTEST_CAP, LONGEST_CAP)
}

/**
 * The days from [first] to [last], each minute taking the level its readings held for most of
 * it, and needing half of it covered. [points] are one writer's, sorted by time. A minute over
 * the threshold counts where it lies in one of [workouts], or within a minute of [movement];
 * the others are [ActiveMinutes.leftOut].
 */
fun activeMinutes(
    points: List<Point>,
    first: LocalDate,
    last: LocalDate,
    zone: ZoneId,
    limits: ActiveLimits,
    workouts: List<Pair<Instant, Instant>>,
    movement: List<Pair<Instant, Instant>>,
    writer: String,
): ActiveMinutes {
    val start = first.atStartOfDay(zone).toInstant()
    val end = last.plusDays(1).atStartOfDay(zone).toInstant()
    val minutes = Duration.between(start, end).toMinutes().toInt()
    val cap = sampleCap(points).seconds
    val seconds = Array(LEVELS) { IntArray(minutes) }
    points.zipWithNext { a, b ->
        val level = when {
            a.value >= limits.vigorous -> VIGOROUS
            a.value >= limits.moderate -> MODERATE
            else -> RESTING
        }
        var from = Duration.between(start, a.time).seconds
        val until = from + minOf(Duration.between(a.time, b.time).seconds, cap)
        while (from < until) {
            if (from < 0) {
                from = minOf(until, 0)
                continue
            }
            val minute = (from / 60).toInt()
            if (minute >= minutes) break
            val next = minOf(until, (minute + 1) * 60L)
            seconds[level][minute] += (next - from).toInt()
            from = next
        }
    }
    val inWorkout = marked(minutes, start, workouts, Duration.ZERO)
    val moving = marked(minutes, start, movement, Duration.ofMinutes(1))
    val moderate = IntArray(minutes)
    val vigorous = IntArray(minutes)
    var covered = 0
    var leftOut = 0
    for (minute in 0 until minutes) {
        val total = seconds.sumOf { it[minute] }
        if (total < 30) continue
        covered++
        val level = (0 until LEVELS).maxBy { seconds[it][minute] }
        if (level == RESTING) continue
        if (!inWorkout[minute] && !moving[minute]) {
            leftOut++
            continue
        }
        if (level == VIGOROUS) vigorous[minute] = 1 else moderate[minute] = 1
    }
    val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.map { date ->
        val from = Duration.between(start, date.atStartOfDay(zone).toInstant()).toMinutes().toInt()
        val to = Duration.between(start, date.plusDays(1).atStartOfDay(zone).toInstant()).toMinutes().toInt()
        ActiveDay(date, (from until to).sumOf { moderate[it] }, (from until to).sumOf { vigorous[it] })
    }.toList()
    return ActiveMinutes(days, limits, writer, leftOut, if (minutes == 0) 0.0 else covered.toDouble() / minutes)
}

/** The minutes any of [spans] touches, widened by [margin] either side. */
private fun marked(minutes: Int, start: Instant, spans: List<Pair<Instant, Instant>>, margin: Duration): BooleanArray {
    val marks = BooleanArray(minutes)
    spans.forEach { (from, until) ->
        val a = (Duration.between(start, from.minus(margin)).seconds / 60).toInt().coerceAtLeast(0)
        // The end is exclusive: a span ending on the minute does not touch the next one.
        val b = (Duration.between(start, until.plus(margin).minusNanos(1)).seconds / 60).toInt().coerceAtMost(minutes - 1)
        for (minute in a..b) marks[minute] = true
    }
    return marks
}

/**
 * The days from [first] to [last] read from Health Connect, or null where there is no
 * maximum heart rate to measure against or no reading in the window.
 *
 * One writer only, [preferred] if it wrote heart rate on the last day read, otherwise the one
 * whose readings covered most of it, the sparser of two that cover about as much: reading every
 * writer took 18 s for four weeks on the phone, and a second writer adds nothing. Steps and
 * distance come from every writer, since only their presence is asked; a record longer than a
 * quarter of an hour says nothing about any minute in it and is passed over.
 */
suspend fun HealthRepository.readActiveMinutes(
    first: LocalDate,
    last: LocalDate,
    max: Int?,
    preferred: String?,
    zone: ZoneId = HealthRepository.DEFAULT_ZONE,
): ActiveMinutes? {
    val chosenMax = max ?: observedMaxHeartRate(last) ?: return null
    val start = first.atStartOfDay(zone).toInstant()
    val end = minOf(last.plusDays(1).atStartOfDay(zone).toInstant(), Instant.now())
    if (!end.isAfter(start)) return null
    val writer = activeWriter(end, preferred) ?: return null
    val range = TimeRangeFilter.between(start, end)

    val restingFrom = last.minusDays(RESTING_DAYS).atStartOfDay(zone).toInstant()
    val resting = read(RestingHeartRateRecord::class, TimeRangeFilter.between(restingFrom, end))
        .map { it.beatsPerMinute.toInt() }.sorted().let { it.getOrNull(it.size / 2) }

    val points = mutableListOf<Point>()
    forEachPage(HeartRateRecord::class, range, setOf(DataOrigin(writer))) { page ->
        page.forEach { record ->
            record.samples.forEach { if (it.time >= start && it.time < end) points += Point(it.time, it.beatsPerMinute.toDouble()) }
        }
    }
    if (points.isEmpty()) return null
    points.sortBy { it.time }

    val workouts = read(ExerciseSessionRecord::class, range).map { it.startTime to it.endTime }
    val movement = mutableListOf<Pair<Instant, Instant>>()
    fun short(from: Instant, until: Instant) = Duration.between(from, until) <= LONGEST_MOVEMENT
    catching {
        forEachPage(StepsRecord::class, range) { page ->
            page.filter { short(it.startTime, it.endTime) }.forEach { movement += it.startTime to it.endTime }
        }
    }
    catching {
        forEachPage(DistanceRecord::class, range) { page ->
            page.filter { short(it.startTime, it.endTime) }.forEach { movement += it.startTime to it.endTime }
        }
    }
    return activeMinutes(points, first, last, zone, activeLimits(chosenMax, resting), workouts, movement, writer)
}

/** The writer to read; see [readActiveMinutes]. From the day before [end]. */
private suspend fun HealthRepository.activeWriter(end: Instant, preferred: String?): String? {
    val byWriter = mutableMapOf<String, MutableList<Instant>>()
    forEachPage(HeartRateRecord::class, TimeRangeFilter.between(end.minus(Duration.ofDays(1)), end)) { page ->
        page.forEach { record ->
            byWriter.getOrPut(record.metadata.dataOrigin.packageName) { mutableListOf() } += record.samples.map { it.time }
        }
    }
    if (preferred != null && preferred in byWriter) return preferred
    return chooseWriter(byWriter)
}

/**
 * The writer whose readings cover most minutes, and of those within a twentieth of the best
 * the one with the fewest readings: it is the cheapest to read over four weeks.
 */
fun chooseWriter(byWriter: Map<String, List<Instant>>): String? {
    val covered = byWriter.mapValues { (_, times) ->
        val sorted = times.sorted()
        val cap = sampleCap(sorted.map { Point(it, 0.0) }).seconds
        sorted.zipWithNext { a, b -> minOf(Duration.between(a, b).seconds, cap) }.sum()
    }
    val best = covered.values.maxOrNull()?.takeIf { it > 0 } ?: return null
    return covered.filterValues { it >= best * 19 / 20 }.keys.minBy { byWriter.getValue(it).size }
}

/** The WHO guideline: 150 moderate minutes a week, or 75 vigorous. */
const val WHO_WEEKLY = 150

private const val RESTING = 0
private const val MODERATE = 1
private const val VIGOROUS = 2
private const val LEVELS = 3
private const val RESERVE_MODERATE = 0.4
private const val RESERVE_VIGOROUS = 0.6
private const val MAX_MODERATE = 0.64
private const val MAX_VIGOROUS = 0.77
private const val RESTING_DAYS = 30L
private val SHORTEST_CAP: Duration = Duration.ofMinutes(1)
private val LONGEST_CAP: Duration = Duration.ofMinutes(5)
private val LONGEST_MOVEMENT: Duration = Duration.ofMinutes(15)
