package de.steppicrew.healthconnectview.health

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * The PDF reports beside blood pressure's: weight, resting heart rate and blood glucose. What
 * each says, before any of it is drawn -- see `export/ReadingReportPdf.kt` for the drawing.
 *
 * Values stay in the unit Health Connect stores (kg, bpm, mmol/L); the drawing converts, as
 * every other surface does.
 */

/** One stored reading. [origin] is the writing app's package, for [oneWriterPerDay]. */
data class Reading(val time: Instant, val value: Double, val origin: String)

/** Count, mean and range of some values. */
data class ValueStats(val count: Int, val mean: Double, val low: Double, val high: Double)

fun statsOf(values: Collection<Double>): ValueStats? =
    if (values.isEmpty()) null else ValueStats(values.size, values.average(), values.min(), values.max())

/** A stretch of days -- a day or a week from its Monday -- with the stats of its readings. */
data class PeriodStats(val start: LocalDate, val stats: ValueStats)

/**
 * Each day's readings from the one app with the most of them that day, oldest first.
 *
 * Two apps copying the same scale or watch store every reading twice, sometimes seconds apart,
 * so a plain list double-counts and a report would show each weigh-in two times. One writer a
 * day is the rule the HRV nights and the daily means already use; a filter to one source
 * leaves only that one anyway.
 */
fun oneWriterPerDay(readings: List<Reading>, zone: ZoneId): List<Reading> =
    readings.groupBy { it.time.atZone(zone).toLocalDate() }
        .values
        .flatMap { day -> day.groupBy { it.origin }.values.maxBy { it.size } }
        .sortedBy { it.time }

private fun List<Reading>.inDays(first: LocalDate, last: LocalDate, zone: ZoneId) =
    filter { it.time.atZone(zone).toLocalDate() in first..last }

private fun List<Reading>.byPeriod(zone: ZoneId, startOf: (LocalDate) -> LocalDate): List<PeriodStats> =
    groupBy { startOf(it.time.atZone(zone).toLocalDate()) }
        .mapNotNull { (start, list) -> statsOf(list.map { it.value })?.let { PeriodStats(start, it) } }
        .sortedBy { it.start }

private fun monday(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

// --- Weight ---

/**
 * A weight log: where it started and ended, the range between, a mean per week, and every
 * weigh-in. Weeks rather than days, because the question at an appointment is the course over
 * months, and a day's noise of half a kilo says nothing about it.
 */
data class WeightReport(
    val first: LocalDate,
    val last: LocalDate,
    val overall: ValueStats?,
    val weeks: List<PeriodStats>,
    val readings: List<Reading>,
) {
    val firstReading: Reading? get() = readings.firstOrNull()
    val lastReading: Reading? get() = readings.lastOrNull()

    /** Last weigh-in minus first; null with fewer than two. */
    val change: Double? get() = if (readings.size < 2) null else readings.last().value - readings.first().value
}

fun weightReport(readings: List<Reading>, first: LocalDate, last: LocalDate, zone: ZoneId): WeightReport {
    val kept = oneWriterPerDay(readings, zone).inDays(first, last, zone)
    return WeightReport(
        first = first,
        last = last,
        overall = statsOf(kept.map { it.value }),
        weeks = kept.byPeriod(zone, ::monday),
        readings = kept,
    )
}

// --- Resting heart rate ---

/** One day's resting heart rate and the four-week mean ending on it, where there is one. */
data class RestingDay(val date: LocalDate, val value: Double, val rolling: Double?)

/**
 * A resting heart rate log: one value a day, each beside the wearer's own four-week mean,
 * because a resting rate is read against the person's usual level rather than a norm.
 */
data class RestingReport(
    val first: LocalDate,
    val last: LocalDate,
    val overall: ValueStats?,
    val weeks: List<PeriodStats>,
    val days: List<RestingDay>,
    /** Mean of the first and last [ROLLING_DAYS] of the window, when the window holds both. */
    val firstMonth: ValueStats?,
    val lastMonth: ValueStats?,
)

/**
 * [readings] should reach [ROLLING_DAYS] - 1 days before [first], so the first days of the
 * window have their four-week mean too; those days are not listed.
 */
fun restingReport(readings: List<Reading>, first: LocalDate, last: LocalDate, zone: ZoneId): RestingReport {
    val daily = oneWriterPerDay(readings, zone)
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .mapValues { (_, day) -> day.map { it.value }.average() }
    val rolling = rollingMean(daily, first, last).toMap()
    val days = daily.filterKeys { it in first..last }.toSortedMap().map { (date, value) -> RestingDay(date, value, rolling[date]) }
    val values = days.map { it.value }
    // Only worth comparing when the window is long enough for the two months not to overlap.
    val long = first.plusDays(2L * ROLLING_DAYS - 1) <= last
    return RestingReport(
        first = first,
        last = last,
        overall = statsOf(values),
        weeks = days.groupBy { monday(it.date) }
            .mapNotNull { (start, list) -> statsOf(list.map { it.value })?.let { PeriodStats(start, it) } }
            .sortedBy { it.start },
        days = days,
        firstMonth = if (long) statsOf(days.filter { it.date < first.plusDays(ROLLING_DAYS.toLong()) }.map { it.value }) else null,
        lastMonth = if (long) statsOf(days.filter { it.date > last.minusDays(ROLLING_DAYS.toLong()) }.map { it.value }) else null,
    )
}

// --- Blood glucose ---

/**
 * One glucose reading with the context its writer gave. [relation] and [meal] are Health
 * Connect's `RELATION_TO_MEAL_*` and `MealType.MEAL_TYPE_*` codes, 0 when unknown.
 */
data class GlucoseReading(val time: Instant, val mmol: Double, val relation: Int, val meal: Int, val origin: String)

/**
 * The bands of the international consensus on time in range: very low below 54 mg/dL
 * (3.0 mmol/L), low below 70 (3.9), in range to 180 (10.0), high to 250 (13.9), very high above.
 * Made for continuous monitors and people with diabetes; for spot readings it is a share of
 * readings, not of time, and the report says so.
 *
 * The consensus gives each unit its own round limits, and at the factor of 18 they do not
 * quite meet: 70 mg/dL is stored as 3.889 mmol/L, below 3.9. The lower limit of the range sits
 * between the two, so 70 mg/dL and 3.9 mmol/L are both in range and 69 and 3.8 both low.
 */
enum class GlucoseBand(val below: Double) {
    VERY_LOW(3.0),
    LOW(3.85),
    IN_RANGE(10.0 + BAND_EDGE),
    HIGH(13.9 + BAND_EDGE),
    VERY_HIGH(Double.MAX_VALUE),
    ;

    companion object {
        fun of(mmol: Double): GlucoseBand = entries.first { mmol < it.below }
    }
}

/** 10.0 itself is in range and 13.9 is high: the consensus bands are closed at the top. */
private const val BAND_EDGE = 1e-9

/**
 * A glucose log: overall, by relation to a meal (a fasting value and one after eating answer
 * different questions and must not be averaged together), the share in each consensus band, a
 * line per measured day, and every reading with its context.
 */
data class GlucoseReport(
    val first: LocalDate,
    val last: LocalDate,
    val overall: ValueStats?,
    /** Keyed by relation code, in the order fasting, before, after, general, unknown; empty ones left out. */
    val byRelation: List<Pair<Int, ValueStats>>,
    /** Readings per band, in [GlucoseBand] order. */
    val bands: List<Int>,
    val days: List<PeriodStats>,
    val readings: List<GlucoseReading>,
)

/** Fasting first: it is the value a doctor reads first. */
val RELATION_ORDER = listOf(2, 3, 4, 1, 0)

fun glucoseReport(readings: List<GlucoseReading>, first: LocalDate, last: LocalDate, zone: ZoneId): GlucoseReport {
    val chosen = oneWriterPerDay(readings.map { Reading(it.time, it.mmol, it.origin) }, zone).toSet()
    val kept = readings
        .filter { Reading(it.time, it.mmol, it.origin) in chosen }
        .filter { it.time.atZone(zone).toLocalDate() in first..last }
        .distinctBy { Triple(it.time, it.mmol, it.origin) }
        .sortedBy { it.time }
    val byRelation = kept.groupBy { if (it.relation in RELATION_ORDER) it.relation else 0 }
    return GlucoseReport(
        first = first,
        last = last,
        overall = statsOf(kept.map { it.mmol }),
        byRelation = RELATION_ORDER.mapNotNull { code -> byRelation[code]?.let { list -> statsOf(list.map { it.mmol })?.let { code to it } } },
        bands = GlucoseBand.entries.map { band -> kept.count { GlucoseBand.of(it.mmol) == band } },
        days = kept.map { Reading(it.time, it.mmol, it.origin) }.byPeriod(zone) { it },
        readings = kept,
    )
}
