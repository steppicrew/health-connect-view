package de.steppicrew.healthconnectview.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.ValueZones

/**
 * A compact curve of recent readings, coloured by value.
 *
 * The colours come from fixed value bands, never the window's own min and max. A
 * window-relative scale would paint every day in the full sweep, making a calm day look
 * identical to an alarming one and making two days incomparable.
 *
 * Each segment is drawn separately so the colour follows the value along the line; a single
 * path could only take one colour.
 */
@Composable
fun SparkCurve(
    points: List<Point>,
    zones: ValueZones,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    val values = points.map { it.value }
    // The vertical axis uses the data's own extent so the shape is visible, while colour
    // uses the fixed zones. Those are deliberately different questions: "how did it move"
    // and "was it high".
    val low = values.min()
    val high = values.max()
    val span = (high - low).takeIf { it > 0.0 } ?: 1.0

    Canvas(modifier = modifier) {
        val stepX = size.width / (points.size - 1)
        val stroke = STROKE_WIDTH.dp.toPx()

        points.zipWithNext().forEachIndexed { index, (from, to) ->
            val startOffset = Offset(
                x = index * stepX,
                y = size.height - ((from.value - low) / span).toFloat() * size.height,
            )
            val endOffset = Offset(
                x = (index + 1) * stepX,
                y = size.height - ((to.value - low) / span).toFloat() * size.height,
            )
            // Gradient per span, so the colour follows the value rather than the direction
            // the line happens to be travelling in.
            val fromColor = zones.colorFor(from.value)
            val toColor = zones.colorFor(to.value)
            drawLine(
                brush = if (fromColor == toColor) {
                    SolidColor(fromColor)
                } else {
                    Brush.linearGradient(listOf(fromColor, toColor), startOffset, endOffset)
                },
                start = startOffset,
                end = endOffset,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val STROKE_WIDTH = 2.5f
