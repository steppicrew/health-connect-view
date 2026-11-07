package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.ActiveMinutes
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.health.WHO_WEEKLY
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.ui.active.ActiveBars
import de.steppicrew.healthconnectview.ui.active.ActiveRead
import de.steppicrew.healthconnectview.ui.components.ProgressRing

/**
 * The active-minutes tile: the seven days up to the dashboard's day as a ring against the
 * WHO's 150, the weighted minutes inside it. A wide tile adds the days as bars. A tap opens the
 * screen, which says how the minutes are made.
 */
@Composable
internal fun ActiveTileBody(data: ActiveTileData, width: Int, height: Int) {
    if (data.locked) {
        LockableIcon(Icons.AutoMirrored.Filled.DirectionsRun, stringResource(R.string.settings_pro), locked = true)
        return
    }
    when (val read = data.read) {
        null -> TileLoading()
        ActiveRead.Failed -> TileFailed()
        ActiveRead.NoAccess -> Note(stringResource(R.string.detail_no_permission_title))
        ActiveRead.NoMax -> Note(stringResource(R.string.active_no_max_title))
        ActiveRead.Empty -> Note(stringResource(R.string.active_none))
        // A tall tile stacks the ring over the days: beside each other, a ring as tall as the
        // tile took nearly all its width and left the bars a sliver.
        is ActiveRead.Data -> when {
            width > 1 && height > 1 -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Ring(read.minutes, Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true))
                ActiveBars(read.minutes.days, Span.WEEK, Modifier.fillMaxWidth().weight(1f))
            }
            width > 1 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Ring(read.minutes, Modifier.fillMaxHeight().aspectRatio(1f))
                ActiveBars(read.minutes.days, Span.WEEK, Modifier.weight(1f).fillMaxHeight())
            }
            else -> Ring(read.minutes, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Note(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

@Composable
private fun Ring(minutes: ActiveMinutes, modifier: Modifier) {
    // Two laps at most: a hiking week at 470 % drew five nested rings and read as a drawing
    // fault (the owner, 07.11.2026). The number inside says how far over it went.
    ProgressRing(progress = (minutes.weighted.toFloat() / WHO_WEEKLY).coerceAtMost(MAX_LAPS), modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Formatting.integer(minutes.weighted.toLong()), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                text = stringResource(R.string.active_tile_of, Formatting.integer(WHO_WEEKLY.toLong())),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private const val MAX_LAPS = 2f
