package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.ExerciseCompletionGoal
import androidx.health.connect.client.records.ExercisePerformanceTarget
import androidx.health.connect.client.records.PlannedExerciseSessionRecord
import java.time.Duration
import java.time.Instant

/**
 * A workout as a training app planned it: blocks of steps, each with a goal that ends it and
 * targets to hold meanwhile. [completedSessionId] is the workout that carried it out, where the
 * writer linked one. Health Connect's own classes, flattened so they can be tested and compared
 * without the library's constructors and their validation.
 */
data class WorkoutPlan(
    val recordId: String,
    val start: Instant,
    val end: Instant,
    val title: String?,
    val notes: String?,
    val exerciseType: Int,
    val blocks: List<PlanBlock>,
    val completedSessionId: String?,
    val origin: String,
)

/** Steps done [repetitions] times in a row: "5 × (3 min fast, 2 min easy)". */
data class PlanBlock(val repetitions: Int, val description: String?, val steps: List<PlanStep>)

/** One step: what to do ([exerciseType] is a segment kind), in which phase, until when, how hard. */
data class PlanStep(
    val exerciseType: Int,
    val phase: Int,
    val description: String?,
    val goal: PlanGoal,
    val targets: List<PlanTarget>,
)

/** What ends a step. */
sealed interface PlanGoal {
    data class Distance(val meters: Double) : PlanGoal
    data class Time(val duration: Duration) : PlanGoal
    data class DistanceAndTime(val meters: Double, val duration: Duration) : PlanGoal
    data class Repetitions(val count: Int) : PlanGoal
    data class Steps(val count: Int) : PlanGoal
    data class Calories(val kilocalories: Double, val active: Boolean) : PlanGoal
    /** Ended by hand, when the user says so. */
    data object Manual : PlanGoal
    data object Unknown : PlanGoal
}

/** What to hold during a step. Ranges are low to high; speed in metres per second. */
sealed interface PlanTarget {
    data class HeartRate(val low: Double, val high: Double) : PlanTarget
    data class Speed(val low: Double, val high: Double) : PlanTarget
    data class Power(val low: Double, val high: Double) : PlanTarget
    data class Cadence(val low: Double, val high: Double) : PlanTarget
    data class Weight(val kilograms: Double) : PlanTarget
    /** Rate of perceived exertion, 0 to 10. */
    data class Effort(val rpe: Int) : PlanTarget
    /** As many repetitions as possible. */
    data object AsManyAsPossible : PlanTarget
    data object Unknown : PlanTarget
}

fun PlannedExerciseSessionRecord.toPlan(): WorkoutPlan = WorkoutPlan(
    recordId = metadata.id,
    start = startTime,
    end = endTime,
    title = title?.trim()?.takeIf { it.isNotEmpty() },
    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
    exerciseType = exerciseType,
    blocks = blocks.map { block ->
        PlanBlock(
            repetitions = block.repetitions,
            description = block.description?.trim()?.takeIf { it.isNotEmpty() },
            steps = block.steps.map { step ->
                PlanStep(
                    exerciseType = step.exerciseType,
                    phase = step.exercisePhase,
                    description = step.description?.trim()?.takeIf { it.isNotEmpty() },
                    goal = step.completionGoal.toGoal(),
                    targets = step.performanceTargets.map { it.toTarget() },
                )
            },
        )
    },
    completedSessionId = completedExerciseSessionId,
    origin = metadata.dataOrigin.packageName,
)

private fun ExerciseCompletionGoal.toGoal(): PlanGoal = when (this) {
    is ExerciseCompletionGoal.DistanceGoal -> PlanGoal.Distance(distance.inMeters)
    is ExerciseCompletionGoal.DurationGoal -> PlanGoal.Time(duration)
    is ExerciseCompletionGoal.DistanceAndDurationGoal -> PlanGoal.DistanceAndTime(distance.inMeters, duration)
    is ExerciseCompletionGoal.RepetitionsGoal -> PlanGoal.Repetitions(repetitions)
    is ExerciseCompletionGoal.StepsGoal -> PlanGoal.Steps(steps)
    is ExerciseCompletionGoal.TotalCaloriesBurnedGoal -> PlanGoal.Calories(totalCalories.inKilocalories, active = false)
    is ExerciseCompletionGoal.ActiveCaloriesBurnedGoal -> PlanGoal.Calories(activeCalories.inKilocalories, active = true)
    is ExerciseCompletionGoal.ManualCompletion -> PlanGoal.Manual
    else -> PlanGoal.Unknown
}

private fun ExercisePerformanceTarget.toTarget(): PlanTarget = when (this) {
    is ExercisePerformanceTarget.HeartRateTarget -> PlanTarget.HeartRate(minHeartRate, maxHeartRate)
    is ExercisePerformanceTarget.SpeedTarget -> PlanTarget.Speed(minSpeed.inMetersPerSecond, maxSpeed.inMetersPerSecond)
    is ExercisePerformanceTarget.PowerTarget -> PlanTarget.Power(minPower.inWatts, maxPower.inWatts)
    is ExercisePerformanceTarget.CadenceTarget -> PlanTarget.Cadence(minCadence, maxCadence)
    is ExercisePerformanceTarget.WeightTarget -> PlanTarget.Weight(mass.inKilograms)
    is ExercisePerformanceTarget.RateOfPerceivedExertionTarget -> PlanTarget.Effort(rpe)
    is ExercisePerformanceTarget.AmrapTarget -> PlanTarget.AsManyAsPossible
    else -> PlanTarget.Unknown
}

/**
 * The distance and time a plan adds up to, each block's steps counted as often as the block
 * repeats. Null where a step ends otherwise -- by repetitions, by hand -- since the plan then
 * says nothing about the total: "10 km" is a promise only where every step is measured in it.
 */
data class PlanTotals(val meters: Double?, val duration: Duration?)

fun WorkoutPlan.totals(): PlanTotals {
    val steps = blocks.flatMap { block -> List(block.repetitions.coerceAtLeast(1)) { block.steps }.flatten() }
    if (steps.isEmpty()) return PlanTotals(null, null)
    fun meters(goal: PlanGoal): Double? = when (goal) {
        is PlanGoal.Distance -> goal.meters
        is PlanGoal.DistanceAndTime -> goal.meters
        else -> null
    }
    fun time(goal: PlanGoal): Duration? = when (goal) {
        is PlanGoal.Time -> goal.duration
        is PlanGoal.DistanceAndTime -> goal.duration
        else -> null
    }
    val distances = steps.map { meters(it.goal) }
    val times = steps.map { time(it.goal) }
    return PlanTotals(
        meters = distances.takeIf { null !in it }?.filterNotNull()?.sum(),
        duration = times.takeIf { null !in it }?.filterNotNull()?.fold(Duration.ZERO, Duration::plus),
    )
}

/**
 * The plan a workout carried out, from [plans] read around it: the one the workout names, else
 * the one naming the workout, by any of [sessionIds] -- the copies of one workout that
 * [dedupeSessions] merged, since the plan may point at a copy other than the one kept.
 */
fun planFor(plans: List<WorkoutPlan>, plannedId: String?, sessionIds: Set<String>): WorkoutPlan? =
    plans.firstOrNull { plannedId != null && it.recordId == plannedId }
        ?: plans.firstOrNull { it.completedSessionId != null && it.completedSessionId in sessionIds }
