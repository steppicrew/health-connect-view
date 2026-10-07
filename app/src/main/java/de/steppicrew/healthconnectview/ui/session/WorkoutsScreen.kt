package de.steppicrew.healthconnectview.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.WorkoutFamily
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.health.familiesIn
import de.steppicrew.healthconnectview.health.familyOf
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.OnResume
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.iconFor
import de.steppicrew.healthconnectview.ui.components.labelFor
import de.steppicrew.healthconnectview.ui.components.sessionName

/**
 * The past year's workouts, newest first, filtered by kind: "my last rides" without stepping
 * back through the weeks. A tap opens the same session screen as everywhere else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    viewModel: WorkoutsViewModel,
    onBack: () -> Unit,
    onOpenSession: (Session) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val family by viewModel.family.collectAsStateWithLifecycle()
    val historyGranted by viewModel.historyGranted.collectAsStateWithLifecycle()
    OnResume { viewModel.onResume() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workouts_by_kind)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val inner = Modifier.padding(padding)
        when (val current = state) {
            UiState.Loading -> LoadingView(inner)
            UiState.NoPermission -> MessageView(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.detail_no_permission_title),
                body = stringResource(R.string.detail_no_permission_body),
                modifier = inner,
            )
            UiState.Empty -> MessageView(
                icon = Icons.Default.Inbox,
                title = stringResource(R.string.workouts_none),
                body = if (historyGranted) "" else stringResource(R.string.history_limit_note),
                modifier = inner,
                actionLabel = stringResource(R.string.history_grant).takeIf { !historyGranted },
                onAction = onOpenPermissions.takeIf { !historyGranted },
            )
            is UiState.Error -> MessageView(
                icon = Icons.Default.Warning,
                title = stringResource(R.string.detail_error_title),
                body = current.message,
                modifier = inner,
            )
            is UiState.Data -> WorkoutList(
                sessions = current.value,
                family = family,
                historyGranted = historyGranted,
                onSelect = viewModel::select,
                onOpenSession = onOpenSession,
                onOpenPermissions = onOpenPermissions,
                modifier = inner,
            )
        }
    }
}

@Composable
private fun WorkoutList(
    sessions: List<Session>,
    family: WorkoutFamily?,
    historyGranted: Boolean,
    onSelect: (WorkoutFamily?) -> Unit,
    onOpenSession: (Session) -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val families = remember(sessions) { familiesIn(sessions) }
    // A kind asked for by the route that does not occur shows everything rather than nothing.
    val shownFamily = family?.takeIf { it in families }
    val shown = remember(sessions, shownFamily) {
        if (shownFamily == null) sessions else sessions.filter { familyOf(it.exerciseType) == shownFamily }
    }

    Column(modifier.fillMaxSize()) {
        // Only the kinds there are: a chip leading to an empty list is a question with no answer.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = shownFamily == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.workouts_all)) },
            )
            families.forEach { kind ->
                FilterChip(
                    selected = kind == shownFamily,
                    onClick = { onSelect(kind) },
                    label = { Text(stringResource(labelFor(kind))) },
                    leadingIcon = {
                        Icon(
                            imageVector = iconFor(kind),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.recordId.ifEmpty { it.start.toString() } }) { session ->
                WorkoutRow(session, onClick = { onOpenSession(session) })
                HorizontalDivider()
            }
            item(key = "footer") {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(
                            if (historyGranted) R.string.workouts_span_year else R.string.history_limit_note,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!historyGranted) {
                        TextButton(onClick = onOpenPermissions) {
                            Text(stringResource(R.string.history_grant))
                        }
                    }
                }
            }
        }
    }
}

/** One workout: what, when, how long, and whether it has a route. */
@Composable
private fun WorkoutRow(session: Session, onClick: () -> Unit) {
    val style = MaterialTheme.typography.bodyLarge
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = iconFor(session),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = firstLineInset(style, ROW_ICON.dp)).size(ROW_ICON.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(text = sessionName(session), style = style)
            Text(
                text = Formatting.date(session.start) + "  " + stringResource(
                    R.string.session_span,
                    Formatting.time(session.start),
                    Formatting.time(session.end),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (session.route != null) {
            Icon(
                imageVector = Icons.Default.Route,
                contentDescription = stringResource(R.string.route_available),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = firstLineInset(style, ROW_ICON.dp)).size(ROW_ICON.dp),
            )
        }
        Text(
            text = Formatting.duration(session.duration),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val ROW_ICON = 24
