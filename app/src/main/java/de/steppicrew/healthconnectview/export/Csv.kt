package de.steppicrew.healthconnectview.export

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * CSV as RFC 4180 writes it: comma-separated, CRLF line ends, fields quoted only where they
 * must be. Numbers use a dot and no grouping whatever the device language -- a file is read by
 * other programs, and "1.686" written by a German phone would be read as one and a bit.
 */
object Csv {

    fun line(fields: List<String>): String = fields.joinToString(",") { escape(it) } + "\r\n"

    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    /** Plain decimal, no exponent, no trailing zeros: 8.42, 10161, 0.35. */
    fun number(value: Double): String =
        if (value.isNaN() || value.isInfinite()) "" else BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    /** ISO 8601 with the offset, so a reading keeps its moment across zones and spreadsheets. */
    fun time(instant: Instant, zone: ZoneId): String =
        instant.atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
