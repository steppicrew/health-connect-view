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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * A ring filled to [progress] of its goal, with [content] in the middle.
 *
 * Drawn on a Canvas for the same reason as LineChart: it is a few lines of geometry, and a
 * charting dependency would be a much larger commitment than the shape warrants.
 *
 * Past the goal, each further hundred percent is a thin ring of its own around the main one,
 * starting again at twelve o'clock, so 107 % and 180 % no longer read alike as one full green
 * ring -- the owner's choice, 12.10.2026, over laps drawn on top of each other. Up to
 * [MAX_LAPS] are drawn; beyond, the outermost stays full and the printed share says the rest.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    // A thin ring's track a shade stronger: in the light theme the main one's all but
    // vanishes on the card, and a short arc then floats on its own.
    val lapTrackColor = MaterialTheme.colorScheme.outlineVariant
    // Green once the goal is met, so a finished day is legible at a glance without reading
    // the number. Fixed rather than themed, for the same reason the sleep band is: it encodes
    // a state -- done -- and under dynamic colour a themed hue drifts with the wallpaper until
    // "reached" and "not reached" are no longer distinguishable at a glance.
    val fillColor = if (progress >= 1f) GOAL_MET else MaterialTheme.colorScheme.primary
    // The thin rings only where a day went past its goal, so a ring short of it keeps its size.
    val laps = ceil(progress - 1f).toInt().coerceIn(0, MAX_LAPS)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val side = minOf(size.width, size.height)
            val thin = LAP_WIDTH.dp.toPx()
            val step = thin + LAP_GAP.dp.toPx()
            // Outermost first, each lap's centre line half its stroke inside the one before.
            for (lap in laps downTo 1) {
                val diameter = side - thin - 2f * step * (laps - lap)
                ring(lapTrackColor, 1f, diameter, thin)
                ring(GOAL_MET, (progress - lap).coerceIn(0f, 1f), diameter, thin)
            }
            val stroke = STROKE_WIDTH.dp.toPx()
            // Shrink by one stroke width so the ring's outer edge sits inside the bounds, or
            // inside the thin rings, rather than being clipped.
            val diameter = side - stroke - 2f * step * laps
            ring(trackColor, 1f, diameter, stroke)
            ring(fillColor, progress.coerceIn(0f, 1f), diameter, stroke)
        }
        content()
    }
}

/** A ring centred in the canvas, filled to [share] from twelve o'clock. */
private fun DrawScope.ring(color: Color, share: Float, diameter: Float, width: Float) {
    if (share <= 0f) return
    drawArc(
        color = color,
        // Start at twelve o'clock: a ring that begins at three reads as an arbitrary offset
        // rather than as progress.
        startAngle = START_ANGLE,
        sweepAngle = FULL_CIRCLE * share,
        useCenter = false,
        topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f),
        size = Size(diameter, diameter),
        style = Stroke(width = width, cap = StrokeCap.Round),
    )
}

/** Reads as "done" across both themes; deliberately not the theme's own accent. */
private val GOAL_MET = Color(0xFF16A34A)

private const val STROKE_WIDTH = 8f
private const val FULL_CIRCLE = 360f
private const val START_ANGLE = -90f

/** A further hundred percent's ring, the space between rings, and the most drawn. */
private const val LAP_WIDTH = 3
private const val LAP_GAP = 3
private const val MAX_LAPS = 3
