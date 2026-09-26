package de.steppicrew.healthconnectview.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.DayPart
import de.steppicrew.healthconnectview.health.PartAverage
import de.steppicrew.healthconnectview.health.PressureReport
import de.steppicrew.healthconnectview.health.dayPartOf
import de.steppicrew.healthconnectview.health.labelRes
import de.steppicrew.healthconnectview.health.pressureCategory
import de.steppicrew.healthconnectview.registry.ValueZones
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * Draws a [PressureReport] as an A4 PDF: the kind of log a doctor asks for, from readings the
 * user already has.
 *
 * Laid out in two passes. The lines are built first, each knowing its height, then split into
 * pages -- so "page 2 of 5" is known before page 1 is drawn, and a table that runs onto a new
 * page repeats its column headings there.
 *
 * The same privacy rule as the CSV export: written straight into the file the user chose, with
 * no copy kept. Android's own `PdfDocument`, so no library and nothing new that could reach
 * the network.
 */
class PressureReportPdf(private val context: Context) {

    private val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val times = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    private val title = paint(TITLE_SIZE, bold = true)
    private val heading = paint(HEADING_SIZE, bold = true)
    private val text = paint(TEXT_SIZE)
    private val bold = paint(TEXT_SIZE, bold = true)
    private val small = paint(SMALL_SIZE).apply { color = MUTED }
    private val rule = Paint().apply { color = RULE; strokeWidth = 0.5f }
    private val shade = Paint().apply { color = SHADE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    /** A horizontal slice of a page. [header] is the table heading to repeat after a page break. */
    private class Line(val height: Float, val header: Line? = null, val draw: Canvas.(Float) -> Unit)

    /** Returns the number of pages written. */
    fun write(report: PressureReport, zone: ZoneId, source: String?, out: OutputStream): Int {
        val pages = paginate(lines(report, zone, source))
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, lines ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                var y = MARGIN
                lines.forEach { line ->
                    line.draw(page.canvas, y)
                    y += line.height
                }
                footer(page.canvas, index + 1, pages.size)
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
        return pages.size
    }

    private fun lines(report: PressureReport, zone: ZoneId, source: String?): List<Line> = buildList {
        add(textLine(context.getString(R.string.report_title), title, TITLE_SIZE + 10))
        add(textLine(context.getString(R.string.report_period, report.first.format(dates), report.last.format(dates)), bold))
        add(textLine(context.getString(R.string.report_created, LocalDate.now(zone).format(dates)), small))
        source?.let { add(textLine(context.getString(R.string.report_source, it), small)) }
        add(Line(GAP) {})

        if (report.overall == null) {
            add(textLine(context.getString(R.string.report_none), text))
            return@buildList
        }

        add(textLine(context.getString(R.string.report_summary), heading, HEADING_SIZE + 10))
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
        add(Line(GAP) {})

        add(textLine(context.getString(R.string.report_days), heading, HEADING_SIZE + 10))
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
        add(Line(GAP) {})

        add(textLine(context.getString(R.string.report_readings), heading, HEADING_SIZE + 10))
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

    /**
     * Greedy page filling. A page that opens inside a table starts with that table's column
     * headings, and those headings are never left alone at the foot of a page.
     */
    private fun paginate(lines: List<Line>): List<List<Line>> {
        val usable = PAGE_HEIGHT - MARGIN - FOOTER
        val pages = mutableListOf<MutableList<Line>>(mutableListOf())
        var y = MARGIN
        lines.forEachIndexed { index, line ->
            val next = lines.getOrNull(index + 1)
            val keepWithNext = next?.header === line
            val needed = line.height + if (keepWithNext) next.height else 0f
            if (y + needed > usable && pages.last().isNotEmpty()) {
                pages.add(mutableListOf())
                y = MARGIN
                line.header?.let {
                    pages.last().add(it)
                    y += it.height
                }
            }
            pages.last().add(line)
            y += line.height
        }
        return pages
    }

    private fun footer(canvas: Canvas, page: Int, pages: Int) {
        val top = PAGE_HEIGHT - FOOTER
        canvas.drawLine(MARGIN, top, PAGE_WIDTH - MARGIN, top, rule)
        var y = top + SMALL_SIZE + 6
        wrapped(context.getString(R.string.report_footer), small, CONTENT_WIDTH - 70f).forEach { line ->
            line.draw(canvas, y - SMALL_SIZE)
            y += line.height
        }
        val number = context.getString(R.string.report_page, page, pages)
        canvas.drawText(number, PAGE_WIDTH - MARGIN - small.measureText(number), top + SMALL_SIZE + 6, small)
    }

    private fun textLine(value: String, paint: Paint, height: Float = paint.textSize + 5): Line =
        Line(height) { y -> drawText(value, MARGIN, y + paint.textSize, paint) }

    /** Word-wrapped to the content width; the report's only running text is short. */
    private fun wrapped(value: String, paint: Paint, width: Float = CONTENT_WIDTH): List<Line> {
        val lines = mutableListOf<String>()
        var current = ""
        value.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > width && current.isNotEmpty()) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines.map { textLine(it, paint, paint.textSize + 3) }
    }

    private fun headerLine(columns: FloatArray, vararg labels: Int): Line = Line(ROW) { y ->
        labels.forEachIndexed { index, label -> cell(context.getString(label), columns[index], y, bold) }
        drawLine(MARGIN, y + ROW - 1, PAGE_WIDTH - MARGIN, y + ROW - 1, rule)
    }

    /** A table row, every other one shaded so a long column stays readable on paper. */
    private fun row(index: Int, header: Line, content: Canvas.(Float) -> Unit): Line = Line(ROW, header) { y ->
        if (index % 2 == 1) drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW, shade)
        content(y)
    }

    private fun Canvas.cell(value: String, x: Float, y: Float, paint: Paint = text) {
        drawText(value, MARGIN + x, y + ROW - 5, paint)
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

    private fun paint(size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = INK
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private companion object {
        // A4 in PostScript points, which is what PdfDocument measures in.
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val FOOTER = 44f
        const val GAP = 14f
        const val ROW = 16f
        const val DOT = 3.5f

        const val TITLE_SIZE = 18f
        const val HEADING_SIZE = 12f
        const val TEXT_SIZE = 9.5f
        const val SMALL_SIZE = 7.5f

        const val INK = 0xFF1B1B1F.toInt()
        const val MUTED = 0xFF5F5F66.toInt()
        const val RULE = 0xFFBBBBC2.toInt()
        const val SHADE = 0xFFF1F1F4.toInt()
    }
}
