package de.steppicrew.healthconnectview.ui.compare

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.registry.RecordTypeSpec

/**
 * Picks the type to set beside [current]: every type with a chart that access is granted for,
 * by name in the user's language.
 */
@Composable
fun CompareTypeDialog(
    current: String,
    load: suspend () -> List<RecordTypeSpec<*>>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val resources = LocalResources.current
    val candidates by produceState<List<RecordTypeSpec<*>>?>(null, resources) {
        value = load()
            .filter { it.type.simpleName != current }
            .sortedBy { resources.getString(it.displayNameRes) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compare_pick)) },
        text = {
            LazyColumn(Modifier.heightIn(max = LIST_MAX_HEIGHT.dp)) {
                items(candidates.orEmpty(), key = { it.type.simpleName.orEmpty() }) { spec ->
                    Text(
                        text = stringResource(spec.displayNameRes),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(spec.type.simpleName.orEmpty())
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val LIST_MAX_HEIGHT = 400
