package de.steppicrew.healthconnectview.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.health.YearHeatmap
import de.steppicrew.healthconnectview.health.pressureCategory
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.registry.ValueZones
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToLong

/**
 * [heatmap] as a calendar: a column per week, the locale's first weekday at the top, month
 * names above the week each month starts in. A day with no colour is a faint empty square, so
 * "nothing recorded" never looks like "little". A tap picks a day ([onSelect]).
 */
@Composable
fun YearHeatmapGrid(
    heatmap: YearHeatmap,
    /** A day's colour; null for a day with nothing recorded, drawn as an empty square. */
    colorOf: (LocalDate) -> Color?,
    selected: LocalDate?,
    /** A tap on a day; null leaves taps to whatever holds the grid, as a tile does. */
    onSelect: ((LocalDate) -> Unit)?,
    description: String,
    modifier: Modifier = Modifier,
) {
    val firstDay = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val today = remember { LocalDate.now() }
    val start = remember(heatmap.first, firstDay) { heatmap.first.with(TemporalAdjusters.previousOrSame(firstDay)) }
    val weeks = (ChronoUnit.DAYS.between(start, heatmap.last) / DAYS_PER_WEEK + 1).toInt()
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val ring = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val months = remember { DateTimeFormatter.ofPattern("LLL", Locale.getDefault()) }
    val density = LocalDensity.current
    val gap = with(density) { GAP.dp.toPx() }
    val labelHeight = with(density) { LABEL_HEIGHT.dp.toPx() }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = with(density) { maxWidth.toPx() }
        val cell = (width - gap * (weeks - 1)) / weeks
        val height = labelHeight + cell * DAYS_PER_WEEK + gap * (DAYS_PER_WEEK - 1)
        fun dateAt(offset: Offset): LocalDate? {
            val column = (offset.x / (cell + gap)).toInt()
            val row = ((offset.y - labelHeight) / (cell + gap)).toInt()
            if (offset.y < labelHeight || column !in 0 until weeks || row !in 0 until DAYS_PER_WEEK) return null
            return start.plusDays(column * DAYS_PER_WEEK.toLong() + row).takeIf { it in heatmap.first..heatmap.last && !it.isAfter(today) }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(with(density) { height.toDp() })
                .semantics { contentDescription = description }
                .then(
                    if (onSelect == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(heatmap, cell) { detectTapGestures { offset -> dateAt(offset)?.let(onSelect) } }
                    },
                ),
        ) {
            var lastLabelEnd = Float.NEGATIVE_INFINITY
            for (column in 0 until weeks) {
                val x = column * (cell + gap)
                for (row in 0 until DAYS_PER_WEEK) {
                    val day = start.plusDays(column * DAYS_PER_WEEK.toLong() + row)
                    if (day !in heatmap.first..heatmap.last) continue
                    // The month's name over the week its first day falls in, where it fits.
                    if (day.dayOfMonth == 1 && x >= lastLabelEnd) {
                        val label = measurer.measure(day.format(months), labelStyle)
                        if (x + label.size.width <= size.width) {
                            drawText(label, topLeft = Offset(x, 0f))
                            lastLabelEnd = x + label.size.width + gap * 2
                        }
                    }
                    // Days to come are left out, not drawn as days with nothing recorded.
                    if (day.isAfter(today)) continue
                    val y = labelHeight + row * (cell + gap)
                    drawRoundRect(
                        color = colorOf(day) ?: empty,
                        topLeft = Offset(x, y),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * CORNER),
                    )
                    if (day == selected) {
                        drawRoundRect(ring, Offset(x, y), Size(cell, cell), CornerRadius(cell * CORNER), style = Stroke(gap * 1.5f))
                    }
                }
            }
        }
    }
}

/**
 * Each day's colour: its shade, or -- where a day has a second value, blood pressure's
 * diastolic -- the grade of the day's averages, as every reading of it is coloured elsewhere.
 */
@Composable
fun heatmapColors(heatmap: YearHeatmap): (LocalDate) -> Color? {
    val shades = heatmapShades()
    return { day ->
        heatmap.values[day]?.let { first ->
            val second = heatmap.secondValues[day]
            if (second != null) ValueZones.ZONE_COLORS[pressureCategory(first, second).ordinal] else shades[heatmap.step(first)]
        }
    }
}

/**
 * The five shades, lightest first: one hue at rising strength over the surface, so the order
 * reads in light and dark alike and in greyscale.
 */
@Composable
fun heatmapShades(): List<Color> {
    val hue = MaterialTheme.colorScheme.primary
    return SHADE_ALPHAS.map { hue.copy(alpha = it) }
}

/**
 * A calendar's figure as [spec] writes it: sleep and training are hours, read as a duration;
 * everything else in its own unit.
 */
@Composable
fun heatmapValue(spec: RecordTypeSpec<*>): (Double) -> String {
    val unit = spec.displayUnitRes?.let { " " + stringResource(it) }.orEmpty()
    return if (spec.tile.form == TileSpec.Form.SESSIONS) {
        // "0", not "0s": the legend's start, where seconds were never meant.
        { v -> if (v == 0.0) "0" else Formatting.duration(Duration.ofMinutes((v * MINUTES_PER_HOUR).roundToLong())) }
    } else {
        { v -> Formatting.number(v, spec.valueDecimals) + unit }
    }
}

/**
 * The five shades from the lightest's start to the darkest's, in [spec]'s unit. A floor is
 * named: "1.790 kcal" alone would read as the year's lowest day.
 */
@Composable
fun HeatmapLegend(heatmap: YearHeatmap, spec: RecordTypeSpec<*>, modifier: Modifier = Modifier) {
    val value = heatmapValue(spec)
    val low = value(heatmap.low) + heatmap.lowLabel?.let { " (" + stringResource(it) + ")" }.orEmpty()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(low, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        heatmapShades().forEach { shade ->
            Box(
                Modifier
                    .padding(start = 3.dp)
                    .size(10.dp)
                    .background(shade, RoundedCornerShape(2.dp)),
            )
        }
        Text(
            "≥ " + value(heatmap.high),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private val SHADE_ALPHAS = listOf(0.2f, 0.4f, 0.6f, 0.8f, 1f)
private const val DAYS_PER_WEEK = 7
private const val GAP = 1.5f
private const val LABEL_HEIGHT = 14
private const val CORNER = 0.2f
private const val MINUTES_PER_HOUR = 60.0
