package de.steppicrew.healthconnectview.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.firstLineTextInset

/**
 * What the insights tile shows: the week's largest changes, or chosen types in a chosen order,
 * moved or not. [candidates] are the type names that have a week to compare, by name; a type
 * chosen earlier that has none this week stays listed, so choosing it is never lost.
 */
@Composable
fun InsightPicksDialog(
    candidates: List<String>,
    current: List<String>,
    onDismiss: () -> Unit,
    /** The chosen types in order; empty for the largest changes. */
    onSave: (List<String>) -> Unit,
) {
    var own by remember { mutableStateOf(current.isNotEmpty()) }
    var picks by remember { mutableStateOf(current) }
    val names = (current + candidates).distinct().filter { RecordRegistry.specOrNull(it) != null }
    val label = @Composable { name: String -> stringResource(RecordRegistry.specOrNull(name)!!.displayNameRes) }
    val others = names.filter { it !in picks }.map { it to label(it) }.sortedBy { it.second }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.insights_pick_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ModeRow(R.string.insights_pick_auto, selected = !own) { own = false }
                ModeRow(R.string.insights_pick_own, selected = own) { own = true }
                if (own) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        picks.forEachIndexed { index, name ->
                            PickRow(
                                label = label(name),
                                checked = true,
                                onCheck = { picks = picks - name },
                                onUp = if (index > 0) {
                                    { picks = picks.toMutableList().apply { add(index - 1, removeAt(index)) } }
                                } else {
                                    null
                                },
                                onDown = if (index < picks.lastIndex) {
                                    { picks = picks.toMutableList().apply { add(index + 1, removeAt(index)) } }
                                } else {
                                    null
                                },
                            )
                        }
                        others.forEach { (name, text) ->
                            PickRow(label = text, checked = false, onCheck = { picks = picks + name })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(if (own) picks else emptyList())
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ModeRow(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    val style = MaterialTheme.typography.bodyLarge
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(top = firstLineInset(style, TOGGLE_SIZE.dp)))
        Text(
            text = stringResource(label),
            style = style,
            modifier = Modifier.padding(start = 12.dp, top = firstLineTextInset(style, TOGGLE_SIZE.dp)),
        )
    }
}

/** A type with its box, and while chosen the arrows that move it up and down the tile. */
@Composable
private fun PickRow(label: String, checked: Boolean, onCheck: () -> Unit, onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null) {
    val style = MaterialTheme.typography.bodyMedium
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .weight(1f)
                .toggleable(value = checked, onValueChange = { onCheck() }, role = Role.Checkbox)
                .padding(vertical = 12.dp),
        ) {
            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(top = firstLineInset(style, TOGGLE_SIZE.dp)))
            Text(
                text = label,
                style = style,
                modifier = Modifier.padding(start = 12.dp, top = firstLineTextInset(style, TOGGLE_SIZE.dp)),
            )
        }
        if (checked) {
            IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.tile_move_up))
            }
            IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.tile_move_down))
            }
        }
    }
}

