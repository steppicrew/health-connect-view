package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.ui.components.firstLineInset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R

/**
 * Picks a type to pin.
 *
 * Lists only types that can actually render a tile, so every entry does something when tapped,
 * and says so when every one is pinned already.
 * Types already on the dashboard come last under a heading of their own: another tile of one
 * is for a different window or face, and without Pro they are shown locked, a tap starting the
 * purchase, as the export menu's locked entries do.
 */
@Composable
fun AddTileDialog(
    candidates: List<AddCandidate>,
    /** Whether a type already pinned may be added again. */
    repeatUnlocked: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onBuy: () -> Unit,
) {
    val (pinned, fresh) = candidates.partition { it.pinned }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_add_tile)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT.dp)) {
                // Every type pinned: say so rather than open on an empty list, or on nothing
                // but the locked repeats.
                if (fresh.isEmpty()) {
                    item(key = "all-pinned") {
                        Text(
                            text = stringResource(R.string.dashboard_add_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(fresh, key = { it.spec.type.simpleName.orEmpty() }) { candidate ->
                    CandidateRow(candidate, locked = false) {
                        onAdd(candidate.spec.type.simpleName.orEmpty())
                        onDismiss()
                    }
                }
                if (pinned.isNotEmpty()) {
                    item(key = "pinned") {
                        Text(
                            text = stringResource(R.string.dashboard_add_again),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(pinned, key = { "again-" + it.spec.type.simpleName.orEmpty() }) { candidate ->
                        CandidateRow(candidate, locked = !repeatUnlocked) {
                            if (repeatUnlocked) onAdd(candidate.spec.type.simpleName.orEmpty()) else onBuy()
                            onDismiss()
                        }
                    }
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

@Composable
private fun CandidateRow(candidate: AddCandidate, locked: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = stringResource(candidate.spec.displayNameRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (locked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = stringResource(R.string.settings_pro),
                // On the name's first line, should a long name wrap.
                modifier = Modifier
                    .padding(top = firstLineInset(MaterialTheme.typography.bodyLarge, 18.dp))
                    .size(18.dp),
            )
        }
    }
}

private const val LIST_MAX_HEIGHT = 400
