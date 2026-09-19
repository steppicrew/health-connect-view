package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A single calendar day, local time, for a dashboard tile.
 *
 * Separate from [TimeRange], which means "the last N days from now". A day tile needs exact
 * midnight-to-midnight bounds: Health Connect requires a LocalDateTime filter for aggregation,
 * and an unaligned window silently returns nothing for a daily total.
 */
fun dayFilter(date: LocalDate): TimeRangeFilter =
    TimeRangeFilter.between(date.atStartOfDay(), date.plusDays(1).atStartOfDay())

/** Instant bounds for the same day, for reading raw records. */
fun dayInstants(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): TimeRangeFilter =
    TimeRangeFilter.between(
        date.atStartOfDay(zone).toInstant(),
        date.plusDays(1).atStartOfDay(zone).toInstant(),
    )

/**
 * A fixed window of history ending now, for internal probes.
 *
 * Not user-selectable: every screen that lets the user choose a window uses [Span], which
 * carries an offset and so can reach data older than a year. This deliberately carries no
 * label -- it is never rendered as a chip.
 */
enum class TimeRange(val days: Long) {
    WEEK(7L),
    MONTH(30L),
    QUARTER(90L),
    YEAR(365L);

    /** Anything beyond 30 days needs the history permission to return complete data. */
    val needsHistoryPermission: Boolean get() = days > 30L

    fun start(now: Instant = Instant.now()): Instant = now.minus(days, ChronoUnit.DAYS)

    /** Instant-based filter, for reading raw records. */
    fun filter(now: Instant = Instant.now()): TimeRangeFilter =
        TimeRangeFilter.between(start(now), now)

    /**
     * Local-time filter for day-grouped aggregation.
     *
     * The window is snapped to midnight boundaries. Health Connect slices a Period.ofDays(1)
     * request from the filter's start instant, so an unaligned start produces buckets running
     * (say) 20:58 to 20:58 -- which straddle two calendar days and return no value for a
     * "daily total". Aggregation is a local-time concept, so this must not use instants.
     */
    fun localFilter(zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): TimeRangeFilter {
        val endOfToday = LocalDateTime.ofInstant(now, zone).toLocalDate().plusDays(1).atStartOfDay()
        return TimeRangeFilter.between(endOfToday.minusDays(days), endOfToday)
    }
}
