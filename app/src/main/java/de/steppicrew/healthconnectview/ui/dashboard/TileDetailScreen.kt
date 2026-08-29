package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.detail.RecordRow
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

                is UiState.Data -> SpanContent(current.value, viewModel::selectSource)
            }
        }
    }
}

@Composable
private fun SpanContent(data: TileDetailData, onSelectSource: (String?) -> Unit) {
    LazyColumn {
        item(key = "summary") { SpanSummary(data, onSelectSource) }

        if (data.truncated) {
            item(key = "truncated") {
                Text(
                    text = pluralStringResource(
                        R.plurals.detail_truncated,
                        HealthRepository.MAX_RECORDS,
                        HealthRepository.MAX_RECORDS,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        item(key = "records_header") {
            Text(
                text = pluralStringResource(
                    R.plurals.detail_records_header,
                    data.records.size,
                    data.records.size,
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        items(data.records, key = { it.metadata.id }) { record ->
            RecordRow(spec = data.spec, record = record, onClick = {})
        }
    }
}

@Composable
private fun SpanSummary(data: TileDetailData, onSelectSource: (String?) -> Unit) {
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

        // Reading a dashed line against a curve is fiddly; say the answer in words too.
        data.goal?.let { goal ->
            val reached = (data.total ?: 0.0) >= goal
            Text(
                text = if (reached) {
                    stringResource(R.string.chart_goal_reached, Formatting.number(goal))
                } else {
                    stringResource(
                        R.string.chart_goal_remaining,
                        Formatting.number(data.total ?: 0.0),
                        Formatting.number(goal),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (reached) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (data.contributingApps.isNotEmpty()) {
            SourceSection(data = data, onSelectSource = onSelectSource)
        }

        if (data.points.isNotEmpty()) {
            LineChart(
                points = data.points,
                // Safe to smooth now that each rise spans the interval the record actually
                // covered: the curve rounds the corners of a real ramp rather than inventing
                // a slope where the data says a vertical jump. The clamp keeps every segment
                // within the two values it joins, so a plateau cannot bulge.
                smooth = data.spec.tile.smoothChart,
                goal = data.goal,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(
                    when {
                        data.approximated -> R.string.chart_source_cumulative_scaled
                        data.cumulative -> R.string.chart_source_cumulative
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

/**
 * Source picker plus a plain statement of what the number above actually is.
 *
 * "All sources" stays first and is the default: it is Health Connect's deduplicated total,
 * which is the right answer for the metric and is deliberately not any single app's figure.
 * Selecting one app answers a different question, so the caption says which question is being
 * answered rather than leaving the user to infer it from a changed number.
 *
 * Only shown where more than one app wrote; with a single writer there is nothing to choose.
 */
@Composable
private fun SourceSection(data: TileDetailData, onSelectSource: (String?) -> Unit) {
    val context = LocalContext.current
    val sources = data.contributingApps.sortedBy { context.appLabelFor(it) }

    Column(Modifier.padding(top = 8.dp)) {
        if (sources.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = data.selectedSource == null,
                    onClick = { onSelectSource(null) },
                    label = { Text(stringResource(R.string.source_all)) },
                )
                sources.forEach { packageName ->
                    FilterChip(
                        selected = data.selectedSource == packageName,
                        onClick = { onSelectSource(packageName) },
                        label = { Text(context.appLabelFor(packageName)) },
                    )
                }
            }
        }

        Text(
            text = data.selectedSource?.let {
                stringResource(R.string.source_showing_one, context.appLabelFor(it))
            } ?: if (sources.size > 1) {
                stringResource(R.string.source_all_explained)
            } else {
                stringResource(R.string.detail_written_by, context.appLabelFor(sources.first()))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        // The overlap winner is Health Connect's own priority setting, not ours to define.
        if (sources.size > 1) {
            Text(
                text = stringResource(R.string.source_priority_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
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
