package de.steppicrew.healthconnectview.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.registry.Point

/**
 * A compact curve of recent readings, coloured by value.
 *
 * The colour scale is a fixed value range supplied by the type's TileSpec, never the window's
 * own min and max. A window-relative scale would paint every day in the full blue-to-red
 * sweep, making a calm day look identical to an alarming one and making two days
 * incomparable.
 *
 * Each segment is drawn separately so the colour follows the value along the line; a single
 * path could only take one colour.
 */
@Composable
fun SparkCurve(
    points: List<Point>,
    scale: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    val values = points.map { it.value }
    // The vertical axis uses the data's own extent so the shape is visible, while colour uses
    // the fixed clinical scale. Those are deliberately different questions: "how did it move"
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
            drawLine(
                color = colorForValue(to.value, scale),
                start = startOffset,
                end = endOffset,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Blue at or below the bottom of the scale, red at or above the top.
 *
 * Values outside the scale clamp rather than wrapping, so an extreme reading stays at the end
 * of the colour range instead of cycling back to looking calm.
 *
 * Shared with the full-size chart, so a reading is the same colour on the tile and on the
 * detail screen it opens. Two colours for the same number would read as two different
 * measurements.
 */
fun colorForValue(value: Double, scale: ClosedFloatingPointRange<Double>): Color {
    val span = (scale.endInclusive - scale.start).takeIf { it > 0.0 } ?: return COOL
    val fraction = ((value - scale.start) / span).toFloat().coerceIn(0f, 1f)
    return lerp(COOL, WARM, fraction)
}

// Fixed, not theme colours: these encode a value, so they must not shift with the wallpaper
// the way a dynamic palette does.
private val COOL = Color(0xFF3B82F6)
private val WARM = Color(0xFFEF4444)

private const val STROKE_WIDTH = 2.5f
