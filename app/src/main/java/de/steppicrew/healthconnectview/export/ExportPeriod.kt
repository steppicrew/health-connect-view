package de.steppicrew.healthconnectview.export

import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

/**
 * The days an export covers, [first] through [last] inclusive.
 *
 * Chosen when exporting rather than taken from the chart: "4 Wochen" never lines up with a
 * calendar month, and a report for a doctor is asked for as "August" or "last year".
 */
data class ExportPeriod(val first: LocalDate, val last: LocalDate) {

    init {
        require(!last.isBefore(first)) { "period ends before it starts" }
    }

    /**
     * The period in a file name: `2026-08` for a whole month, `2026` for a whole year,
     * `2026-08-14` for one day, else both ends. Sorts by date in a folder.
     */
    val fileTag: String
        get() = when {
            first == last -> first.toString()
            first.dayOfYear == 1 && last == first.withDayOfYear(first.lengthOfYear()) -> first.year.toString()
            first.dayOfMonth == 1 && last == YearMonth.from(first).atEndOfMonth() -> YearMonth.from(first).toString()
            else -> "${first}_$last"
        }

    /**
     * Whether Health Connect would cut this period short without READ_HEALTH_DATA_HISTORY,
     * which limits reads to the last 30 days -- the same rule as the chart's.
     */
    fun needsHistory(today: LocalDate = LocalDate.now()): Boolean = first.isBefore(today.minusDays(HISTORY_FREE_DAYS))

    /** The ready-made choices beside the window on screen and a range of one's own. */
    enum class Preset { SHOWN, LAST_MONTH, THIS_MONTH, LAST_YEAR, THIS_YEAR }

    companion object {
        private const val HISTORY_FREE_DAYS = 30L

        /**
         * The period for [preset]. The current month and year end today: the days still to
         * come hold nothing, and a file named for them would promise what it does not have.
         */
        fun of(preset: Preset, shown: ExportPeriod, today: LocalDate = LocalDate.now()): ExportPeriod = when (preset) {
            Preset.SHOWN -> shown
            Preset.LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { ExportPeriod(it.atDay(1), it.atEndOfMonth()) }
            Preset.THIS_MONTH -> ExportPeriod(today.withDayOfMonth(1), today)
            Preset.LAST_YEAR -> Year.of(today.year - 1).let { ExportPeriod(it.atDay(1), it.atDay(it.length())) }
            Preset.THIS_YEAR -> ExportPeriod(today.withDayOfYear(1), today)
        }
    }
}
