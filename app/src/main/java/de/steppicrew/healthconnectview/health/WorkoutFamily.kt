package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.ExerciseSessionRecord

/**
 * Broad kinds of workout, for filtering a list by what was done.
 *
 * Health Connect has 61 exercise types, and writers disagree about which one a session is: the
 * same indoor ride arrives as biking from a watch and as stationary biking from the machine's
 * own app (CLAUDE.md). Filtering by the exact type would split one habit across two chips, so
 * related types share a kind; the session's own type still names it where it is shown.
 */
enum class WorkoutFamily { CYCLING, RUNNING, WALKING, HIKING, SWIMMING, STRENGTH, YOGA, ROWING, OTHER }

/** The kind an exercise type belongs to; [WorkoutFamily.OTHER] for anything not grouped. */
fun familyOf(exerciseType: Int?): WorkoutFamily = when (exerciseType) {
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
    -> WorkoutFamily.CYCLING

    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    -> WorkoutFamily.RUNNING

    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> WorkoutFamily.WALKING
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> WorkoutFamily.HIKING

    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    -> WorkoutFamily.SWIMMING

    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
    -> WorkoutFamily.STRENGTH

    ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
    ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
    -> WorkoutFamily.YOGA

    ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
    -> WorkoutFamily.ROWING

    else -> WorkoutFamily.OTHER
}

/**
 * The kinds that occur in [sessions], in the enum's order with [WorkoutFamily.OTHER] last, so
 * the chips offer only what there is to find.
 */
fun familiesIn(sessions: List<Session>): List<WorkoutFamily> =
    sessions.filter { it.kind == Session.Kind.EXERCISE }
        .map { familyOf(it.exerciseType) }
        .distinct()
        .sorted()

/**
 * Whether a workout of [exerciseType] is about getting somewhere, so that stopping is a break:
 * a ride or a run stands still at a café, a strength session does not "stop" between sets. On
 * the phone a 23-minute strength session read a ten-minute "break" out of the all-day walking
 * distance around it (09.10.2026). Unknown types are left out for the same reason.
 */
fun coversDistance(exerciseType: Int?): Boolean = when (familyOf(exerciseType)) {
    WorkoutFamily.CYCLING,
    WorkoutFamily.RUNNING,
    WorkoutFamily.WALKING,
    WorkoutFamily.HIKING,
    WorkoutFamily.SWIMMING,
    WorkoutFamily.ROWING,
    -> true
    else -> exerciseType in DISTANCE_TYPES
}

/** Distance sports not in a family of their own. */
private val DISTANCE_TYPES = setOf(
    ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING,
    ExerciseSessionRecord.EXERCISE_TYPE_SKATING,
    ExerciseSessionRecord.EXERCISE_TYPE_SKIING,
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING,
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING,
    ExerciseSessionRecord.EXERCISE_TYPE_PADDLING,
    ExerciseSessionRecord.EXERCISE_TYPE_SAILING,
    ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR,
)
