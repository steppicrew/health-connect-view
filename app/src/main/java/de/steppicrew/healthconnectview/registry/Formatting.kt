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
    /**
     * [value] with exactly [decimals] places where its unit fixes them -- mg/dL is whole,
     * mmol/L has one -- and by magnitude, as [number], where it does not (null).
     */
    fun number(value: Double, decimals: Int?, locale: Locale = Locale.getDefault()): String {
        if (decimals == null) return number(value, locale)
        return NumberFormat.getInstance(locale).apply {
            maximumFractionDigits = decimals
            minimumFractionDigits = decimals
        }.format(value)
    }

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

    /**
     * A number at an exact number of decimals, for an axis label.
     *
     * [number] chooses decimals from the value's own magnitude, which is right for a reading
     * shown on its own and wrong for a column of gridline labels: on a scale stepping by 0.5
     * the whole values would print without a decimal and the halves with one, so neighbouring
     * labels read as different kinds of number. The step decides here, so every label on an
     * axis states the same precision -- and a whole-numbered step states none.
     */
    /**
     * A change, with "+" before a rise, to [decimals] places. The sign is taken from the
     * rounded value, so a loss of 0,02 kg reads "0,0", not "-0,0": a minus before a zero
     * claims a direction the shown figure does not have.
     */
    fun signed(value: Double, decimals: Int, locale: Locale = Locale.getDefault()): String {
        val rounded = java.math.BigDecimal(value).setScale(decimals, java.math.RoundingMode.HALF_EVEN).toDouble()
        if (rounded == 0.0) return axisLabel(0.0, decimals, locale)
        return (if (rounded > 0) "+" else "") + axisLabel(rounded, decimals, locale)
    }

    fun axisLabel(value: Double, decimals: Int, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getInstance(locale).apply {
            maximumFractionDigits = decimals
            minimumFractionDigits = decimals
        }.format(value)

    fun integer(value: Long, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getInstance(locale).format(value)

    /**
     * Date and time to the minute. MEDIUM would add seconds, which are noise for a health
     * reading and made a record's start and end read inconsistently -- the start showed
     * seconds while the end, formatted SHORT, did not.
     */
    fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
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

    /** Clock time alone, for a moment already known to fall on the day being shown. */
    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(zone)
            .format(instant)

    /** Day and month without a year, for an axis tick where the year is already established. */
    fun dayAndMonth(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            .withZone(zone)
            .format(instant)

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

    /**
     * [metresPerSecond] as a pace, minutes and seconds per km or per mile, the way runners
     * read speed: "5:12 min/km". Null when barely moving -- a pause is hours per km, and a number
     * that large says nothing a runner can use.
     */
    fun pace(metresPerSecond: Double, imperial: Boolean = Quantity.DISTANCE.alternateShown): String? {
        val metres = if (imperial) METRES_PER_MILE else 1000.0
        val seconds = (metres / metresPerSecond).takeIf { it.isFinite() && it < MAX_PACE_SECONDS } ?: return null
        val total = kotlin.math.round(seconds).toLong()
        return String.format(Locale.getDefault(), "%d:%02d min/%s", total / 60, total % 60, if (imperial) "mi" else "km")
    }

    private const val METRES_PER_MILE = 1609.344
    private const val MAX_PACE_SECONDS = 60.0 * 60
}
