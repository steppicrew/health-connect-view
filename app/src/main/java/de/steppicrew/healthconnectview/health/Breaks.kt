package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.registry.RecordRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * A stretch inside a workout where nothing moved, inferred from missing readings.
 *
 * No writer stores a pause: on the phone, a ride on 18.09.2026 ran 09:37-17:27 with a break of
 * more than five hours, and neither copy of the session carried a single segment. What the
 * break did leave is a hole in the movement data -- Health Sync's speed and distance stopped
 * from 10:41 to 16:22 -- while heart rate went on, at resting level, from the watch's all-day
 * readings. A break is therefore read from the readings that only exist while moving.
 */
data class Break(val start: Instant, val end: Instant) {
    val duration: Duration get() = Duration.between(start, end)
}

/**
 * A stretch a movement reading covers: a speed sample's instant, a distance record's whole
 * interval. Only time no reading covers can be a stop -- an app writing distance in 30-minute
 * records has no hole between the start of one and the start of the next, which read as a
 * break of nearly every half hour of a seeded run until intervals counted whole.
 */
data class Covered(val start: Instant, val end: Instant = start)

/** Each gap longer than [minGap] between consecutive [times], as a break; see [gapsIn]. */
fun breaksIn(times: List<Instant>, minGap: Duration = MIN_BREAK): List<Break> =
    gapsIn(times.map(::Covered), minGap)

/**
 * Each stretch longer than [minGap] that none of [covered] reaches, as a break.
 *
 * Only gaps *between* readings count. Nothing before the first or after the last reading is
 * a break: a session whose writer started recording a minute late is not paused, and a missing
 * edge says too little to tell the two apart.
 */
fun gapsIn(covered: List<Covered>, minGap: Duration = MIN_BREAK): List<Break> =
    uncovered(covered).filter { it.duration > minGap }

/** Every stretch between [covered] that none of them reaches, in order, of any length. */
private fun uncovered(covered: List<Covered>): List<Break> {
    val sorted = covered.sortedBy { it.start }
    if (sorted.isEmpty()) return emptyList()
    val gaps = mutableListOf<Break>()
    var reach = sorted.first().end
    sorted.drop(1).forEach { next ->
        if (next.start > reach) gaps += Break(reach, next.start)
        if (next.end > reach) reach = next.end
    }
    return gaps
}

/**
 * How a workout's time divides into moving and stopped, from its movement readings.
 *
 * Two thresholds, for two questions. Every gap over [MIN_STOP] counts against [moving]: a watch
 * that pauses itself at each red light leaves a hole there, and on the phone Health Sync's
 * per-second speed for the 18.09 ride had seven of 24 s to 1:52 min. Only gaps over [MIN_BREAK]
 * are [breaks], listed and left out of the figures: a halt at a junction is part of the ride,
 * five hours at a café are not.
 *
 * A writer that goes on recording while standing -- speed 0 rather than no reading -- leaves
 * no hole, so its stops count as moving. Reading stops from zero speed is not done: a slow
 * climb on a mountain bike reads close to zero too.
 */
data class Movement(val breaks: List<Break>, val moving: Duration)

/** [movementIn] for instant readings alone. */
@JvmName("movementInTimes")
fun movementIn(start: Instant, end: Instant, times: List<Instant>): Movement? =
    movementIn(start, end, times.map(::Covered))

/**
 * [Movement] over [start]..[end] from what the movement readings in it cover. Null where
 * there are fewer than two readings, which say nothing about stops.
 */
fun movementIn(start: Instant, end: Instant, covered: List<Covered>): Movement? {
    if (covered.size < 2) return null
    // Relative to how often this writer records: one saving a reading every 30 s would
    // otherwise read as stopped between every two of them. Records that meet count as a gap
    // of nothing.
    val sorted = covered.sortedBy { it.start }
    val gaps = sorted.zipWithNext { a, b -> Duration.between(a.end, b.start).coerceAtLeast(Duration.ZERO) }.sorted()
    val usual = gaps[gaps.size / 2]
    val stopped = gapsIn(covered, maxOf(MIN_STOP, usual.multipliedBy(STOP_FACTOR))).fold(Duration.ZERO) { total, gap -> total + gap.duration }
    return Movement(
        breaks = gapsIn(covered),
        moving = Duration.between(start, end).minus(stopped).coerceAtLeast(Duration.ZERO),
    )
}

private fun Duration.coerceAtLeast(floor: Duration): Duration = if (this < floor) floor else this

/**
 * The parts of [start]..[end] outside [breaks]: the time actually spent moving. The whole
 * window where there are none.
 */
fun activePieces(start: Instant, end: Instant, breaks: List<Break>): List<Pair<Instant, Instant>> {
    val pieces = mutableListOf<Pair<Instant, Instant>>()
    var from = start
    breaks.sortedBy { it.start }.forEach { pause ->
        val pauseStart = pause.start.coerceIn(start, end)
        if (pauseStart > from) pieces += from to pauseStart
        from = maxOf(from, pause.end.coerceIn(start, end))
    }
    if (end > from) pieces += from to end
    return pieces
}

/**
 * Pieces' figures combined into the session's, the way one aggregate over them would have:
 * a sum for a total, and for a mean each piece's mean weighted by how long the piece ran.
 *
 * Time-weighting is an approximation of the platform's sample-weighted mean, and a close one
 * for a series sampled at a steady rate through the session -- which is what heart rate, power
 * and speed are while moving. Pieces with no figure take no weight.
 */
fun combinePieces(values: List<Pair<Double, Duration>>, averaged: Boolean): Double? {
    if (values.isEmpty()) return null
    if (!averaged) return values.sumOf { it.first }
    val weight = values.sumOf { it.second.toMillis() }
    if (weight <= 0) return values.map { it.first }.average()
    return values.sumOf { (value, length) -> value * length.toMillis() } / weight
}

/**
 * The shortest gap read as a break. Long enough that a red light, a GPS dropout under a bridge
 * or a watch saving readings in batches (one every 2 minutes) does not break a ride in two;
 * short enough that a stop for a drink does.
 */
val MIN_BREAK: Duration = Duration.ofMinutes(5)

/**
 * The shortest gap that counts as standing still. Health Sync's speed comes every second while
 * moving, so 20 s without one is a stop rather than a missed sample.
 */
val MIN_STOP: Duration = Duration.ofSeconds(20)

/** For a writer recording less often than that, a stop is this many of its usual intervals. */
private const val STOP_FACTOR = 3L

/**
 * How an exercise session divides into moving, stopped and breaks, read from the movement data
 * recorded during it.
 *
 * Speed and distance are only written while moving, so a hole in them is a stop; heart rate is
 * not, since a watch goes on measuring it all day. The session's own writer is asked first --
 * its readings belong to the same recording -- and otherwise the writer with the most. Garmin's
 * own copy of the 18.09 ride, measured on the phone, was 121 minutes long from the right start:
 * its active time laid end to end, with its readings packed into that span. Mixing it with
 * Health Sync's real timeline would fill the hole.
 *
 * Another writer's distance is never used: a watch writes distance all day, from steps, so its
 * holes are wherever its wearer sat down -- an indoor ride with no speed of its own would be
 * "paused" by the walk to the bike. Speed is written only while a workout is recorded.
 *
 * Null for anything but a workout that [coversDistance], where a type is not granted, or where
 * nothing was recorded -- then no breaks are claimed and the session counts whole.
 */
suspend fun HealthRepository.movementDuring(session: Session): Movement? {
    if (session.kind != Session.Kind.EXERCISE || !coversDistance(session.exerciseType)) return null
    val granted = grantedPermissions()
    val window = TimeRangeFilter.between(session.start, session.end)
    // Speed first and distance only where it has none: one speed record holds a ride's
    // thousands of samples, where distance came as 4,224 records -- five pages to read
    // instead of one, for every workout on a page. The session's own writer comes before any
    // other in either type, for the reason above; another writer's speed is the last resort.
    var fallback: List<Covered>? = null
    MOVEMENT_TYPES.mapNotNull(RecordRegistry::specOrNull)
        .filter { it.permission in granted }
        .forEach { spec ->
            val records = try {
                readForChart(spec.type, window)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            val byWriter = records.groupBy { spec.originOf(it) }.mapValues { (_, group) ->
                group.flatMap { record ->
                    if (record is DistanceRecord) {
                        listOf(Covered(record.startTime, record.endTime))
                    } else {
                        spec.pointsOf(record).map { Covered(it.time) }.ifEmpty { listOf(Covered(spec.timeOf(record))) }
                    }
                }.filter { it.end >= session.start && it.start <= session.end }
            }
            byWriter[session.origin]?.let { own -> movementIn(session.start, session.end, own)?.let { return it } }
            if (fallback == null && spec.type.simpleName == SPEED) fallback = byWriter.values.maxByOrNull { it.size }
        }
    fallback?.let { return movementIn(session.start, session.end, it) }
    return null
}

/**
 * [sessions] with each workout's breaks and moving time read, so totals count time on the
 * move and bands leave the breaks out. Other kinds pass through as they are. [onFraction]
 * hears the share of sessions done: four weeks of workouts are some 55 reads.
 */
suspend fun HealthRepository.withMovement(
    sessions: List<Session>,
    onFraction: (Float) -> Unit = {},
): List<Session> = coroutineScope {
    val gate = Semaphore(CONCURRENT_MOVEMENT_READS)
    val done = AtomicInteger(0)
    sessions.map { session ->
        async {
            try {
                if (session.kind != Session.Kind.EXERCISE) return@async session
                val movement = gate.withPermit { movementDuring(session) } ?: return@async session
                session.copy(breaks = movement.breaks, moving = movement.moving)
            } finally {
                onFraction(done.incrementAndGet().toFloat() / sessions.size)
            }
        }
    }.awaitAll()
}

/** Written only while moving: their absence inside a workout is a stop. In the order asked. */
private val MOVEMENT_TYPES = listOf("SpeedRecord", "DistanceRecord")

/** The one movement type another writer's readings may stand in for the session's own. */
private const val SPEED = "SpeedRecord"

/** As for a session's other reads: Health Connect serves an app largely in turn. */
private const val CONCURRENT_MOVEMENT_READS = 4
