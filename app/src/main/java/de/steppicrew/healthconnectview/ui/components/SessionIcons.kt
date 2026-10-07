package de.steppicrew.healthconnectview.ui.components

import androidx.annotation.StringRes
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.records.ExerciseSessionRecord
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.WorkoutFamily
import de.steppicrew.healthconnectview.health.familyOf

/**
 * An icon for a session, so a row is scannable without reading it.
 *
 * Types with no distinctive icon fall back to a generic sports mark rather than to nothing:
 * an activity with no icon reads as a rendering fault next to rows that have one.
 */
fun iconFor(session: Session): ImageVector = when (session.kind) {
    Session.Kind.SLEEP -> Icons.Default.Bedtime
    Session.Kind.MINDFULNESS -> Icons.Default.SelfImprovement
    Session.Kind.EXERCISE -> iconFor(familyOf(session.exerciseType))
}

/** The icon for a kind of workout, shared by its rows and its filter chip. */
fun iconFor(family: WorkoutFamily): ImageVector = when (family) {
    WorkoutFamily.CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
    WorkoutFamily.RUNNING -> Icons.AutoMirrored.Filled.DirectionsRun
    WorkoutFamily.WALKING -> Icons.AutoMirrored.Filled.DirectionsWalk
    WorkoutFamily.HIKING -> Icons.Default.Hiking
    WorkoutFamily.SWIMMING -> Icons.Default.Pool
    WorkoutFamily.STRENGTH -> Icons.Default.FitnessCenter
    WorkoutFamily.YOGA -> Icons.Default.SelfImprovement
    WorkoutFamily.ROWING -> Icons.Default.Rowing
    WorkoutFamily.OTHER -> Icons.Default.Sports
}

/** A kind of workout's name, for its filter chip. */
@StringRes
fun labelFor(family: WorkoutFamily): Int = when (family) {
    WorkoutFamily.CYCLING -> R.string.exercise_biking
    WorkoutFamily.RUNNING -> R.string.exercise_running
    WorkoutFamily.WALKING -> R.string.exercise_walking
    WorkoutFamily.HIKING -> R.string.exercise_hiking
    WorkoutFamily.SWIMMING -> R.string.exercise_swimming
    WorkoutFamily.STRENGTH -> R.string.exercise_strength
    WorkoutFamily.YOGA -> R.string.family_yoga
    WorkoutFamily.ROWING -> R.string.exercise_rowing
    WorkoutFamily.OTHER -> R.string.family_other
}

/**
 * A name for an exercise type, in the user's language, where it says something worth showing;
 * null for the rest, which are named by their kind or not at all rather than guessed.
 */
@StringRes
private fun exerciseNameRes(type: Int?): Int? = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> R.string.exercise_biking
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> R.string.exercise_biking_stationary
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> R.string.exercise_running
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> R.string.exercise_running_treadmill
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> R.string.exercise_walking
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> R.string.exercise_hiking
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    -> R.string.exercise_swimming
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
    -> R.string.exercise_strength
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> R.string.exercise_yoga
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> R.string.exercise_pilates
    ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> R.string.exercise_stretching
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
    -> R.string.exercise_rowing
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> R.string.exercise_elliptical
    else -> null
}

/**
 * What to call a session: the writer's own title, else its type in the user's language, else
 * "Sleep", "Mindfulness" or "Activity" by its kind.
 */
@Composable
fun sessionName(session: Session): String =
    session.title ?: stringResource(
        when (session.kind) {
            Session.Kind.SLEEP -> R.string.session_sleep
            Session.Kind.MINDFULNESS -> R.string.type_mindfulness_session
            Session.Kind.EXERCISE -> exerciseNameRes(session.exerciseType) ?: R.string.session_untitled
        },
    )
