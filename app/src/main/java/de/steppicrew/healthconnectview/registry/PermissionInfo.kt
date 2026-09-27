package de.steppicrew.healthconnectview.registry

import androidx.annotation.StringRes
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PlannedExerciseSessionRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import de.steppicrew.healthconnectview.R
import kotlin.reflect.KClass

/**
 * What each permission is for, in the words behind the "i" on the permission screen: what the
 * data is, where the app shows it, and -- for the sensitive ones -- why it stays put. A
 * request that explains itself reads as less greedy than a list of switches.
 *
 * Keyed by record type, and types sharing a permission point at one text that names both:
 * ticking steps ticks step rate as well, which the row says and the text explains.
 * `PermissionInfoTest` fails when a type has none, so a new registry entry needs its words.
 *
 * Mindfulness sits outside this table for the library's experimental marker; see [infoFor].
 */
object PermissionInfo {

    private val byType: Map<KClass<out Record>, Int> = mapOf(
        ActiveCaloriesBurnedRecord::class to R.string.perm_info_active_calories,
        BasalMetabolicRateRecord::class to R.string.perm_info_basal_metabolic_rate,
        CyclingPedalingCadenceRecord::class to R.string.perm_info_exercise,
        DistanceRecord::class to R.string.perm_info_distance,
        ElevationGainedRecord::class to R.string.perm_info_elevation_gained,
        ExerciseSessionRecord::class to R.string.perm_info_exercise,
        FloorsClimbedRecord::class to R.string.perm_info_floors_climbed,
        PlannedExerciseSessionRecord::class to R.string.perm_info_planned_exercise,
        PowerRecord::class to R.string.perm_info_power,
        SpeedRecord::class to R.string.perm_info_speed,
        StepsRecord::class to R.string.perm_info_steps,
        StepsCadenceRecord::class to R.string.perm_info_steps,
        TotalCaloriesBurnedRecord::class to R.string.perm_info_total_calories,
        Vo2MaxRecord::class to R.string.perm_info_vo2_max,
        WheelchairPushesRecord::class to R.string.perm_info_wheelchair_pushes,
        BodyFatRecord::class to R.string.perm_info_body_fat,
        BodyWaterMassRecord::class to R.string.perm_info_body_water_mass,
        BoneMassRecord::class to R.string.perm_info_bone_mass,
        HeightRecord::class to R.string.perm_info_height,
        LeanBodyMassRecord::class to R.string.perm_info_lean_body_mass,
        WeightRecord::class to R.string.perm_info_weight,
        BloodGlucoseRecord::class to R.string.perm_info_blood_glucose,
        BloodPressureRecord::class to R.string.perm_info_blood_pressure,
        BodyTemperatureRecord::class to R.string.perm_info_body_temperature,
        HeartRateRecord::class to R.string.perm_info_heart_rate,
        HeartRateVariabilityRmssdRecord::class to R.string.perm_info_heart_rate_variability,
        OxygenSaturationRecord::class to R.string.perm_info_oxygen_saturation,
        RespiratoryRateRecord::class to R.string.perm_info_respiratory_rate,
        RestingHeartRateRecord::class to R.string.perm_info_resting_heart_rate,
        SkinTemperatureRecord::class to R.string.perm_info_skin_temperature,
        HydrationRecord::class to R.string.perm_info_hydration,
        NutritionRecord::class to R.string.perm_info_nutrition,
        SleepSessionRecord::class to R.string.perm_info_sleep,
        CervicalMucusRecord::class to R.string.perm_info_cervical_mucus,
        IntermenstrualBleedingRecord::class to R.string.perm_info_intermenstrual_bleeding,
        MenstruationFlowRecord::class to R.string.perm_info_menstruation,
        MenstruationPeriodRecord::class to R.string.perm_info_menstruation,
        OvulationTestRecord::class to R.string.perm_info_ovulation_test,
        BasalBodyTemperatureRecord::class to R.string.perm_info_basal_body_temperature,
        SexualActivityRecord::class to R.string.perm_info_sexual_activity,
    )

    /** The text for [spec]'s permission, or null for a type nobody has written one for yet. */
    @StringRes
    fun infoFor(spec: RecordTypeSpec<*>): Int? =
        byType[spec.type] ?: R.string.perm_info_mindfulness.takeIf { spec.permission == MINDFULNESS_PERMISSION }

    private const val MINDFULNESS_PERMISSION = "android.permission.health.READ_MINDFULNESS"
}
