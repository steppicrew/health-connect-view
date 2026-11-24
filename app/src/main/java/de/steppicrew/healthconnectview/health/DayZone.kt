package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.util.catching
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The clock a day is drawn on: where the user was that day, not where the phone is now.
 *
 * After a flight the phone's zone moved every day from home: a walk at 04:56 in Germany on the
 * travel day sat squashed at 00:00 of a day running on Ecuador's clock (07.11.2026). Health
 * Connect keeps each record's own offset, and its daily totals already follow it; a day's
 * chart now does too.
 *
 * [moments] are records' times with the offsets they were written in. The phone's own zone is
 * kept wherever one of its offsets that day is the day's usual one, so a day at home that
 * changes to winter time stays a 25-hour day in the phone's zone. Elsewhere the day's most
 * frequent offset decides; with none recorded, the phone's zone.
 */
fun zoneOfDay(date: LocalDate, moments: List<Pair<Instant, ZoneOffset>>, phone: ZoneId): ZoneId {
    val onDay = moments.filter { (time, offset) -> time.atOffset(offset).toLocalDate() == date }
    val usual = onDay.groupingBy { it.second }.eachCount().maxByOrNull { it.value }?.key ?: return phone
    val start = date.atStartOfDay(phone).toInstant()
    val end = date.plusDays(1).atStartOfDay(phone).toInstant()
    val phoneOffsets = setOf(phone.rules.getOffset(start), phone.rules.getOffset(end.minusSeconds(1)))
    return if (usual in phoneOffsets) phone else usual
}

/**
 * [zoneOfDay] from Health Connect: a day's workouts and nights, else its steps, else its heart
 * rate, read over every instant the day can cover anywhere on earth.
 *
 * Sessions first because their offsets were the ones to trust, measured on the phone around a
 * flight from Germany to Ecuador (07.11.2026): every workout and night carried +01:00 up to the
 * travel day and -05:00 after it, from both writers. The continuous types did not agree with
 * themselves: the watch's app stamped 720 of the travel day's heart-rate records -05:00, hours
 * spent in Germany among them, while Health Sync changed only a day later. A record count is
 * no measure of time either -- one Health Sync record holds an hour of readings.
 */
suspend fun HealthRepository.dayZone(date: LocalDate, phone: ZoneId = HealthRepository.DEFAULT_ZONE): ZoneId =
    recordedDayZone(date, phone) ?: phone

/** [dayZone], or null where the day has no record to tell. */
suspend fun HealthRepository.recordedDayZone(date: LocalDate, phone: ZoneId = HealthRepository.DEFAULT_ZONE): ZoneId? {
    // UTC+14 begins a day first and UTC-12 ends it last.
    val range = TimeRangeFilter.between(
        date.atStartOfDay(ZoneOffset.ofHours(EARLIEST_HOURS)).toInstant(),
        date.plusDays(1).atStartOfDay(ZoneOffset.ofHours(LATEST_HOURS)).toInstant(),
    )
    val probes: List<suspend () -> List<Pair<Instant, ZoneOffset>>> = listOf(
        {
            read(ExerciseSessionRecord::class, range).mapNotNull { r -> r.endZoneOffset?.let { r.endTime to it } } +
                read(SleepSessionRecord::class, range).mapNotNull { r -> r.endZoneOffset?.let { r.endTime to it } }
        },
        {
            val moments = mutableListOf<Pair<Instant, ZoneOffset>>()
            forEachPage(StepsRecord::class, range) { page ->
                page.forEach { r -> r.startZoneOffset?.let { moments += r.startTime to it } }
            }
            moments
        },
        {
            val moments = mutableListOf<Pair<Instant, ZoneOffset>>()
            forEachPage(HeartRateRecord::class, range) { page ->
                page.forEach { r -> r.startZoneOffset?.let { moments += r.startTime to it } }
            }
            moments
        },
    )
    for (probe in probes) {
        val moments = catching { probe() }.getOrDefault(emptyList())
            .filter { (time, offset) -> time.atOffset(offset).toLocalDate() == date }
        if (moments.isNotEmpty()) return zoneOfDay(date, moments, phone)
    }
    return null
}

/**
 * Where a day's clock is not a plain 24 hours. Each day is drawn whole on its own clock (see
 * [dayZone]), so where two days' clocks differ, hours at that edge fall on both days' charts
 * or on neither: back from Ecuador (22./23.11.2026) the 23rd began at midnight in Germany, six
 * hours before the 22nd ended at midnight in Ecuador, and the airport showed twice; flying
 * out, six hours fell between the two days. Drawing such a day whole -- midnight where it
 * began to midnight where it ended -- needs a clock that switches within it, which Android
 * does not let an app build ([java.time.zone.ZoneRulesProvider] is a blocked API there), so
 * the change is explained and marked instead.
 *
 * A summer-time change is the other kind: the day is already 23 or 25 hours long, and [span]
 * is the hour that came twice, or the moment one was skipped.
 */
data class ClockChange(
    val kind: Kind,
    val from: ZoneOffset,
    val to: ZoneOffset,
    /** The hours concerned: inside the day where doubled, outside it where missing. */
    val span: ClosedRange<Instant>,
    /** The other day a travel change lies between; null for summer time. */
    val neighbour: LocalDate? = null,
    /** The day's own length, for summer time. */
    val dayLength: Duration? = null,
) {
    enum class Kind { SUMMER_TIME, DOUBLED_AT_START, DOUBLED_AT_END, MISSING_BEFORE, MISSING_AFTER }

    val length: Duration get() = Duration.between(span.start, span.endInclusive)

    /** Whether [span] lies on this day's chart, so it can be marked there. */
    val onChart: Boolean get() = kind == Kind.SUMMER_TIME || kind == Kind.DOUBLED_AT_START || kind == Kind.DOUBLED_AT_END
}

/**
 * The clock changes of [date] drawn on [zone]: summer time within it, and against the days
 * before and after on theirs. A neighbour with no records, null, has no clock to compare.
 */
fun clockChanges(date: LocalDate, zone: ZoneId, previous: ZoneId?, next: ZoneId?): List<ClockChange> {
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val changes = mutableListOf<ClockChange>()
    zone.rules.nextTransition(start)?.takeIf { it.instant < end }?.let { transition ->
        val repeated = if (transition.isOverlap) transition.duration.abs() else Duration.ZERO
        changes += ClockChange(
            ClockChange.Kind.SUMMER_TIME,
            transition.offsetBefore,
            transition.offsetAfter,
            transition.instant..transition.instant.plus(repeated),
            dayLength = Duration.between(start, end),
        )
    }
    previous?.let { other ->
        val previousEnd = date.atStartOfDay(other).toInstant()
        if (previousEnd != start) {
            changes += ClockChange(
                if (start < previousEnd) ClockChange.Kind.DOUBLED_AT_START else ClockChange.Kind.MISSING_BEFORE,
                other.rules.getOffset(previousEnd),
                zone.rules.getOffset(start),
                minOf(start, previousEnd)..maxOf(start, previousEnd),
                neighbour = date.minusDays(1),
            )
        }
    }
    next?.let { other ->
        val nextStart = date.plusDays(1).atStartOfDay(other).toInstant()
        if (nextStart != end) {
            changes += ClockChange(
                if (nextStart < end) ClockChange.Kind.DOUBLED_AT_END else ClockChange.Kind.MISSING_AFTER,
                zone.rules.getOffset(end),
                other.rules.getOffset(nextStart),
                minOf(end, nextStart)..maxOf(end, nextStart),
                neighbour = date.plusDays(1),
            )
        }
    }
    return changes
}

/**
 * Where the clock actually changed, as far as workouts and nights tell: from the last one on
 * the old clock to the first on the new. Only those, measured on the flight out (07.11.2026):
 * both writers stamped them right, while the watch's heart rate carried the new offset for
 * hours still spent at home -- stamped when it synced, after landing -- and would put the
 * change too early. Nobody starts a workout in the air, and a night on the plane keeps the
 * old clock, so the change lies in [window]. Back from Ecuador, 22.11.2026: Galapagos to
 * Quito in the afternoon, Quito to Berlin overnight -- two changes, neither at midnight.
 */
data class ClockShift(val from: ZoneOffset, val to: ZoneOffset, val window: ClosedRange<Instant>)

/**
 * The [ClockShift]s that touch [day]: [moments] are workouts' and nights' starts and ends with
 * their offsets, from either side of it. Moments in time order; wherever the offset changes
 * from one to the next, the clock changed between them.
 */
fun clockShifts(moments: List<Pair<Instant, ZoneOffset>>, day: ClosedRange<Instant>): List<ClockShift> =
    moments.sortedBy { it.first }
        .zipWithNext()
        .filter { (a, b) -> a.second != b.second }
        .map { (a, b) -> ClockShift(a.second, b.second, a.first..b.first) }
        .filter { it.window.start < day.endInclusive && it.window.endInclusive > day.start }

private const val EARLIEST_HOURS = 14
private const val LATEST_HOURS = -12
