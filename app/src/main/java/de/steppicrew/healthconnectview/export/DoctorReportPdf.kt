package de.steppicrew.healthconnectview.export

import android.content.Context
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.WeightRecord
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.DoctorReport
import de.steppicrew.healthconnectview.health.ValueStats
import de.steppicrew.healthconnectview.health.pressureExtremes
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Quantity
import java.io.OutputStream
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.reflect.KClass

/**
 * A [DoctorReport] as one A4 PDF: an overview of every type on the first page, then each
 * type's own report on pages of its own, exactly as it prints alone -- so a doctor who knows
 * the single logs finds the same tables here.
 */
class DoctorReportPdf(context: Context) : ReportPdf(context) {

    /** Returns the number of pages written. */
    fun write(report: DoctorReport, zone: ZoneId, out: OutputStream): Int =
        write(lines(report, zone), context.getString(R.string.report_footer_general), out)

    private fun lines(report: DoctorReport, zone: ZoneId): List<Line> = buildList {
        opening(context.getString(R.string.report_doctor_title), report.first, report.last, zone, source = null)
        if (report.parts == 0) {
            add(textLine(context.getString(R.string.report_none), text))
            return@buildList
        }

        add(section(context.getString(R.string.report_overview)))
        val columns = floatArrayOf(0f, 130f, 225f, 315f, 405f)
        val header = headerLine(
            columns,
            R.string.report_col_type, R.string.report_col_mean, R.string.report_col_low, R.string.report_col_high,
            R.string.report_col_count,
        )
        add(header)
        overview(report).forEachIndexed { index, row ->
            add(
                row(index, header) { y ->
                    cell(context.getString(row.name), columns[0], y, bold)
                    cell(row.mean, columns[1], y)
                    cell(row.low, columns[2], y)
                    cell(row.high, columns[3], y)
                    cell(row.count.toString(), columns[4], y)
                },
            )
        }
        if (report.pressure != null) wrapped(context.getString(R.string.report_doctor_pressure_extremes), small).forEach(::add)
        if (report.parts < PARTS) wrapped(context.getString(R.string.report_doctor_left_out), small).forEach(::add)
        add(gap())
        // Which app each type came from, where one was chosen: the single logs say it in their
        // opening, and here the opening is shared.
        val chosen = TYPES.mapNotNull { (type, name) -> report.sources[type]?.let { name to it } }
        chosen.forEach { (name, app) ->
            wrapped("${context.getString(name)}: ${context.getString(R.string.report_source, app)}", small).forEach(::add)
        }

        val readings = ReadingReportPdf(context)
        report.pressure?.let {
            part(R.string.report_title)
            addAll(PressureReportPdf(context).body(it, zone))
            // The grading's basis, which the single log prints in its footer.
            add(gap())
            wrapped(context.getString(R.string.report_footer), small).forEach(::add)
        }
        report.weight?.let {
            part(R.string.report_weight_title)
            addAll(readings.weightBody(it, zone))
        }
        report.resting?.let {
            part(R.string.report_rhr_title)
            addAll(readings.restingBody(it, zone))
        }
        report.glucose?.let {
            part(R.string.report_glucose_title)
            addAll(readings.glucoseBody(it, zone))
        }
    }

    /** A type's own title at the top of a new page. */
    private fun MutableList<Line>.part(titleRes: Int) {
        add(Line(TITLE_SIZE + 10, newPage = true) { y -> drawText(context.getString(titleRes), MARGIN, y + TITLE_SIZE, title) })
        add(gap())
    }

    private class OverviewRow(val name: Int, val mean: String, val low: String, val high: String, val count: Int)

    private fun overview(report: DoctorReport): List<OverviewRow> = buildList {
        report.pressure?.let { pressure ->
            val overall = pressure.overall ?: return@let
            val extremes = pressureExtremes(pressure.readings) ?: return@let
            fun mmHg(systolic: Double, diastolic: Double) = "${systolic.roundToInt()}/${diastolic.roundToInt()} mmHg"
            add(
                OverviewRow(
                    R.string.type_blood_pressure,
                    mmHg(overall.systolic, overall.diastolic),
                    mmHg(extremes.first.systolic, extremes.first.diastolic),
                    mmHg(extremes.second.systolic, extremes.second.diastolic),
                    overall.count,
                ),
            )
        }
        report.weight?.overall?.let { stats ->
            val kg = Quantity.MASS
            add(stats.row(R.string.type_weight) { "${Formatting.axisLabel(kg.convert(it), 1)} ${kg.symbol()}" })
        }
        report.resting?.overall?.let { stats ->
            add(stats.row(R.string.type_resting_heart_rate) { "${it.roundToInt()} ${context.getString(R.string.unit_bpm)}" })
        }
        report.glucose?.overall?.let { stats ->
            val unit = Quantity.GLUCOSE
            val decimals = if (unit.alternateShown) 0 else 1
            add(stats.row(R.string.type_blood_glucose) { "${Formatting.axisLabel(unit.convert(it), decimals)} ${unit.symbol()}" })
        }
    }

    private fun ValueStats.row(name: Int, format: (Double) -> String) =
        OverviewRow(name, format(mean), format(low), format(high), count)

    private companion object {
        const val PARTS = 4

        val TYPES: List<Pair<KClass<out Record>, Int>> = listOf(
            BloodPressureRecord::class to R.string.type_blood_pressure,
            WeightRecord::class to R.string.type_weight,
            RestingHeartRateRecord::class to R.string.type_resting_heart_rate,
            BloodGlucoseRecord::class to R.string.type_blood_glucose,
        )
    }
}
