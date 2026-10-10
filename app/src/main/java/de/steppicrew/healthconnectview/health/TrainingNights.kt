package de.steppicrew.healthconnectview.health

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * The night's lowest heart rate after a training day against after a rest day: each group's
 * mean and how many nights it holds. Set side by side, no verdict -- a group needs
 * [MIN_GROUP_NIGHTS] before it gets a mean at all, and either may be missing.
 */
data class TrainingNights(val afterTraining: Group?, val afterRest: Group?) {
    data class Group(val meanLow: Double, val nights: Int)
}

/**
 * The days with a workout of at least [MIN_TRAINING], by the day it began. A walk of ten
 * minutes is not what "a training day" means, and counting it would leave hardly a rest day.
 */
fun trainingDays(sessions: List<Session>, zone: ZoneId): Set<LocalDate> =
    sessions
        .filter { it.kind == Session.Kind.EXERCISE && Duration.between(it.start, it.end) >= MIN_TRAINING }
        .map { it.start.atZone(zone).toLocalDate() }
        .toSet()

/** Each morning's night -- its longest sleep, as on the sleep tile -- by the day it ended on. */
fun nightsByMorning(sessions: List<Session>, zone: ZoneId): Map<LocalDate, Session> =
    sessions
        .filter { it.kind == Session.Kind.SLEEP }
        .groupBy { it.end.atZone(zone).toLocalDate() }
        .mapNotNull { (morning, sleeps) -> sleeps.night()?.let { morning to it } }
        .toMap()

/**
 * [lows] -- each night's lowest heart rate, by the morning it ended on -- split by whether the
 * day before that morning was one of [training]. A group with fewer than [MIN_GROUP_NIGHTS]
 * nights has no mean: two nights are an anecdote. Null where neither group has enough.
 */
fun splitByTraining(lows: Map<LocalDate, Double>, training: Set<LocalDate>): TrainingNights? {
    val (after, rest) = lows.entries.partition { (morning, _) -> morning.minusDays(1) in training }
    fun group(nights: List<Map.Entry<LocalDate, Double>>) =
        nights.takeIf { it.size >= MIN_GROUP_NIGHTS }?.let { TrainingNights.Group(it.map { night -> night.value }.average(), it.size) }
    val split = TrainingNights(group(after), group(rest))
    return split.takeIf { it.afterTraining != null || it.afterRest != null }
}

/** Shorter than this a workout does not make its day a training day. */
val MIN_TRAINING: Duration = Duration.ofMinutes(20)

const val MIN_GROUP_NIGHTS = 3
