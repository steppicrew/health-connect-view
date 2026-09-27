package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.dashboard.TileColor

/**
 * Picks a tile's colour from the palette. Each swatch is drawn as a small tile in its own
 * colours, "Aa" in its text colour, so what is chosen is what the tile will look like in the
 * theme in force. Picking applies at once and closes; there is nothing to confirm.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TileColorDialog(
    displayName: String,
    current: TileColor,
    onDismiss: () -> Unit,
    onPick: (TileColor) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tile_color_title, displayName)) },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                TileColor.entries.forEach { color ->
                    Swatch(color, selected = color == current) {
                        onPick(color)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun Swatch(color: TileColor, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(color.labelRes)
    TileColored(color) {
        val shape = RoundedCornerShape(12.dp)
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(SWATCH.dp)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                )
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = null)
                } else {
                    Text("Aa", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private val TileColor.labelRes: Int
    get() = when (this) {
        TileColor.DEFAULT -> R.string.tile_color_default
        TileColor.BLUE -> R.string.tile_color_blue
        TileColor.TEAL -> R.string.tile_color_teal
        TileColor.GREEN -> R.string.tile_color_green
        TileColor.AMBER -> R.string.tile_color_amber
        TileColor.CORAL -> R.string.tile_color_coral
        TileColor.ROSE -> R.string.tile_color_rose
        TileColor.PURPLE -> R.string.tile_color_purple
        TileColor.GREY -> R.string.tile_color_grey
    }

private const val SWATCH = 56
