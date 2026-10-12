package de.steppicrew.healthconnectview.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.firstLineTextInset
import de.steppicrew.healthconnectview.ui.components.parseDecimalInput

/** What the goal dialog saves: the goal, whether it adapts, and the floor of an adaptive one. */
data class GoalChoice(val goal: Double?, val adaptive: Boolean, val floor: Double?)

/**
 * Sets a ring tile's daily goal: a fixed one, or where the type offers it ([adaptiveOffered])
 * an adaptive one that starts from the value typed and may be kept above a floor.
 *
 * Input is parsed leniently by [parseDecimalInput], since the keyboard follows the locale.
 */
@Composable
fun GoalDialog(
    typeName: String,
    displayName: String,
    currentGoal: Double?,
    /** The unit the goal is typed in, beside the field. */
    unit: String?,
    onDismiss: () -> Unit,
    onSave: (String, GoalChoice) -> Unit,
    adaptiveOffered: Boolean = false,
    currentAdaptive: Boolean = false,
    currentFloor: Double? = null,
) {
    var text by remember(typeName) {
        mutableStateOf(currentGoal?.let { formatForEditing(it) } ?: "")
    }
    var adaptive by remember(typeName) { mutableStateOf(currentAdaptive && adaptiveOffered) }
    var floorText by remember(typeName) {
        mutableStateOf(currentFloor?.let { formatForEditing(it) } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_title)) },
        text = {
            Column {
                Text(displayName)
                if (adaptiveOffered) {
                    ModeRow(R.string.goal_fixed, selected = !adaptive) { adaptive = false }
                    ModeRow(R.string.goal_adaptive, selected = adaptive) { adaptive = true }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(if (adaptive) R.string.goal_start_hint else R.string.goal_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = unit?.let { { Text(it) } },
                )
                if (adaptive) {
                    OutlinedTextField(
                        value = floorText,
                        onValueChange = { floorText = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.goal_floor_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        suffix = unit?.let { { Text(it) } },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val floor = parseDecimalInput(floorText)?.takeIf { adaptive && it > 0.0 }
                    onSave(typeName, GoalChoice(parseDecimalInput(text), adaptive, floor))
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Column {
                TextButton(
                    onClick = {
                        onSave(typeName, GoalChoice(null, adaptive = false, floor = null))
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.goal_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

/** A fixed or an adaptive goal, the radio button on the label's first line. */
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
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(top = firstLineInset(style, TOGGLE_SIZE.dp)),
        )
        Text(
            text = stringResource(label),
            style = style,
            modifier = Modifier.padding(start = 12.dp, top = firstLineTextInset(style, TOGGLE_SIZE.dp)),
        )
    }
}

/** Whole numbers lose their ".0" so a goal of 10000 does not present as "10000.0". */
private fun formatForEditing(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private const val TOGGLE_SIZE = 24
