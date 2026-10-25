package de.steppicrew.healthconnectview.ui.components

import androidx.annotation.StringRes
import androidx.health.connect.client.records.ExerciseSegment
import de.steppicrew.healthconnectview.R

/**
 * A segment kind's name in the user's language. Kinds that are also whole workouts -- running,
 * yoga -- share the workout's name; an unknown kind is "other exercise" rather than a number.
 */
@StringRes
fun segmentNameRes(type: Int): Int = when (type) {
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL -> R.string.segment_arm_curl
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION -> R.string.segment_back_extension
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_BALL_SLAM -> R.string.segment_ball_slam
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS -> R.string.segment_barbell_shoulder_press
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS -> R.string.segment_bench_press
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_SIT_UP -> R.string.segment_bench_sit_up
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE -> R.string.segment_burpee
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH -> R.string.segment_crunch
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT -> R.string.segment_deadlift
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION -> R.string.segment_double_arm_triceps_extension
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_CURL_LEFT_ARM -> R.string.segment_dumbbell_curl_left_arm
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_CURL_RIGHT_ARM -> R.string.segment_dumbbell_curl_right_arm
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_FRONT_RAISE -> R.string.segment_dumbbell_front_raise
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_LATERAL_RAISE -> R.string.segment_dumbbell_lateral_raise
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW -> R.string.segment_dumbbell_row
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_LEFT_ARM -> R.string.segment_dumbbell_triceps_extension_left_arm
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_RIGHT_ARM -> R.string.segment_dumbbell_triceps_extension_right_arm
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_TWO_ARM -> R.string.segment_dumbbell_triceps_extension_two_arm
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_FORWARD_TWIST -> R.string.segment_forward_twist
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_FRONT_RAISE -> R.string.segment_front_raise
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> R.string.segment_high_intensity_interval_training
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST -> R.string.segment_hip_thrust
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_HULA_HOOP -> R.string.segment_hula_hoop
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMPING_JACK -> R.string.segment_jumping_jack
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMP_ROPE -> R.string.segment_jump_rope
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING -> R.string.segment_kettlebell_swing
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE -> R.string.segment_lateral_raise
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN -> R.string.segment_lat_pull_down
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL -> R.string.segment_leg_curl
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION -> R.string.segment_leg_extension
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS -> R.string.segment_leg_press
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_RAISE -> R.string.segment_leg_raise
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE -> R.string.segment_lunge
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_MOUNTAIN_CLIMBER -> R.string.segment_mountain_climber
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT -> R.string.segment_other_workout
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE -> R.string.segment_pause
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK -> R.string.segment_plank
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP -> R.string.segment_pull_up
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_PUNCH -> R.string.segment_punch
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST -> R.string.segment_rest
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_ROWING_MACHINE -> R.string.segment_rowing_machine
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS -> R.string.segment_shoulder_press
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_SINGLE_ARM_TRICEPS_EXTENSION -> R.string.segment_single_arm_triceps_extension
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP -> R.string.segment_sit_up
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT -> R.string.segment_squat
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING -> R.string.segment_stair_climbing
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING_MACHINE -> R.string.segment_stair_climbing_machine
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_BACKSTROKE -> R.string.segment_swimming_backstroke
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_BREASTSTROKE -> R.string.segment_swimming_breaststroke
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_BUTTERFLY -> R.string.segment_swimming_butterfly
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_FREESTYLE -> R.string.segment_swimming_freestyle
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_MIXED -> R.string.segment_swimming_mixed
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_OPEN_WATER -> R.string.segment_swimming_open_water
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_OTHER -> R.string.segment_swimming_other
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_POOL -> R.string.segment_swimming_pool
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_UPPER_TWIST -> R.string.segment_upper_twist
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING -> R.string.segment_weightlifting
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_WHEELCHAIR -> R.string.segment_wheelchair
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING -> R.string.exercise_biking
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY -> R.string.exercise_biking_stationary
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_ELLIPTICAL -> R.string.exercise_elliptical
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_PILATES -> R.string.exercise_pilates
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING -> R.string.exercise_running
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING_TREADMILL -> R.string.exercise_running_treadmill
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_STRETCHING -> R.string.exercise_stretching
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_WALKING -> R.string.exercise_walking
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_YOGA -> R.string.exercise_yoga
    else -> R.string.segment_other_workout
}
