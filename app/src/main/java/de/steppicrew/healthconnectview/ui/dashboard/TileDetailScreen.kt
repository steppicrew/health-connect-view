package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.components.LineChart
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.util.appLabelFor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One type, full screen, over a span the user can step through.
 *
 * This is the only view that can reach data older than a year: the trailing ranges elsewhere
 * are anchored to today, so anything before them cannot be requested at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileDetailScreen(
    viewModel: TileDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val span by viewModel.span.collectAsStateWithLifecycle()
    val offset by viewModel.offset.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(titleFor(state)) },
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
        Column(modifier = Modifier.padding(padding)) {
            SpanSelector(selected = span, onSelect = viewModel::setSpan)
            WindowStepper(
                label = windowLabel(state),
                canStepForward = offset > 0,
                onBack = viewModel::stepBack,
                onForward = viewModel::stepForward,
            )

            when (val current = state) {
                is UiState.Loading -> LoadingView()

                is UiState.NoPermission -> MessageView(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.detail_no_permission_title),
                    body = stringResource(R.string.detail_no_permission_body),
                )

                is UiState.Empty -> MessageView(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.detail_empty_title),
                    body = stringResource(R.string.detail_empty_body),
                )

                is UiState.Error -> MessageView(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.detail_error_title),
                    body = current.message,
                )

                is UiState.Data -> SpanContent(current.value)
            }
        }
    }
}

@Composable
private fun SpanContent(data: TileDetailData) {
    Column(Modifier.padding(16.dp)) {
        // First: it changes how a short chart should be read -- not missing data, but data
        // the app is not allowed to see.
        if (data.historyCapped) {
            Text(
                text = stringResource(R.string.detail_history_capped),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        data.total?.let { total ->
            Text(
                text = stringResource(R.string.span_total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Formatting.number(total),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        if (data.contributingApps.isNotEmpty()) {
            val context = LocalContext.current
            Text(
                text = stringResource(
                    R.string.detail_written_by,
                    data.contributingApps.map { context.appLabelFor(it) }.sorted()
                        .joinToString(", "),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (data.points.isNotEmpty()) {
            LineChart(points = data.points, modifier = Modifier.padding(top = 16.dp))
            Text(
                text = stringResource(
                    when {
                        data.aggregated && data.weeklyBuckets ->
                            R.string.chart_source_aggregated_weekly
                        data.aggregated -> R.string.chart_source_aggregated
                        else -> R.string.chart_source_raw
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpanSelector(selected: Span, onSelect: (Span) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Span.entries.forEach { span ->
            FilterChip(
                selected = span == selected,
                onClick = { onSelect(span) },
                label = { Text(stringResource(span.labelRes)) },
            )
        }
    }
}

@Composable
private fun WindowStepper(
    label: String,
    canStepForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = stringResource(R.string.span_previous),
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onForward, enabled = canStepForward) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.span_next),
            )
        }
    }
}

@Composable
private fun titleFor(state: UiState<TileDetailData>): String =
    if (state is UiState.Data) stringResource(state.value.spec.displayNameRes) else ""

@Composable
private fun windowLabel(state: UiState<TileDetailData>): String {
    if (state !is UiState.Data) return ""
    val data = state.value
    return if (data.start == data.end) {
        formatDate(data.start)
    } else {
        formatDate(data.start) + " – " + formatDate(data.end)
    }
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
