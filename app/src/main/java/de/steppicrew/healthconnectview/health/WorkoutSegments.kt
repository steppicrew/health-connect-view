package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.ExerciseSegment
import de.steppicrew.healthconnectview.registry.Point
import java.time.Duration
import java.time.Instant

/**
 * One stretch of a workout with a kind of its own, as its writer split it: a set of bench
 * presses, a length of breaststroke, a rest. [type] is one of `ExerciseSegment`'s
 * `EXERCISE_SEGMENT_TYPE_*`; [repetitions] is 0 where none were counted.
 */
data class WorkoutSegment(val start: Instant, val end: Instant, val type: Int, val repetitions: Int = 0) {
    val duration: Duration get() = Duration.between(start, end)
}

fun ExerciseSegment.toWorkoutSegment(): WorkoutSegment = WorkoutSegment(startTime, endTime, segmentType, repetitions)

/** A rest or a pause, drawn grey and listed apart from the exercises. */
fun isRest(type: Int): Boolean =
    type == ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST || type == ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE

/**
 * All of a workout's segments of one kind together: how many there were, the repetitions and
 * time they add up to, and the mean of [heartRate] readings inside them where any were taken.
 */
data class SegmentSummary(
    val type: Int,
    val sets: Int,
    val repetitions: Int,
    val time: Duration,
    val heartRate: Double?,
)

/**
 * One row per kind, in the order each kind first appears, rests last. A gym session is thirty
 * segments of four exercises and their rests; listed one by one they would be a page of
 * near-identical rows, and the strip under the chart already shows them in order.
 */
fun summariseSegments(segments: List<WorkoutSegment>, heartRate: List<Point>? = null): List<SegmentSummary> {
    val sorted = segments.sortedBy { it.start }
    val (rests, active) = sorted.partition { isRest(it.type) }
    return (active + rests).groupBy { it.type }.map { (type, ofType) ->
        val readings = heartRate.orEmpty().filter { point -> ofType.any { point.time >= it.start && point.time < it.end } }
        SegmentSummary(
            type = type,
            sets = ofType.size,
            repetitions = ofType.sumOf { it.repetitions },
            time = ofType.fold(Duration.ZERO) { total, segment -> total + segment.duration },
            heartRate = readings.takeIf { it.isNotEmpty() }?.map { it.value }?.average(),
        )
    }
}
