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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.health.TrendResult

/**
 * Where the last seven days sit against the 30 days: the 30-day mean as a mark in the middle,
 * the band either side of it where a week still counts as level, and a dot for the week. Measured
 * in the type's own day-to-day spread, as the list is ranked, so a dot near the edge is an
 * unusual week for that type whatever its unit. A week beyond the strip sits on its edge.
 * The text beside it says the same in words, so a screen reader skips it.
 */
@Composable
internal fun InsightStrip(trend: TrendResult, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val level = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = LEVEL_ALPHA)
    val mark = MaterialTheme.colorScheme.onSurfaceVariant
    val dot = MaterialTheme.colorScheme.onSurface
    val position = position(trend)
    Canvas(modifier.fillMaxWidth().height(STRIP_HEIGHT.dp).clearAndSetSemantics { }) {
        val radius = DOT_RADIUS.dp.toPx()
        // Inset by the dot, so one on the edge is drawn whole.
        val left = radius
        val width = size.width - 2 * radius
        fun x(spreads: Double) = left + ((spreads / STRIP_SPREADS + 1) / 2).toFloat() * width
        val trackHeight = TRACK_HEIGHT.dp.toPx()
        val top = (size.height - trackHeight) / 2
        drawRoundRect(track, Offset(left, top), Size(width, trackHeight), CornerRadius(trackHeight / 2))
        drawRect(level, Offset(x(-LEVEL_SPREADS), top), Size(x(LEVEL_SPREADS) - x(-LEVEL_SPREADS), trackHeight))
        drawLine(mark, Offset(x(0.0), 0f), Offset(x(0.0), size.height), strokeWidth = MARK_WIDTH.dp.toPx())
        drawCircle(dot, radius, Offset(x(position), size.height / 2))
    }
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
private const val MARK_WIDTH = 1.5f
private const val LEVEL_ALPHA = 0.35f
