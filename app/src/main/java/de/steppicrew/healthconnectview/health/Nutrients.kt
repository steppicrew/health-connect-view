package de.steppicrew.healthconnectview.health

import androidx.annotation.StringRes
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.units.Mass
import de.steppicrew.healthconnectview.R

/**
 * The nutrients beyond energy that Health Connect aggregates, in the order a food label lists
 * them. Grams for the bulk, milligrams for what is eaten in milligrams.
 */
enum class Nutrient(
    val metric: AggregateMetric<Mass>,
    @param:StringRes val labelRes: Int,
    val milligrams: Boolean = false,
    val ofRecord: (NutritionRecord) -> Mass?,
) {
    FAT(NutritionRecord.TOTAL_FAT_TOTAL, R.string.nutrient_fat, ofRecord = { it.totalFat }),
    SATURATED_FAT(NutritionRecord.SATURATED_FAT_TOTAL, R.string.nutrient_saturated_fat, ofRecord = { it.saturatedFat }),
    CARBOHYDRATE(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL, R.string.nutrient_carbohydrate, ofRecord = { it.totalCarbohydrate }),
    SUGAR(NutritionRecord.SUGAR_TOTAL, R.string.nutrient_sugar, ofRecord = { it.sugar }),
    FIBER(NutritionRecord.DIETARY_FIBER_TOTAL, R.string.nutrient_fiber, ofRecord = { it.dietaryFiber }),
    PROTEIN(NutritionRecord.PROTEIN_TOTAL, R.string.nutrient_protein, ofRecord = { it.protein }),
    SODIUM(NutritionRecord.SODIUM_TOTAL, R.string.nutrient_sodium, milligrams = true, ofRecord = { it.sodium }),
    CAFFEINE(NutritionRecord.CAFFEINE_TOTAL, R.string.nutrient_caffeine, milligrams = true, ofRecord = { it.caffeine }),
    ;

    /** A mass, from an aggregate or a record, in this nutrient's unit. */
    fun of(mass: Mass): Double = if (milligrams) mass.inMilligrams else mass.inGrams

    companion object {
        val metrics: Set<AggregateMetric<*>> = entries.map { it.metric }.toSet()
    }
}

/**
 * One nutrient over the window on screen: the total, and the mean over the [days] on which it
 * was recorded at all. Days without an entry for it are left out of the mean rather than
 * counted as none: a day nobody logged says nothing about what was eaten.
 */
data class NutrientTotal(val nutrient: Nutrient, val total: Double, val days: Int) {
    val perDay: Double get() = total / days
}

/**
 * The window's totals from its deduplicated daily buckets, each a map of the nutrients that
 * day holds (absent where nothing was recorded), in [Nutrient] order. Nutrients recorded on
 * no day are left out; an empty list means none beyond energy was recorded.
 */
fun nutrientTotals(days: List<Map<Nutrient, Double>>): List<NutrientTotal> =
    Nutrient.entries.mapNotNull { nutrient ->
        val values = days.mapNotNull { it[nutrient] }
        if (values.isEmpty()) null else NutrientTotal(nutrient, values.sum(), values.size)
    }
