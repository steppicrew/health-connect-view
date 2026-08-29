package de.steppicrew.healthconnectview.ui.detail

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.Record
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.TimeRange
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.util.appLabelFor
import de.steppicrew.healthconnectview.ui.components.LineChart
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.MessageView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeDetailScreen(
    viewModel: TypeDetailViewModel,
    onBack: () -> Unit,
    onOpenRecord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()

    val title = (state as? UiState.Data)?.value?.spec?.displayNameRes

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title?.let { stringResource(it) } ?: "") },
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
        Column(Modifier.padding(padding)) {
            RangeSelector(selected = range, onSelect = viewModel::setRange)
            HorizontalDivider()

            when (val current = state) {
                is UiState.Loading -> LoadingView()
                is UiState.NoPermission -> MessageView(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.detail_no_permission_title),
                    body = stringResource(R.string.detail_no_permission_body),
                )
                is UiState.Empty -> MessageView(
                    icon = Icons.Default.Inbox,
                    title = stringResource(R.string.detail_empty_title),
                    body = stringResource(R.string.detail_empty_body),
                )
                is UiState.Error -> MessageView(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.detail_error_title),
                    body = current.message,
                )
                is UiState.Data -> DetailContent(
                    data = current.value,
                    onOpenRecord = onOpenRecord,
                )
            }
        }
    }
}

@Composable
private fun RangeSelector(selected: TimeRange, onSelect: (TimeRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimeRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(stringResource(range.labelRes)) },
            )
        }
    }
}

@Composable
private fun DetailContent(
    data: TypeDetailData,
    onOpenRecord: (String) -> Unit,
) {
    LazyColumn {
        // First, because it changes how everything below it should be read: the chart is not
        // short of older data, the app is not allowed to see it.
        if (data.historyCapped) {
            item(key = "history_capped") {
                Text(
                    text = stringResource(R.string.detail_history_capped),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (data.points.isNotEmpty()) {
            item(key = "chart") {
                Column(Modifier.padding(16.dp)) {
                    LineChart(
                        points = data.points,
                        smooth = data.spec.tile.smoothChart,
                        unitRes = data.spec.unitRes,
                    )
                    Text(
                        text = stringResource(
                            when {
                                data.pointsAreAggregated -> R.string.chart_source_aggregated
                                data.pointsAreSampled -> R.string.chart_source_sampled
                                else -> R.string.chart_source_raw
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (data.contributingApps.isNotEmpty()) {
            item(key = "contributors") {
                val context = LocalContext.current
                val names = data.contributingApps
                    .map { context.appLabelFor(it) }
                    .sorted()
                    .joinToString(", ")
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.detail_written_by, names),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Only worth explaining when more than one app writes: that is when the
                    // deduplicated total stops matching what any single app would report.
                    if (data.contributingApps.size > 1) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.detail_multiple_writers,
                                data.contributingApps.size,
                                data.contributingApps.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (data.truncated) {
            item(key = "truncated") {
                Text(
                    text = pluralStringResource(
                        R.plurals.detail_truncated,
                        de.steppicrew.healthconnectview.health.HealthRepository.MAX_RECORDS,
                        de.steppicrew.healthconnectview.health.HealthRepository.MAX_RECORDS,
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
            RecordRow(
                spec = data.spec,
                record = record,
                onClick = { onOpenRecord(record.metadata.id) },
            )
        }
    }
}

/** Appends the localised unit, for the types whose unit is a word rather than a symbol. */
@Composable
internal fun summaryWithUnit(spec: RecordTypeSpec<*>, record: Record): String {
    val value = spec.summaryOf(record)
    val unit = spec.summaryUnitRes?.let { stringResource(it) } ?: return value
    return "$value $unit"
}

/**
 * One stored record: its value, its full time span, and the app that wrote it.
 *
 * Shared with the span view so both lists describe a record identically -- the span and the
 * source are what make a legitimate whole-day summary legible next to itemised records from
 * another app.
 */
@Composable
internal fun RecordRow(
    spec: RecordTypeSpec<*>,
    record: Record,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = summaryWithUnit(spec, record),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = Formatting.timeSpan(spec.timeOf(record), spec.endTimeOf(record)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Every row names its writer. Several apps commonly write the same type -- one
        // itemising as it goes, another posting a daily summary -- so without the source a
        // legitimate whole-day record is indistinguishable from a duplicate or an error.
        Text(
            text = LocalContext.current.appLabelFor(spec.originOf(record)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
