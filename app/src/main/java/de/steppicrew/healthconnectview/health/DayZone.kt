package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.util.catching
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
suspend fun HealthRepository.dayZone(date: LocalDate, phone: ZoneId = HealthRepository.DEFAULT_ZONE): ZoneId {
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
    return phone
}

private const val EARLIEST_HOURS = 14
private const val LATEST_HOURS = -12
