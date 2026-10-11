package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.CycleNow
import de.steppicrew.healthconnectview.ui.cycle.MAX_COLUMNS
import de.steppicrew.healthconnectview.ui.cycle.MIN_COLUMNS
import de.steppicrew.healthconnectview.ui.cycle.RowColors
import de.steppicrew.healthconnectview.ui.cycle.drawCycle

/**
 * The cycle tile (step 76): the day of the running cycle on the dashboard's day and the usual
 * length of the year's completed cycles -- "Tag 12 · meist 28 Tage". A wide tile adds the
 * cycles themselves as the overview draws them, the running one first, aligned on day 1 and
 * as many as the height holds, so this cycle sits over the ones before it.
 *
 * Describes, never predicts: no day to come is drawn, no next period, no fertile window --
 * that would be a medical claim made from someone else's data. A tap opens the overview.
 */
@Composable
internal fun CycleTileBody(data: CycleTileData, width: Int, height: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val now = data.now
    when {
        !data.loaded -> TileLoading()
        data.failed -> TileFailed()
        data.noAccess -> Text(
            text = stringResource(R.string.detail_no_permission_title),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            textAlign = TextAlign.Center,
        )
        now == null -> Text(
            text = stringResource(R.string.cycle_tile_none),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            textAlign = TextAlign.Center,
        )
        width > 1 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp)) {
            CycleFigures(now, inline = true)
            CycleRows(now, Modifier.weight(1f))
        }
        else -> CycleFigures(now, inline = false)
    }
}

/** The day, and the usual length where a completed cycle gives one. */
@Composable
private fun CycleFigures(now: CycleNow, inline: Boolean) {
    val day = stringResource(R.string.cycle_tile_day, now.day)
    val usual = now.stats?.let { stats ->
        stringResource(R.string.cycle_tile_usual, pluralStringResource(R.plurals.cycle_days, stats.medianLength, stats.medianLength))
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (inline) {
        // One line that cannot wrap: a short label and a short figure.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(day, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            if (usual != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = usual,
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            if (usual != null) {
                Text(usual, style = MaterialTheme.typography.labelMedium, color = muted, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * The cycles as rows of days on one scale, the running one on top, as many as fit. The scale is
 * the longest shown, held to the overview's, so a day sits under the same day of every row.
 * The words above say the same, so a screen reader skips the drawing.
 */
@Composable
private fun CycleRows(now: CycleNow, modifier: Modifier) {
    // The overview's cells are drawn on the page; on the tile's raised surface they vanished.
    val colors = RowColors(
        cell = MaterialTheme.colorScheme.outlineVariant,
        test = MaterialTheme.colorScheme.tertiary,
        mucus = MaterialTheme.colorScheme.secondary,
        temperature = MaterialTheme.colorScheme.onSurface,
    )
    BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics { }) {
        val fits = ((maxHeight.value + ROW_GAP) / (MIN_ROW_HEIGHT + ROW_GAP)).toInt().coerceAtLeast(1)
        val shown = now.cycles.take(fits)
        // Fewer cycles than room: taller rows rather than an empty half tile, up to a cap.
        val rowHeight = ((maxHeight.value + ROW_GAP) / shown.size - ROW_GAP).coerceIn(MIN_ROW_HEIGHT, MAX_ROW_HEIGHT)
        val columns = shown.maxOf { it.length ?: it.days.size }.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp)) {
            shown.forEach { cycle ->
                Canvas(Modifier.fillMaxWidth().height(rowHeight.dp)) {
                    drawCycle(cycle.days, columns, scale = null, colors = colors)
                }
            }
        }
    }
}

private const val MIN_ROW_HEIGHT = 14f
private const val MAX_ROW_HEIGHT = 30f
private const val ROW_GAP = 4f
