package de.steppicrew.healthconnectview.health

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * A type's days over a period, for the PDF report of any type with daily totals (step 82): the
 * figure the screen shows for the window, the days' mean, the lowest and highest day, and the
 * days themselves -- by day, or by week from its Monday where a table of days would run long.
 * Values are Health Connect's deduplicated daily totals, already in the shown unit.
 */
data class DailyReport(
    val first: LocalDate,
    val last: LocalDate,
    /** Every day of the period in order; null where Health Connect had nothing. */
    val days: List<Pair<LocalDate, Double?>>,
    /** The window's figure: a sum, or for an averaged type the days' mean. */
    val overall: Double?,
    val stats: ValueStats?,
    val lowest: LocalDate?,
    val highest: LocalDate?,
    /** Weeks from their Monday, where the period is longer than [DAY_TABLE_DAYS]; else empty. */
    val weeks: List<PeriodStats>,
)

/** [values] by day, for [first] through [last]; [averaged] for a type whose figure is a mean. */
fun dailyReport(first: LocalDate, last: LocalDate, values: Map<LocalDate, Double>, averaged: Boolean): DailyReport {
    val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.map { it to values[it] }.toList()
    val present = days.mapNotNull { (day, value) -> value?.let { day to it } }
    val stats = statsOf(present.map { it.second })
    val weeks = if (days.size > DAY_TABLE_DAYS) {
        present.groupBy { (day, _) -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .mapNotNull { (monday, week) -> statsOf(week.map { it.second })?.let { PeriodStats(monday, it) } }
    } else {
        emptyList()
    }
    return DailyReport(
        first = first,
        last = last,
        days = days,
        overall = stats?.let { if (averaged) it.mean else present.sumOf { (_, value) -> value } },
        stats = stats,
        lowest = present.minByOrNull { it.second }?.first,
        highest = present.maxByOrNull { it.second }?.first,
        weeks = weeks,
    )
}

/** Up to two months the report lists every day; past that, weeks. */
const val DAY_TABLE_DAYS = 62
