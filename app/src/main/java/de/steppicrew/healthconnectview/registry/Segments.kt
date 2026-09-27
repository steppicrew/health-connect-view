package de.steppicrew.healthconnectview.registry

import java.time.Duration
import java.time.Instant

/**
 * Splits a series wherever a bucket held no data, or where two neighbours lie further apart
 * than [maxStep].
 *
 * A line drawn straight across a day nothing was recorded claims a value for that day. On
 * health data that is not a cosmetic liberty: an unrecorded day and a day of zero activity
 * mean different things, and only one of them is the user's doing.
 *
 * Each returned list is a run of consecutive points with no gap inside it, so the caller can
 * stroke them solid and mark the gaps between them as gaps.
 */
fun segmentAtGaps(
    points: List<Point>,
    emptyTimes: Collection<Instant>,
    maxStep: Duration? = null,
): List<List<Point>> {
    if (points.isEmpty()) return emptyList()
    if (emptyTimes.isEmpty() && maxStep == null) return listOf(points)

    val gaps = emptyTimes.sorted()
    val segments = mutableListOf<List<Point>>()
    var current = mutableListOf<Point>()

    points.forEach { point ->
        val previous = current.lastOrNull()
        // A gap counts only when it falls between two points that would otherwise be joined;
        // gaps before the first point or after the last one break no line.
        val brokenByGap = previous != null && (
            gaps.any { gap -> gap >= previous.time && gap < point.time } ||
                (maxStep != null && Duration.between(previous.time, point.time) > maxStep)
            )
        if (brokenByGap) {
            segments += current.toList()
            current = mutableListOf()
        }
        current += point
    }
    if (current.isNotEmpty()) segments += current.toList()
    return segments
}

/**
 * The longest step between readings that still counts as continuous, or null where a series
 * is too short to have a rhythm.
 *
 * Readings have no empty buckets to mark a gap: a day of heart rate ran solid from 20:30 to
 * 23:40 across three hours without one reading. A gap is a step several times the series'
 * usual one -- the median, so a few pauses do not stretch the yardstick -- and never shorter
 * than [MIN_GAP], or a watch sampling every 15 seconds by day and every two minutes at night
 * would break at every change of pace. A weekly weigh-in is a rhythm of its own, not a run
 * of gaps: its median step is a week.
 */
fun readingGap(points: List<Point>): Duration? {
    if (points.size < MIN_POINTS_FOR_RHYTHM) return null
    val steps = points.zipWithNext { a, b -> Duration.between(a.time, b.time) }
        .filter { !it.isZero && !it.isNegative }
        .sorted()
    val median = steps.getOrNull(steps.size / 2) ?: return null
    return maxOf(median.multipliedBy(GAP_FACTOR), MIN_GAP)
}

/** How many usual steps a pause must span to count as a gap. */
private const val GAP_FACTOR = 4L

/** A pause shorter than this is never a gap: a watch off the wrist for a shower. */
private val MIN_GAP: Duration = Duration.ofMinutes(30)

private const val MIN_POINTS_FOR_RHYTHM = 3
