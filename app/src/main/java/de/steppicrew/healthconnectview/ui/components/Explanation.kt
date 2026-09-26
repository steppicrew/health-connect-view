package de.steppicrew.healthconnectview.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.settings.SettingsStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Whether one explanation is open, and how to flip it. [expanded] is null until read. */
class ExplanationState(val expanded: Boolean?, val toggle: () -> Unit)

/**
 * The remembered open/closed state of the explanation named [key].
 *
 * Closed unless the user opened it. Null until the stored choice is read, so that an open
 * explanation does not flash closed for a frame on every visit, nor a closed one open. Keys are
 * plain names ("trend", "sources"); renaming one closes it again.
 */
@Composable
fun rememberExplanation(key: String): ExplanationState {
    val context = LocalContext.current
    val store = remember(context) { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val expanded by remember(store, key) { store.settings.map { key in it.expandedExplanations } }
        .collectAsStateWithLifecycle(initialValue = null)
    return ExplanationState(expanded) {
        val current = expanded ?: return@ExplanationState
        scope.launch { store.setExplanationExpanded(key, !current) }
    }
}

/**
 * The "i" that folds an explanation in and out, placed beside the data it explains. Tinted
 * while open, so the icon also says which state it is in.
 */
@Composable
fun InfoToggle(state: ExplanationState, modifier: Modifier = Modifier) {
    IconButton(onClick = state.toggle, enabled = state.expanded != null, modifier = modifier) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(
                if (state.expanded == true) R.string.explanation_hide else R.string.explanation_show,
            ),
            tint = if (state.expanded == true) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
