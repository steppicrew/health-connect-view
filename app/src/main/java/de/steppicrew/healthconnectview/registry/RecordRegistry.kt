package de.steppicrew.healthconnectview.registry

import androidx.health.connect.client.permission.HealthPermission
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

private fun appearanceLabel(value: Int): String = "appearance " + value

private fun flowLabel(value: Int): String = "flow " + value

private fun ovulationLabel(value: Int): String = "result " + value

private fun protectionLabel(value: Int): String = if (value == 1) "protected" else "unprotected"

/**
 * Every Health Connect record type this app can display, as data.
 *
 * MindfulnessSessionRecord is deliberately absent: the library requests
 * READ_MINDFULNESS_SESSION while the platform only defines READ_MINDFULNESS, so the
 * permission could never be granted. Revisit once those names converge.
 */
object RecordRegistry {

    val all: List<RecordTypeSpec<*>> = listOf(
        RecordTypeSpec(
            type = ActiveCaloriesBurnedRecord::class,
            displayNameRes = R.string.type_active_calories_burned,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_kcal,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            points = { listOf(Point(it.startTime, it.energy.inKilocalories)) },
            summary = { Formatting.number(it.energy.inKilocalories) + " kcal" },
            aggregate = ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
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
        ),
        RecordTypeSpec(
            type = DistanceRecord::class,
            displayNameRes = R.string.type_distance,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_km,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            points = { listOf(Point(it.startTime, it.distance.inKilometers)) },
            summary = { Formatting.number(it.distance.inKilometers) + " km" },
            aggregate = DistanceRecord.DISTANCE_TOTAL,
            tile = TileSpec(TileSpec.Form.RING, defaultGoal = 5.0),
        ),
        RecordTypeSpec(
            type = ElevationGainedRecord::class,
            displayNameRes = R.string.type_elevation_gained,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_m,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            points = { listOf(Point(it.startTime, it.elevation.inMeters)) },
            summary = { Formatting.number(it.elevation.inMeters) + " m" },
            aggregate = ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
        ),
        RecordTypeSpec(
            type = ExerciseSessionRecord::class,
            displayNameRes = R.string.type_exercise_session,
            category = Category.ACTIVITY,
            unitRes = null,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
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
            points = { listOf(Point(it.startTime, it.floors)) },
            summary = { Formatting.number(it.floors) },
            aggregate = FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
            tile = TileSpec(TileSpec.Form.RING, defaultGoal = 10.0),
        ),
        RecordTypeSpec(
            type = PlannedExerciseSessionRecord::class,
            displayNameRes = R.string.type_planned_exercise,
            category = Category.ACTIVITY,
            unitRes = null,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
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
        ),
        RecordTypeSpec(
            type = SpeedRecord::class,
            displayNameRes = R.string.type_speed,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_kmh,
            shape = Shape.SERIES,
            startTime = { it.startTime },
            points = { r -> r.samples.map { Point(it.time, it.speed.inKilometersPerHour) } },
            summary = { r -> seriesSummary(r.samples.map { it.speed.inKilometersPerHour }, "km/h") },
            aggregate = SpeedRecord.SPEED_AVG,
        ),
        RecordTypeSpec(
            type = StepsRecord::class,
            displayNameRes = R.string.type_steps,
            summaryUnitRes = R.string.unit_steps,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_steps,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            points = { listOf(Point(it.startTime, it.count.toDouble())) },
            summary = { Formatting.integer(it.count) },
            aggregate = StepsRecord.COUNT_TOTAL,
            tile = TileSpec(TileSpec.Form.RING, defaultGoal = 10_000.0),
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
        ),
        RecordTypeSpec(
            type = TotalCaloriesBurnedRecord::class,
            displayNameRes = R.string.type_total_calories_burned,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_kcal,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            points = { listOf(Point(it.startTime, it.energy.inKilocalories)) },
            summary = { Formatting.number(it.energy.inKilocalories) + " kcal" },
            aggregate = TotalCaloriesBurnedRecord.ENERGY_TOTAL,
            tile = TileSpec(TileSpec.Form.RING, defaultGoal = 2_200.0),
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
        ),
        RecordTypeSpec(
            type = WheelchairPushesRecord::class,
            displayNameRes = R.string.type_wheelchair_pushes,
            summaryUnitRes = R.string.unit_pushes,
            category = Category.ACTIVITY,
            unitRes = R.string.unit_pushes,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            points = { listOf(Point(it.startTime, it.count.toDouble())) },
            summary = { Formatting.integer(it.count) },
            aggregate = WheelchairPushesRecord.COUNT_TOTAL,
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
        ),
        RecordTypeSpec(
            type = BodyWaterMassRecord::class,
            displayNameRes = R.string.type_body_water_mass,
            category = Category.BODY,
            unitRes = R.string.unit_kg,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.mass.inKilograms)) },
            summary = { Formatting.number(it.mass.inKilograms) + " kg" },
        ),
        RecordTypeSpec(
            type = BoneMassRecord::class,
            displayNameRes = R.string.type_bone_mass,
            category = Category.BODY,
            unitRes = R.string.unit_kg,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.mass.inKilograms)) },
            summary = { Formatting.number(it.mass.inKilograms) + " kg" },
        ),
        RecordTypeSpec(
            type = HeightRecord::class,
            displayNameRes = R.string.type_height,
            category = Category.BODY,
            unitRes = R.string.unit_cm,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.height.inMeters * 100.0)) },
            summary = { Formatting.number(it.height.inMeters * 100.0) + " cm" },
            aggregate = HeightRecord.HEIGHT_AVG,
        ),
        RecordTypeSpec(
            type = LeanBodyMassRecord::class,
            displayNameRes = R.string.type_lean_body_mass,
            category = Category.BODY,
            unitRes = R.string.unit_kg,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.mass.inKilograms)) },
            summary = { Formatting.number(it.mass.inKilograms) + " kg" },
        ),
        RecordTypeSpec(
            type = WeightRecord::class,
            displayNameRes = R.string.type_weight,
            category = Category.BODY,
            unitRes = R.string.unit_kg,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.weight.inKilograms)) },
            summary = { Formatting.number(it.weight.inKilograms) + " kg" },
            aggregate = WeightRecord.WEIGHT_AVG,
        ),
        RecordTypeSpec(
            type = BasalBodyTemperatureRecord::class,
            displayNameRes = R.string.type_basal_body_temperature,
            category = Category.VITALS,
            unitRes = R.string.unit_celsius,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.temperature.inCelsius)) },
            summary = { Formatting.number(it.temperature.inCelsius) + " °C" },
        ),
        RecordTypeSpec(
            type = BloodGlucoseRecord::class,
            displayNameRes = R.string.type_blood_glucose,
            category = Category.VITALS,
            unitRes = R.string.unit_mmoll,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.level.inMillimolesPerLiter)) },
            summary = { Formatting.number(it.level.inMillimolesPerLiter) + " mmol/L" },
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
        ),
        RecordTypeSpec(
            type = BodyTemperatureRecord::class,
            displayNameRes = R.string.type_body_temperature,
            category = Category.VITALS,
            unitRes = R.string.unit_celsius,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.temperature.inCelsius)) },
            summary = { Formatting.number(it.temperature.inCelsius) + " °C" },
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
            tile = TileSpec(TileSpec.Form.CURVE, colorScale = 50.0..160.0),
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
        ),
        RecordTypeSpec(
            type = OxygenSaturationRecord::class,
            displayNameRes = R.string.type_oxygen_saturation,
            category = Category.VITALS,
            unitRes = R.string.unit_percent,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.percentage.value)) },
            summary = { Formatting.number(it.percentage.value) + " %" },
        ),
        RecordTypeSpec(
            type = RespiratoryRateRecord::class,
            displayNameRes = R.string.type_respiratory_rate,
            summaryUnitRes = R.string.unit_rpm_breath,
            category = Category.VITALS,
            unitRes = R.string.unit_rpm_breath,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { listOf(Point(it.time, it.rate)) },
            summary = { Formatting.number(it.rate) },
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
        ),
        RecordTypeSpec(
            type = SkinTemperatureRecord::class,
            displayNameRes = R.string.type_skin_temperature,
            category = Category.VITALS,
            unitRes = R.string.unit_celsius,
            shape = Shape.SERIES,
            startTime = { it.startTime },
            // The measurements live in deltas; baseline is often absent, so charting only the
            // baseline would leave the chart empty while records were still listed.
            points = { r -> r.deltas.map { Point(it.time, it.delta.inCelsius) } },
            summary = { r ->
                val baseline = r.baseline?.let { "%s °C".format(Formatting.number(it.inCelsius)) }
                val deltas = r.deltas.map { it.delta.inCelsius }
                when {
                    baseline != null -> baseline
                    deltas.isEmpty() -> "—"
                    else -> "%+.2f °C".format(deltas.average())
                }
            },
        ),
        RecordTypeSpec(
            type = HydrationRecord::class,
            displayNameRes = R.string.type_hydration,
            category = Category.NUTRITION,
            unitRes = R.string.unit_l,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            points = { listOf(Point(it.startTime, it.volume.inLiters)) },
            summary = { Formatting.number(it.volume.inLiters) + " L" },
            aggregate = HydrationRecord.VOLUME_TOTAL,
        ),
        RecordTypeSpec(
            type = NutritionRecord::class,
            displayNameRes = R.string.type_nutrition,
            category = Category.NUTRITION,
            unitRes = R.string.unit_kcal,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            points = { listOf(Point(it.startTime, it.energy?.inKilocalories ?: 0.0)) },
            summary = { it.energy?.let { e -> Formatting.number(e.inKilocalories) + " kcal" } ?: "—" },
            aggregate = NutritionRecord.ENERGY_TOTAL,
        ),
        RecordTypeSpec(
            type = SleepSessionRecord::class,
            displayNameRes = R.string.type_sleep_session,
            category = Category.SLEEP,
            unitRes = R.string.unit_h,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
            points = { listOf(Point(it.startTime, durationHours(it.startTime, it.endTime))) },
            summary = { Formatting.duration(Duration.between(it.startTime, it.endTime)) },
            aggregate = SleepSessionRecord.SLEEP_DURATION_TOTAL,
        ),
        RecordTypeSpec(
            type = CervicalMucusRecord::class,
            displayNameRes = R.string.type_cervical_mucus,
            category = Category.CYCLE,
            unitRes = null,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { emptyList() },
            summary = { appearanceLabel(it.appearance) },
        ),
        RecordTypeSpec(
            type = IntermenstrualBleedingRecord::class,
            displayNameRes = R.string.type_intermenstrual_bleeding,
            category = Category.CYCLE,
            unitRes = null,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { emptyList() },
            summary = { "—" },
        ),
        RecordTypeSpec(
            type = MenstruationFlowRecord::class,
            displayNameRes = R.string.type_menstruation_flow,
            category = Category.CYCLE,
            unitRes = null,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { emptyList() },
            summary = { flowLabel(it.flow) },
        ),
        RecordTypeSpec(
            type = MenstruationPeriodRecord::class,
            displayNameRes = R.string.type_menstruation_period,
            category = Category.CYCLE,
            unitRes = null,
            shape = Shape.INTERVAL,
            startTime = { it.startTime },
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
            summary = { ovulationLabel(it.result) },
        ),
        RecordTypeSpec(
            type = SexualActivityRecord::class,
            displayNameRes = R.string.type_sexual_activity,
            category = Category.CYCLE,
            unitRes = null,
            shape = Shape.INSTANT,
            startTime = { it.time },
            points = { emptyList() },
            summary = { protectionLabel(it.protectionUsed) },
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

    val byCategory: Map<Category, List<RecordTypeSpec<*>>> =
        all.groupBy { it.category }.toSortedMap(compareBy { it.ordinal })

    fun spec(type: KClass<out Record>): RecordTypeSpec<*> = byType.getValue(type)

    fun specOrNull(simpleName: String): RecordTypeSpec<*>? =
        all.firstOrNull { it.type.simpleName == simpleName }
}
