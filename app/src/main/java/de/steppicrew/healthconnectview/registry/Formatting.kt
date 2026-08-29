package de.steppicrew.healthconnectview.registry

import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Locale-aware formatting. Never string-concatenate numbers: a decimal comma and digit
 * grouping are expected in many of the supported locales.
 */
object Formatting {

    /** Decimals scale with magnitude so small and large values both stay readable. */
    fun number(value: Double, locale: Locale = Locale.getDefault()): String {
        val digits = when {
            kotlin.math.abs(value) >= 100.0 -> 0
            kotlin.math.abs(value) >= 10.0 -> 1
            else -> 2
        }
        return NumberFormat.getInstance(locale).apply {
            maximumFractionDigits = digits
            minimumFractionDigits = 0
        }.format(value)
    }

    fun integer(value: Long, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getInstance(locale).format(value)

    fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(zone)
            .format(instant)

    fun date(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(zone)
            .format(instant)

    /**
     * A record's time span: a single time for an instantaneous record, "start - end" for an
     * interval. Without the end, a whole-day summary record and a fifteen-minute one look
     * identical, and the summary reads as a stray midnight entry.
     *
     * The date is shown once; only the end's clock time is appended when the record ends on
     * the same day, which is the common case.
     */
    fun timeSpan(
        start: Instant,
        end: Instant?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (end == null || end == start) return dateTime(start, zone)

        val startLocal = start.atZone(zone)
        val endLocal = end.atZone(zone)
        val endFormat = if (startLocal.toLocalDate() == endLocal.toLocalDate()) {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        } else {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        }
        val endText = endFormat.withLocale(Locale.getDefault()).withZone(zone).format(end)
        return dateTime(start, zone) + " – " + endText
    }

    /** Compact duration such as "7h 32m"; the unit letters come from resources at call sites. */
    fun duration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        return when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m"
            else -> "${duration.seconds}s"
        }
    }
}
