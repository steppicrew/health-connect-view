package de.steppicrew.healthconnectview.health

import java.time.Duration
import java.time.Instant

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
 * Each gap longer than [minGap] between consecutive [times], as a break.
 *
 * Only gaps *between* readings count. Nothing before the first or after the last reading is
 * a break: a session whose writer started recording a minute late is not paused, and a missing
 * edge says too little to tell the two apart.
 */
fun breaksIn(times: List<Instant>, minGap: Duration = MIN_BREAK): List<Break> =
    times.sorted()
        .zipWithNext()
        .filter { (a, b) -> Duration.between(a, b) > minGap }
        .map { (a, b) -> Break(a, b) }

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

/**
 * [Movement] over [start]..[end] from the times of the movement readings in it. Null where
 * there are fewer than two readings, which say nothing about stops.
 */
fun movementIn(start: Instant, end: Instant, times: List<Instant>): Movement? {
    if (times.size < 2) return null
    // Relative to how often this writer records: one saving a reading every 30 s would
    // otherwise read as stopped between every two of them.
    val gaps = times.sorted().zipWithNext { a, b -> Duration.between(a, b) }.sorted()
    val usual = gaps[gaps.size / 2]
    val stopped = breaksIn(times, maxOf(MIN_STOP, usual.multipliedBy(STOP_FACTOR))).fold(Duration.ZERO) { total, gap -> total + gap.duration }
    return Movement(
        breaks = breaksIn(times),
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
