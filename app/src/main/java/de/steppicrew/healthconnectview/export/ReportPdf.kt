package de.steppicrew.healthconnectview.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.registry.ContextTally
import de.steppicrew.healthconnectview.registry.AxisScale
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.ui.components.monotoneControls
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * What every PDF report shares: an A4 page, a heading block, tables, a chart and a footer.
 *
 * Laid out in two passes. The lines are built first, each knowing its height, then split into
 * pages -- so "page 2 of 5" is known before page 1 is drawn, and a table that runs onto a new
 * page repeats its column headings there.
 *
 * The same privacy rule as the CSV export: written straight into the file the user chose, with
 * no copy kept. Android's own `PdfDocument`, so no library and nothing new that could reach
 * the network.
 */
abstract class ReportPdf(protected val context: Context) {

    protected val dates: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    protected val times: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    protected val title = paint(TITLE_SIZE, bold = true)
    protected val heading = paint(HEADING_SIZE, bold = true)
    protected val text = paint(TEXT_SIZE)
    protected val bold = paint(TEXT_SIZE, bold = true)
    protected val small = paint(SMALL_SIZE).apply { color = MUTED }
    private val rule = Paint().apply { color = RULE; strokeWidth = 0.5f }
    private val shade = Paint().apply { color = SHADE }
    protected val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * A horizontal slice of a page. [header] is the table heading to repeat after a page break;
     * [newPage] starts a page with this line, as each part of the combined report does.
     * Internal rather than protected so the combined report can set one report's lines into its own.
     */
    internal class Line(
        val height: Float,
        val header: Line? = null,
        val newPage: Boolean = false,
        val draw: Canvas.(Float) -> Unit,
    )

    /** Draws [lines] onto as many pages as they need, [footerText] on each. Returns the page count. */
    internal fun write(lines: List<Line>, footerText: String, out: OutputStream): Int {
        val pages = paginate(lines)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, pageLines ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                var y = MARGIN
                pageLines.forEach { line ->
                    line.draw(page.canvas, y)
                    y += line.height
                }
                footer(page.canvas, footerText, index + 1, pages.size)
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
        return pages.size
    }

    /** Title, period, when and from what it was made, and the source filter if one is set. */
    internal fun MutableList<Line>.opening(titleText: String, first: LocalDate, last: LocalDate, zone: ZoneId, source: String?) {
        add(textLine(titleText, title, TITLE_SIZE + 10))
        add(textLine(context.getString(R.string.report_period, first.format(dates), last.format(dates)), bold))
        add(textLine(context.getString(R.string.report_created, LocalDate.now(zone).format(dates)), small))
        source?.let { add(textLine(context.getString(R.string.report_source, it), small)) }
        add(gap())
    }

    internal fun gap(): Line = Line(GAP) {}

    /**
     * How the readings were taken, counted once for the whole report -- "Körperhaltung:
     * Sitzend 40 · Stehend 2" -- instead of a column on every row, which the tables have no
     * room for; the file and the app show it per reading. Nothing where no reading had any.
     */
    internal fun contextSection(tallies: List<ContextTally>): List<Line> {
        if (tallies.isEmpty()) return emptyList()
        return buildList {
            add(section(context.getString(R.string.report_context)))
            tallies.forEach { tally ->
                val counts = tally.counts.joinToString(" · ") { (value, count) -> "${context.getString(value)} $count" }
                addAll(wrapped("${context.getString(tally.labelRes)}: $counts", text))
            }
            add(textLine(context.getString(R.string.report_context_note), small))
            add(gap())
        }
    }

    internal fun section(label: String): Line = textLine(label, heading, HEADING_SIZE + 10)

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
            if ((line.newPage || y + needed > usable) && pages.last().isNotEmpty()) {
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

    private fun footer(canvas: Canvas, footerText: String, page: Int, pages: Int) {
        val top = PAGE_HEIGHT - FOOTER
        canvas.drawLine(MARGIN, top, PAGE_WIDTH - MARGIN, top, rule)
        var y = top + SMALL_SIZE + 6
        wrapped(footerText, small, CONTENT_WIDTH - 70f).forEach { line ->
            line.draw(canvas, y - SMALL_SIZE)
            y += line.height
        }
        val number = context.getString(R.string.report_page, page, pages)
        canvas.drawText(number, PAGE_WIDTH - MARGIN - small.measureText(number), top + SMALL_SIZE + 6, small)
    }

    internal fun textLine(value: String, paint: Paint, height: Float = paint.textSize + 5): Line =
        Line(height) { y -> drawText(value, MARGIN, y + paint.textSize, paint) }

    /** Word-wrapped to the content width; a report's only running text is short. */
    internal fun wrapped(value: String, paint: Paint, width: Float = CONTENT_WIDTH): List<Line> {
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

    internal fun headerLine(columns: FloatArray, vararg labels: String): Line = Line(ROW) { y ->
        labels.forEachIndexed { index, label -> cell(label, columns[index], y, bold) }
        drawLine(MARGIN, y + ROW - 1, PAGE_WIDTH - MARGIN, y + ROW - 1, rule)
    }

    internal fun headerLine(columns: FloatArray, vararg labels: Int): Line =
        headerLine(columns, *labels.map { context.getString(it) }.toTypedArray())

    /** A table row, every other one shaded so a long column stays readable on paper. */
    internal fun row(index: Int, header: Line, content: Canvas.(Float) -> Unit): Line = Line(ROW, header) { y ->
        if (index % 2 == 1) drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW, shade)
        content(y)
    }

    protected fun Canvas.cell(value: String, x: Float, y: Float, paint: Paint = text) {
        drawText(value, MARGIN + x, y + ROW - 5, paint)
    }

    /**
     * One series for [chart]: points in the unit shown. [line] joins them -- right for a course,
     * wrong for spot readings of different kinds, where it zigzags between a fasting value and
     * one after a meal as if one had turned into the other.
     */
    internal class ChartSeries(
        val points: List<Pair<Instant, Double>>,
        val dashed: Boolean = false,
        val dots: Boolean = true,
        val line: Boolean = true,
        /** Drawn as the screen draws the type: the same monotone curve, never past a reading. */
        val smooth: Boolean = false,
    )

    /**
     * A plain line chart across the content width: a round value axis with gridlines, dates
     * below, each series drawn in ink. [band] shades a range of values behind the lines -- a
     * target range. Printable in black and white; the dashed series is told apart by its dash.
     */
    internal fun chart(
        series: List<ChartSeries>,
        from: Instant,
        to: Instant,
        zone: ZoneId,
        integral: Boolean = false,
        band: ClosedFloatingPointRange<Double>? = null,
    ): Line = Line(CHART_HEIGHT + GAP) { top ->
        val values = series.flatMap { s -> s.points.map { it.second } } + listOfNotNull(band?.start, band?.endInclusive)
        if (values.isEmpty()) return@Line
        val scale = AxisScale.of(values.min(), values.max(), integral = integral)
        val left = MARGIN + AXIS_WIDTH
        val right = PAGE_WIDTH - MARGIN
        val plotTop = top + SMALL_SIZE
        val bottom = top + CHART_HEIGHT - SMALL_SIZE - 6
        val span = (to.toEpochMilli() - from.toEpochMilli()).coerceAtLeast(1).toFloat()
        fun x(t: Instant) = left + (right - left) * ((t.toEpochMilli() - from.toEpochMilli()) / span)
        fun y(v: Double) = (bottom - (bottom - plotTop) * ((v - scale.min) / (scale.max - scale.min))).toFloat()

        band?.let { drawRect(left, y(it.endInclusive), right, y(it.start), bandPaint) }
        scale.guides.forEach { guide ->
            drawLine(left, y(guide), right, y(guide), rule)
            val label = Formatting.axisLabel(guide, scale.decimals)
            drawText(label, left - 4 - small.measureText(label), y(guide) + SMALL_SIZE / 3, small)
        }
        // Dates at the ends and between, as many as fit without touching.
        val ticks = DATE_TICKS
        (0..ticks).forEach { i ->
            val t = Instant.ofEpochMilli(from.toEpochMilli() + ((to.toEpochMilli() - from.toEpochMilli()) * i / ticks))
            // [to] is the midnight after the last day; the axis ends on that day, not the next.
            val label = (if (i == ticks) t.minusMillis(1) else t).atZone(zone).toLocalDate().format(dates)
            val width = small.measureText(label)
            val at = (x(t) - width / 2).coerceIn(left, right - width)
            drawText(label, at, bottom + SMALL_SIZE + 4, small)
        }
        series.forEach { s ->
            if (s.line) {
                val xs = FloatArray(s.points.size) { x(s.points[it].first) }
                val ys = FloatArray(s.points.size) { y(s.points[it].second) }
                val path = Path()
                if (xs.isNotEmpty()) path.moveTo(xs[0], ys[0])
                if (s.smooth && xs.size > 2) {
                    monotoneControls(xs, ys).forEachIndexed { i, c -> path.cubicTo(c.x1, c.y1, c.x2, c.y2, xs[i + 1], ys[i + 1]) }
                } else {
                    for (i in 1 until xs.size) path.lineTo(xs[i], ys[i])
                }
                drawPath(path, if (s.dashed) dashedInk else lineInk)
            }
            if (s.dots) s.points.forEach { (t, v) -> drawCircle(x(t), y(v), if (s.line) CHART_DOT else LONE_DOT, inkDot) }
        }
    }

    private val lineInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val dashedInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MUTED; style = Paint.Style.STROKE; strokeWidth = 1f; pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
    }
    private val inkDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
    private val bandPaint = Paint().apply { color = BAND }

    private fun paint(size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = INK
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    protected companion object {
        // A4 in PostScript points, which is what PdfDocument measures in.
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val FOOTER = 44f
        const val GAP = 14f
        const val ROW = 16f
        const val DOT = 3.5f

        const val CHART_HEIGHT = 170f
        const val AXIS_WIDTH = 28f
        const val CHART_DOT = 1.6f
        const val LONE_DOT = 2.2f
        const val DATE_TICKS = 4

        const val TITLE_SIZE = 18f
        const val HEADING_SIZE = 12f
        const val TEXT_SIZE = 9.5f
        const val SMALL_SIZE = 7.5f

        const val INK = 0xFF1B1B1F.toInt()
        const val MUTED = 0xFF5F5F66.toInt()
        const val RULE = 0xFFBBBBC2.toInt()
        const val SHADE = 0xFFF1F1F4.toInt()
        const val BAND = 0xFFE3F1E6.toInt()
    }
}
