package de.steppicrew.healthconnectview.registry

import androidx.health.connect.client.permission.HealthPermission
import de.steppicrew.healthconnectview.health.RecordKind
import de.steppicrew.healthconnectview.health.Session
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.*
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.registry.RecordTypeSpec.Shape
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

private fun durationHours(start: Instant, end: Instant): Double =
    Duration.between(start, end).toMinutes() / 60.0

private fun seriesSummary(values: List<Double>, unit: String): String =
    if (values.isEmpty()) "—" else Formatting.number(values.average()) + " " + unit + " (" + values.size + ")"

private fun flowRes(flow: Int): Int = when (flow) {
    MenstruationFlowRecord.FLOW_LIGHT -> R.string.flow_light
    MenstruationFlowRecord.FLOW_MEDIUM -> R.string.flow_medium
    MenstruationFlowRecord.FLOW_HEAVY -> R.string.flow_heavy
    else -> R.string.flow_unspecified
}

private fun ovulationRes(result: Int): Int = when (result) {
    OvulationTestRecord.RESULT_POSITIVE -> R.string.ovulation_positive
    OvulationTestRecord.RESULT_HIGH -> R.string.ovulation_high
    OvulationTestRecord.RESULT_NEGATIVE -> R.string.ovulation_negative
    else -> R.string.ovulation_inconclusive
}

private fun mucusRes(record: CervicalMucusRecord): List<Int> = listOfNotNull(
    when (record.appearance) {
        CervicalMucusRecord.APPEARANCE_DRY -> R.string.mucus_dry
        CervicalMucusRecord.APPEARANCE_STICKY -> R.string.mucus_sticky
        CervicalMucusRecord.APPEARANCE_CREAMY -> R.string.mucus_creamy
        CervicalMucusRecord.APPEARANCE_WATERY -> R.string.mucus_watery
        CervicalMucusRecord.APPEARANCE_EGG_WHITE -> R.string.mucus_egg_white
        CervicalMucusRecord.APPEARANCE_UNUSUAL -> R.string.mucus_unusual
        else -> R.string.mucus_unspecified
    },
    when (record.sensation) {
        CervicalMucusRecord.SENSATION_LIGHT -> R.string.sensation_light
        CervicalMucusRecord.SENSATION_MEDIUM -> R.string.sensation_medium
        CervicalMucusRecord.SENSATION_HEAVY -> R.string.sensation_heavy
        else -> null
    },
)

// Unknown is its own state: rendering it as either answer would state something never recorded.
private fun protectionRes(value: Int): Int = when (value) {
    SexualActivityRecord.PROTECTION_USED_PROTECTED -> R.string.protection_used
    SexualActivityRecord.PROTECTION_USED_UNPROTECTED -> R.string.protection_not_used
    else -> R.string.protection_unspecified
}

/**
 * Every Health Connect record type this app can display, as data.
 *
 * MindfulnessSessionRecord was left out for a while on the belief that the library asked for
 * `READ_MINDFULNESS_SESSION`, which the platform does not define. Checked against
 * connect-client 1.1.0 on 27.09.2026: it resolves to `READ_MINDFULNESS`, the platform's own
 * name, and `RecordRegistryTest` checks that against `android.jar` like every other type.
 */
object RecordRegistry {

    /**
     * The quantity each aggregate measures, so an aggregate converts exactly as its type's
     * records do. Built from the specs, so a new metric needs no second list.
     */
    val quantityOfMetric: Map<AggregateMetric<*>, Quantity> by lazy {
        buildMap {
            all.forEach { spec ->
                val quantity = spec.quantity ?: return@forEach
                listOfNotNull(spec.aggregate, spec.secondaryAggregate)
                    .plus(spec.rangeAggregates?.toList().orEmpty())
                    .plus(spec.stackComponents.map { it.second })
                    .forEach { put(it, quantity) }
            }
        }
    }

    val all: List<RecordTypeSpec<*>> = listOf(
        RecordTypeSpec(
            type = ActiveCaloriesBurnedRecord::class,
            displayNameRes = R.string.type_active_calories_burned,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_kcal,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { listOf(Point(it.startTime, it.energy.inKilocalories)) },
            summary = { Formatting.number(it.energy.inKilocalories) + " kcal" },
            aggregate = ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
            tile = TileSpec(
                TileSpec.Form.NUMBER,
                cumulativeIntraday = true,
                overlaySessions = TileSpec.ACTIVITY_CONTEXT,
                personalRecord = RecordKind.MOST,
            ),
        ),
        RecordTypeSpec(
            type = BasalMetabolicRateRecord::class,
            displayNameRes = R.string.type_basal_metabolic_rate,
            category = Category.BODY,
            unitRes = R.string.unit_kcal_day,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.basalMetabolicRate.inKilocaloriesPerDay)) },
            summary = { Formatting.number(it.basalMetabolicRate.inKilocaloriesPerDay) + " kcal/day" },
            aggregate = BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL,
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true, dailyValue = true),
        ),
        RecordTypeSpec(
            type = CyclingPedalingCadenceRecord::class,
            displayNameRes = R.string.type_cycling_pedaling_cadence,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_rpm,
            shape = Shape.SERIES,
            startTime = { it.startTime },
            points = { r -> r.samples.map { Point(it.time, it.revolutionsPerMinute) } },
            summary = { r -> seriesSummary(r.samples.map { it.revolutionsPerMinute }, "rpm") },
            aggregate = CyclingPedalingCadenceRecord.RPM_AVG,
            rangeAggregates = CyclingPedalingCadenceRecord.RPM_MIN to CyclingPedalingCadenceRecord.RPM_MAX,
        ),
        RecordTypeSpec(
            type = DistanceRecord::class,
            displayNameRes = R.string.type_distance,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_km,
            quantity = Quantity.DISTANCE,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { listOf(Point(it.startTime, it.distance.inKilometers)) },
            summary = { Units.format(Quantity.DISTANCE, it.distance.inKilometers) },
            aggregate = DistanceRecord.DISTANCE_TOTAL,
            tile = TileSpec(
                TileSpec.Form.RING,
                defaultGoal = 5.0,
                cumulativeIntraday = true,
                overlaySessions = TileSpec.ACTIVITY_CONTEXT,
                personalRecord = RecordKind.MOST,
            ),
        ),
        RecordTypeSpec(
            type = ElevationGainedRecord::class,
            displayNameRes = R.string.type_elevation_gained,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_m,
            quantity = Quantity.ELEVATION,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { listOf(Point(it.startTime, it.elevation.inMeters)) },
            summary = { Units.format(Quantity.ELEVATION, it.elevation.inMeters) },
            aggregate = ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
            tile = TileSpec(
                TileSpec.Form.NUMBER,
                cumulativeIntraday = true,
                overlaySessions = TileSpec.ACTIVITY_CONTEXT,
                personalRecord = RecordKind.MOST,
            ),
        ),
        RecordTypeSpec(
            type = ExerciseSessionRecord::class,
            tile = TileSpec(
                TileSpec.Form.SESSIONS,
                smoothChart = false,
                sessionKind = Session.Kind.EXERCISE,
                personalRecord = RecordKind.LONGEST,
            ),
            displayNameRes = R.string.type_exercise_session,
            category = Category.ACTIVITY,
            unitRes = null,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { listOf(Point(it.startTime, durationHours(it.startTime, it.endTime))) },
            summary = { Formatting.duration(Duration.between(it.startTime, it.endTime)) },
            aggregate = ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
        ),
        RecordTypeSpec(
            type = FloorsClimbedRecord::class,
            displayNameRes = R.string.type_floors_climbed,
            summaryUnitRes = R.string.unit_floors,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_floors,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { listOf(Point(it.startTime, it.floors)) },
            summary = { Formatting.number(it.floors) },
            aggregate = FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
            tile = TileSpec(
                TileSpec.Form.RING,
                defaultGoal = 10.0,
                cumulativeIntraday = true,
                integralValues = true,
                overlaySessions = TileSpec.ACTIVITY_CONTEXT,
                personalRecord = RecordKind.MOST,
            ),
        ),
        RecordTypeSpec(
            type = PlannedExerciseSessionRecord::class,
            displayNameRes = R.string.type_planned_exercise,
            category = Category.ACTIVITY,
            unitRes = null,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { emptyList() },
            summary = { it.title ?: it.exerciseType.toString() },
        ),
        RecordTypeSpec(
            type = PowerRecord::class,
            displayNameRes = R.string.type_power,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_w,
            shape = Shape.SERIES,
            startTime = { it.startTime },
            points = { r -> r.samples.map { Point(it.time, it.power.inWatts) } },
            summary = { r -> seriesSummary(r.samples.map { it.power.inWatts }, "W") },
            aggregate = PowerRecord.POWER_AVG,
            rangeAggregates = PowerRecord.POWER_MIN to PowerRecord.POWER_MAX,
        ),
        RecordTypeSpec(
            type = SpeedRecord::class,
            displayNameRes = R.string.type_speed,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_kmh,
            quantity = Quantity.SPEED,
            shape = Shape.SERIES,
            startTime = { it.startTime },
            points = { r -> r.samples.map { Point(it.time, it.speed.inKilometersPerHour) } },
            summary = { r ->
                seriesSummary(
                    r.samples.map { Quantity.SPEED.convert(it.speed.inKilometersPerHour) },
                    Quantity.SPEED.symbol(),
                )
            },
            aggregate = SpeedRecord.SPEED_AVG,
            rangeAggregates = SpeedRecord.SPEED_MIN to SpeedRecord.SPEED_MAX,
        ),
        RecordTypeSpec(
            type = StepsRecord::class,
            displayNameRes = R.string.type_steps,
            summaryUnitRes = R.string.unit_steps,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_steps,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { listOf(Point(it.startTime, it.count.toDouble())) },
            summary = { Formatting.integer(it.count) },
            aggregate = StepsRecord.COUNT_TOTAL,
            tile = TileSpec(
                TileSpec.Form.RING,
                defaultGoal = 10_000.0,
                cumulativeIntraday = true,
                integralValues = true,
                overlaySessions = TileSpec.ACTIVITY_CONTEXT,
                rollingBaseline = true,
                personalRecord = RecordKind.MOST,
            ),
        ),
        RecordTypeSpec(
            type = StepsCadenceRecord::class,
            displayNameRes = R.string.type_steps_cadence,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_spm,
            shape = Shape.SERIES,
            startTime = { it.startTime },
            points = { r -> r.samples.map { Point(it.time, it.rate) } },
            summary = { r -> seriesSummary(r.samples.map { it.rate }, "spm") },
            aggregate = StepsCadenceRecord.RATE_AVG,
            rangeAggregates = StepsCadenceRecord.RATE_MIN to StepsCadenceRecord.RATE_MAX,
        ),
        RecordTypeSpec(
            type = TotalCaloriesBurnedRecord::class,
            displayNameRes = R.string.type_total_calories_burned,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_kcal,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { listOf(Point(it.startTime, it.energy.inKilocalories)) },
            summary = { Formatting.number(it.energy.inKilocalories) + " kcal" },
            aggregate = TotalCaloriesBurnedRecord.ENERGY_TOTAL,
            // Basal first: it is the floor the day is built on, so it belongs at the bottom
            // of the bar. Note BasalMetabolicRate stores no records at all on a real device
            // -- it is derived from height and weight -- so this can only come from the
            // aggregate, never from a record read.
            stackComponents = listOf(
                R.string.calories_basal to BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL,
                R.string.calories_active to ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
            ),
            tile = TileSpec(
                TileSpec.Form.RING,
                defaultGoal = 2_200.0,
                cumulativeIntraday = true,
                overlaySessions = TileSpec.ACTIVITY_CONTEXT,
            ),
        ),
        RecordTypeSpec(
            type = Vo2MaxRecord::class,
            displayNameRes = R.string.type_vo2_max,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_vo2,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.vo2MillilitersPerMinuteKilogram)) },
            summary = { Formatting.number(it.vo2MillilitersPerMinuteKilogram) + " mL/kg/min" },
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true, personalRecord = RecordKind.HIGHEST, occasional = true),
        ),
        RecordTypeSpec(
            type = WheelchairPushesRecord::class,
            displayNameRes = R.string.type_wheelchair_pushes,
            summaryUnitRes = R.string.unit_pushes,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_pushes,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { listOf(Point(it.startTime, it.count.toDouble())) },
            summary = { Formatting.integer(it.count) },
            aggregate = WheelchairPushesRecord.COUNT_TOTAL,
            tile = TileSpec(
                TileSpec.Form.NUMBER,
                cumulativeIntraday = true,
                integralValues = true,
                overlaySessions = TileSpec.ACTIVITY_CONTEXT,
                personalRecord = RecordKind.MOST,
            ),
        ),
        RecordTypeSpec(
            type = BodyFatRecord::class,
            displayNameRes = R.string.type_body_fat,
            category = Category.BODY,
            unitRes = R.string.unit_percent,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.percentage.value)) },
            summary = { Formatting.number(it.percentage.value) + " %" },
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true, carryLastReading = true, occasional = true),
        ),
        RecordTypeSpec(
            type = BodyWaterMassRecord::class,
            displayNameRes = R.string.type_body_water_mass,
            category = Category.BODY,
            unitRes = R.string.unit_kg,
            quantity = Quantity.MASS,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.mass.inKilograms)) },
            summary = { Units.format(Quantity.MASS, it.mass.inKilograms) },
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true, carryLastReading = true, occasional = true),
        ),
        RecordTypeSpec(
            type = BoneMassRecord::class,
            displayNameRes = R.string.type_bone_mass,
            category = Category.BODY,
            unitRes = R.string.unit_kg,
            quantity = Quantity.MASS,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.mass.inKilograms)) },
            summary = { Units.format(Quantity.MASS, it.mass.inKilograms) },
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true, carryLastReading = true, occasional = true),
        ),
        RecordTypeSpec(
            type = HeightRecord::class,
            displayNameRes = R.string.type_height,
            category = Category.BODY,
            unitRes = R.string.unit_cm,
            quantity = Quantity.BODY_HEIGHT,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.height.inMeters * 100.0)) },
            summary = { Units.format(Quantity.BODY_HEIGHT, it.height.inMeters * 100.0) },
            aggregate = HeightRecord.HEIGHT_AVG,
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true, carryLastReading = true, occasional = true),
        ),
        RecordTypeSpec(
            type = LeanBodyMassRecord::class,
            displayNameRes = R.string.type_lean_body_mass,
            category = Category.BODY,
            unitRes = R.string.unit_kg,
            quantity = Quantity.MASS,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.mass.inKilograms)) },
            summary = { Units.format(Quantity.MASS, it.mass.inKilograms) },
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true, carryLastReading = true, occasional = true),
        ),
        RecordTypeSpec(
            type = WeightRecord::class,
            displayNameRes = R.string.type_weight,
            category = Category.BODY,
            unitRes = R.string.unit_kg,
            quantity = Quantity.MASS,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.weight.inKilograms)) },
            summary = { Units.format(Quantity.MASS, it.weight.inKilograms) },
            aggregate = WeightRecord.WEIGHT_AVG,
            rangeAggregates = WeightRecord.WEIGHT_MIN to WeightRecord.WEIGHT_MAX,
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true, carryLastReading = true, occasional = true),
        ),
        RecordTypeSpec(
            type = BloodGlucoseRecord::class,
            displayNameRes = R.string.type_blood_glucose,
            category = Category.VITALS,
            unitRes = R.string.unit_mmoll,
            shape = Shape.INSTANT,
            startTime = { it.time },
            quantity = Quantity.GLUCOSE,
            points = { listOf(Point(it.time, it.level.inMillimolesPerLiter)) },
            summary = { Units.format(Quantity.GLUCOSE, it.level.inMillimolesPerLiter) },
            // Several readings a day, before and after meals: across days a mean per day with
            // its low-high band, as oxygen saturation, rather than every reading in a zigzag.
            // The band is what keeps a meal's peak visible behind the mean.
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true, dailyMeans = true),
        ),
        RecordTypeSpec(
            type = BloodPressureRecord::class,
            displayNameRes = R.string.type_blood_pressure,
            category = Category.VITALS,
            unitRes = R.string.unit_mmhg,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.systolic.inMillimetersOfMercury)) },
            summary = { Formatting.number(it.systolic.inMillimetersOfMercury) + "/" + Formatting.number(it.diastolic.inMillimetersOfMercury) + " mmHg" },
            aggregate = BloodPressureRecord.SYSTOLIC_AVG,
            secondaryPoints = { listOf(Point(it.time, it.diastolic.inMillimetersOfMercury)) },
            secondaryAggregate = BloodPressureRecord.DIASTOLIC_AVG,
            // Occasional only on a day of one reading: with a morning and an evening reading
            // the time of day is the point, and the day keeps its chart.
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true, occasional = true),
        ),
        RecordTypeSpec(
            type = BodyTemperatureRecord::class,
            displayNameRes = R.string.type_body_temperature,
            category = Category.VITALS,
            unitRes = R.string.unit_celsius,
            quantity = Quantity.TEMPERATURE,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.temperature.inCelsius)) },
            summary = { Units.format(Quantity.TEMPERATURE, it.temperature.inCelsius) },
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true),
        ),
        RecordTypeSpec(
            type = HeartRateRecord::class,
            displayNameRes = R.string.type_heart_rate,
            category = Category.VITALS,
            unitRes = R.string.unit_bpm,
            shape = Shape.SERIES,
            startTime = { it.startTime },
            points = { r -> r.samples.map { Point(it.time, it.beatsPerMinute.toDouble()) } },
            summary = { r -> seriesSummary(r.samples.map { it.beatsPerMinute.toDouble() }, "bpm") },
            aggregate = HeartRateRecord.BPM_AVG,
            rangeAggregates = HeartRateRecord.BPM_MIN to HeartRateRecord.BPM_MAX,
            tile = TileSpec(
                TileSpec.Form.CURVE,
                defaultZones = ValueZones.DEFAULT_HEART_RATE,
                integralValues = true,
                overlaySessions = TileSpec.ACTIVITY_CONTEXT,
            ),
        ),
        RecordTypeSpec(
            type = HeartRateVariabilityRmssdRecord::class,
            displayNameRes = R.string.type_hrv,
            category = Category.VITALS,
            unitRes = R.string.unit_ms,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.heartRateVariabilityMillis)) },
            summary = { Formatting.number(it.heartRateVariabilityMillis) + " ms" },
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true, nightlyStatus = true),
        ),
        RecordTypeSpec(
            type = OxygenSaturationRecord::class,
            displayNameRes = R.string.type_oxygen_saturation,
            category = Category.VITALS,
            unitRes = R.string.unit_percent,
            shape = Shape.INSTANT,
            startTime = { it.time },
            // A saturation of 0 is a moment the sensor could not measure, written as a number
            // by some writers; charted, it is a plunge to the axis nobody survived. The record
            // list still shows it, as what the app stored.
            points = { listOfNotNull(Point(it.time, it.percentage.value).takeIf { p -> p.value > 0.0 }) },
            summary = { Formatting.number(it.percentage.value) + " %" },
            // The general adult reference, named as such in the legend -- not the wearer's own
            // range, and not a diagnosis.
            tile = TileSpec(
                TileSpec.Form.NUMBER,
                markReadings = true,
                dailyMeans = true,
                rollingBaseline = true,
                referenceRange = ReferenceRange(95.0, 100.0, R.string.reference_spo2),
            ),
        ),
        RecordTypeSpec(
            type = RespiratoryRateRecord::class,
            displayNameRes = R.string.type_respiratory_rate,
            summaryUnitRes = R.string.unit_rpm_breath,
            category = Category.VITALS,
            unitRes = R.string.unit_rpm_breath,
            shape = Shape.INSTANT,
            startTime = { it.time },
            // As for oxygen saturation: on the phone Health Sync wrote several 0 breaths/min a
            // day where the watch had no reading, and the chart dived to the axis at each one.
            points = { listOfNotNull(Point(it.time, it.rate).takeIf { p -> p.value > 0.0 }) },
            summary = { Formatting.number(it.rate) },
            tile = TileSpec(
                TileSpec.Form.NUMBER,
                markReadings = true,
                integralValues = true,
                dailyMeans = true,
                // The wearer's usual range rather than a dashed mean: "is tonight normal for
                // me" is read off a band at a glance, and a mean says only where the middle is.
                usualRange = true,
            ),
        ),
        RecordTypeSpec(
            type = RestingHeartRateRecord::class,
            displayNameRes = R.string.type_resting_heart_rate,
            category = Category.VITALS,
            unitRes = R.string.unit_bpm,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.beatsPerMinute.toDouble())) },
            summary = { Formatting.integer(it.beatsPerMinute) + " bpm" },
            aggregate = RestingHeartRateRecord.BPM_AVG,
            rangeAggregates = RestingHeartRateRecord.BPM_MIN to RestingHeartRateRecord.BPM_MAX,
            tile = TileSpec(
                TileSpec.Form.NUMBER,
                markReadings = true,
                integralValues = true,
                dailyValue = true,
                usualRange = true,
                personalRecord = RecordKind.LOWEST,
            ),
        ),
        RecordTypeSpec(
            type = SkinTemperatureRecord::class,
            displayNameRes = R.string.type_skin_temperature,
            category = Category.VITALS,
            unitRes = R.string.unit_celsius,
            quantity = Quantity.TEMPERATURE_CHANGE,
            shape = Shape.SERIES,
            startTime = { it.startTime },
            // The measurements live in deltas; baseline is often absent, so charting only the
            // baseline would leave the chart empty while records were still listed.
            points = { r -> r.deltas.map { Point(it.time, it.delta.inCelsius) } },
            summary = { r ->
                val baseline = r.baseline?.let { Units.format(Quantity.TEMPERATURE, it.inCelsius) }
                val deltas = r.deltas.map { it.delta.inCelsius }
                when {
                    baseline != null -> baseline
                    deltas.isEmpty() -> "—"
                    else -> "%+.2f %s".format(
                        Quantity.TEMPERATURE_CHANGE.convert(deltas.average()),
                        Quantity.TEMPERATURE_CHANGE.symbol(),
                    )
                }
            },
            // Read through the night, every few minutes: across days a mean per day with its
            // band, like the other frequent readings.
            tile = TileSpec(TileSpec.Form.NUMBER, dailyMeans = true),
        ),
        RecordTypeSpec(
            type = HydrationRecord::class,
            displayNameRes = R.string.type_hydration,
            category = Category.NUTRITION,
            unitRes = R.string.unit_l,
            quantity = Quantity.VOLUME,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { listOf(Point(it.startTime, it.volume.inLiters)) },
            summary = { Units.format(Quantity.VOLUME, it.volume.inLiters) },
            aggregate = HydrationRecord.VOLUME_TOTAL,
            tile = TileSpec(
                TileSpec.Form.NUMBER,
                cumulativeIntraday = true,
            ),
        ),
        RecordTypeSpec(
            type = NutritionRecord::class,
            // Energy eaten adds up through the day like hydration: a running total on a day,
            // a bar per day across days. As a reading it drew a line between meals, as if
            // something were eaten in between.
            tile = TileSpec(TileSpec.Form.NUMBER, cumulativeIntraday = true),
            displayNameRes = R.string.type_nutrition,
            category = Category.NUTRITION,
            unitRes = R.string.unit_kcal,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { listOf(Point(it.startTime, it.energy?.inKilocalories ?: 0.0)) },
            summary = { it.energy?.let { e -> Formatting.number(e.inKilocalories) + " kcal" } ?: "—" },
            aggregate = NutritionRecord.ENERGY_TOTAL,
        ),
        RecordTypeSpec(
            type = SleepSessionRecord::class,
            tile = TileSpec(
                TileSpec.Form.SESSIONS,
                smoothChart = false,
                sessionKind = Session.Kind.SLEEP,
                rollingBaseline = true,
            ),
            displayNameRes = R.string.type_sleep_session,
            category = Category.SLEEP,
            unitRes = R.string.unit_h,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { listOf(Point(it.startTime, durationHours(it.startTime, it.endTime))) },
            summary = { Formatting.duration(Duration.between(it.startTime, it.endTime)) },
            aggregate = SleepSessionRecord.SLEEP_DURATION_TOTAL,
        ),
        mindfulnessSpec(),
        RecordTypeSpec(
            type = CervicalMucusRecord::class,
            displayNameRes = R.string.type_cervical_mucus,
            category = Category.CYCLE,
            unitRes = null,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { emptyList() },
            summaryRes = { mucusRes(it) },
        ),
        RecordTypeSpec(
            type = IntermenstrualBleedingRecord::class,
            displayNameRes = R.string.type_intermenstrual_bleeding,
            category = Category.CYCLE,
            unitRes = null,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { emptyList() },
            // The record carries no value: its existence is the observation.
            summaryRes = { listOf(R.string.cycle_recorded) },
        ),
        RecordTypeSpec(
            type = MenstruationFlowRecord::class,
            displayNameRes = R.string.type_menstruation_flow,
            category = Category.CYCLE,
            unitRes = null,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { emptyList() },
            summaryRes = { listOf(flowRes(it.flow)) },
        ),
        RecordTypeSpec(
            type = MenstruationPeriodRecord::class,
            tile = TileSpec(TileSpec.Form.NUMBER, smoothChart = false),
            displayNameRes = R.string.type_menstruation_period,
            category = Category.CYCLE,
            unitRes = null,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            endTime = { it.endTime },
            points = { emptyList() },
            summary = { Formatting.duration(Duration.between(it.startTime, it.endTime)) },
        ),
        RecordTypeSpec(
            type = OvulationTestRecord::class,
            displayNameRes = R.string.type_ovulation_test,
            category = Category.CYCLE,
            unitRes = null,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { emptyList() },
            summaryRes = { listOf(ovulationRes(it.result)) },
        ),
        RecordTypeSpec(
            type = BasalBodyTemperatureRecord::class,
            displayNameRes = R.string.type_basal_body_temperature,
            // Filed with the cycle rather than the vitals: a basal reading means little on its
            // own and is read for the shift across a cycle, which is where the overview draws it.
            category = Category.CYCLE,
            unitRes = R.string.unit_celsius,
            quantity = Quantity.TEMPERATURE,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.temperature.inCelsius)) },
            summary = { Units.format(Quantity.TEMPERATURE, it.temperature.inCelsius) },
            tile = TileSpec(TileSpec.Form.NUMBER, markReadings = true),
        ),
        RecordTypeSpec(
            type = SexualActivityRecord::class,
            displayNameRes = R.string.type_sexual_activity,
            category = Category.CYCLE,
            unitRes = null,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { emptyList() },
            summaryRes = { listOf(protectionRes(it.protectionUsed)) },
        ),
    )

    private val byType: Map<KClass<out Record>, RecordTypeSpec<*>> = all.associateBy { it.type }

    /** Distinct read permissions; several types share one, so this is smaller than [all]. */
    val allReadPermissions: Set<String> = all.map { it.permission }.toSet()

    /**
     * Unlocks reading further back than the platform's default 30-day window.
     *
     * Deliberately not part of [allReadPermissions]: it belongs to no record type, and the
     * granted/total counter shown to the user counts types. Folding it in would report 41 of
     * 41 types while only 40 exist. It has to be requested explicitly alongside the type
     * permissions -- declaring it in the manifest grants nothing on its own, and without it
     * every range longer than 30 days silently returns 30 days of data.
     */
    val HISTORY_PERMISSION: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    /**
     * Every exercise route at once, the standing form of the per-session consent. Like
     * history, it belongs to no record type, and it is never part of "select all": location
     * is the most sensitive thing Health Connect holds, so it is ticked on its own or not at
     * all. Without it a route is still shown when asked for, one session at a time, through
     * the system's own consent dialog.
     *
     * The library has no constant for it (it has only the write one); the platform defines
     * it, and this is its value.
     */
    const val ROUTES_PERMISSION: String = "android.permission.health.READ_EXERCISE_ROUTES"

    val byCategory: Map<Category, List<RecordTypeSpec<*>>> =
        all.groupBy { it.category }.toSortedMap(compareBy { it.ordinal })

    fun spec(type: KClass<out Record>): RecordTypeSpec<*> = byType.getValue(type)

    fun specOrNull(simpleName: String): RecordTypeSpec<*>? =
        all.firstOrNull { it.type.simpleName == simpleName }
}

/**
 * Meditation, breathing, guided tracks: sessions with a duration, shown like sleep -- the list,
 * a day's total, hours per day across days, all from the sessions themselves. Its own function for the library's experimental
 * marker on the record type, so the opt-in stays on this one entry.
 */
@OptIn(ExperimentalMindfulnessSessionApi::class)
private fun mindfulnessSpec(): RecordTypeSpec<*> = RecordTypeSpec(
    type = MindfulnessSessionRecord::class,
    tile = TileSpec(
        TileSpec.Form.SESSIONS,
        smoothChart = false,
        sessionKind = Session.Kind.MINDFULNESS,
    ),
    displayNameRes = R.string.type_mindfulness_session,
    category = Category.MINDFULNESS,
    unitRes = R.string.unit_h,
    shape = Shape.INTERVAL,
    startTime = { it.startTime },
    endTime = { it.endTime },
    points = { listOf(Point(it.startTime, durationHours(it.startTime, it.endTime))) },
    summary = { Formatting.duration(Duration.between(it.startTime, it.endTime)) },
    // No aggregate, although the library offers MINDFULNESS_DURATION_TOTAL: Health Connect on
    // the phone (Android 16, 27.09.2026) refused it -- "Unsupported aggregation type
    // MindfulnessSession_duration" -- and the whole screen failed. A sessions tile totals its
    // own deduplicated list anyway, as sleep does.
)
