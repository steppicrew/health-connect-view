package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.SleepSessionRecord
import java.time.Duration
import java.time.Instant

/** One stretch of a night in one stage, as the writing app recorded it. */
data class SleepStage(val start: Instant, val end: Instant, val kind: StageKind)

/**
 * The stages a night is drawn in, top lane first -- the order of a hypnogram, with waking at
 * the top and the deepest sleep at the bottom.
 *
 * The platform has eight codes; three of them (awake, awake in bed, out of bed) all answer
 * "not asleep" and are drawn as one, since no writer measured on this phone distinguishes them
 * and three lanes for one state would read as three findings. [ASLEEP] is sleep a writer did
 * not classify further, kept apart so it is never passed off as light sleep.
 */
enum class StageKind { AWAKE, REM, LIGHT, DEEP, ASLEEP }

fun stageKindOf(type: Int): StageKind? = when (type) {
    SleepSessionRecord.STAGE_TYPE_AWAKE,
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> StageKind.AWAKE
    SleepSessionRecord.STAGE_TYPE_REM -> StageKind.REM
    SleepSessionRecord.STAGE_TYPE_LIGHT -> StageKind.LIGHT
    SleepSessionRecord.STAGE_TYPE_DEEP -> StageKind.DEEP
    SleepSessionRecord.STAGE_TYPE_SLEEPING -> StageKind.ASLEEP
    // Unknown says nothing about the night; drawing it would put a stage where none was
    // recorded, so it is left as a gap.
    else -> null
}

fun SleepSessionRecord.Stage.toSleepStage(): SleepStage? =
    stageKindOf(stage)?.let { SleepStage(startTime, endTime, it) }

/**
 * Time in each stage, in hypnogram order, leaving out stages the night never reached.
 *
 * Summing is safe here where it is not for metrics: these are one writer's stages of one night
 * -- the session was already chosen from its duplicates -- and a writer's stages do not overlap
 * each other.
 */
fun stageTotals(stages: List<SleepStage>): List<Pair<StageKind, Duration>> =
    StageKind.entries.mapNotNull { kind ->
        val total = stages.filter { it.kind == kind }
            .fold(Duration.ZERO) { sum, stage -> sum + Duration.between(stage.start, stage.end) }
        if (total.isZero) null else kind to total
    }
