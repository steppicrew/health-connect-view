package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import java.time.LocalDate
import java.time.Period

/**
 * What counts as a type's personal best. Only for types where "more" or "less" is something a
 * person works towards; a weight or a blood pressure has no best, only a reading.
 */
enum class RecordKind {
    /** The day with the highest deduplicated total: steps, distance, floors. */
    MOST,

    /** The lowest day's value: resting heart rate, where lower reads as fitter. */
    LOWEST,

    /** The highest single reading: VO2 max, measured now and then rather than summed. */
    HIGHEST,

    /** The longest exercise session. */
    LONGEST,
}

/**
 * A type's best over the year before today, with the day it was set. [value] is in the type's
 * shown unit, or seconds for [RecordKind.LONGEST].
 */
data class PersonalRecord(val kind: RecordKind, val value: Double, val date: LocalDate)

/**
 * The highest (or with [lowest], the lowest) of [values], and its day.
 *
 * A tie goes to the latest day: equalling a best again is the news, not when it was first set.
 * Zero and below are left out -- an empty day aggregates to nothing or zero, and a resting rate
 * of 0 would otherwise always be the "lowest".
 */
fun bestDay(values: Map<LocalDate, Double>, lowest: Boolean): Pair<LocalDate, Double>? =
    values.entries
        .filter { it.value > 0.0 }
        .sortedByDescending { it.key }
        .let { entries -> if (lowest) entries.minByOrNull { it.value } else entries.maxByOrNull { it.value } }
        ?.let { it.key to it.value }

/**
 * [spec]'s personal record over the [RECORD_DAYS] days before [today], or null where the type
 * has none or nothing was recorded.
 *
 * Today is left out: its total is still running, and aggregated to midnight Health Connect adds
 * energy for hours still to come (CLAUDE.md). Without the history permission the platform
 * returns nothing older than 30 days, so the search quietly covers those; the explanation says
 * so rather than the title.
 *
 * Daily figures come from aggregation, which applies Health Connect's priority between writers,
 * so a day recorded by a phone and a watch counts once -- the record cannot be a day counted
 * twice. Sessions come from [sessionsIn], which already keeps one writer's copy of a workout.
 */
suspend fun HealthRepository.personalRecord(
    spec: RecordTypeSpec<*>,
    origins: Set<DataOrigin>,
    today: LocalDate,
): PersonalRecord? {
    val kind = spec.tile.personalRecord ?: return null
    val from = today.minusDays(RECORD_DAYS)
    val zone = HealthRepository.DEFAULT_ZONE
    return when (kind) {
        RecordKind.MOST, RecordKind.LOWEST -> {
            val metric = spec.aggregate ?: return null
            val daily = bucketedTotals(
                metric,
                TimeRangeFilter.between(from.atStartOfDay(), today.atStartOfDay()),
                Period.ofDays(1),
                origins,
            ).mapNotNull { bucket ->
                val value = bucket.result[metric]?.let { numericAggregate(it, metric) } ?: return@mapNotNull null
                bucket.startTime.toLocalDate() to value
            }.toMap()
            bestDay(daily, lowest = kind == RecordKind.LOWEST)
        }

        RecordKind.HIGHEST -> {
            val readings = readForChart(
                spec.type,
                TimeRangeFilter.between(from.atStartOfDay(zone).toInstant(), today.atStartOfDay(zone).toInstant()),
                origins = origins,
            ).flatMap { spec.pointsOf(it) }
            val byDay = readings
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .mapValues { (_, ofDay) -> ofDay.maxOf { it.value } }
            bestDay(byDay, lowest = false)
        }

        RecordKind.LONGEST -> {
            val start = from.atStartOfDay(zone).toInstant()
            val end = today.atStartOfDay(zone).toInstant()
            // Credited like the lists credit a session: by the day it ended on.
            val sessions = sessionsIn(start, end, setOf(Session.Kind.EXERCISE))
                .filter { !it.end.isBefore(start) && it.end.isBefore(end) }
            longestMoving(sessions)?.let { longest ->
                bestDay(
                    mapOf(longest.end.atZone(zone).toLocalDate() to longest.counted.seconds.toDouble()),
                    lowest = false,
                )
            }
        }
    }?.let { (date, value) -> PersonalRecord(kind, value, date) }
}

/** How far back a record is searched: a year, like the longest streak. */
const val RECORD_DAYS = 365L

/**
 * The workout with the longest moving time, reading as few of them as it can.
 *
 * Moving time is never longer than a workout's length, so the workouts are tried longest
 * first and the search stops at the first one too short to beat the best found. On the phone
 * the 18.09 ride -- 7h 49m long, 2h 3m moving -- was the longest by length and not by moving
 * time, which a year of reads would have found the slow way: one or two hundred workouts.
 */
suspend fun HealthRepository.longestMoving(sessions: List<Session>): Session? {
    var best: Session? = null
    for (session in sessions.sortedByDescending { it.duration }) {
        val leader = best
        if (leader != null && session.duration <= leader.counted) break
        val read = withMovement(listOf(session)).first()
        if (leader == null || read.counted > leader.counted) best = read
    }
    return best
}
