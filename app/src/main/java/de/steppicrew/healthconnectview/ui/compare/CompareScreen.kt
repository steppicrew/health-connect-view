package de.steppicrew.healthconnectview.ui.compare

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.components.InfoGroup
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.OnResume
import de.steppicrew.healthconnectview.ui.components.SpanSelector
import de.steppicrew.healthconnectview.ui.components.WindowStepper
import de.steppicrew.healthconnectview.ui.components.windowLabel
import de.steppicrew.healthconnectview.ui.dashboard.DataLineChart
import de.steppicrew.healthconnectview.ui.dashboard.ExpandableDataChart
import de.steppicrew.healthconnectview.ui.dashboard.TileDetailData

/**
 * Two types, one chart each, on one time axis.
 *
 * Never two scales on one chart: two y-axes read badly on a phone, and lines that cross on a
 * shared plot invite a cause-and-effect reading the data cannot carry. Stacked, a day sits at
 * the same x in both, which is all a comparison by eye needs -- and the note beneath says that
 * moving together is not one causing the other.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    viewModel: CompareViewModel,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val types by viewModel.types.collectAsStateWithLifecycle()
    val span by viewModel.span.collectAsStateWithLifecycle()
    val offset by viewModel.offset.collectAsStateWithLifecycle()
    val historyGranted by viewModel.historyGranted.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    OnResume { viewModel.onResume() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::swap) {
                        Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.compare_swap))
                    }
                    IconButton(onClick = { picking = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.compare_pick))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SpanSelector(selected = span, onSelect = viewModel::setSpan)
            WindowStepper(
                label = windowLabel(span, offset),
                canStepForward = offset > 0,
                onBack = viewModel::stepBack,
                onForward = viewModel::stepForward,
                onNow = viewModel::stepToNow,
            )
            val historyNeeded = !historyGranted && span.needsHistoryPermission(offset)
            when (val current = state) {
                UiState.Loading -> LoadingView()
                UiState.NoPermission -> MessageView(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.detail_no_permission_title),
                    body = stringResource(R.string.detail_no_permission_body),
                    actionLabel = stringResource(R.string.history_grant),
                    onAction = onOpenPermissions,
                )
                UiState.Empty -> MessageView(
                    icon = Icons.Default.Inbox,
                    title = stringResource(R.string.detail_empty_title),
                    body = stringResource(if (historyNeeded) R.string.history_limit_note else R.string.detail_empty_body),
                    actionLabel = stringResource(R.string.history_grant).takeIf { historyNeeded },
                    onAction = onOpenPermissions.takeIf { historyNeeded },
                )
                is UiState.Error -> MessageView(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.detail_error_title),
                    body = current.message,
                )
                is UiState.Data -> Charts(current.value, windowLabel(span, offset), historyNeeded, onOpenPermissions)
            }
        }
    }

    val shown = types
    if (picking && shown != null) {
        CompareTypeDialog(
            current = shown.first,
            load = viewModel::candidates,
            onDismiss = { picking = false },
            onPick = viewModel::replaceSecond,
        )
    }
}

@Composable
private fun Charts(data: Compared, period: String, historyNeeded: Boolean, onOpenPermissions: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Chart(data.first, data, period)
        Chart(data.second, data, period)
        Text(
            text = stringResource(R.string.compare_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (historyNeeded) {
            Text(
                text = stringResource(R.string.history_limit_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onOpenPermissions) { Text(stringResource(R.string.history_grant)) }
        }
    }
}

/** One type's chart, as on its own screen, drawn on the shared axis. */
@Composable
private fun Chart(chart: TileDetailData, data: Compared, period: String) {
    InfoGroup {
        val name = stringResource(chart.spec.displayNameRes)
        // "Schritte (Schritte)" says nothing twice; "Schlaf (h)" needs its unit.
        val unit = chart.spec.displayUnitRes?.let { stringResource(it) }
            ?.takeUnless { it.equals(name, ignoreCase = true) }
            ?.let { " ($it)" }.orEmpty()
        Text(name + unit, style = MaterialTheme.typography.titleSmall)
        if (chart.points.isEmpty()) {
            Text(
                text = stringResource(R.string.compare_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            ExpandableDataChart(chart, period, Modifier.padding(top = 8.dp), axis = data.extent)
        }
    }
}
