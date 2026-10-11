package de.steppicrew.healthconnectview.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.health.TrendResult

/**
 * Where the last seven days sit against the 30 days: the 30-day mean as a mark in the middle,
 * the band either side of it where a week still counts as level, and a dot for the week. Measured
 * in the type's own day-to-day spread, as the list is ranked, so a dot near the edge is an
 * unusual week for that type whatever its unit. A week beyond the strip sits on its edge.
 *
 * The level band is green and the strip beyond it runs from yellow to red towards the edges, the
 * owner's request, 11.10.2026; the dot takes the colour of where it sits, ringed in the surface so
 * it stands out on a track of its own colour. Red means unusual, not bad: more steps is a red dot
 * too. The fixed status steps of the dataviz palette, the same in light and dark, and never the
 * only cue -- the dot's place says it, and the text beside it says it in words, so a screen
 * reader skips the strip.
 */
@Composable
internal fun InsightStrip(trend: TrendResult, modifier: Modifier = Modifier) {
    val mark = MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.surface
    val position = position(trend)
    val dot = colourAt(position)
    Canvas(modifier.fillMaxWidth().height(STRIP_HEIGHT.dp).clearAndSetSemantics { }) {
        val radius = DOT_RADIUS.dp.toPx()
        val ringWidth = RING_WIDTH.dp.toPx()
        // Inset by the dot and its ring, so one on the edge is drawn whole.
        val left = radius + ringWidth
        val width = size.width - 2 * left
        fun x(spreads: Double) = left + ((spreads / STRIP_SPREADS + 1) / 2).toFloat() * width
        val trackHeight = TRACK_HEIGHT.dp.toPx()
        val top = (size.height - trackHeight) / 2
        val stops = STOPS.map { (spreads, colour) -> ((spreads / STRIP_SPREADS + 1) / 2).toFloat() to colour }
        val brush = Brush.horizontalGradient(
            colorStops = (stops.reversed().map { (at, colour) -> 1f - at to colour } + stops).toTypedArray(),
            startX = left,
            endX = left + width,
        )
        drawRoundRect(brush, Offset(left, top), Size(width, trackHeight), CornerRadius(trackHeight / 2), alpha = TRACK_ALPHA)
        drawLine(mark, Offset(x(0.0), 0f), Offset(x(0.0), size.height), strokeWidth = MARK_WIDTH.dp.toPx())
        val centre = Offset(x(position), size.height / 2)
        drawCircle(ring, radius + ringWidth, centre)
        drawCircle(dot, radius, centre)
    }
}

/** The strip's colour [spreads] from the mean: green while level, then yellow to red. */
internal fun colourAt(spreads: Double): Color {
    val distance = kotlin.math.abs(spreads).coerceAtMost(STRIP_SPREADS)
    if (distance <= LEVEL_SPREADS) return GOOD
    val (from, to) = STOPS.zipWithNext().first { (_, next) -> distance <= next.first }
    val fraction = ((distance - from.first) / (to.first - from.first)).toFloat()
    return lerp(from.second, to.second, fraction)
}

/** The week's distance from the 30-day mean in spreads, signed, held on the strip. */
internal fun position(trend: TrendResult): Double =
    if (trend.spread > 0.0) ((trend.recent - trend.baseline) / trend.spread).coerceIn(-STRIP_SPREADS, STRIP_SPREADS) else 0.0

/** Spreads either side of the mean the strip reaches, and where "level" ends -- as `trendOf`. */
internal const val STRIP_SPREADS = 2.5
private const val LEVEL_SPREADS = 0.5
private const val STRIP_HEIGHT = 12
private const val TRACK_HEIGHT = 6
private const val DOT_RADIUS = 4.5f
private const val RING_WIDTH = 1.5f
private const val MARK_WIDTH = 1.5f
/** The track is faded, so the dot of the same colour stands out on it. */
private const val TRACK_ALPHA = 0.45f

private val GOOD = Color(0xFF0CA30C)
private val WARNING = Color(0xFFFAB219)
private val SERIOUS = Color(0xFFEC835A)
private val CRITICAL = Color(0xFFD03B3B)

/** Spreads from the mean and the colour there, for one half of the strip; the other mirrors it. */
private val STOPS = listOf(
    0.0 to GOOD,
    LEVEL_SPREADS to GOOD,
    LEVEL_SPREADS to WARNING,
    (LEVEL_SPREADS + STRIP_SPREADS) / 2 to SERIOUS,
    STRIP_SPREADS to CRITICAL,
)
