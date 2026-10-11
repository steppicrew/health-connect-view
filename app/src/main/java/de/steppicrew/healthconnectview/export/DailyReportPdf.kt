package de.steppicrew.healthconnectview.export

import android.content.Context
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.DailyReport
import de.steppicrew.healthconnectview.registry.Formatting
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId

/**
 * The report of any type with daily totals as an A4 PDF (step 82): the period's figure, the
 * days' mean, the lowest and highest day, a chart of the days, and the days in a table -- by
 * week past two months. Content in `health/DailyReport.kt`.
 */
class DailyReportPdf(context: Context) : ReportPdf(context) {

    fun write(
        report: DailyReport,
        /** The type's name, the unit shown, decimals, and whether its figure is a mean. */
        name: String,
        unit: String?,
        decimals: Int?,
        averaged: Boolean,
        integral: Boolean,
        zone: ZoneId,
        source: String?,
        out: OutputStream,
    ): Int {
        fun value(v: Double) = Formatting.number(v, decimals) + (unit?.let { " $it" } ?: "")
        val lines = buildList {
            opening(context.getString(R.string.report_daily_title, name), report.first, report.last, zone, source)
            val stats = report.stats
            if (stats == null) {
                add(textLine(context.getString(R.string.report_none), text))
                return@buildList
            }
            wrapped(context.getString(R.string.report_daily_caption), small).forEach(::add)
            add(gap())

            add(section(context.getString(R.string.report_summary)))
            val columns = floatArrayOf(0f, 170f)
            val header = Line(0f) {}
            buildList {
                // A sum and its daily mean are two answers; a mean's figure is the mean itself.
                if (!averaged) report.overall?.let { add(context.getString(R.string.report_overall) to value(it)) }
                add(context.getString(R.string.report_col_mean) to value(stats.mean))
                report.lowest?.let { add(context.getString(R.string.report_col_low) to "${value(stats.low)}  (${it.format(dates)})") }
                report.highest?.let { add(context.getString(R.string.report_col_high) to "${value(stats.high)}  (${it.format(dates)})") }
                add(context.getString(R.string.report_col_days) to "${stats.count} / ${report.days.size}")
            }.forEachIndexed { index, (label, text) ->
                add(row(index, header) { y -> cell(label, columns[0], y, bold); cell(text, columns[1], y) })
            }
            add(gap())

            add(section(context.getString(R.string.report_course)))
            val points = report.days.mapNotNull { (day, v) -> v?.let { day.atStartOfDay(zone).toInstant() to it } }
            add(chart(listOf(ChartSeries(points)), start(report.first, zone), end(report.last, zone), zone, integral = integral))

            if (report.weeks.isNotEmpty()) {
                add(section(context.getString(R.string.report_by_week)))
                val weekColumns = floatArrayOf(0f, 130f, 220f, 310f, 400f)
                val weekHeader = headerLine(
                    weekColumns,
                    R.string.report_col_week,
                    R.string.report_col_mean,
                    R.string.report_col_low,
                    R.string.report_col_high,
                    R.string.report_col_days,
                )
                add(weekHeader)
                report.weeks.forEachIndexed { index, week ->
                    add(
                        row(index, weekHeader) { y ->
                            cell(week.start.format(dates), weekColumns[0], y)
                            cell(value(week.stats.mean), weekColumns[1], y)
                            cell(value(week.stats.low), weekColumns[2], y)
                            cell(value(week.stats.high), weekColumns[3], y)
                            cell(week.stats.count.toString(), weekColumns[4], y)
                        },
                    )
                }
            } else {
                add(section(context.getString(R.string.report_days)))
                val dayColumns = floatArrayOf(0f, 170f)
                val dayHeader = headerLine(dayColumns, R.string.report_col_date, R.string.report_col_value)
                add(dayHeader)
                // An empty day stays in the table, empty: a gap is part of the record.
                report.days.forEachIndexed { index, (day, v) ->
                    add(row(index, dayHeader) { y -> cell(day.format(dates), dayColumns[0], y); cell(v?.let(::value) ?: "–", dayColumns[1], y) })
                }
            }
        }
        return write(lines, context.getString(R.string.report_footer_general), out)
    }

    private fun start(first: LocalDate, zone: ZoneId) = first.atStartOfDay(zone).toInstant()

    private fun end(last: LocalDate, zone: ZoneId) = last.plusDays(1).atStartOfDay(zone).toInstant()
}
