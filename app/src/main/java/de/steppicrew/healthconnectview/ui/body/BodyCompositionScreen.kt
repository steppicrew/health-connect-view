package de.steppicrew.healthconnectview.ui.body

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.components.ExpandableChart
import de.steppicrew.healthconnectview.ui.components.InfoGroup
import de.steppicrew.healthconnectview.ui.components.LineChart
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.OnResume
import de.steppicrew.healthconnectview.ui.components.SpanSelector
import de.steppicrew.healthconnectview.ui.components.WindowStepper
import de.steppicrew.healthconnectview.ui.components.chartTitle
import de.steppicrew.healthconnectview.ui.components.windowLabel
import java.time.Instant

/**
 * Weight and its parts, one small chart each on the same time axis.
 *
 * Not one chart: on an axis from bone's 3 kg to weight's 80 kg, a fat change of half a
 * kilogram is a flat line, and that change is what this screen is opened for. Not stacked
 * either: water and bone are part of lean mass, so a stack would count them twice. Each chart
 * keeps its own scale, and the line above it says by how much the part moved in the window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyCompositionScreen(
    viewModel: BodyCompositionViewModel,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val span by viewModel.span.collectAsStateWithLifecycle()
    val offset by viewModel.offset.collectAsStateWithLifecycle()
    val historyGranted by viewModel.historyGranted.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start() }
    OnResume { viewModel.onResume() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.body_composition)) },
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            SpanSelector(selected = span, onSelect = viewModel::setSpan, spans = SPANS)
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
                    title = stringResource(R.string.body_composition_empty),
                    body = if (historyNeeded) stringResource(R.string.history_limit_note) else "",
                    actionLabel = stringResource(R.string.history_grant).takeIf { historyNeeded },
                    onAction = onOpenPermissions.takeIf { historyNeeded },
                )
                is UiState.Error -> MessageView(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.detail_error_title),
                    body = current.message,
                )
                is UiState.Data -> Parts(current.value, windowLabel(span, offset), historyNeeded, onOpenPermissions)
            }
        }
    }
}

@Composable
private fun Parts(data: BodyComposition, period: String, historyNeeded: Boolean, onOpenPermissions: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Which chart is touched and at what moment, so every other shows its value there too.
        var reading by remember(data) { mutableStateOf<Pair<Int, Instant>?>(null) }
        data.parts.forEachIndexed { index, part ->
            PartChart(
                part,
                data,
                period,
                linkedTime = reading?.takeIf { it.first != index }?.second,
                onSelectTime = { time -> reading = time?.let { index to it } ?: reading?.takeIf { it.first != index } },
            )
        }
        Text(
            text = stringResource(
                if (data.weekly) R.string.body_composition_note_weekly else R.string.body_composition_note,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
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

/** A part's latest value, its change over the window, and its own chart beneath. */
@Composable
private fun PartChart(
    part: BodyPart,
    data: BodyComposition,
    period: String,
    linkedTime: Instant?,
    onSelectTime: (Instant?) -> Unit,
) {
    val unit = " " + stringResource(part.unitRes)
    val first = part.points.first()
    val last = part.points.last()

    InfoGroup {
        Text(stringResource(part.labelRes), style = MaterialTheme.typography.titleSmall)
        Text(
            text = Formatting.number(last.value, CHANGE_DECIMALS) + unit,
            style = MaterialTheme.typography.headlineSmall,
        )
        // Since the window's first reading, named by its date: over an uneven gap a bare
        // change would read as a rate.
        Text(
            text = if (part.points.size > 1) {
                stringResource(R.string.context_change, Formatting.signed(last.value - first.value, CHANGE_DECIMALS) + unit, Formatting.date(first.time))
            } else {
                stringResource(R.string.body_composition_one)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (part.derived) {
            Text(
                text = stringResource(R.string.body_fat_mass_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExpandableChart(chartTitle(stringResource(part.labelRes), part.unitRes, period)) { expanded, onExpand ->
            LineChart(
                points = part.points,
                // Softened, monotone so it never overshoots a reading; the readings stay marked.
                smooth = true,
                unitRes = part.unitRes,
                valueDecimals = CHANGE_DECIMALS,
                markReadings = true,
                extent = data.extent,
                compactAxis = !expanded,
                fillHeight = true,
                onExpand = onExpand,
                holdSelection = expanded,
                // Inline only: full screen, one chart is read alone.
                linkedTime = if (expanded) null else linkedTime,
                onSelectTime = if (expanded) null else onSelectTime,
                modifier = if (expanded) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth().height(PART_CHART_HEIGHT.dp).padding(top = 8.dp)
                },
            )
        }
    }
}

private val SPANS = listOf(Span.WEEK, Span.MONTH, Span.YEAR)

/** Kilograms and percent alike to one place: a scale reads no finer. */
private const val CHANGE_DECIMALS = 1

private const val PART_CHART_HEIGHT = 140
