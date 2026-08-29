package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.registry.ValueZones

/**
 * Sets the value boundaries a curve is coloured by.
 *
 * One field per boundary rather than a free-text range, because the bands are contiguous by
 * definition: where one ends the next begins, so asking for both ends would let the user
 * describe a gap or an overlap that the colouring cannot represent.
 *
 * Each row shows the colour it controls, so the effect of a change is visible without
 * closing the dialog and finding a chart.
 */
@Composable
fun ZonesDialog(
    typeName: String,
    displayName: String,
    currentZones: ValueZones?,
    defaultZones: ValueZones?,
    onDismiss: () -> Unit,
    onSave: (String, ValueZones?) -> Unit,
) {
    val startingBounds = (currentZones ?: defaultZones)?.bounds.orEmpty()
    val fields = remember(typeName) {
        mutableStateListOf<String>().apply {
            addAll(startingBounds.map { formatBound(it) })
        }
    }
    var error by remember(typeName) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zones_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.zones_explained),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )

                // A row per band, labelled with the range it covers so the number being
                // edited is tied to the colour it produces.
                ValueZones.ZONE_COLORS.forEachIndexed { index, color ->
                    val lower = fields.getOrNull(index - 1).orEmpty()
                    val upper = fields.getOrNull(index)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .size(ZONE_SWATCH.dp)
                                .background(color, CircleShape),
                        ) {}

                        if (upper == null) {
                            // The top band is open-ended: there is no boundary above the last
                            // one, so it is stated rather than left as an empty field.
                            Text(
                                text = stringResource(R.string.zones_above, lower),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            OutlinedTextField(
                                value = upper,
                                onValueChange = { fields[index] = it; error = false },
                                singleLine = true,
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.zones_upper_bound,
                                            lower.ifEmpty { "0" },
                                        ),
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                if (error) {
                    Text(
                        text = stringResource(R.string.zones_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = fields.map { it.replace(',', '.').trim().toDoubleOrNull() }
                    // Rejected rather than silently repaired: a boundary the user cannot see
                    // the effect of is worse than being told the numbers do not work.
                    if (parsed.any { it == null } || parsed.filterNotNull().let {
                            it != it.sorted() || it.distinct().size != it.size
                        }
                    ) {
                        error = true
                    } else {
                        onSave(typeName, ValueZones(parsed.filterNotNull()).sanitised())
                        onDismiss()
                    }
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
                    Text(stringResource(R.string.zones_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

/** Whole numbers lose their ".0", matching how the goal dialog presents a bound. */
private fun formatBound(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private const val ZONE_SWATCH = 18
