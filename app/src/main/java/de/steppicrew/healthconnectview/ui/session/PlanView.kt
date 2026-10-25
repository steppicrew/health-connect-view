package de.steppicrew.healthconnectview.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.PlannedExerciseStep
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.PlanGoal
import de.steppicrew.healthconnectview.health.PlanStep
import de.steppicrew.healthconnectview.health.PlanTarget
import de.steppicrew.healthconnectview.health.WorkoutPlan
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Quantity
import de.steppicrew.healthconnectview.ui.components.exerciseNameRes
import de.steppicrew.healthconnectview.ui.components.segmentNameRes
import de.steppicrew.healthconnectview.registry.KMH_PER_MS

/** What to call a plan: its own title, else its kind of workout, else "Planned workout". */
@Composable
fun planName(plan: WorkoutPlan): String =
    plan.title ?: stringResource(exerciseNameRes(plan.exerciseType) ?: R.string.type_planned_exercise)

/**
 * A plan's steps as the training app wrote them, block by block: "4×" over a block done four
 * times, then each step's phase and exercise, what ends it, and what to hold meanwhile.
 * [pace] shows speed targets as minutes per km, as a runner reads them.
 */
@Composable
fun PlanSteps(plan: WorkoutPlan, pace: Boolean, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        plan.notes?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        plan.blocks.forEach { block ->
            val repeated = block.repetitions > 1
            if (repeated || block.description != null) {
                Text(
                    text = listOfNotNull(
                        (Formatting.number(block.repetitions.toDouble()) + "×").takeIf { repeated },
                        block.description,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            block.steps.forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (repeated) 12.dp else 0.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = listOfNotNull(phaseName(step.phase), stringResource(segmentNameRes(step.exerciseType)))
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val details = step.targets.mapNotNull { targetText(it, pace) } + listOfNotNull(step.description)
                        if (details.isNotEmpty()) {
                            Text(
                                text = details.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                            )
                        }
                    }
                    goalText(step)?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun phaseName(phase: Int): String? = when (phase) {
    PlannedExerciseStep.EXERCISE_PHASE_WARMUP -> R.string.plan_phase_warmup
    PlannedExerciseStep.EXERCISE_PHASE_ACTIVE -> R.string.plan_phase_active
    PlannedExerciseStep.EXERCISE_PHASE_REST -> R.string.plan_phase_rest
    PlannedExerciseStep.EXERCISE_PHASE_COOLDOWN -> R.string.plan_phase_cooldown
    PlannedExerciseStep.EXERCISE_PHASE_RECOVERY -> R.string.plan_phase_recovery
    else -> null
}?.let { stringResource(it) }

/** What ends a step: "1 km", "10m", "12 repetitions", "until you stop". */
@Composable
private fun goalText(step: PlanStep): String? = when (val goal = step.goal) {
    is PlanGoal.Distance -> distanceText(goal.meters)
    is PlanGoal.Time -> Formatting.duration(goal.duration)
    is PlanGoal.DistanceAndTime -> distanceText(goal.meters) + " · " + Formatting.duration(goal.duration)
    is PlanGoal.Repetitions -> pluralStringResource(R.plurals.session_segment_reps, goal.count, goal.count)
    is PlanGoal.Steps -> pluralStringResource(R.plurals.plan_goal_steps, goal.count, goal.count)
    is PlanGoal.Calories -> Formatting.number(goal.kilocalories, 0) + Formatting.UNIT_SPACE + stringResource(R.string.unit_kcal)
    PlanGoal.Manual -> stringResource(R.string.plan_goal_manual)
    PlanGoal.Unknown -> null
}

/** A distance in km or miles, as the rest of the app shows it. */
fun distanceText(meters: Double): String {
    val unit = Quantity.DISTANCE
    return Formatting.number(unit.convert(meters / 1000)) + Formatting.UNIT_SPACE + unit.symbol()
}

@Composable
private fun targetText(target: PlanTarget, pace: Boolean): String? {
    fun range(low: Double, high: Double, unit: String): String =
        (if (low == high) Formatting.number(low, 0) else Formatting.number(low, 0) + "–" + Formatting.number(high, 0)) +
            Formatting.UNIT_SPACE + unit
    return when (target) {
        is PlanTarget.HeartRate -> stringResource(R.string.plan_target_heart, range(target.low, target.high, stringResource(R.string.unit_bpm)))
        is PlanTarget.Speed -> stringResource(R.string.plan_target_speed, speedRange(target.low, target.high, pace))
        is PlanTarget.Power -> stringResource(R.string.plan_target_power, range(target.low, target.high, stringResource(R.string.unit_w)))
        is PlanTarget.Cadence -> stringResource(R.string.plan_target_cadence, range(target.low, target.high, stringResource(R.string.unit_rpm)))
        is PlanTarget.Weight -> stringResource(
            R.string.plan_target_weight,
            Formatting.number(Quantity.MASS.convert(target.kilograms)) + Formatting.UNIT_SPACE + Quantity.MASS.symbol(),
        )
        is PlanTarget.Effort -> stringResource(R.string.plan_target_effort, Formatting.number(target.rpe.toDouble()))
        PlanTarget.AsManyAsPossible -> stringResource(R.string.plan_target_amrap)
        PlanTarget.Unknown -> null
    }
}

/**
 * A speed range in km/h, or as a pace where [pace]: the faster speed is the shorter pace, so
 * the pace runs from the high speed's to the low one's.
 */
private fun speedRange(low: Double, high: Double, pace: Boolean): String {
    if (pace) {
        val fast = Formatting.pace(high)?.substringBefore(Formatting.UNIT_SPACE)
        val slow = Formatting.pace(low)
        if (fast != null && slow != null) return if (low == high) slow else "$fast–$slow"
    }
    val unit = Quantity.SPEED
    val from = Formatting.number(unit.convert(low * KMH_PER_MS), 1)
    val to = Formatting.number(unit.convert(high * KMH_PER_MS), 1)
    return (if (low == high) from else "$from–$to") + Formatting.UNIT_SPACE + unit.symbol()
}

