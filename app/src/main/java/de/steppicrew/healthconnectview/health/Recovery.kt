package de.steppicrew.healthconnectview.health

import de.steppicrew.healthconnectview.registry.Point
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * How heart rate changed after a workout ended: from the reading at the end to the readings one
 * and two minutes later, negative where it fell. A number, no verdict.
 *
 * Only where a reading lies within [RECOVERY_TOLERANCE] of each moment. On the phone most
 * workouts are followed by one all-day reading every two minutes, so "a minute after" could
 * be a reading from 110 s, and right after stopping heart rate falls by up to half a beat a
 * second: a figure from such a reading would look exact and be wrong. The indoor bike, whose
 * copy carries a reading every 15 s, qualifies; most rides do not, and show only the curve.
 */
data class Recovery(
    /** The change after one minute, in beats per minute; null where no reading is close enough. */
    val afterOne: Double?,
    /** The change after two minutes. */
    val afterTwo: Double?,
)

/** [points], one writer's, measured against [end]; null where neither minute can be told. */
fun recoveryOf(points: List<Point>, end: Instant, tolerance: Duration = RECOVERY_TOLERANCE): Recovery? {
    fun near(time: Instant): Double? = points
        .map { it to abs(Duration.between(time, it.time).toMillis()) }
        .filter { (_, off) -> off <= tolerance.toMillis() }
        .minByOrNull { (_, off) -> off }
        ?.first?.value
    val atEnd = near(end) ?: return null
    val one = near(end.plus(ONE_MINUTE))?.let { it - atEnd }
    val two = near(end.plus(TWO_MINUTES))?.let { it - atEnd }
    return if (one == null && two == null) null else Recovery(one, two)
}

/** How far from a moment a reading may lie and still stand for it. */
val RECOVERY_TOLERANCE: Duration = Duration.ofSeconds(10)

/** How long after a workout its heart rate is drawn. */
val AFTER_END: Duration = Duration.ofMinutes(5)

private val ONE_MINUTE: Duration = Duration.ofMinutes(1)
private val TWO_MINUTES: Duration = Duration.ofMinutes(2)
