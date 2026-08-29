package de.steppicrew.healthconnectview.health

import androidx.annotation.StringRes
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.R
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * A window of history that can be stepped backwards and forwards.
 *
 * Distinct from [TimeRange], which means "the last N days from now" and cannot be moved. A
 * span is anchored to calendar boundaries and carries an offset, so "the week before last" is
 * expressible -- which is what makes data older than a year reachable at all. [TimeRange]
 * tops out at 365 days from today, so anything before that simply could not be requested.
 */
enum class Span(@param:StringRes val labelRes: Int) {
    DAY(R.string.span_day),
    WEEK(R.string.span_week),
    MONTH(R.string.span_month),
    YEAR(R.string.span_year);

    /**
     * How far back the window's start sits from its end. Calendar units, not fixed day
     * counts, so a month step lands on the same day of the month and a year step survives
     * leap years.
     */
    private val period: Period
        get() = when (this) {
            DAY -> Period.ofDays(1)
            WEEK -> Period.ofDays(7)
            MONTH -> Period.ofDays(28)
            YEAR -> Period.ofYears(1)
        }

    /** Bucket width for aggregation: a day for short spans, longer ones for a year. */
    val bucket: Period
        get() = when (this) {
            DAY -> Period.ofDays(1)
            WEEK -> Period.ofDays(1)
            MONTH -> Period.ofDays(1)
            YEAR -> Period.ofDays(7)
        }

    /**
     * Exclusive end of the window, [offset] steps back from today. Offset 0 ends after today,
     * so the current period is included in full.
     */
    fun endDate(offset: Int, today: LocalDate = LocalDate.now()): LocalDate {
        var end = today.plusDays(1)
        repeat(offset) { end = end.minus(period) }
        return end
    }

    /** Inclusive start of the window. */
    fun startDate(offset: Int, today: LocalDate = LocalDate.now()): LocalDate =
        endDate(offset, today).minus(period)

    /**
     * Local-time filter for aggregation, snapped to midnight.
     *
     * Health Connect slices buckets from the filter's start, so an unaligned start yields
     * buckets straddling two calendar days and returns null values.
     */
    fun localFilter(offset: Int, today: LocalDate = LocalDate.now()): TimeRangeFilter =
        TimeRangeFilter.between(
            startDate(offset, today).atStartOfDay(),
            endDate(offset, today).atStartOfDay(),
        )

    /** Instant filter for reading raw records over the same window. */
    fun instantFilter(
        offset: Int,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): TimeRangeFilter = TimeRangeFilter.between(
        startDate(offset, today).atStartOfDay(zone).toInstant(),
        endDate(offset, today).atStartOfDay(zone).toInstant(),
    )

    /**
     * Whether this window reaches further back than 30 days and so needs
     * READ_HEALTH_DATA_HISTORY. Without it Health Connect silently returns only the last 30
     * days, which looks exactly like having no older data.
     */
    fun needsHistoryPermission(offset: Int, today: LocalDate = LocalDate.now()): Boolean =
        startDate(offset, today).isBefore(today.minusDays(HISTORY_FREE_DAYS))

    private companion object {
        const val HISTORY_FREE_DAYS = 30L
    }
}
