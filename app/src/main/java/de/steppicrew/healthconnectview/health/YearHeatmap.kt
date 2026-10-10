package de.steppicrew.healthconnectview.health

import java.time.LocalDate
import kotlin.math.ceil

/**
 * A year of daily values as a calendar, each day in one of [STEPS] shades of one hue. A day
 * absent from [values] had nothing recorded and gets no shade at all -- never the lightest,
 * which would read as "little".
 */
data class YearHeatmap(
    val first: LocalDate,
    val last: LocalDate,
    val values: Map<LocalDate, Double>,
    /** The value the lightest step starts at: zero for a count, the year's lowest for a level. */
    val low: Double,
    /**
     * Where the darkest step starts: the 95th percentile, not the maximum, so one marathon
     * day does not leave every other day pale. Days above it share the darkest step.
     */
    val high: Double,
    /** The day's second value where a type has one: blood pressure's diastolic. */
    val secondValues: Map<LocalDate, Double> = emptyMap(),
    /** What [low] is where it is a floor rather than zero or the lowest day: the basal rate. */
    val lowLabel: Int? = null,
) {
    /** The step of [value], 0 (lightest) to [STEPS] - 1. */
    fun step(value: Double): Int {
        if (high <= low) return STEPS / 2
        return ((value - low) / (high - low) * STEPS).toInt().coerceIn(0, STEPS - 1)
    }

    companion object {
        const val STEPS = 5
    }
}

/**
 * The heatmap of [values] for [first] through [last], or null when no day has a value.
 * [fromZero] for a quantity that adds up -- steps, distance -- where a day's share of zero is
 * the question; a level such as resting heart rate or weight is shaded across its own range.
 */
fun yearHeatmap(
    values: Map<LocalDate, Double>,
    first: LocalDate,
    last: LocalDate,
    fromZero: Boolean,
    /**
     * Where a day's figure cannot go below a floor -- total calories never below the basal
     * rate -- the shading starts there; from zero every day of it looked the same.
     */
    floor: Double? = null,
): YearHeatmap? {
    val kept = values.filterKeys { it in first..last }
    if (kept.isEmpty()) return null
    val sorted = kept.values.sorted()
    val top = sorted[(ceil(sorted.size * TOP_SHARE).toInt() - 1).coerceIn(0, sorted.lastIndex)]
    val low = floor?.takeIf { it < top } ?: if (fromZero) 0.0 else sorted.first()
    return YearHeatmap(first, last, kept, low, top)
}

/** The share of days at or below the darkest step's start; see [YearHeatmap.high]. */
private const val TOP_SHARE = 0.95
