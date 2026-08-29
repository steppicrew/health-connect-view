package de.steppicrew.healthconnectview.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Sports
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.health.connect.client.records.ExerciseSessionRecord
import de.steppicrew.healthconnectview.health.Session

/**
 * An icon for a session, so a row is scannable without reading it.
 *
 * Types with no distinctive icon fall back to a generic sports mark rather than to nothing:
 * an activity with no icon reads as a rendering fault next to rows that have one.
 */
fun iconFor(session: Session): ImageVector = when (session.kind) {
    Session.Kind.SLEEP -> Icons.Default.Bedtime
    Session.Kind.EXERCISE -> when (session.exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        -> Icons.AutoMirrored.Filled.DirectionsBike

        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        -> Icons.AutoMirrored.Filled.DirectionsRun

        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> Icons.AutoMirrored.Filled.DirectionsWalk
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> Icons.Default.Hiking

        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        -> Icons.Default.Pool

        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        -> Icons.Default.FitnessCenter

        ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
        -> Icons.Default.SelfImprovement

        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> Icons.Default.Rowing
        else -> Icons.Default.Sports
    }
}
