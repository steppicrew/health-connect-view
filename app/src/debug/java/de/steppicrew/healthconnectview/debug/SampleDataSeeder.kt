package de.steppicrew.healthconnectview.debug

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.sin
import kotlin.random.Random

/**
 * Writes obviously-synthetic health data, for screenshots and for exercising the UI without
 * touching anyone's real records. Debug builds only — this source set is not part of release,
 * and the seeder needs WRITE permissions that the release manifest never requests.
 */
object SampleDataSeeder {

    suspend fun seed(context: Context, days: Int = 30) {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        // Local midnight, not UTC midnight: Health Connect groups daily aggregates by the
        // record's own zone offset, so seeding in UTC on a UTC+2 device lands the records
        // outside the day buckets they belong to.
        val today = Instant.now().atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val offset = zone.rules.getOffset(today)
        val random = Random(seed = 42)

        val records = buildList {
            repeat(days) { dayOffset ->
                val dayStart = today.minus(dayOffset.toLong(), ChronoUnit.DAYS)

                // Steps, split into a few blocks so the raw list has several rows per day.
                repeat(3) { block ->
                    val start = dayStart.plus((7 + block * 4).toLong(), ChronoUnit.HOURS)
                    add(
                        StepsRecord(
                            startTime = start,
                            startZoneOffset = offset,
                            endTime = start.plus(2, ChronoUnit.HOURS),
                            endZoneOffset = offset,
                            count = (1500 + random.nextInt(2500)).toLong(),
                            metadata = Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE)),
                        ),
                    )
                }

                // Weight drifting slowly, so the chart shows a readable trend.
                add(
                    WeightRecord(
                        time = dayStart.plus(7, ChronoUnit.HOURS),
                        zoneOffset = offset,
                        weight = Mass.kilograms(78.0 + sin(dayOffset / 6.0) * 1.4),
                        metadata = Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE)),
                    ),
                )

                // Heart rate as a sampled series.
                val samples = (0 until 12).map { i ->
                    HeartRateRecord.Sample(
                        time = dayStart.plus((8 + i).toLong(), ChronoUnit.HOURS),
                        beatsPerMinute = (58 + random.nextInt(45)).toLong(),
                    )
                }
                add(
                    HeartRateRecord(
                        startTime = samples.first().time,
                        startZoneOffset = offset,
                        endTime = samples.last().time.plus(1, ChronoUnit.MINUTES),
                        endZoneOffset = offset,
                        samples = samples,
                        metadata = Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE)),
                    ),
                )
            }
        }

        client.insertRecords(records)
    }
}
