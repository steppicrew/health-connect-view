package de.steppicrew.healthconnectview.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Point

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
) {
    if (points.isEmpty()) return

    val values = points.map { it.value }
    val minValue = values.min()
    val maxValue = values.max()
    // A flat series would divide by zero; give it a nominal span so it draws as a centre line.
    val span = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth()) {
        // Max sits at the top of the plot and min at the bottom, matching where the line
        // actually reaches; putting them side by side would read as start/end instead.
        Text(
            text = Formatting.number(maxValue),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT.dp)
                .padding(vertical = 8.dp),
        ) {
            val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width

            fun yFor(value: Double): Float =
                (size.height * (1.0 - (value - minValue) / span)).toFloat()

            // Horizontal guides at min, middle and max.
            listOf(minValue, (minValue + maxValue) / 2.0, maxValue).forEach { guide ->
                val y = yFor(guide)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            val offsets = points.mapIndexed { index, point ->
                Offset(index * stepX, yFor(point.value))
            }
            val path = if (smooth && offsets.size > 2) {
                smoothPath(offsets)
            } else {
                Path().apply {
                    points.forEachIndexed { index, point ->
                        val x = index * stepX
                        val y = yFor(point.value)
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
            }
            drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx()))

            // Mark individual readings when there are few enough for dots to stay legible.
            if (points.size <= MAX_DOTS) {
                points.forEachIndexed { index, point ->
                    drawCircle(
                        color = lineColor,
                        radius = 3.dp.toPx(),
                        center = Offset(index * stepX, yFor(point.value)),
                    )
                }
            }
        }

        Text(
            text = Formatting.number(minValue),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Formatting.date(points.first().time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Formatting.date(points.last().time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val CHART_HEIGHT = 200
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

        val control1 = Offset(
            x = current.x + (next.x - previous.x) / CATMULL_ROM_TENSION,
            y = (current.y + (next.y - previous.y) / CATMULL_ROM_TENSION).coerceIn(lowY, highY),
        )
        val control2 = Offset(
            x = next.x - (following.x - current.x) / CATMULL_ROM_TENSION,
            y = (next.y - (following.y - current.y) / CATMULL_ROM_TENSION).coerceIn(lowY, highY),
        )

        cubicTo(control1.x, control1.y, control2.x, control2.y, next.x, next.y)
    }
}

/** Standard Catmull-Rom conversion factor; larger values give a tighter curve. */
private const val CATMULL_ROM_TENSION = 6f

private const val MAX_DOTS = 60
