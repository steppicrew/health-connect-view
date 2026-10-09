package de.steppicrew.healthconnectview.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.profileOf
import de.steppicrew.healthconnectview.registry.Point

/**
 * A reading that can be drawn over a night, the same wherever it is: on the night's own screen
 * as a chip, and on a large sleep tile as the curve beside the stages. Chosen from what the phone
 * holds -- every recent night had all four (`NightShapeActivity`).
 */
enum class NightLine(
    val typeName: String,
    /** Keys the remembered choice of chips by; see ChartLinesStore. */
    val key: String,
    /** A chip's name where the type's own runs to "Herzfrequenzvariabilität" and wraps the row. */
    @param:StringRes val shortLabel: Int? = null,
    /** HRV comes every five minutes: dots joined by faint dashes, never a solid line. */
    val dots: Boolean = false,
    /** A floor on its own scale, so a normal night does not fill the height. */
    val minSpan: Double? = null,
    /** A reading a minute zigzags across a night; slice medians keep its shape. */
    val smoothed: Boolean = false,
) {
    HEART_RATE("HeartRateRecord", "heart_rate", minSpan = 20.0),
    BREATH("RespiratoryRateRecord", "breath", minSpan = 8.0, smoothed = true),
    OXYGEN("OxygenSaturationRecord", "oxygen", shortLabel = R.string.chart_short_oxygen, minSpan = 10.0, smoothed = true),
    HRV("HeartRateVariabilityRmssdRecord", "hrv", shortLabel = R.string.chart_short_hrv, dots = true),
    ;

    /** The colour this reading wears on every chart; see [SeriesColors]. */
    @Composable
    fun color(): Color = when (this) {
        HEART_RATE -> SeriesColors.orange()
        BREATH -> SeriesColors.aqua()
        OXYGEN -> SeriesColors.blue()
        HRV -> SeriesColors.violet()
    }

    /** The readings as drawn: sliced where [smoothed], as recorded otherwise. */
    fun shown(points: List<Point>): List<Point> = if (smoothed) profileOf(points) else points

    companion object {
        fun of(typeName: String?): NightLine? = entries.firstOrNull { it.typeName == typeName }
    }
}
