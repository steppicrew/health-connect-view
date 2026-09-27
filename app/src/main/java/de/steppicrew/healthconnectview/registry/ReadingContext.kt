package de.steppicrew.healthconnectview.registry

import androidx.annotation.StringRes
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.Record
import de.steppicrew.healthconnectview.R

/**
 * One piece of context a writer stored with a reading: blood glucose's relation to a meal,
 * the meal and the specimen, blood pressure's body position and where it was measured.
 *
 * None of it was shown anywhere but the glucose report, yet it decides how a reading is read:
 * 7.8 mmol/L is high fasting and ordinary after a meal, and a pressure taken standing or at the
 * wrist is not comparable with one taken seated at the upper arm.
 */
data class ContextItem(
    /** What the item is, "Körperhaltung". */
    @param:StringRes val labelRes: Int,
    /** Its value, "Sitzend". */
    @param:StringRes val valueRes: Int,
    /** Stable English words for the CSV, the same in every language. */
    val csvKey: String,
    val csvValue: String,
)

/**
 * The context [record] carries, in a fixed order; empty for a type with none. A value the
 * writer left unknown is left out rather than shown as "unknown": most writers never set these,
 * and a row of "Keine Angabe" on every reading would only be noise.
 */
fun readingContext(record: Record): List<ContextItem> = when (record) {
    is BloodGlucoseRecord -> listOfNotNull(
        RELATIONS[record.relationToMeal]?.let { (value, csv) -> ContextItem(R.string.report_col_relation, value, "relation_to_meal", csv) },
        MEALS[record.mealType]?.let { (value, csv) -> ContextItem(R.string.report_col_meal, value, "meal", csv) },
        SPECIMENS[record.specimenSource]?.let { (value, csv) -> ContextItem(R.string.context_specimen, value, "specimen", csv) },
    )
    is BloodPressureRecord -> listOfNotNull(
        POSITIONS[record.bodyPosition]?.let { (value, csv) -> ContextItem(R.string.context_position, value, "body_position", csv) },
        LOCATIONS[record.measurementLocation]?.let { (value, csv) -> ContextItem(R.string.context_location, value, "location", csv) },
    )
    else -> emptyList()
}

/** The CSV column: "relation_to_meal=fasting;meal=breakfast", empty for none. */
fun List<ContextItem>.csv(): String = joinToString(";") { "${it.csvKey}=${it.csvValue}" }

/** How often each value of one item occurred over a report's readings, most frequent first. */
data class ContextTally(@param:StringRes val labelRes: Int, val counts: List<Pair<Int, Int>>)

/**
 * The context of many readings counted per item, in the order the items first appear: what a
 * report states once instead of on every row -- "Körperhaltung: Sitzend 40 · Stehend 2" says
 * whether the readings were taken alike, which is what a reader needs to trust a comparison.
 */
fun tally(readings: List<List<ContextItem>>): List<ContextTally> =
    readings.flatten()
        .groupBy { it.labelRes }
        .map { (label, items) ->
            ContextTally(label, items.groupingBy { it.valueRes }.eachCount().toList().sortedByDescending { it.second })
        }

/** The label of a relation code, including unknown, for the report's per-reading column. */
@StringRes
fun relationLabel(code: Int): Int = RELATIONS[code]?.first ?: R.string.glucose_relation_unknown

/** The label of a meal code, or null for none. */
@StringRes
fun mealLabel(code: Int): Int? = MEALS[code]?.first

private val RELATIONS: Map<Int, Pair<Int, String>> = mapOf(
    BloodGlucoseRecord.RELATION_TO_MEAL_FASTING to (R.string.glucose_relation_fasting to "fasting"),
    BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL to (R.string.glucose_relation_before to "before_meal"),
    BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL to (R.string.glucose_relation_after to "after_meal"),
    BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL to (R.string.glucose_relation_general to "general"),
)

private val MEALS: Map<Int, Pair<Int, String>> = mapOf(
    MealType.MEAL_TYPE_BREAKFAST to (R.string.meal_breakfast to "breakfast"),
    MealType.MEAL_TYPE_LUNCH to (R.string.meal_lunch to "lunch"),
    MealType.MEAL_TYPE_DINNER to (R.string.meal_dinner to "dinner"),
    MealType.MEAL_TYPE_SNACK to (R.string.meal_snack to "snack"),
)

private val SPECIMENS: Map<Int, Pair<Int, String>> = mapOf(
    BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID to (R.string.specimen_interstitial to "interstitial_fluid"),
    BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD to (R.string.specimen_capillary to "capillary_blood"),
    BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA to (R.string.specimen_plasma to "plasma"),
    BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM to (R.string.specimen_serum to "serum"),
    BloodGlucoseRecord.SPECIMEN_SOURCE_TEARS to (R.string.specimen_tears to "tears"),
    BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD to (R.string.specimen_whole_blood to "whole_blood"),
)

private val POSITIONS: Map<Int, Pair<Int, String>> = mapOf(
    BloodPressureRecord.BODY_POSITION_SITTING_DOWN to (R.string.position_sitting to "sitting"),
    BloodPressureRecord.BODY_POSITION_STANDING_UP to (R.string.position_standing to "standing"),
    BloodPressureRecord.BODY_POSITION_LYING_DOWN to (R.string.position_lying to "lying"),
    BloodPressureRecord.BODY_POSITION_RECLINING to (R.string.position_reclining to "reclining"),
)

private val LOCATIONS: Map<Int, Pair<Int, String>> = mapOf(
    BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM to (R.string.location_left_upper_arm to "left_upper_arm"),
    BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_UPPER_ARM to (R.string.location_right_upper_arm to "right_upper_arm"),
    BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_WRIST to (R.string.location_left_wrist to "left_wrist"),
    BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_WRIST to (R.string.location_right_wrist to "right_wrist"),
)
