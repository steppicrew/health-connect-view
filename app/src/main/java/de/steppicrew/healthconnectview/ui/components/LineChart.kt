package de.steppicrew.healthconnectview.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.Layout
import java.time.Duration
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Point
import java.time.Instant

/**
 * The app's only chart.
 *
 * Hand-drawn on a Compose Canvas rather than pulled from a charting library: the shape here
 * is a single series of timestamped values, which is a few lines of geometry, and this keeps
 * the app free of a dependency whose API churns between majors. Everything renders through
 * this one signature, so swapping the implementation stays a single-file change.
 */
@Composable
fun LineChart(
    points: List<Point>,
    modifier: Modifier = Modifier,
    smooth: Boolean = false,
    /** Drawn as a dashed reference line, and kept within the vertical range so it is visible. */
    goal: Double? = null,
    /** Marked with a dot where the series first reaches [goal]. */
    goalCrossing: Instant? = null,
    /** Unit shown beside a touched point's value; omitted when the type has none. */
    @StringRes unitRes: Int? = null,
) {
    if (points.isEmpty()) return

    val values = points.map { it.value }
    // The goal takes part in the scale: a goal above the day's total must stay on the chart,
    // or "not reached yet" looks identical to "reached", which is the whole point of drawing
    // it. A goal already beaten simply sits below the peak.
    val minValue = minOf(values.min(), goal ?: values.min())
    val maxValue = maxOf(values.max(), goal ?: values.max())
    // A flat series would divide by zero; give it a nominal span so it draws as a centre line.
    val span = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()

    var selected by remember(points) { mutableStateOf<Int?>(null) }

    // Each point's horizontal position as a fraction of the width. Computed once here so the
    // touch handler and the drawing agree exactly on where a point sits.
    val fractions = remember(points) { horizontalFractions(points) }

    fun nearestIndex(x: Float, width: Int): Int? {
        if (fractions.isEmpty() || width <= 0) return null
        val target = (x / width).coerceIn(0f, 1f)
        return fractions.indices.minByOrNull { kotlin.math.abs(fractions[it] - target) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // The readout occupies a fixed row whether or not anything is selected, so touching
        // the chart does not shift the layout under the finger.
        SelectionReadout(
            point = selected?.let(points::getOrNull),
            unitRes = unitRes,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT.dp)
                .padding(vertical = 8.dp)
                .pointerInput(points) {
                    // Drag as well as tap: reading a series means sweeping along it, and
                    // lifting clears so the chart does not keep a stale highlight.
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset -> selected = nearestIndex(offset.x, size.width) },
                        onDrag = { change, _ ->
                            selected = nearestIndex(change.position.x, size.width)
                        },
                        onDragEnd = { selected = null },
                        onDragCancel = { selected = null },
                    )
                }
                .pointerInput(points) {
                    detectTapGestures(
                        onPress = { offset ->
                            selected = nearestIndex(offset.x, size.width)
                            // Held highlight while the finger is down, cleared on release.
                            tryAwaitRelease()
                            selected = null
                        },
                    )
                },
        ) {
            val firstTime = points.first().time.toEpochMilli()
            val lastTime = points.last().time.toEpochMilli()
            val timeSpan = (lastTime - firstTime).takeIf { it > 0L }

            fun xFor(index: Int): Float = fractions[index] * size.width

            fun yFor(value: Double): Float =
                (size.height * (1.0 - (value - minValue) / span)).toFloat()

            val offsets = points.mapIndexed { index, point ->
                Offset(xFor(index), yFor(point.value))
            }
            goal?.let { target ->
                val y = yFor(target)
                drawLine(
                    color = goalColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(GOAL_DASH_ON.dp.toPx(), GOAL_DASH_OFF.dp.toPx()),
                    ),
                )
            }

            // Guides labelled at their own line, so a value can be read off the chart
            // rather than inferred from the endpoints. Four intervals gives five labels,
            // which stays legible at the height this chart is drawn.
            val guides = (0..GUIDE_INTERVALS).map { step ->
                minValue + (maxValue - minValue) * step / GUIDE_INTERVALS
            }
            guides.forEach { guide ->
                val y = yFor(guide)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )

                val label = textMeasurer.measure(Formatting.number(guide), labelStyle)
                // Nudged inside the plot at the extremes so the top and bottom labels are not
                // half-clipped by the canvas edge.
                val labelY = (y - label.size.height / 2f)
                    .coerceIn(0f, size.height - label.size.height)
                // The label sits on top of the grid and goal lines, so it is backed out to
                // stay readable where they cross it.
                drawRect(
                    color = surfaceColor,
                    topLeft = Offset(0f, labelY),
                    size = androidx.compose.ui.geometry.Size(
                        width = label.size.width.toFloat() + LABEL_PAD.dp.toPx(),
                        height = label.size.height.toFloat(),
                    ),
                )
                drawText(
                    textLayoutResult = label,
                    color = labelColor,
                    topLeft = Offset(0f, labelY),
                )
            }

            // Placed by time like every other point, so the marker sits exactly where the
            // line meets the goal rather than at the nearest sample.
            val crossingX = goalCrossing?.let { crossing ->
                if (timeSpan == null) null
                else {
                    val offsetMillis = crossing.toEpochMilli() - firstTime
                    (size.width * offsetMillis.toDouble() / timeSpan.toDouble()).toFloat()
                }
            }

            val path = if (smooth && offsets.size > 2) {
                smoothPath(offsets)
            } else {
                Path().apply {
                    points.forEachIndexed { index, point ->
                        val x = xFor(index)
                        val y = yFor(point.value)
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
            }
            drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx()))

            // Drawn after the line so the marker is not overdrawn by it.
            if (crossingX != null && goal != null) {
                val y = yFor(goal)
                drawCircle(
                    color = goalColor,
                    radius = GOAL_MARKER_RADIUS.dp.toPx(),
                    center = Offset(crossingX, y),
                )
                // A ring of background colour separates the marker from the line beneath it,
                // which shares its position by definition.
                drawCircle(
                    color = surfaceColor,
                    radius = (GOAL_MARKER_RADIUS - GOAL_MARKER_RING).dp.toPx(),
                    center = Offset(crossingX, y),
                )
            }

            // The selected point: a full-height rule plus a marker, so the position is
            // readable even where the line is flat and a dot alone would be ambiguous.
            selected?.let { index ->
                val x = xFor(index)
                val y = yFor(points[index].value)
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(color = surfaceColor, radius = 7.dp.toPx(), center = Offset(x, y))
                drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(x, y))
            }

            // Mark individual readings when there are few enough for dots to stay legible.
            if (points.size <= MAX_DOTS) {
                points.forEachIndexed { index, point ->
                    drawCircle(
                        color = lineColor,
                        radius = 3.dp.toPx(),
                        center = Offset(xFor(index), yFor(point.value)),
                    )
                }
            }
        }

        TimeAxis(points = points, fractions = fractions)
    }
}

private const val CHART_HEIGHT = 200
/**
 * Tick labels along the time axis.
 *
 * Positioned by the same fractions the plot uses, so a label sits under the moment it names
 * rather than being spread evenly and implying a regularity the data does not have.
 *
 * The format follows the span: clock times read naturally within a day, dates across weeks,
 * and a date on an intraday chart would repeat itself at every tick.
 */
@Composable
private fun TimeAxis(points: List<Point>, fractions: List<Float>) {
    val first = points.first().time
    val last = points.last().time
    val spanHours = Duration.between(first, last).toHours()
    val intraday = spanHours in 1..HOURS_IN_DAY

    val ticks = remember(points) { axisTicks(points, fractions) }

    Layout(
        content = {
            ticks.forEach { tick ->
                Text(
                    text = if (intraday) {
                        Formatting.time(tick.time)
                    } else {
                        Formatting.dayAndMonth(tick.time)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0

        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                // Centred on the tick, then held inside the chart so the first and last
                // labels are not half off the edge.
                val centre = ticks[index].fraction * constraints.maxWidth
                val x = (centre - placeable.width / 2f).toInt()
                    .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

/** One tick: where it sits across the width, and the moment it names. */
private data class AxisTick(val fraction: Float, val time: Instant)

/**
 * Evenly spaced ticks across the elapsed time, each snapped to the nearest real point.
 *
 * Snapping matters: a label reading a time no sample was taken at invites the reader to
 * believe the series was measured there. Duplicate snaps are dropped, so a sparse series shows
 * fewer labels rather than the same one repeated.
 */
private fun axisTicks(points: List<Point>, fractions: List<Float>): List<AxisTick> {
    if (points.size < 2 || fractions.size != points.size) return emptyList()

    return (0..AXIS_TICKS).map { step ->
        val target = step / AXIS_TICKS.toFloat()
        val index = fractions.indices.minByOrNull { kotlin.math.abs(fractions[it] - target) }
            ?: 0
        AxisTick(fraction = fractions[index], time = points[index].time)
    }.distinctBy { it.time }
}

/**
 * Each point's horizontal position as a fraction of the plot width, from its timestamp.
 *
 * Shared by the drawing and the touch handler so both agree on where a point is: computing it
 * twice invites them to drift, and a highlight that lands beside the line it names is worse
 * than no highlight.
 */
private fun horizontalFractions(points: List<Point>): List<Float> {
    if (points.isEmpty()) return emptyList()
    val first = points.first().time.toEpochMilli()
    val span = points.last().time.toEpochMilli() - first

    // A series with no elapsed time (one point, or several sharing an instant) has no
    // meaningful time axis, so fall back to even spacing.
    if (span <= 0L) {
        if (points.size == 1) return listOf(0.5f)
        return points.indices.map { it / (points.size - 1).toFloat() }
    }
    return points.map { (it.time.toEpochMilli() - first).toDouble().div(span).toFloat() }
}

/**
 * The touched point's value and time, in a row that is always present.
 *
 * Reserving the space keeps the chart from jumping when a touch begins, which on a chart is
 * disorienting: the thing being pointed at moves out from under the finger.
 */
@Composable
private fun SelectionReadout(point: Point?, @StringRes unitRes: Int?) {
    val unit = unitRes?.let { stringResource(it) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = point?.let { selected ->
                Formatting.number(selected.value) + (unit?.let { " $it" } ?: "")
            }.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = point?.let { Formatting.dateTime(it.time) }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A cubic curve through every point, for series where the underlying quantity varies
 * continuously rather than in steps.
 *
 * Control points are placed from each neighbour pair (a Catmull-Rom spline converted to
 * Bezier), then clamped so a segment can never leave the range of the two values it joins.
 * Without that clamp an overshoot invents readings that were never recorded -- dipping below
 * zero between two step counts, for instance -- which for health data is not a cosmetic
 * problem but a false statement.
 */
private fun smoothPath(offsets: List<Offset>): Path = Path().apply {
    moveTo(offsets.first().x, offsets.first().y)

    offsets.zipWithNext().forEachIndexed { index, (current, next) ->
        val previous = offsets.getOrElse(index - 1) { current }
        val following = offsets.getOrElse(index + 2) { next }

        val lowY = minOf(current.y, next.y)
        val highY = maxOf(current.y, next.y)

        // Control points stay inside the segment horizontally as well as vertically. With
        // points placed by timestamp the neighbours can be far apart in x, and an unclamped
        // control point would reach past the segment and double the line back on itself.
        val lowX = minOf(current.x, next.x)
        val highX = maxOf(current.x, next.x)

        val control1 = Offset(
            x = (current.x + (next.x - previous.x) / CATMULL_ROM_TENSION).coerceIn(lowX, highX),
            y = (current.y + (next.y - previous.y) / CATMULL_ROM_TENSION).coerceIn(lowY, highY),
        )
        val control2 = Offset(
            x = (next.x - (following.x - current.x) / CATMULL_ROM_TENSION).coerceIn(lowX, highX),
            y = (next.y - (following.y - current.y) / CATMULL_ROM_TENSION).coerceIn(lowY, highY),
        )

        cubicTo(control1.x, control1.y, control2.x, control2.y, next.x, next.y)
    }
}

/** Standard Catmull-Rom conversion factor; larger values give a tighter curve. */
private const val CATMULL_ROM_TENSION = 6f

private const val GOAL_DASH_ON = 6f
private const val GOAL_DASH_OFF = 4f

private const val GOAL_MARKER_RADIUS = 6f
private const val GOAL_MARKER_RING = 2.5f

/** Four intervals gives five labelled gridlines, legible at this chart's height. */
private const val GUIDE_INTERVALS = 4

/** Breathing room right of a gridline label, so the line does not touch the glyphs. */
private const val LABEL_PAD = 4f

/** Four intervals gives five ticks, which fit without crowding at phone width. */
private const val AXIS_TICKS = 4

private const val HOURS_IN_DAY = 24L

private const val MAX_DOTS = 60
