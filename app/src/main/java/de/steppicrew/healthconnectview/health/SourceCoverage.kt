package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import java.time.Instant
import java.time.LocalDate

/**
 * What one app wrote for a type over the compared days: on how many of them, how much, since
 * when, and for sleep how many nights carried stages. Counts only -- nothing is kept.
 */
data class WriterCoverage(
    val packageName: String,
    val days: Int,
    val records: Int,
    /** The day of its oldest record of this type, where it was looked up; else null. */
    val since: LocalDate? = null,
    /** Sleep only: nights with stages, of the nights it wrote. Null for other types. */
    val nightsWithStages: Int? = null,
) {
    /** Entries on a day it wrote anything, rounded: how densely it records when it does. */
    val perDay: Int get() = if (days == 0) 0 else Math.round(records.toDouble() / days).toInt()
}

/** Every writer of a type over the last [days] days, the most complete first. */
data class SourceCoverage(val days: Int, val writers: List<WriterCoverage>) {
    /** The writer to offer and why, or null where there is none to name; see [suggestWriter]. */
    val suggestion: Suggestion? get() = suggestWriter(writers)
}

/** The writer [suggestWriter] names, and which count set it apart -- said beside it. */
data class Suggestion(val packageName: String, val reason: Reason) {
    enum class Reason { MOST_DAYS, MOST_ENTRIES, LONGEST_HISTORY }
}

/**
 * The writer covering the most days, more entries a day breaking a tie, then the longer
 * history -- or null with fewer than two writers, or where the top two match on all three,
 * since naming one of two equals would be a verdict the counts do not support.
 *
 * Days first because a gap is what a reader notices: a chart with holes on the days the watch
 * was off. Density only matters between equally complete writers, and history only between
 * equally dense ones: on the phone a watch's own app and a sync app both had every night of
 * the month with stages, and only "since" told them apart.
 */
fun suggestWriter(writers: List<WriterCoverage>): Suggestion? {
    if (writers.size < 2) return null
    val (first, second) = writers.sortedWith(RANKING)
    val reason = when {
        first.days != second.days -> Suggestion.Reason.MOST_DAYS
        first.perDay != second.perDay -> Suggestion.Reason.MOST_ENTRIES
        first.since != null && first.since != second.since -> Suggestion.Reason.LONGEST_HISTORY
        else -> return null
    }
    return Suggestion(first.packageName, reason)
}

/** Most days, then most entries a day, then the oldest record; an unknown "since" ranks last. */
private val RANKING: Comparator<WriterCoverage> =
    compareByDescending<WriterCoverage> { it.days }
        .thenByDescending { it.perDay }
        .thenBy(nullsLast()) { it.since }

/**
 * Counts each writer's records of [spec] over the [days] days up to [now], in one pass over
 * every writer's records together. Records are counted by the day they began; a night by the
 * morning it ended, as everywhere else.
 *
 * With [lookUpSince], one more tiny read per writer finds its oldest record. Only worth it
 * with the history permission: without it Health Connect returns nothing older than 30 days,
 * and "since" would read as the day the window began.
 *
 * Raw records, deliberately: the question is what each app wrote, not the deduplicated total.
 */
suspend fun HealthRepository.sourceCoverage(
    spec: RecordTypeSpec<*>,
    now: Instant,
    days: Int = COVERAGE_DAYS,
    lookUpSince: Boolean,
): SourceCoverage {
    val zone = HealthRepository.DEFAULT_ZONE
    val firstDay = now.atZone(zone).toLocalDate().minusDays(days - 1L)
    val start = firstDay.atStartOfDay(zone).toInstant()
    val sleep = spec.type == SleepSessionRecord::class

    val daysByWriter = mutableMapOf<String, MutableSet<LocalDate>>()
    val recordsByWriter = mutableMapOf<String, Int>()
    val stagedByWriter = mutableMapOf<String, Int>()

    forEachPage(spec.type, TimeRangeFilter.between(if (sleep) start.minus(SESSION_MARGIN) else start, now)) { page ->
        page.forEach { record ->
            val time = if (sleep) spec.endTimeOf(record) ?: spec.timeOf(record) else spec.timeOf(record)
            if (time < start) return@forEach
            val writer = spec.originOf(record)
            daysByWriter.getOrPut(writer) { mutableSetOf() } += time.atZone(zone).toLocalDate()
            recordsByWriter[writer] = (recordsByWriter[writer] ?: 0) + 1
            if (record is SleepSessionRecord && record.stages.isNotEmpty()) {
                stagedByWriter[writer] = (stagedByWriter[writer] ?: 0) + 1
            }
        }
    }

    val writers = daysByWriter.map { (writer, covered) ->
        WriterCoverage(
            packageName = writer,
            days = covered.size,
            records = recordsByWriter[writer] ?: 0,
            since = if (lookUpSince) oldestDay(spec, writer, now) else null,
            nightsWithStages = if (sleep) stagedByWriter[writer] ?: 0 else null,
        )
    }.sortedWith(RANKING)
    return SourceCoverage(days, writers)
}

/** The day of [writer]'s oldest record of [spec]: one ascending read of a single record. */
private suspend fun HealthRepository.oldestDay(spec: RecordTypeSpec<*>, writer: String, now: Instant): LocalDate? =
    oldest(spec.type, TimeRangeFilter.before(now), setOf(DataOrigin(writer)))
        ?.let { spec.timeOf(it).atZone(HealthRepository.DEFAULT_ZONE).toLocalDate() }

/** Days compared: as far as Health Connect reads without the history permission. */
const val COVERAGE_DAYS = 30
