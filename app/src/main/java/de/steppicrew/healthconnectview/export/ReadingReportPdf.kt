package de.steppicrew.healthconnectview.export

import android.content.Context
import androidx.annotation.StringRes
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.MealType
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.GlucoseBand
import de.steppicrew.healthconnectview.health.GlucoseReport
import de.steppicrew.healthconnectview.health.PeriodStats
import de.steppicrew.healthconnectview.health.RestingReport
import de.steppicrew.healthconnectview.health.ValueStats
import de.steppicrew.healthconnectview.health.WeightReport
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Quantity
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The weight, resting heart rate and blood glucose reports as A4 PDFs (content in
 * `health/ReadingReports.kt`). Each is a summary, a chart of the course, a table by week or day,
 * and -- where there are separate readings to ask about -- every reading.
 */
class ReadingReportPdf(context: Context) : ReportPdf(context) {

    private val footerText get() = context.getString(R.string.report_footer_general)

    // --- Weight ---

    fun write(report: WeightReport, zone: ZoneId, source: String?, out: OutputStream): Int =
        write(weightLines(report, zone, source), footerText, out)

    private fun weightLines(report: WeightReport, zone: ZoneId, source: String?): List<Line> = buildList {
        opening(context.getString(R.string.report_weight_title), report.first, report.last, zone, source)
        val overall = report.overall
        if (overall == null) {
            add(textLine(context.getString(R.string.report_none), text))
            return@buildList
        }
        val kg = Quantity.MASS
        // One decimal throughout: 104.6 kg is not 105, and a column should state one precision.
        fun mass(value: Double) = "${Formatting.axisLabel(kg.convert(value), 1)} ${kg.symbol()}"

        add(section(context.getString(R.string.report_summary)))
        val columns = floatArrayOf(0f, 170f)
        val header = Line(0f) {}
        buildList {
            report.firstReading?.let { add(R.string.report_weight_first to "${mass(it.value)}  (${day(it.time, zone)})") }
            report.lastReading?.let { add(R.string.report_weight_last to "${mass(it.value)}  (${day(it.time, zone)})") }
            report.change?.let { add(R.string.report_weight_change to signed(kg.convert(it)) + " " + kg.symbol()) }
            add(R.string.report_col_mean to mass(overall.mean))
            add(R.string.report_col_low to mass(overall.low))
            add(R.string.report_col_high to mass(overall.high))
            add(R.string.report_col_count to overall.count.toString())
        }.forEachIndexed { index, (label, value) ->
            add(row(index, header) { y -> cell(context.getString(label), columns[0], y, bold); cell(value, columns[1], y) })
        }
        add(gap())

        add(section(context.getString(R.string.report_course)))
        add(chart(listOf(ChartSeries(report.readings.map { it.time to kg.convert(it.value) }, smooth = true)), start(report.first, zone), end(report.last, zone), zone))

        add(section(context.getString(R.string.report_by_week)))
        periodTable(report.weeks, R.string.report_col_week) { mass(it) }
        add(gap())

        add(section(context.getString(R.string.report_readings)))
        wrapped(context.getString(R.string.report_one_writer), small).forEach(::add)
        val readingColumns = floatArrayOf(0f, 110f, 170f)
        val readingHeader = headerLine(readingColumns, R.string.report_col_date, R.string.report_col_time, R.string.report_col_value)
        add(readingHeader)
        report.readings.forEachIndexed { index, reading ->
            val local = reading.time.atZone(zone)
            add(
                row(index, readingHeader) { y ->
                    cell(local.toLocalDate().format(dates), readingColumns[0], y)
                    cell(local.toLocalTime().format(times), readingColumns[1], y)
                    cell(mass(reading.value), readingColumns[2], y)
                },
            )
        }
    }

    // --- Resting heart rate ---

    fun write(report: RestingReport, zone: ZoneId, source: String?, out: OutputStream): Int =
        write(restingLines(report, zone, source), footerText, out)

    private fun restingLines(report: RestingReport, zone: ZoneId, source: String?): List<Line> = buildList {
        opening(context.getString(R.string.report_rhr_title), report.first, report.last, zone, source)
        val overall = report.overall
        if (overall == null) {
            add(textLine(context.getString(R.string.report_none), text))
            return@buildList
        }
        fun bpm(value: Double) = "${value.roundToInt()} ${context.getString(R.string.unit_bpm)}"

        add(section(context.getString(R.string.report_summary)))
        val columns = floatArrayOf(0f, 190f)
        val header = Line(0f) {}
        buildList {
            add(R.string.report_col_mean to bpm(overall.mean))
            add(R.string.report_col_low to bpm(overall.low))
            add(R.string.report_col_high to bpm(overall.high))
            add(R.string.report_col_days to overall.count.toString())
            report.firstMonth?.let { add(R.string.report_rhr_first_month to bpm(it.mean)) }
            report.lastMonth?.let { add(R.string.report_rhr_last_month to bpm(it.mean)) }
        }.forEachIndexed { index, (label, value) ->
            add(row(index, header) { y -> cell(context.getString(label), columns[0], y, bold); cell(value, columns[1], y) })
        }
        add(gap())

        add(section(context.getString(R.string.report_course)))
        val noon = { date: LocalDate -> date.atTime(12, 0).atZone(zone).toInstant() }
        add(
            chart(
                listOf(
                    ChartSeries(report.days.mapNotNull { d -> d.rolling?.let { noon(d.date) to it } }, dashed = true, dots = false, smooth = true),
                    ChartSeries(report.days.map { noon(it.date) to it.value }, smooth = true),
                ),
                start(report.first, zone),
                end(report.last, zone),
                zone,
                integral = true,
            ),
        )
        add(textLine(context.getString(R.string.report_rhr_legend), small))
        add(gap())

        if (report.days.size > WEEKS_INSTEAD_OF_DAYS) {
            add(section(context.getString(R.string.report_by_week)))
            periodTable(report.weeks, R.string.report_col_week, R.string.report_col_days) { bpm(it) }
            add(gap())
        }

        add(section(context.getString(R.string.report_days)))
        wrapped(context.getString(R.string.report_rhr_caption), small).forEach(::add)
        val dayColumns = floatArrayOf(0f, 130f, 220f)
        val dayHeader = headerLine(dayColumns, R.string.report_col_date, R.string.report_col_value, R.string.report_col_rolling)
        add(dayHeader)
        report.days.forEachIndexed { index, day ->
            add(
                row(index, dayHeader) { y ->
                    cell(day.date.format(dates), dayColumns[0], y)
                    cell(bpm(day.value), dayColumns[1], y)
                    cell(day.rolling?.let { bpm(it) } ?: "–", dayColumns[2], y, if (day.rolling == null) small else text)
                },
            )
        }
    }

    // --- Blood glucose ---

    fun write(report: GlucoseReport, zone: ZoneId, source: String?, out: OutputStream): Int =
        write(glucoseLines(report, zone, source), footerText, out)

    private fun glucoseLines(report: GlucoseReport, zone: ZoneId, source: String?): List<Line> = buildList {
        opening(context.getString(R.string.report_glucose_title), report.first, report.last, zone, source)
        val overall = report.overall
        if (overall == null) {
            add(textLine(context.getString(R.string.report_none), text))
            return@buildList
        }
        val unit = Quantity.GLUCOSE
        // mg/dL is whole numbers everywhere it is printed; mmol/L carries one decimal.
        val mgdl = unit.alternateShown
        fun level(mmol: Double) = Formatting.axisLabel(unit.convert(mmol), if (mgdl) 0 else 1)
        val unitSymbol = unit.symbol()

        add(section(context.getString(R.string.report_summary)))
        val statColumns = floatArrayOf(0f, 150f, 230f, 310f, 390f)
        val statHeader = headerLine(
            statColumns,
            context.getString(R.string.report_col_relation),
            context.getString(R.string.report_col_mean_unit, unitSymbol),
            context.getString(R.string.report_col_low),
            context.getString(R.string.report_col_high),
            context.getString(R.string.report_col_count),
        )
        add(statHeader)
        (listOf(R.string.report_overall to overall) + report.byRelation.map { (code, stats) -> relationLabel(code) to stats })
            .forEachIndexed { index, (label, stats) ->
                add(
                    row(index, statHeader) { y ->
                        cell(context.getString(label), statColumns[0], y, bold)
                        cell(level(stats.mean), statColumns[1], y)
                        cell(level(stats.low), statColumns[2], y)
                        cell(level(stats.high), statColumns[3], y)
                        cell(stats.count.toString(), statColumns[4], y)
                    },
                )
            }
        add(gap())

        add(section(context.getString(R.string.report_glucose_bands)))
        wrapped(context.getString(R.string.report_glucose_bands_caption), small).forEach(::add)
        val bandColumns = floatArrayOf(0f, 130f, 250f, 330f)
        val bandHeader = headerLine(
            bandColumns,
            context.getString(R.string.report_col_band),
            context.getString(R.string.report_col_range_unit, unitSymbol),
            context.getString(R.string.report_col_count),
            context.getString(R.string.report_col_share),
        )
        add(bandHeader)
        val total = report.bands.sum()
        GlucoseBand.entries.forEachIndexed { index, band ->
            val count = report.bands[index]
            // Each unit's own round limits, as the consensus states them, not one converted.
            val range = if (mgdl) MGDL_RANGES[index] else mmolRange(band)
            add(
                row(index, bandHeader) { y ->
                    cell(context.getString(bandLabel(band)), bandColumns[0], y, if (band == GlucoseBand.IN_RANGE) bold else text)
                    cell(range, bandColumns[1], y)
                    cell(count.toString(), bandColumns[2], y)
                    cell(if (total == 0) "–" else "${(count * 100.0 / total).roundToInt()} %", bandColumns[3], y)
                },
            )
        }
        add(gap())

        add(section(context.getString(R.string.report_course)))
        add(
            chart(
                listOf(ChartSeries(report.readings.map { it.time to unit.convert(it.mmol) }, line = false)),
                start(report.first, zone),
                end(report.last, zone),
                zone,
                band = unit.convert(3.9)..unit.convert(10.0),
            ),
        )
        add(textLine(context.getString(R.string.report_glucose_legend), small))
        add(gap())

        add(section(context.getString(R.string.report_days)))
        add(textLine(context.getString(R.string.report_glucose_days_caption), small))
        periodTable(report.days, R.string.report_col_date) { level(it) }
        add(gap())

        add(section(context.getString(R.string.report_readings)))
        wrapped(context.getString(R.string.report_one_writer), small).forEach(::add)
        val readingColumns = floatArrayOf(0f, 100f, 150f, 220f, 330f)
        val readingHeader = headerLine(
            readingColumns,
            context.getString(R.string.report_col_date),
            context.getString(R.string.report_col_time),
            unitSymbol,
            context.getString(R.string.report_col_relation),
            context.getString(R.string.report_col_meal),
        )
        add(readingHeader)
        report.readings.forEachIndexed { index, reading ->
            val local = reading.time.atZone(zone)
            add(
                row(index, readingHeader) { y ->
                    cell(local.toLocalDate().format(dates), readingColumns[0], y)
                    cell(local.toLocalTime().format(times), readingColumns[1], y)
                    cell(level(reading.mmol), readingColumns[2], y)
                    cell(context.getString(relationLabel(reading.relation)), readingColumns[3], y)
                    cell(mealLabel(reading.meal)?.let(context::getString) ?: "–", readingColumns[4], y)
                },
            )
        }
    }

    // --- Shared ---

    /** A table of periods: start, mean, lowest, highest, and how many values made them. */
    private fun MutableList<Line>.periodTable(
        periods: List<PeriodStats>,
        @StringRes startLabel: Int,
        @StringRes countLabel: Int = R.string.report_col_count,
        format: (Double) -> String,
    ) {
        val columns = floatArrayOf(0f, 130f, 220f, 310f, 400f)
        val header = headerLine(columns, startLabel, R.string.report_col_mean, R.string.report_col_low, R.string.report_col_high, countLabel)
        add(header)
        periods.forEachIndexed { index, period ->
            val stats: ValueStats = period.stats
            add(
                row(index, header) { y ->
                    cell(period.start.format(dates), columns[0], y)
                    cell(format(stats.mean), columns[1], y)
                    cell(format(stats.low), columns[2], y)
                    cell(format(stats.high), columns[3], y)
                    cell(stats.count.toString(), columns[4], y)
                },
            )
        }
    }

    private fun day(time: Instant, zone: ZoneId) = time.atZone(zone).toLocalDate().format(dates)

    private fun signed(value: Double) = (if (value > 0) "+" else "") + Formatting.axisLabel(value, 1)

    private fun start(first: LocalDate, zone: ZoneId) = first.atStartOfDay(zone).toInstant()

    private fun end(last: LocalDate, zone: ZoneId) = last.plusDays(1).atStartOfDay(zone).toInstant()

    @StringRes
    private fun relationLabel(code: Int): Int = when (code) {
        BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> R.string.glucose_relation_fasting
        BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> R.string.glucose_relation_before
        BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> R.string.glucose_relation_after
        BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> R.string.glucose_relation_general
        else -> R.string.glucose_relation_unknown
    }

    @StringRes
    private fun mealLabel(code: Int): Int? = when (code) {
        MealType.MEAL_TYPE_BREAKFAST -> R.string.meal_breakfast
        MealType.MEAL_TYPE_LUNCH -> R.string.meal_lunch
        MealType.MEAL_TYPE_DINNER -> R.string.meal_dinner
        MealType.MEAL_TYPE_SNACK -> R.string.meal_snack
        else -> null
    }

    private fun mmolRange(band: GlucoseBand): String {
        fun n(value: Double) = Formatting.axisLabel(value, 1)
        return when (band) {
            GlucoseBand.VERY_LOW -> "< ${n(3.0)}"
            GlucoseBand.LOW -> "${n(3.0)} – ${n(3.8)}"
            GlucoseBand.IN_RANGE -> "${n(3.9)} – ${n(10.0)}"
            GlucoseBand.HIGH -> "${n(10.1)} – ${n(13.9)}"
            GlucoseBand.VERY_HIGH -> "> ${n(13.9)}"
        }
    }

    @StringRes
    private fun bandLabel(band: GlucoseBand): Int = when (band) {
        GlucoseBand.VERY_LOW -> R.string.glucose_band_very_low
        GlucoseBand.LOW -> R.string.glucose_band_low
        GlucoseBand.IN_RANGE -> R.string.glucose_band_in_range
        GlucoseBand.HIGH -> R.string.glucose_band_high
        GlucoseBand.VERY_HIGH -> R.string.glucose_band_very_high
    }

    private companion object {
        /** Up to about five weeks the day table is short enough on its own. */
        const val WEEKS_INSTEAD_OF_DAYS = 35

        val MGDL_RANGES = listOf("< 54", "54 – 69", "70 – 180", "181 – 250", "> 250")
    }
}
