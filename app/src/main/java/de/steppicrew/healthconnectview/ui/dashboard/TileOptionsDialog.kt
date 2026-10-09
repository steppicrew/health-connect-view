package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.ui.components.NightLine
import de.steppicrew.healthconnectview.dashboard.TileFace
import de.steppicrew.healthconnectview.health.Span

/**
 * Chooses what a large tile describes: which window, and whether it shows the value, the chart
 * or both. Chips rather than a menu, as in settings: every choice is visible at once and one
 * tap away, and there are only seven.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TileOptionsDialog(
    displayName: String,
    currentSpan: Span,
    currentFace: TileFace,
    onDismiss: () -> Unit,
    onSave: (Span, TileFace, String?) -> Unit,
    /** The curves the type may draw beside its own, by type name; none offers no choice. */
    companions: List<String> = emptyList(),
    currentCompanion: String? = null,
) {
    var span by remember { mutableStateOf(currentSpan) }
    var face by remember { mutableStateOf(currentFace) }
    var companion by remember { mutableStateOf(currentCompanion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tile_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(displayName)
                Text(
                    text = stringResource(R.string.tile_options_span),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Span.entries.forEach { choice ->
                        FilterChip(
                            selected = span == choice,
                            onClick = { span = choice },
                            label = { Text(stringResource(choice.labelRes)) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.tile_options_face),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TileFace.entries.forEach { choice ->
                        FilterChip(
                            selected = face == choice,
                            onClick = { face = choice },
                            label = { Text(stringResource(choice.labelRes())) },
                        )
                    }
                }
                // Drawn over a single day's chart -- a night -- so offered with that in view.
                if (companions.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.tile_options_companion),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = companion == null,
                            onClick = { companion = null },
                            label = { Text(stringResource(R.string.tile_companion_none)) },
                        )
                        companions.forEach { type ->
                            val line = NightLine.of(type)
                            val name = line?.shortLabel ?: RecordRegistry.specOrNull(type)?.displayNameRes ?: return@forEach
                            FilterChip(
                                selected = companion == type,
                                onClick = { companion = type },
                                label = { Text(stringResource(name)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(span, face, companion)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun TileFace.labelRes(): Int = when (this) {
    TileFace.VALUE -> R.string.tile_face_value
    TileFace.CHART -> R.string.tile_face_chart
    TileFace.BOTH -> R.string.tile_face_both
}
