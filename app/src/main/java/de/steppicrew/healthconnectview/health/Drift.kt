package de.steppicrew.healthconnectview.health

import de.steppicrew.healthconnectview.registry.Point
import java.time.Duration
import java.time.Instant

/**
 * A workout's heart rate against its speed, first half of the moving time against the second:
 * the two side by side, no grade and no percentage.
 *
 * Heartbeats per kilometre carry the comparison in one figure: the same speed with more beats
 * to the kilometre is the heart working harder for it. Warmth, lost fluid and tiredness do
 * that, and so do a climb or a headwind, which these figures cannot tell apart; the warm-up
 * falls into the first half. Only for workouts with a speed recorded through them -- on the
 * phone the indoor bike and the power meter write one summary value per workout, too little
 * to halve.
 */
data class Drift(val first: Half, val second: Half) {
    data class Half(
        /** The mean of the heart-rate readings in this half, beats per minute. */
        val heartRate: Double,
        /** The mean of the speed readings in this half, metres per second. */
        val speed: Double,
    ) {
        /** Heartbeats per kilometre: beats a minute over kilometres a minute. */
        val beatsPerKm: Double get() = heartRate / (speed * SECONDS_PER_MINUTE / METRES_PER_KM)
    }
}

/**
 * [heartRate] and [speed] over the moving [pieces], cut where half the moving time has passed;
 * null where the workout moved for less than [MIN_DRIFT_MOVING], or a half has too few readings
 * of either or hardly any speed.
 */
fun driftOf(heartRate: List<Point>, speed: List<Point>, pieces: List<Pair<Instant, Instant>>): Drift? {
    val moving = pieces.fold(Duration.ZERO) { total, (from, to) -> total + Duration.between(from, to) }
    if (moving < MIN_DRIFT_MOVING) return null
    val (first, second) = halve(pieces, moving.dividedBy(2))
    fun half(within: List<Pair<Instant, Instant>>): Drift.Half? {
        fun inside(points: List<Point>) = points.filter { point -> within.any { (from, to) -> point.time >= from && point.time < to } }
        val beats = inside(heartRate).takeIf { it.size >= MIN_HALF_READINGS } ?: return null
        val pace = inside(speed).takeIf { it.size >= MIN_HALF_READINGS } ?: return null
        val meanSpeed = pace.map { it.value }.average().takeIf { it >= MIN_SPEED } ?: return null
        return Drift.Half(beats.map { it.value }.average(), meanSpeed)
    }
    return Drift(half(first) ?: return null, half(second) ?: return null)
}

/** [pieces] split where [at] of moving time has passed, a piece across the cut cut in two. */
internal fun halve(
    pieces: List<Pair<Instant, Instant>>,
    at: Duration,
): Pair<List<Pair<Instant, Instant>>, List<Pair<Instant, Instant>>> {
    val first = mutableListOf<Pair<Instant, Instant>>()
    val second = mutableListOf<Pair<Instant, Instant>>()
    var left = at
    pieces.forEach { (from, to) ->
        val length = Duration.between(from, to)
        when {
            left <= Duration.ZERO -> second += from to to
            length <= left -> {
                first += from to to
                left -= length
            }
            else -> {
                val cut = from.plus(left)
                first += from to cut
                second += cut to to
                left = Duration.ZERO
            }
        }
    }
    return first to second
}

/** Below this, each half is a few minutes of mostly warm-up. */
val MIN_DRIFT_MOVING: Duration = Duration.ofMinutes(20)

private const val MIN_HALF_READINGS = 10

/** About 1 km/h: below it a half was standing still, and beats per kilometre run off to nothing. */
private const val MIN_SPEED = 0.3

private const val SECONDS_PER_MINUTE = 60.0
private const val METRES_PER_KM = 1000.0
