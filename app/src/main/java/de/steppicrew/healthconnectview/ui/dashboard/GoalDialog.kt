package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import de.steppicrew.healthconnectview.R

/**
 * Sets a ring tile's daily goal.
 *
 * Input is parsed leniently -- both a decimal comma and a point are accepted, since the app
 * is translated and the keyboard's separator follows the locale while [String.toDoubleOrNull]
 * does not.
 */
@Composable
fun GoalDialog(
    typeName: String,
    displayName: String,
    currentGoal: Double?,
    onDismiss: () -> Unit,
    onSave: (String, Double?) -> Unit,
) {
    var text by remember(typeName) {
        mutableStateOf(currentGoal?.let { formatForEditing(it) } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_title)) },
        text = {
            Column {
                Text(displayName)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.goal_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(typeName, text.replace(',', '.').trim().toDoubleOrNull())
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
                        onSave(typeName, null)
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

/** Whole numbers lose their ".0" so a goal of 10000 does not present as "10000.0". */
private fun formatForEditing(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
