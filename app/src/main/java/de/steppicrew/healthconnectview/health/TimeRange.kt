package de.steppicrew.healthconnectview.health

import androidx.annotation.StringRes
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Selectable window of history. */
enum class TimeRange(@param:StringRes val labelRes: Int, val days: Long) {
    WEEK(R.string.range_week, 7L),
    MONTH(R.string.range_month, 30L),
    QUARTER(R.string.range_quarter, 90L),
    YEAR(R.string.range_year, 365L);

    /** Anything beyond 30 days needs the history permission to return complete data. */
    val needsHistoryPermission: Boolean get() = days > 30L

    fun start(now: Instant = Instant.now()): Instant = now.minus(days, ChronoUnit.DAYS)

    /** Instant-based filter, for reading raw records. */
    fun filter(now: Instant = Instant.now()): TimeRangeFilter =
        TimeRangeFilter.between(start(now), now)

    /**
     * Local-time filter. Aggregation buckets by calendar day, which is a local-time
     * concept, so grouped queries must not use instants.
     */
    fun localFilter(zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): TimeRangeFilter =
        TimeRangeFilter.between(
            LocalDateTime.ofInstant(start(now), zone),
            LocalDateTime.ofInstant(now, zone),
        )
}
