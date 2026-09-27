package de.steppicrew.healthconnectview.export

import android.content.Context
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.DayPart
import de.steppicrew.healthconnectview.health.PartAverage
import de.steppicrew.healthconnectview.health.PressureReport
import de.steppicrew.healthconnectview.health.dayPartOf
import de.steppicrew.healthconnectview.health.labelRes
import de.steppicrew.healthconnectview.health.pressureCategory
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.registry.tally
import java.io.OutputStream
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Draws a [PressureReport] as an A4 PDF: the kind of log a doctor asks for, from readings the
 * user already has. The page mechanics are [ReportPdf]'s.
 */
class PressureReportPdf(context: Context) : ReportPdf(context) {

    /** Returns the number of pages written. */
    fun write(report: PressureReport, zone: ZoneId, source: String?, out: OutputStream): Int =
        write(lines(report, zone, source), context.getString(R.string.report_footer), out)

    private fun lines(report: PressureReport, zone: ZoneId, source: String?): List<Line> = buildList {
        opening(context.getString(R.string.report_title), report.first, report.last, zone, source)

        if (report.overall == null) {
            add(textLine(context.getString(R.string.report_none), text))
            return@buildList
        }

        add(section(context.getString(R.string.report_summary)))
        val summaryColumns = floatArrayOf(0f, 110f, 230f, 310f)
        val summaryHeader = headerLine(
            summaryColumns,
            R.string.report_col_part, R.string.report_col_average, R.string.report_col_count, R.string.report_col_grade,
        )
        add(summaryHeader)
        listOf(
            R.string.report_overall to report.overall,
            R.string.bp_part_morning to report.parts.morning,
            R.string.bp_part_evening to report.parts.evening,
        ).forEachIndexed { index, (label, average) ->
            add(
                row(index, summaryHeader) { y ->
                    cell(context.getString(label), summaryColumns[0], y, bold)
                    if (average == null) {
                        cell(context.getString(R.string.bp_part_none), summaryColumns[1], y, small)
                    } else {
                        graded(average.systolic, average.diastolic, summaryColumns[1], y)
                        cell(average.count.toString(), summaryColumns[2], y)
                        cell(
                            context.getString(pressureCategory(average.systolic, average.diastolic).labelRes()),
                            summaryColumns[3],
                            y,
                        )
                    }
                },
            )
        }
        // The day rule, since "evening" here includes 01:00 on the next date.
        wrapped(context.getString(R.string.bp_parts_rule).substringBefore("\n\n"), small).forEach(::add)
        add(gap())

        add(section(context.getString(R.string.report_days)))
        add(textLine(context.getString(R.string.report_days_caption), small))
        val dayColumns = floatArrayOf(0f, 150f, 300f)
        val dayHeader = headerLine(dayColumns, R.string.report_col_date, R.string.bp_part_morning, R.string.bp_part_evening)
        add(dayHeader)
        report.days.forEachIndexed { index, day ->
            add(
                row(index, dayHeader) { y ->
                    cell(day.date.format(dates), dayColumns[0], y)
                    partCell(day.morning, dayColumns[1], y)
                    partCell(day.evening, dayColumns[2], y)
                },
            )
        }
        add(gap())

        addAll(contextSection(tally(report.readings.map { it.context })))
        add(section(context.getString(R.string.report_readings)))
        val readingColumns = floatArrayOf(0f, 110f, 170f, 250f, 350f)
        val readingHeader = headerLine(
            readingColumns,
            R.string.report_col_date, R.string.report_col_time, R.string.report_col_part,
            R.string.report_col_mmhg, R.string.report_col_grade,
        )
        add(readingHeader)
        report.readings.forEachIndexed { index, reading ->
            val local = reading.time.atZone(zone)
            val (day, part) = dayPartOf(reading.time, zone)
            val partLabel = when {
                part == DayPart.MORNING -> R.string.bp_part_morning
                // 00:35 on the 3rd is listed on the 2nd in the table above; saying so here keeps
                // the two tables from seeming to disagree.
                day != local.toLocalDate() -> R.string.report_part_evening_before
                else -> R.string.bp_part_evening
            }
            add(
                row(index, readingHeader) { y ->
                    // The calendar date and clock, as measured; the part column says which
                    // evening an after-midnight reading was counted for.
                    cell(local.toLocalDate().format(dates), readingColumns[0], y)
                    cell(local.toLocalTime().format(times), readingColumns[1], y)
                    cell(context.getString(partLabel), readingColumns[2], y)
                    graded(reading.systolic, reading.diastolic, readingColumns[3], y)
                    cell(
                        context.getString(pressureCategory(reading.systolic, reading.diastolic).labelRes()),
                        readingColumns[4],
                        y,
                    )
                },
            )
        }
    }

    /** "132/84" with its grade's colour beside it, as on the screen. */
    private fun Canvas.graded(systolic: Double, diastolic: Double, x: Float, y: Float, suffix: String = "") {
        dot.color = ValueZones.ZONE_COLORS[pressureCategory(systolic, diastolic).ordinal].toArgb()
        drawCircle(MARGIN + x + DOT, y + ROW / 2, DOT, dot)
        cell("${systolic.roundToInt()}/${diastolic.roundToInt()}$suffix", x + DOT * 2 + 4, y)
    }

    private fun Canvas.partCell(average: PartAverage?, x: Float, y: Float) {
        if (average == null) {
            cell("–", x, y, small)
        } else {
            graded(average.systolic, average.diastolic, x, y, " (${average.count})")
        }
    }
}
