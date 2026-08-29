package de.steppicrew.healthconnectview.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.StrokeCap

/**
 * A ring filled to [progress] of its goal, with [content] in the middle.
 *
 * Drawn on a Canvas for the same reason as LineChart: it is a few lines of geometry, and a
 * charting dependency would be a much larger commitment than the shape warrants.
 *
 * Progress is clamped by the caller, so a day that beats its goal shows a full ring rather
 * than wrapping past the top and reading as a small remainder.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = STROKE_WIDTH.dp.toPx()
            // Shrink by one stroke width so the ring's outer edge sits inside the bounds
            // rather than being clipped.
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = FULL_CIRCLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (progress > 0f) {
                drawArc(
                    color = fillColor,
                    // Start at twelve o'clock: a ring that begins at three reads as an
                    // arbitrary offset rather than as progress.
                    startAngle = START_ANGLE,
                    sweepAngle = FULL_CIRCLE * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

private const val STROKE_WIDTH = 8f
private const val FULL_CIRCLE = 360f
private const val START_ANGLE = -90f
