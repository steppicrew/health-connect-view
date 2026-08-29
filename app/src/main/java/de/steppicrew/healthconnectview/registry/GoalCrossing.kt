package de.steppicrew.healthconnectview.registry

import java.time.Instant

/**
 * The moment a rising series first reaches [goal].
 *
 * Interpolated between the two points that straddle the goal rather than reported at the
 * later one. A goal met partway through a climb would otherwise be announced at the end of
 * that climb -- with steps recorded in fifteen-minute intervals, up to fifteen minutes late,
 * and on a coarser series considerably worse.
 *
 * Returns null when the series never reaches the goal, so "not yet" stays distinct from
 * "reached at some unknown time".
 */
fun goalCrossing(points: List<Point>, goal: Double?): Instant? {
    if (goal == null || goal <= 0.0 || points.isEmpty()) return null

    // A series already at or above the goal from its first point was met before this window
    // began; there is no crossing to place inside it.
    if (points.first().value >= goal) return points.first().time

    points.zipWithNext().forEach { (from, to) ->
        if (from.value < goal && to.value >= goal) {
            val rise = to.value - from.value
            // A vertical step has no interior to interpolate: the goal is met at its end.
            if (rise <= 0.0) return to.time

            val fraction = (goal - from.value) / rise
            val spanMillis = to.time.toEpochMilli() - from.time.toEpochMilli()
            return from.time.plusMillis((spanMillis * fraction).toLong())
        }
    }
    return null
}
