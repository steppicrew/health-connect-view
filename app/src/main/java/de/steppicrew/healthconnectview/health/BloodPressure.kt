package de.steppicrew.healthconnectview.health

import de.steppicrew.healthconnectview.registry.ValueZones
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Morning and evening blood pressure, kept apart.
 *
 * The usual advice is to measure twice a day, and the two differ systematically: one blended
 * average hides a morning surge, which is often the reading that matters. So the detail view
 * shows the two parts of the day side by side.
 *
 * The rule: the day starts at [DAY_START] and splits at [EVENING_START]. A reading between
 * midnight and 04:00 is someone measuring late before bed, so it belongs to the evening of the
 * day before -- a plain clock split would call it the next morning. Every reading lands in
 * exactly one of the two parts; there is no "other" bucket to explain.
 */
enum class DayPart { MORNING, EVENING }

val DAY_START: LocalTime = LocalTime.of(4, 0)
val EVENING_START: LocalTime = LocalTime.of(14, 0)

data class PressureReading(val time: Instant, val systolic: Double, val diastolic: Double)

data class PartAverage(val count: Int, val systolic: Double, val diastolic: Double)

data class DayPartSplit(val morning: PartAverage?, val evening: PartAverage?)

/** The day a reading counts for, and which part of that day. */
fun dayPartOf(time: Instant, zone: ZoneId): Pair<LocalDate, DayPart> {
    val local = time.atZone(zone)
    val clock = local.toLocalTime()
    return when {
        clock < DAY_START -> local.toLocalDate().minusDays(1) to DayPart.EVENING
        clock < EVENING_START -> local.toLocalDate() to DayPart.MORNING
        else -> local.toLocalDate() to DayPart.EVENING
    }
}

/**
 * The instants bounding the readings that count for [first] through [last]: from 04:00 on the
 * first day to 04:00 after the last. Reading a calendar window would drop the last evening's
 * after-midnight readings and claim the previous day's.
 */
fun dayPartWindow(first: LocalDate, last: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
    ZonedDateTime.of(first, DAY_START, zone).toInstant() to
        ZonedDateTime.of(last.plusDays(1), DAY_START, zone).toInstant()

/**
 * Averages per part of the day, or null where a part has no reading.
 *
 * Several apps can hold the same measurement -- the cuff's app and a sync app copying it -- so
 * identical readings at the same instant count once. Averaging raw records is otherwise
 * sound here, unlike summing them: blood pressure has no aggregate that splits by time of
 * day, and one reading per sitting is what the average is of.
 */
fun splitByDayPart(readings: List<PressureReading>, zone: ZoneId): DayPartSplit {
    val distinct = readings.distinctBy { Triple(it.time, it.systolic, it.diastolic) }
    val byPart = distinct.groupBy { dayPartOf(it.time, zone).second }
    fun average(part: DayPart): PartAverage? = byPart[part]?.let { list ->
        PartAverage(
            count = list.size,
            systolic = list.map { it.systolic }.average(),
            diastolic = list.map { it.diastolic }.average(),
        )
    }
    return DayPartSplit(morning = average(DayPart.MORNING), evening = average(DayPart.EVENING))
}

/**
 * Where a reading sits, after the European Society of Hypertension's classification, with low
 * pressure added below it. Each value is classified on its own and the higher category wins,
 * as the classification specifies -- 128/92 is grade 1 because of the 92.
 *
 * In the order of [ValueZones.ZONE_COLORS], so the colours carry over: blue low, green normal,
 * yellow high normal, orange grade 1, red grade 2 and above.
 */
enum class PressureCategory { LOW, NORMAL, HIGH_NORMAL, GRADE_1, GRADE_2 }

/** Systolic bands: below 90 low, 90-129 normal, 130-139 high normal, 140-159, 160 and up. */
val SYSTOLIC_ZONES = ValueZones(listOf(90.0, 130.0, 140.0, 160.0), stepped = true)

/** Diastolic bands: below 60 low, 60-84 normal, 85-89 high normal, 90-99, 100 and up. */
val DIASTOLIC_ZONES = ValueZones(listOf(60.0, 85.0, 90.0, 100.0), stepped = true)

fun pressureCategory(systolic: Double, diastolic: Double): PressureCategory {
    val high = maxOf(SYSTOLIC_ZONES.zoneOf(systolic), DIASTOLIC_ZONES.zoneOf(diastolic))
    // Raised wins over low: 150/55 is a hypertension finding, not a low one.
    if (high > PressureCategory.NORMAL.ordinal) return PressureCategory.entries[high]
    val low = systolic < SYSTOLIC_ZONES.bounds.first() || diastolic < DIASTOLIC_ZONES.bounds.first()
    return if (low) PressureCategory.LOW else PressureCategory.NORMAL
}
