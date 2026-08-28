package de.steppicrew.healthconnectview.debug

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.sin
import kotlin.random.Random

/**
 * Writes obviously-synthetic health data for exercising the UI and taking store screenshots
 * without touching anyone's real records. Debug builds only.
 *
 * The volumes and cadences below are modelled on the *shape* of a real device's data,
 * measured with [DataShapeActivity] — hundreds of records per day for continuously sampled
 * types, only a handful for things like weight — because the earlier fixture (a few records
 * per day) exercised none of the paths that matter: pagination, dense charts, or sparse
 * series with a single point. No real values were copied; the ranges are plausible defaults.
 */
object SampleDataSeeder {

    private const val DAYS = 30

    suspend fun seed(context: Context) {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        // Local midnight: Health Connect buckets daily aggregates by the record's own zone
        // offset, so seeding against UTC on a UTC+2 device lands records in the wrong day.
        // Start from yesterday: Health Connect rejects records dated in the future, and a
        // day's fixture spans the full 24 hours.
        val today = Instant.now().atZone(zone).toLocalDate()
            .minusDays(1).atStartOfDay(zone).toInstant()
        val offset = zone.rules.getOffset(today)
        val random = Random(seed = 42)

        fun metadata() = Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE))

        val records = buildList<Record> {
            repeat(DAYS) { dayOffset ->
                val dayStart = today.minus(dayOffset.toLong(), ChronoUnit.DAYS)

                // Steps: many short intervals through waking hours, as a phone or watch
                // writes them, rather than three tidy blocks.
                repeat(STEP_RECORDS_PER_DAY) { i ->
                    val start = dayStart
                        .plus(7, ChronoUnit.HOURS)
                        .plus((i * 5).toLong(), ChronoUnit.MINUTES)
                    add(
                        StepsRecord(
                            startTime = start,
                            startZoneOffset = offset,
                            endTime = start.plus(5, ChronoUnit.MINUTES),
                            endZoneOffset = offset,
                            count = (5 + random.nextInt(150)).toLong(),
                            metadata = metadata(),
                        ),
                    )
                }

                // Heart rate: sampled series, several samples per record.
                repeat(HEART_RATE_RECORDS_PER_DAY) { i ->
                    val start = dayStart
                        .plus(6, ChronoUnit.HOURS)
                        .plus((i * 10).toLong(), ChronoUnit.MINUTES)
                    val samples = (0 until 11).map { s ->
                        HeartRateRecord.Sample(
                            time = start.plus((s * 5).toLong(), ChronoUnit.SECONDS),
                            beatsPerMinute = (52 + random.nextInt(90)).toLong(),
                        )
                    }
                    add(
                        HeartRateRecord(
                            startTime = samples.first().time,
                            startZoneOffset = offset,
                            endTime = samples.last().time.plus(1, ChronoUnit.SECONDS),
                            endZoneOffset = offset,
                            samples = samples,
                            metadata = metadata(),
                        ),
                    )
                }

                // Oxygen saturation: frequent single readings.
                repeat(SPO2_RECORDS_PER_DAY) { i ->
                    add(
                        OxygenSaturationRecord(
                            time = dayStart.plus((i * 30).toLong(), ChronoUnit.MINUTES),
                            zoneOffset = offset,
                            percentage = Percentage((92 + random.nextInt(8)).toDouble()),
                            metadata = metadata(),
                        ),
                    )
                }

                // Once-daily figures.
                add(
                    RestingHeartRateRecord(
                        time = dayStart.plus(5, ChronoUnit.HOURS),
                        zoneOffset = offset,
                        beatsPerMinute = (46 + random.nextInt(10)).toLong(),
                        metadata = metadata(),
                    ),
                )
                val sleepStart = dayStart.minus(1, ChronoUnit.DAYS).plus(23, ChronoUnit.HOURS)
                add(
                    SleepSessionRecord(
                        startTime = sleepStart,
                        startZoneOffset = offset,
                        endTime = sleepStart.plus((6 + random.nextInt(3)).toLong(), ChronoUnit.HOURS),
                        endZoneOffset = offset,
                        metadata = metadata(),
                    ),
                )

                // Weight only every few days, so the sparse-series path is exercised too.
                if (dayOffset % 7 == 0) {
                    add(
                        WeightRecord(
                            time = dayStart.plus(7, ChronoUnit.HOURS),
                            zoneOffset = offset,
                            weight = Mass.kilograms(79.0 + sin(dayOffset / 6.0) * 1.4),
                            metadata = metadata(),
                        ),
                    )
                }
            }
        }

        // Insert in batches: a single call with tens of thousands of records exceeds the
        // binder transaction limit.
        records.chunked(BATCH_SIZE).forEach { client.insertRecords(it) }
    }

    private const val STEP_RECORDS_PER_DAY = 120
    private const val HEART_RATE_RECORDS_PER_DAY = 60
    private const val SPO2_RECORDS_PER_DAY = 48
    private const val BATCH_SIZE = 500
}
