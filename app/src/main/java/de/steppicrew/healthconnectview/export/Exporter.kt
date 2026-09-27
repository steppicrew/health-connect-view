package de.steppicrew.healthconnectview.export

import android.content.Context
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.BloodPressureRecord
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.PressureReading
import de.steppicrew.healthconnectview.health.dayPartWindow
import de.steppicrew.healthconnectview.health.pressureReport
import de.steppicrew.healthconnectview.health.SESSION_MARGIN
import de.steppicrew.healthconnectview.health.numericAggregate
import de.steppicrew.healthconnectview.health.recordsIn
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * Writes a type's data for a window into a file the user chose.
 *
 * The only place the app puts health data anywhere but the screen, and only on an explicit
 * request into a destination picked in the system's own file dialog. Rows go straight from each
 * page read into the stream; the app keeps no copy.
 *
 * Two shapes, because they answer different questions: [writeRecords] is what each app stored,
 * duplicates included and labelled by writer; [writeDailyTotals] is Health Connect's
 * deduplicated figure per day, the one the app shows as a total.
 */
class Exporter(private val context: Context, private val repository: HealthRepository) {

    private val zone: ZoneId get() = HealthRepository.DEFAULT_ZONE

    /** Returns the number of data rows written. */
    suspend fun writeRecords(
        spec: RecordTypeSpec<*>,
        start: Instant,
        end: Instant,
        origins: Set<DataOrigin>,
        out: OutputStream,
    ): Int {
        val unit = spec.displayUnitRes?.let(context::getString).orEmpty()
        val sleep = spec.type == SleepSessionRecord::class
        // Nights by the day they ended on, as everywhere else in the app: read widened, keep by end.
        val range = if (sleep) {
            TimeRangeFilter.between(start.minus(SESSION_MARGIN), end.plus(SESSION_MARGIN))
        } else {
            TimeRangeFilter.between(start, end)
        }
        var rows = 0
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write(BOM)
        writer.write(Csv.line(listOf("start", "end", "time", "value", "unit", "text", "source")))
        repository.forEachPage(spec.type, range, origins) { page ->
            page.forEach { record ->
                val recordStart = spec.timeOf(record)
                val recordEnd = spec.endTimeOf(record)
                if (sleep && !(recordEnd != null && recordEnd > start && recordEnd <= end)) return@forEach
                val words = spec.summaryResOf(record)?.joinToString(", ") { context.getString(it) }
                val points = spec.pointsOf(record)
                val fixed = listOf(
                    Csv.time(recordStart, zone),
                    recordEnd?.let { Csv.time(it, zone) }.orEmpty(),
                )
                val source = record.metadata.dataOrigin.packageName
                if (points.isEmpty()) {
                    // A record with no number -- a cycle observation -- is one row of words.
                    writer.write(Csv.line(fixed + listOf(Csv.time(recordStart, zone), "", "", words ?: spec.summaryOf(record), source)))
                    rows++
                } else {
                    // One row per reading, so a heart-rate series is a column of samples rather
                    // than a record's average.
                    points.forEach { point ->
                        writer.write(
                            Csv.line(fixed + listOf(Csv.time(point.time, zone), Csv.number(point.value), unit, words.orEmpty(), source)),
                        )
                        rows++
                    }
                }
            }
        }
        writer.flush()
        return rows
    }

    /**
     * One row per day of [from] until [until] (exclusive). A day Health Connect has nothing for
     * is an empty value, never zero -- the same rule as the tile's dash.
     */
    suspend fun writeDailyTotals(
        spec: RecordTypeSpec<*>,
        from: LocalDate,
        until: LocalDate,
        origins: Set<DataOrigin>,
        out: OutputStream,
    ): Int {
        val metric = requireNotNull(spec.aggregate) { "no daily totals for ${spec.type.simpleName}" }
        val unit = spec.displayUnitRes?.let(context::getString).orEmpty()
        val byDay = repository.bucketedTotals(
            metric,
            TimeRangeFilter.between(from.atStartOfDay(), until.atStartOfDay()),
            Period.ofDays(1),
            origins,
        ).associate { it.startTime.toLocalDate() to it.result[metric]?.let { numericAggregate(it, metric) } }

        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write(BOM)
        writer.write(Csv.line(listOf("date", "value", "unit")))
        var rows = 0
        generateSequence(from) { it.plusDays(1) }.takeWhile { it < until }.forEach { day ->
            writer.write(Csv.line(listOf(day.toString(), byDay[day]?.let(Csv::number).orEmpty(), unit)))
            rows++
        }
        writer.flush()
        return rows
    }

    /**
     * The blood pressure log for [first] through [last] as a PDF. Returns the number of
     * readings in it.
     *
     * Paged through every record rather than the capped list read, so a year of three readings
     * a day is complete; read from 04:00 to 04:00 so each evening keeps its after-midnight
     * readings, as on screen.
     */
    suspend fun writePressureReport(
        first: LocalDate,
        last: LocalDate,
        origins: Set<DataOrigin>,
        source: String?,
        out: OutputStream,
    ): Int {
        val (start, end) = dayPartWindow(first, last, zone)
        val readings = mutableListOf<PressureReading>()
        repository.forEachPage(BloodPressureRecord::class, TimeRangeFilter.between(start, end), origins) { page ->
            page.forEach {
                readings += PressureReading(it.time, it.systolic.inMillimetersOfMercury, it.diastolic.inMillimetersOfMercury)
            }
        }
        val report = pressureReport(readings, first, last, zone)
        PressureReportPdf(context).write(report, zone, source, out)
        return report.readings.size
    }

    /**
     * Whether [writeRecords] over the same window would write a row: any record, a night kept
     * by its end as the file keeps it. Checked before the save dialog opens, so an empty window
     * says so instead of leaving a file of headers.
     */
    suspend fun hasRecords(spec: RecordTypeSpec<*>, start: Instant, end: Instant, origins: Set<DataOrigin>): Boolean =
        // A night's widened read can start with the one after the window, so look at a few.
        repository.recordsIn(spec, start, end, origins, if (spec.type == SleepSessionRecord::class) SLEEP_PROBE else 1)
            .isNotEmpty()

    /** Whether [writeDailyTotals] would have a value on any day, rather than only empty ones. */
    suspend fun hasDailyTotals(spec: RecordTypeSpec<*>, from: LocalDate, until: LocalDate, origins: Set<DataOrigin>): Boolean {
        val metric = spec.aggregate ?: return false
        return repository.total(metric, TimeRangeFilter.between(from.atStartOfDay(), until.atStartOfDay()), origins) != null
    }

    /** Whether [writePressureReport] would have a reading, over the report's own 04:00 window. */
    suspend fun hasPressureReadings(first: LocalDate, last: LocalDate, origins: Set<DataOrigin>): Boolean {
        val (start, end) = dayPartWindow(first, last, zone)
        return repository.read(BloodPressureRecord::class, TimeRangeFilter.between(start, end), 1, origins).isNotEmpty()
    }

    private companion object {
        /** Nights read to find one kept by its end; more than a window's edges can hold. */
        const val SLEEP_PROBE = 5

        /**
         * Marks the file as UTF-8. Without it Excel reads "Stärke" as "StÃ¤rke"; programs that
         * do not need it skip it.
         */
        const val BOM = "\uFEFF"
    }
}
