package de.steppicrew.healthconnectview.health

import java.time.LocalDate
import java.time.ZoneId

/** One day's line in the report: each part's average, or null where it had no reading. */
data class ReportDay(val date: LocalDate, val morning: PartAverage?, val evening: PartAverage?)

/**
 * What a blood pressure report says, before any of it is drawn.
 *
 * [days] holds only days with a reading: a log of a year with thirty measured days should not
 * be three hundred lines of dashes. [readings] is every sitting, oldest first, because a
 * per-day average hides the one high reading a doctor asks about.
 */
data class PressureReport(
    val first: LocalDate,
    val last: LocalDate,
    val overall: PartAverage?,
    val parts: DayPartSplit,
    val days: List<ReportDay>,
    val readings: List<PressureReading>,
)

/**
 * The report for [first] through [last], by the same day rule as the detail view: a reading
 * after midnight belongs to the evening before, so one at 01:00 on the day after [last] is in
 * and one at 01:00 on [first] is not. Read the input over [dayPartWindow] and nothing is lost
 * or claimed twice at either end.
 */
fun pressureReport(
    readings: List<PressureReading>,
    first: LocalDate,
    last: LocalDate,
    zone: ZoneId,
): PressureReport {
    val kept = distinctReadings(readings)
        .filter { dayPartOf(it.time, zone).first in first..last }
        .sortedBy { it.time }
    val days = kept.groupBy { dayPartOf(it.time, zone).first }.map { (date, ofDay) ->
        val split = splitByDayPart(ofDay, zone)
        ReportDay(date, split.morning, split.evening)
    }
    return PressureReport(
        first = first,
        last = last,
        overall = averageOf(kept),
        parts = splitByDayPart(kept, zone),
        days = days,
        readings = kept,
    )
}
