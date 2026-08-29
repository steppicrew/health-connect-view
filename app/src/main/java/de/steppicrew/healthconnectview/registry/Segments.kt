package de.steppicrew.healthconnectview.registry

import java.time.Instant

/**
 * Splits a series wherever a bucket held no data.
 *
 * A line drawn straight across a day nothing was recorded claims a value for that day. On
 * health data that is not a cosmetic liberty: an unrecorded day and a day of zero activity
 * mean different things, and only one of them is the user's doing.
 *
 * Each returned list is a run of consecutive points with no gap inside it, so the caller can
 * stroke them separately and leave the gaps visibly empty.
 */
fun segmentAtGaps(points: List<Point>, emptyTimes: Collection<Instant>): List<List<Point>> {
    if (points.isEmpty()) return emptyList()
    if (emptyTimes.isEmpty()) return listOf(points)

    val gaps = emptyTimes.sorted()
    val segments = mutableListOf<List<Point>>()
    var current = mutableListOf<Point>()

    points.forEach { point ->
        val previous = current.lastOrNull()
        // A gap counts only when it falls between two points that would otherwise be joined;
        // gaps before the first point or after the last one break no line.
        val brokenByGap = previous != null && gaps.any { gap ->
            gap >= previous.time && gap < point.time
        }
        if (brokenByGap) {
            segments += current.toList()
            current = mutableListOf()
        }
        current += point
    }
    if (current.isNotEmpty()) segments += current.toList()
    return segments
}
