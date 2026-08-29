package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.registry.RecordTypeSpec

/**
 * Picks a type to pin.
 *
 * Lists only types that can actually render a tile and are not already pinned, so every entry
 * does something when tapped.
 */
@Composable
fun AddTileDialog(
    candidates: List<RecordTypeSpec<*>>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_add_tile)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT.dp)) {
                items(candidates, key = { it.type.simpleName.orEmpty() }) { spec ->
                    Text(
                        text = stringResource(spec.displayNameRes),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAdd(spec.type.simpleName.orEmpty())
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private const val LIST_MAX_HEIGHT = 400
