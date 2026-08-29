package de.steppicrew.healthconnectview.debug

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
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

    /** Varied so the activity icons and titles differ between days in the session list. */
    private val EXERCISE_TYPES = listOf(
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
    )

    private val EXERCISE_TITLES = listOf(
        "Morning run",
        "Ride home",
        "Strength",
        "Pool session",
        "Evening yoga",
    )

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

                // Exercise on most days, varying the activity so the icons and the session
                // list have something to distinguish. Not every day: a tile that always
                // reads the same never shows its empty state during development.
                if (dayOffset % 3 != 2) {
                    val exerciseStart = dayStart.plus(18, ChronoUnit.HOURS)
                    val minutes = (25 + random.nextInt(40)).toLong()
                    add(
                        ExerciseSessionRecord(
                            startTime = exerciseStart,
                            startZoneOffset = offset,
                            endTime = exerciseStart.plus(minutes, ChronoUnit.MINUTES),
                            endZoneOffset = offset,
                            exerciseType = EXERCISE_TYPES[dayOffset % EXERCISE_TYPES.size],
                            title = EXERCISE_TITLES[dayOffset % EXERCISE_TITLES.size],
                            metadata = metadata(),
                        ),
                    )
                    // Heart rate through the session, denser than the resting samples, so a
                    // session curve has the resolution a real workout would give it.
                    //
                    // A wandering value rather than a clean wave: heart rate drifts and is
                    // pushed around by effort, it does not oscillate on a period. A sine
                    // looked obviously synthetic the moment the chart was tall enough to
                    // read, which matters when these frames end up in a store listing.
                    var bpm = 96
                    repeat(minutes.toInt()) { minute ->
                        val at = exerciseStart.plus(minute.toLong(), ChronoUnit.MINUTES)
                        // Effort builds over the first third and eases near the end, with the
                        // drift doing the rest.
                        val target = when {
                            minute < minutes / 3 -> 118 + minute
                            minute > minutes - 6 -> 104
                            else -> 138
                        }
                        add(
                            HeartRateRecord(
                                startTime = at,
                                startZoneOffset = offset,
                                endTime = at.plus(59, ChronoUnit.SECONDS),
                                endZoneOffset = offset,
                                samples = (0 until 4).map { sample ->
                                    // Pulled gently towards the target, plus beat-to-beat
                                    // noise; the result never repeats and stays plausible.
                                    bpm += ((target - bpm) / 8) + random.nextInt(-3, 4)
                                    bpm = bpm.coerceIn(70, 178)
                                    HeartRateRecord.Sample(
                                        time = at.plus((sample * 15).toLong(), ChronoUnit.SECONDS),
                                        beatsPerMinute = bpm.toLong(),
                                    )
                                },
                                metadata = metadata(),
                            ),
                        )
                    }
                }

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
