package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OvulationTestRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * What was recorded on one local calendar day, merged across every writing app.
 *
 * The cycle types have no aggregate metric, so there is no platform deduplication to lean on.
 * They are merged by *day* instead: a day bled if any writer says so, and carries the heaviest
 * flow any writer reported. Nothing is counted, so two apps logging the same day cannot
 * double anything.
 */
data class CycleDay(
    /** Bleeding recorded as a period or a flow entry. */
    val bleeding: Boolean = false,
    /** Heaviest flow code reported for the day, or null where no flow entry exists. */
    val flow: Int? = null,
    /** Intermenstrual bleeding. Deliberately not bleeding: it does not start a cycle. */
    val spotting: Boolean = false,
    /** Most telling ovulation test result of the day, or null where none was taken. */
    val ovulationTest: Int? = null,
    /** A cervical mucus observation exists. Shown as a mark; its value is not interpreted. */
    val mucus: Boolean = false,
    /** Basal body temperature in °C, or null. */
    val temperature: Double? = null,
)

/**
 * One cycle: from the first day of a period to the day before the next one.
 *
 * [length] is null for the cycle still running, whose end is not yet known -- it must not be
 * shown as a short cycle.
 */
data class Cycle(
    val start: LocalDate,
    val length: Int?,
    /** Bleeding days at the start of the cycle, the period itself. */
    val periodLength: Int,
    /** Index 0 is cycle day 1. Covers [length] days, or up to the last day with data. */
    val days: List<CycleDay>,
)

/** Median and range over completed cycles; null where there are none. */
data class CycleStats(
    val completed: Int,
    val medianLength: Int,
    val shortest: Int,
    val longest: Int,
    val medianPeriodLength: Int,
)

/** Everything the cycle view reads, as fetched. Lists are empty where access was not granted. */
data class CycleRecords(
    val periods: List<MenstruationPeriodRecord> = emptyList(),
    val flows: List<MenstruationFlowRecord> = emptyList(),
    val spotting: List<IntermenstrualBleedingRecord> = emptyList(),
    val ovulationTests: List<OvulationTestRecord> = emptyList(),
    val mucus: List<CervicalMucusRecord> = emptyList(),
    val temperatures: List<BasalBodyTemperatureRecord> = emptyList(),
)

/**
 * Merges every writer's records into one entry per local calendar day.
 *
 * Dates come from each record's own zone offset, falling back to [zone]: a flow entry logged
 * at 23:30 while travelling belongs to the day it was logged on, not to whatever day that
 * instant is at home. Some apps write only period records and some only daily flow entries,
 * so bleeding is taken from either.
 */
fun mergeCycleDays(records: CycleRecords, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, CycleDay> {
    val days = mutableMapOf<LocalDate, CycleDay>()
    fun update(date: LocalDate, change: (CycleDay) -> CycleDay) {
        days[date] = change(days[date] ?: EMPTY)
    }

    records.periods.forEach { period ->
        val first = localDate(period.startTime, period.startZoneOffset, zone)
        // The end is exclusive: a period written as midnight to midnight covers the days
        // before its end, and counting the end day would add a bleeding day nobody logged.
        val last = localDate(period.endTime.minusNanos(1), period.endZoneOffset, zone).coerceAtLeast(first)
        generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }
            .forEach { update(it) { day -> day.copy(bleeding = true) } }
    }
    records.flows.forEach { flow ->
        update(localDate(flow.time, flow.zoneOffset, zone)) { day ->
            day.copy(bleeding = true, flow = maxOf(day.flow ?: flow.flow, flow.flow))
        }
    }
    records.spotting.forEach {
        update(localDate(it.time, it.zoneOffset, zone)) { day -> day.copy(spotting = true) }
    }
    records.ovulationTests.forEach { test ->
        update(localDate(test.time, test.zoneOffset, zone)) { day ->
            val kept = day.ovulationTest
            day.copy(ovulationTest = if (kept == null || rank(test.result) > rank(kept)) test.result else kept)
        }
    }
    records.mucus.forEach {
        update(localDate(it.time, it.zoneOffset, zone)) { day -> day.copy(mucus = true) }
    }
    // Basal temperature is by definition the waking reading, so a later one the same day --
    // a second app, or a re-measure after getting up -- is not the basal value.
    records.temperatures
        .groupBy { localDate(it.time, it.zoneOffset, zone) }
        .forEach { (date, readings) ->
            val waking = readings.minBy { it.time }
            update(date) { day -> day.copy(temperature = waking.temperature.inCelsius) }
        }
    return days
}

private fun localDate(time: Instant, offset: ZoneOffset?, zone: ZoneId): LocalDate =
    time.atZone(offset ?: zone).toLocalDate()

/** Order of evidence: a positive test outranks a high one, which outranks a negative. */
private fun rank(result: Int): Int = when (result) {
    OvulationTestRecord.RESULT_POSITIVE -> 3
    OvulationTestRecord.RESULT_HIGH -> 2
    OvulationTestRecord.RESULT_NEGATIVE -> 1
    else -> 0
}

/**
 * Splits merged days into cycles.
 *
 * A cycle starts on the first bleeding day after at least [minGap] days without bleeding.
 * The gap tolerance keeps a period with one unlogged day in the middle from reading as two
 * cycles two days apart -- a very common shape in hand-logged data.
 *
 * Only cycles that *start* inside [window] are returned, but each is kept whole even where it
 * runs past the window's end: the same rule as sleep, where trimming a session to the range
 * makes the clipped edge read as the real one. Days before the first start are dropped, since
 * without a day 1 they cannot be placed.
 *
 * [lastDay] ends the running cycle, normally today.
 */
fun buildCycles(
    days: Map<LocalDate, CycleDay>,
    window: ClosedRange<LocalDate>,
    lastDay: LocalDate,
    minGap: Int = MIN_GAP_DAYS,
): List<Cycle> {
    val starts = cycleStarts(days, minGap)
    return starts.mapIndexedNotNull { index, start ->
        if (start !in window) return@mapIndexedNotNull null
        val next = starts.getOrNull(index + 1)
        val length = next?.let { ChronoUnit.DAYS.between(start, it).toInt() }
        val end = next?.minusDays(1) ?: lastDay
        val span = ChronoUnit.DAYS.between(start, end).toInt() + 1
        val cycleDays = (0 until span.coerceAtLeast(0)).map { days[start.plusDays(it.toLong())] ?: EMPTY }
        Cycle(
            start = start,
            length = length,
            periodLength = periodLength(cycleDays, minGap),
            days = cycleDays,
        )
    }
}

/** Through the last bleeding day of the opening run, bridging the same gaps as [cycleStarts]. */
private fun periodLength(days: List<CycleDay>, minGap: Int): Int {
    var last = -1
    var dry = 0
    for ((index, day) in days.withIndex()) {
        if (day.bleeding) {
            last = index
            dry = 0
        } else if (++dry >= minGap) {
            break
        }
    }
    return last + 1
}

/** First days of bleeding runs separated by at least [minGap] dry days. */
internal fun cycleStarts(days: Map<LocalDate, CycleDay>, minGap: Int = MIN_GAP_DAYS): List<LocalDate> {
    val bleeding = days.filterValues { it.bleeding }.keys.sorted()
    val starts = mutableListOf<LocalDate>()
    var previous: LocalDate? = null
    bleeding.forEach { day ->
        val dry = previous?.let { ChronoUnit.DAYS.between(it, day) - 1 }
        if (dry == null || dry >= minGap) starts += day
        previous = day
    }
    return starts
}

/**
 * Median and range rather than a mean: one missed period in a year of logging produces a
 * double-length "cycle", and a mean would carry that into every number shown.
 */
fun cycleStats(cycles: List<Cycle>): CycleStats? {
    val completed = cycles.filter { it.length != null }
    if (completed.isEmpty()) return null
    val lengths = completed.mapNotNull { it.length }.sorted()
    return CycleStats(
        completed = completed.size,
        medianLength = lengths.median(),
        shortest = lengths.first(),
        longest = lengths.last(),
        medianPeriodLength = completed.map { it.periodLength }.sorted().median(),
    )
}

private fun List<Int>.median(): Int = this[size / 2]

private val EMPTY = CycleDay()

/**
 * Dry days needed between two bleeding runs for the second to start a new cycle. Shorter
 * gaps are read as one period with unlogged days in it.
 */
const val MIN_GAP_DAYS = 3
