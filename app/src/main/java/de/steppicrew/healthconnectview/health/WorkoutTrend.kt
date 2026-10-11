package de.steppicrew.healthconnectview.health

import java.time.Duration
import java.time.Instant

/**
 * One workout's figures for the course of a kind over the year (step 81): its distance and
 * mean heart rate, each Health Connect's deduplicated aggregate over the workout's window --
 * the time-overlap rule every session statistic follows -- or null where none was recorded.
 */
data class WorkoutTrendPoint(
    val start: Instant,
    val exerciseType: Int?,
    val duration: Duration,
    val metres: Double?,
    val heartRate: Double?,
) {
    /**
     * Over the whole workout, breaks included: the moving time needs each workout's readings,
     * a minute's reading for a year of rides. Null without a distance or a duration.
     */
    val metresPerSecond: Double?
        get() {
            val seconds = duration.seconds.takeIf { it > 0 } ?: return null
            return metres?.takeIf { it > 0 }?.let { it / seconds }
        }
}

/** Whether a kind is read as a pace rather than a speed, as runners and walkers read it. */
fun WorkoutFamily.readsPace(): Boolean =
    this == WorkoutFamily.RUNNING || this == WorkoutFamily.WALKING || this == WorkoutFamily.HIKING

/** Whether a kind covers a distance worth a speed at all; a strength session goes nowhere. */
fun WorkoutFamily.hasSpeed(): Boolean = readsPace() || this == WorkoutFamily.CYCLING
