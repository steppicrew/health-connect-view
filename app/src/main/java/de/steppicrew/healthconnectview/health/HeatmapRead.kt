package de.steppicrew.healthconnectview.health

import android.util.Log
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.TileSpec
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * Shaded from zero: what adds up -- steps, a day's training. A level across its own range, and
 * a night too: from zero, 6 h and 8 h nights came out almost the same dark.
 */
fun countsFromZero(spec: RecordTypeSpec<*>): Boolean =
    spec.tile.cumulativeIntraday ||
        spec.tile.form == TileSpec.Form.SESSIONS && spec.tile.sessionKind == Session.Kind.EXERCISE

/** Whether a calendar of [spec] can be read on its own, without its chart: an aggregate or sessions. */
fun readsHeatmap(spec: RecordTypeSpec<*>): Boolean =
    spec.tile.form == TileSpec.Form.SESSIONS && spec.tile.sessionKind != null ||
        spec.aggregate != null && spec.tile.form != TileSpec.Form.SESSIONS

/**
 * The calendar's own figure, as the year's value face words it, from the days the grid shows:
 * a sum for what adds up, a mean for a level, a night's mean for sleep and the hours trained
 * for workouts -- hours where [spec] is sessions. Read off the grid, so it covers exactly its
 * days, a calendar year's too, which no chart window does. Null with nothing to combine.
 */
fun calendarHeadline(spec: RecordTypeSpec<*>, heatmap: YearHeatmap): Pair<Double, Double?>? {
    val values = heatmap.values.values.toList()
    val value = when (spec.tile.sessionKind.takeIf { spec.tile.form == TileSpec.Form.SESSIONS }) {
        null -> spec.combine(values)
        Session.Kind.SLEEP -> values.takeIf { it.isNotEmpty() }?.average()
        else -> values.takeIf { it.isNotEmpty() }?.sum()
    } ?: return null
    return value to spec.combine(heatmap.secondValues.values.toList())
}

/**
 * Hours a day from [sessions]: each night by the morning it ended on, workouts summed by the
 * day they began, moving time where it was read.
 */
fun sessionDays(sessions: List<Session>, kind: Session.Kind, zone: ZoneId): Map<LocalDate, Double> = when (kind) {
    Session.Kind.SLEEP -> nightsByMorning(sessions, zone)
        .mapValues { (_, night) -> Duration.between(night.start, night.end).toMinutes() / MINUTES_PER_HOUR }
    else -> sessions
        .filter { it.kind == kind }
        .groupBy { it.start.atZone(zone).toLocalDate() }
        .mapValues { (_, day) -> day.sumOf { (it.moving ?: Duration.between(it.start, it.end)).toMinutes() } / MINUTES_PER_HOUR }
}

/**
 * The calendar of [spec] for [first] through [last], read for days up to [through] -- a
 * calendar year shows its whole grid, but nothing after today is asked for. One daily
 * aggregate per day, deduplicated as every total is, in quarters; or the sessions themselves.
 * Null where no day has a value or the read failed.
 */
suspend fun HealthRepository.readHeatmap(
    spec: RecordTypeSpec<*>,
    first: LocalDate,
    last: LocalDate,
    origins: Set<DataOrigin>,
    through: LocalDate = last,
): YearHeatmap? = runCatching {
    val zone = HealthRepository.DEFAULT_ZONE
    val end = minOf(last, through).plusDays(1)
    val started = System.currentTimeMillis()
    val kind = spec.tile.sessionKind
    if (spec.tile.form == TileSpec.Form.SESSIONS && kind != null) {
        val sessions = sessionsIn(first.atStartOfDay(zone).toInstant(), end.atStartOfDay(zone).toInstant(), setOf(kind))
        return@runCatching yearHeatmap(sessionDays(sessions, kind, zone), first, last, fromZero = countsFromZero(spec))
    }
    val metric = spec.aggregate ?: return@runCatching null
    // Blood pressure's diastolic from the same buckets: a day is graded on both.
    val second = spec.secondaryAggregate
    // A stack's first part is the floor the day is built on -- the basal rate under total
    // calories -- and the shading starts at its mean day.
    val floorPart = spec.stackComponents.firstOrNull()
    val days = generateSequence(first) { it.plusDays(PIECE_DAYS) }.takeWhile { it < end }.toList().flatMap { from ->
        val to = minOf(from.plusDays(PIECE_DAYS), end)
        bucketedTotals(
            metric,
            TimeRangeFilter.between(from.atStartOfDay(), to.atStartOfDay()),
            Period.ofDays(1),
            origins,
            also = setOfNotNull(second, floorPart?.second),
        ).mapNotNull { bucket ->
            val value = bucket.result[metric]?.let { numericAggregate(it, metric) } ?: return@mapNotNull null
            val other = second?.let { bucket.result[it]?.let { v -> numericAggregate(v, it) } }
            val floor = floorPart?.second?.let { bucket.result[it]?.let { v -> numericAggregate(v, it) } }
            HeatDay(bucket.startTime.toLocalDate(), value, other, floor)
        }
    }
    Log.d(TAG, "${days.size} days of ${spec.type.simpleName} in ${System.currentTimeMillis() - started} ms")
    val floor = days.mapNotNull { it.floor }.takeIf { it.isNotEmpty() }?.average()
    yearHeatmap(days.associate { it.date to it.value }, first, last, fromZero = countsFromZero(spec), floor = floor)
        ?.let { map ->
            map.copy(
                secondValues = days.mapNotNull { day -> day.second?.let { day.date to it } }.toMap(),
                lowLabel = floorPart?.first?.takeIf { floor != null && map.low == floor },
            )
        }
}.getOrNull()

/** One day of a heatmap read: the value, a second one, and the floor beneath it. */
private class HeatDay(val date: LocalDate, val value: Double, val second: Double?, val floor: Double?)

/** Days per request: a quarter. */
private const val PIECE_DAYS = 92L
private const val MINUTES_PER_HOUR = 60.0
private const val TAG = "Heatmap"
