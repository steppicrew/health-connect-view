package de.steppicrew.healthconnectview.debug

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
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

    /** Every type this seeder writes; cleared before re-seeding so fixtures cannot stack. */
    private val SEEDED_TYPES = listOf(
        StepsRecord::class,
        HeartRateRecord::class,
        SleepSessionRecord::class,
        ExerciseSessionRecord::class,
        WeightRecord::class,
        OxygenSaturationRecord::class,
        RestingHeartRateRecord::class,
        TotalCaloriesBurnedRecord::class,
        ActiveCaloriesBurnedRecord::class,
        DistanceRecord::class,
        FloorsClimbedRecord::class,
    )

/** One kind of seeded workout: what it was, what it is called, and when it happens. */
private data class SeededWorkout(val type: Int, val title: String, val startHour: Int)

/**
 * Varied so the icons and titles differ between days in the session list.
 *
 * The hour travels with the title, because they contradict each other otherwise: a fixed
 * 18:00 start produced a session called "Morning run" at six in the evening, which was
 * visible in a store screenshot. Fixture data has to be internally consistent or it
 * advertises itself as fake.
 */
private val SEEDED_WORKOUTS = listOf(
    SeededWorkout(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, "Morning run", startHour = 7),
    SeededWorkout(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, "Ride home", startHour = 17),
    SeededWorkout(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, "Strength", startHour = 18),
    SeededWorkout(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, "Pool session", startHour = 12),
    SeededWorkout(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, "Evening yoga", startHour = 20),
)

    suspend fun seed(context: Context) {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        // Local midnight: Health Connect buckets daily aggregates by the record's own zone
        // offset, so seeding against UTC on a UTC+2 device lands records in the wrong day.
        // Today, not yesterday. The fixture used to start a day back because Health Connect
        // rejects future-dated records and a day's fixture spans 24 hours -- but that left
        // the dashboard, which opens on today, showing a dash on every tile. That reads as
        // the app failing to load rather than as a day with nothing in it, which is the
        // wrong impression to give in a screenshot or during development.
        //
        // Records past `now` are dropped instead, so today is filled up to the current
        // moment and the day genuinely is in progress.
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val offset = zone.rules.getOffset(today)
        val random = Random(seed = 42)

        fun metadata() = Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE))

        // Health Connect validates in the record *constructor*, not at insert, so a
        // future-dated record throws while the fixture is still being built -- there is no
        // list to filter afterwards. Every creation site is therefore guarded up front.
        //
        // Today is seeded like any other day and simply stops at the current moment, which
        // is what makes the dashboard show a day in progress rather than a grid of dashes.
        fun past(vararg moments: Instant): Boolean = moments.none { it.isAfter(now) }

        val records = buildList<Record> {
            repeat(DAYS) { dayOffset ->
                val dayStart = today.minus(dayOffset.toLong(), ChronoUnit.DAYS)

                // Steps: many short intervals through waking hours, as a phone or watch
                // writes them, rather than three tidy blocks.
                repeat(STEP_RECORDS_PER_DAY) { i ->
                    val start = dayStart
                        .plus(7, ChronoUnit.HOURS)
                        .plus((i * 5).toLong(), ChronoUnit.MINUTES)
                    if (!past(start.plus(5, ChronoUnit.MINUTES))) return@repeat
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

                // Heart rate across the whole day, following a plausible daily rhythm.
                //
                // Two earlier faults, both visible in a store screenshot: the samples began
                // at 06:00, so the overnight sleep band on the chart sat above a blank
                // stretch with no readings under it; and the value was uniform noise between
                // 52 and 142, which draws as a solid band rather than as a day. Real heart
                // rate is low and steady asleep, climbs on waking, and moves around a higher
                // daytime level.
                // Where a workout is seeded below it writes its own, much denser samples for
                // that window. Both series over the same minutes puts two different readings
                // on the same instants, and the session chart then zigzags between them --
                // which looks like wild variation and is really just two fixtures overlapping.
                val workoutToday = SEEDED_WORKOUTS[dayOffset % SEEDED_WORKOUTS.size]
                    .takeIf { dayOffset % 3 != 2 }
                repeat(HEART_RATE_RECORDS_PER_DAY) { i ->
                    val minuteOfDay = i * (MINUTES_PER_DAY / HEART_RATE_RECORDS_PER_DAY)
                    val start = dayStart.plus(minuteOfDay.toLong(), ChronoUnit.MINUTES)
                    val hour = minuteOfDay / 60
                    // Two hours is wider than any seeded workout, so the skip covers the
                    // whole session however long this day's happens to be.
                    if (workoutToday != null &&
                        hour >= workoutToday.startHour &&
                        hour < workoutToday.startHour + 2
                    ) {
                        return@repeat
                    }
                    // Asleep until 07:00 and again from 23:00, matching the sleep session.
                    val resting = when {
                        hour < 6 || hour >= 23 -> 51
                        hour < 7 -> 55
                        hour < 9 -> 72
                        else -> 76
                    }
                    // Drifts around the level rather than jumping to a new random value each
                    // sample: independent draws produced a solid band of noise on the tile's
                    // spark curve, where a day should read as a shape. Awake readings still
                    // wander further than asleep ones, which is most of what makes a night
                    // look like a night.
                    val spread = if (hour < 7 || hour >= 23) 2 else 7
                    var bpm = resting + random.nextInt(spread + 1)
                    val samples = (0 until 11).map { sample ->
                        bpm = (bpm + random.nextInt(-spread, spread + 1))
                            .coerceIn(resting - spread, resting + spread * 3)
                        HeartRateRecord.Sample(
                            time = start.plus((sample * 5).toLong(), ChronoUnit.SECONDS),
                            beatsPerMinute = bpm.toLong(),
                        )
                    }
                    if (!past(samples.last().time.plus(1, ChronoUnit.SECONDS))) return@repeat
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
                    val spo2At = dayStart.plus((i * 30).toLong(), ChronoUnit.MINUTES)
                    if (!past(spo2At)) return@repeat
                    add(
                        OxygenSaturationRecord(
                            time = spo2At,
                            zoneOffset = offset,
                            percentage = Percentage((92 + random.nextInt(8)).toDouble()),
                            metadata = metadata(),
                        ),
                    )
                }

                // The quantities the default dashboard shows. Without these the first screen
                // a new user (or a screenshot) sees is a grid of dashes, which reads as the
                // app failing to load rather than as a day with nothing recorded.
                repeat(ACTIVITY_RECORDS_PER_DAY) { i ->
                    val start = dayStart
                        .plus(7, ChronoUnit.HOURS)
                        .plus((i * 30).toLong(), ChronoUnit.MINUTES)
                    val end = start.plus(30, ChronoUnit.MINUTES)
                    if (!past(end)) return@repeat
                    add(
                        TotalCaloriesBurnedRecord(
                            startTime = start,
                            startZoneOffset = offset,
                            endTime = end,
                            endZoneOffset = offset,
                            energy = Energy.kilocalories(58.0 + random.nextInt(45)),
                            metadata = metadata(),
                        ),
                    )
                    add(
                        ActiveCaloriesBurnedRecord(
                            startTime = start,
                            startZoneOffset = offset,
                            endTime = end,
                            endZoneOffset = offset,
                            energy = Energy.kilocalories(9.0 + random.nextInt(38)),
                            metadata = metadata(),
                        ),
                    )
                    add(
                        DistanceRecord(
                            startTime = start,
                            startZoneOffset = offset,
                            endTime = end,
                            endZoneOffset = offset,
                            distance = Length.meters(120.0 + random.nextInt(700)),
                            metadata = metadata(),
                        ),
                    )
                    // Floors only sometimes: a value on every slot would make the chart a
                    // solid ramp and never exercise the gap handling.
                    if (random.nextInt(3) == 0) {
                        add(
                            FloorsClimbedRecord(
                                startTime = start,
                                startZoneOffset = offset,
                                endTime = end,
                                endZoneOffset = offset,
                                floors = 1.0 + random.nextInt(3),
                                metadata = metadata(),
                            ),
                        )
                    }
                }

                // Once-daily figures.
                val restingAt = dayStart.plus(5, ChronoUnit.HOURS)
                if (past(restingAt)) add(
                    RestingHeartRateRecord(
                        time = restingAt,
                        zoneOffset = offset,
                        beatsPerMinute = (46 + random.nextInt(10)).toLong(),
                        metadata = metadata(),
                    ),
                )
                val sleepStart = dayStart.minus(1, ChronoUnit.DAYS).plus(23, ChronoUnit.HOURS)
                val sleepEnd = sleepStart.plus((6 + random.nextInt(3)).toLong(), ChronoUnit.HOURS)
                if (past(sleepEnd)) add(
                    SleepSessionRecord(
                        startTime = sleepStart,
                        startZoneOffset = offset,
                        endTime = sleepEnd,
                        endZoneOffset = offset,
                        metadata = metadata(),
                    ),
                )

                // Exercise on most days, varying the activity so the icons and the session
                // list have something to distinguish. Not every day: a tile that always
                // reads the same never shows its empty state during development.
                if (dayOffset % 3 != 2) {
                    val workout = SEEDED_WORKOUTS[dayOffset % SEEDED_WORKOUTS.size]
                    val exerciseStart = dayStart.plus(workout.startHour.toLong(), ChronoUnit.HOURS)
                    val minutes = (25 + random.nextInt(40)).toLong()
                    // Skipped whole rather than truncated: half a workout with its heart rate
                    // cut off mid-effort is a worse fixture than no workout today.
                    if (!past(exerciseStart.plus(minutes, ChronoUnit.MINUTES))) return@repeat
                    add(
                        ExerciseSessionRecord(
                            startTime = exerciseStart,
                            startZoneOffset = offset,
                            endTime = exerciseStart.plus(minutes, ChronoUnit.MINUTES),
                            endZoneOffset = offset,
                            exerciseType = workout.type,
                            title = workout.title,
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
                    val weighedAt = dayStart.plus(7, ChronoUnit.HOURS)
                    if (past(weighedAt)) add(
                        WeightRecord(
                            time = weighedAt,
                            zoneOffset = offset,
                            weight = Mass.kilograms(79.0 + sin(dayOffset / 6.0) * 1.4),
                            metadata = metadata(),
                        ),
                    )
                }
            }
        }

        // Health Connect rejects anything dated in the future, and today's fixture runs to
        // midnight, so the tail of the current day is dropped rather than inserted. What
        // remains is a day in progress -- which is exactly what the app shows for today.
        // Matched on the concrete types this seeder creates, because the library's
        // InstantaneousRecord / IntervalRecord interfaces are public in bytecode but
        // `internal` to Kotlin, so an `is` check against them does not compile.

        // Delete this app's own previous fixtures first.
        //
        // The emulator's health database survives `pm clear` and even `pm uninstall`, so
        // re-seeding used to stack a second copy of every day on top of the first: sessions
        // appeared two and three times, totals doubled, and a session's heart-rate curve
        // became several runs overlaid, spiking wherever the copies disagreed. That reads as
        // broken aggregation rather than as a dirty fixture, which is a day lost to
        // debugging the wrong thing.
        //
        // Scoped to records this package wrote, so anything another app put there is left
        // alone -- deleting by time range would take real data with it on a device that has
        // any.
        val fixtureWindow = TimeRangeFilter.between(
            today.minus(DAYS.toLong() + 1, ChronoUnit.DAYS),
            now,
        )
        SEEDED_TYPES.forEach { type ->
            runCatching { client.deleteRecords(type, fixtureWindow) }
        }

        // Insert in batches: a single call with tens of thousands of records exceeds the
        // binder transaction limit.
        records.chunked(BATCH_SIZE).forEach { client.insertRecords(it) }
    }

    /** Half-hourly through the waking day, which is how a watch reports these. */
    private const val ACTIVITY_RECORDS_PER_DAY = 30

    private const val STEP_RECORDS_PER_DAY = 120
    private const val HEART_RATE_RECORDS_PER_DAY = 96

    /** Used to spread the day's heart-rate records evenly across all 24 hours. */
    private const val MINUTES_PER_DAY = 24 * 60
    private const val SPO2_RECORDS_PER_DAY = 48
    private const val BATCH_SIZE = 500
}
