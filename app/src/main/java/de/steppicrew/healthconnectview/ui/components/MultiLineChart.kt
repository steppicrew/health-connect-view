package de.steppicrew.healthconnectview.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.dashboard.ChartLinesStore
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.ValueZones
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * One line a [MultiLineChart] can show. Lines with the same [unitKey] share a scale; any other
 * is drawn on its own and read by touch.
 */
data class ChartSeries(
    /** Stable across screens, since the choice of lines is remembered by it. */
    val key: String,
    val label: String,
    val points: List<Point>,
    val color: Color,
    val unitKey: String,
    @param:StringRes val unitRes: Int? = null,
    val unitText: String? = null,
    val valueDecimals: Int? = null,
    val integral: Boolean = false,
    val minSpan: Double? = null,
    /** Value bands, drawn only while this line is shown alone; with others, its colour names it. */
    val zones: ValueZones? = null,
    /** Readings further apart than this are not joined. */
    val maxGap: Duration? = null,
)

/**
 * Several lines over one time axis, switched on and off by a chip each -- the owner's request,
 * 09.10.2026: heart rate with breath rate through a night, speed with heart rate on a ride.
 *
 * The chip last switched on owns the axis and is drawn on top; the others are fitted to their
 * own range unless they share its unit, and every value is read off the readout by touch. Two
 * labelled scales on one chart invite comparing heights that mean nothing across units, so
 * only one is ever labelled. At most [MAX_LINES] at once: three colours stay apart for every
 * kind of colour vision, six do not (the palette validator's all-pairs check).
 *
 * Which lines are shown is remembered per [chartId] ([ChartLinesStore]).
 */
@Composable
fun MultiLineChart(
    chartId: String,
    series: List<ChartSeries>,
    /** Shown where nothing was chosen yet, axis owner first. */
    defaultShown: List<String>,
    modifier: Modifier = Modifier,
    extent: ClosedRange<Instant>? = null,
    breaks: List<ClosedRange<Instant>> = emptyList(),
    fillHeight: Boolean = false,
    onExpand: (() -> Unit)? = null,
    holdSelection: Boolean = false,
) {
    val available = series.filter { it.points.size > 1 }
    if (available.isEmpty()) return
    val keys = available.map { it.key }

    val store = ChartLinesStore(LocalContext.current)
    val scope = rememberCoroutineScope()
    var shown by rememberSaveable(chartId) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(chartId) {
        if (shown == null) shown = runCatching { store.shown(chartId).first() }.getOrNull() ?: defaultShown
    }
    // Only lines this chart has: a choice saved where speed was recorded must not leave a
    // walk without one blank.
    val visible = (shown ?: defaultShown).filter { it in keys }.ifEmpty { listOf(keys.first()) }

    fun choose(lines: List<String>) {
        shown = lines
        scope.launch { store.save(chartId, lines) }
    }

    val primary = available.first { it.key == visible.first() }
    val others = visible.drop(1).mapNotNull { key -> available.firstOrNull { it.key == key } }
    val alone = others.isEmpty()
    // Resolved here: the readout's formatter runs outside composition.
    val units = others.map { line -> line.unitText ?: line.unitRes?.let { stringResource(it) } }

    Column(modifier = modifier.fillMaxWidth()) {
        Box((if (fillHeight) Modifier.weight(1f) else Modifier).fillMaxWidth()) {
            LineChart(
                points = primary.points,
                smooth = false,
                unitRes = primary.unitRes,
                unitText = primary.unitText,
                valueDecimals = primary.valueDecimals,
                zones = if (alone) primary.zones else null,
                lineColorOverride = if (alone) null else primary.color,
                markReadings = false,
                integral = primary.integral,
                minSpan = primary.minSpan,
                extent = extent,
                breaks = breaks,
                fillHeight = fillHeight,
                onExpand = onExpand,
                holdSelection = holdSelection,
                overlays = others.mapIndexed { index, line ->
                    OverlayLine(
                        points = line.points,
                        color = line.color,
                        format = { value ->
                            Formatting.number(value, line.valueDecimals) + (units[index]?.let { " $it" } ?: "")
                        },
                        sharesScale = line.unitKey == primary.unitKey,
                        maxGap = line.maxGap,
                    )
                },
            )
        }

        // Single-line chips: the icon rule for wrapping text does not apply.
        FlowRow(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            available.forEach { line ->
                val on = line.key in visible
                val owner = line.key == primary.key
                FilterChip(
                    selected = on,
                    onClick = {
                        choose(
                            when {
                                // Switched on: it takes the axis; the oldest beyond the limit goes.
                                !on -> (listOf(line.key) + visible).take(MAX_LINES)
                                // The last one stays: a chart with no line is no chart.
                                visible.size == 1 -> visible
                                else -> visible - line.key
                            },
                        )
                    },
                    label = { Text(line.label) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(SWATCH.dp)
                                .background(if (on) line.color else line.color.copy(alpha = OFF_ALPHA), CircleShape),
                        )
                    },
                    trailingIcon = if (owner && !alone) {
                        {
                            Icon(
                                imageVector = Icons.Default.Height,
                                contentDescription = stringResource(R.string.chart_line_axis),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * The colours a [MultiLineChart] draws in, by what the line is rather than by its position, so
 * heart rate is the same colour on every chart. The three slots validated together for every
 * kind of colour vision, in light and in dark (dataviz palette, all-pairs check): blue, orange
 * and aqua. A line beyond those three would need a fourth that no pairing clears.
 */
object SeriesColors {
    @Composable
    fun blue(): Color = pick(Color(0xFF2A78D6), Color(0xFF3987E5))

    @Composable
    fun orange(): Color = pick(Color(0xFFEB6834), Color(0xFFD95926))

    @Composable
    fun aqua(): Color = pick(Color(0xFF1BAF7A), Color(0xFF199E70))

    @Composable
    private fun pick(light: Color, dark: Color): Color =
        if (MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE) dark else light

    private const val DARK_SURFACE = 0.5f
}

private const val MAX_LINES = 3
private const val SWATCH = 10
private const val OFF_ALPHA = 0.35f
