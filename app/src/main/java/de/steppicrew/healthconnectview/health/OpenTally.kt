package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.metadata.DataOrigin
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import java.time.Instant
import java.time.LocalDate

/** One record reduced to who wrote it, when it claims to end, and what it adds. */
data class TallyRecord(val origin: String, val end: Instant, val value: Double)

/**
 * The fullest single writer's own figure for today, when one of today's records claims hours
 * still to come; null otherwise.
 *
 * A writer keeping a running tally posts today's record as 00:00-23:59 and raises its value
 * through the day. Health Connect cannot know the value is "so far": asked for the total up to
 * now it apportions the record across all 24 hours and keeps only the elapsed share, filling
 * the rest from its basal estimate. Measured on the phone at 10:32, Garmin's record said
 * 906 kcal and the platform's total up to now said 786.
 *
 * One writer cannot overlap itself, so its own records summed are a floor the deduplicated
 * total can never truly be below -- the same reasoning that lets a single selected source be
 * summed. Writers are compared, never added, so nothing is double-counted. Days without a
 * record reaching past now are left entirely to the platform.
 */
fun openTally(records: List<TallyRecord>, now: Instant): Double? {
    if (records.none { it.end.isAfter(now) }) return null
    return records.groupBy { it.origin }.values.maxOf { group -> group.sumOf { it.value } }
}

/** [openTally] for [spec] today. Null for averaged types, where a floor means nothing. */
suspend fun HealthRepository.openTally(
    spec: RecordTypeSpec<*>,
    origins: Set<DataOrigin>,
    today: LocalDate = LocalDate.now(),
    now: Instant = Instant.now(),
): Double? {
    if (spec.aggregate == null || spec.isAveraged || spec.endTime == null) return null
    val records = mutableListOf<TallyRecord>()
    forEachPage(spec.type, dayInstants(today), origins) { page ->
        page.forEach { record ->
            records += TallyRecord(
                origin = spec.originOf(record),
                end = spec.endTimeOf(record) ?: spec.timeOf(record),
                value = spec.pointsOf(record).sumOf { it.value },
            )
        }
    }
    return openTally(records, now)
}

/** The larger of the platform's total and [floor], keeping the platform's when there is none. */
fun atLeast(total: Double?, floor: Double?): Double? =
    listOfNotNull(total, floor).maxOrNull()
