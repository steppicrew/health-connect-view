package de.steppicrew.healthconnectview.ui.cycle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Cycle
import de.steppicrew.healthconnectview.health.CycleDay
import de.steppicrew.healthconnectview.health.CycleStats
import de.steppicrew.healthconnectview.health.MIN_GAP_DAYS
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.OnResume
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.WindowStepper
import de.steppicrew.healthconnectview.ui.components.windowLabel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Every cycle in a year as one row, aligned on day 1.
 *
 * Aligning rows is what a calendar cannot do: it puts the same cycle day of every month in
 * one column, so a drifting cycle length, a period getting longer, or a temperature shift
 * arriving later each month becomes visible as a shape rather than something to count out.
 * Only what was recorded is drawn. Nothing is predicted -- no fertile window, no next
 * period -- because a read-only viewer has no business making a contraceptive claim.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleScreen(
    viewModel: CycleViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val offset by viewModel.offset.collectAsStateWithLifecycle()

    // Also the first load. Access can change in system settings while the app is away, and a
    // read attempted while backgrounded is refused, so a resume must read again either way.
    OnResume { viewModel.load() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cycle_title)) },
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
            WindowStepper(
                label = windowLabel(Span.YEAR, offset),
                canStepForward = offset > 0,
                onBack = viewModel::stepBack,
                onForward = viewModel::stepForward,
            )
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
                    body = stringResource(R.string.cycle_empty_body),
                )
                is UiState.Error -> MessageView(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.detail_error_title),
                    body = current.message,
                )
                is UiState.Data -> CycleContent(current.value)
            }
        }
    }
}

@Composable
private fun CycleContent(data: CycleData) {
    val columns = data.cycles.maxOf { it.days.size }.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    val temperatures = data.cycles.flatMap { cycle -> cycle.days.mapNotNull { it.temperature } }
    val scale = if (temperatures.isEmpty()) null else temperatures.min()..temperatures.max()

    LazyColumn {
        if (data.historyCapped) {
            item(key = "history_capped") {
                Note(stringResource(R.string.detail_history_capped), MaterialTheme.colorScheme.error)
            }
        }
        data.stats?.let { stats -> item(key = "stats") { StatsBlock(stats) } }
        item(key = "method") {
            Note(pluralStringResource(R.plurals.cycle_method, MIN_GAP_DAYS, MIN_GAP_DAYS))
            Note(stringResource(R.string.cycle_legend))
        }
        if (data.notGranted.isNotEmpty()) {
            item(key = "not_granted") {
                val names = data.notGranted.map { stringResource(it) }.joinToString(", ")
                Note(stringResource(R.string.cycle_not_granted, names))
            }
        }
        items(data.cycles, key = { it.start.toString() }) { cycle ->
            CycleRow(cycle, columns, scale)
        }
    }
}

@Composable
private fun StatsBlock(stats: CycleStats) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        StatLine(
            R.string.cycle_stat_length,
            pluralStringResource(R.plurals.cycle_days, stats.medianLength, stats.medianLength),
        )
        StatLine(
            R.string.cycle_stat_range,
            pluralStringResource(R.plurals.cycle_days_range, stats.longest, stats.shortest, stats.longest),
        )
        StatLine(
            R.string.cycle_stat_period,
            pluralStringResource(R.plurals.cycle_days, stats.medianPeriodLength, stats.medianPeriodLength),
        )
        StatLine(R.string.cycle_stat_completed, stats.completed.toString())
    }
}

@Composable
private fun StatLine(label: Int, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Note(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun CycleRow(cycle: Cycle, columns: Int, scale: ClosedFloatingPointRange<Double>?) {
    val length = cycle.length
    val lengthText = if (length != null) {
        pluralStringResource(R.plurals.cycle_days, length, length)
    } else {
        pluralStringResource(R.plurals.cycle_running, cycle.days.size, cycle.days.size)
    }
    val colors = RowColors(
        cell = MaterialTheme.colorScheme.surfaceVariant,
        test = MaterialTheme.colorScheme.tertiary,
        mucus = MaterialTheme.colorScheme.secondary,
        temperature = MaterialTheme.colorScheme.onSurface,
    )

    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = cycle.start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + " · " + lengthText,
            style = MaterialTheme.typography.labelMedium,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .padding(top = 2.dp),
        ) {
            drawCycle(cycle.days, columns, scale, colors)
        }
    }
}

private data class RowColors(val cell: Color, val test: Color, val mucus: Color, val temperature: Color)

private fun DrawScope.drawCycle(
    days: List<CycleDay>,
    columns: Int,
    scale: ClosedFloatingPointRange<Double>?,
    colors: RowColors,
) {
    val cell = size.width / columns
    val gap = (cell * CELL_GAP).coerceAtMost(2.dp.toPx())
    val band = size.height * BAND_HEIGHT
    val bandTop = (size.height - band) / 2
    val corner = CornerRadius(gap)

    days.take(columns).forEachIndexed { index, day ->
        val left = index * cell
        val centre = left + cell / 2
        drawRoundRect(
            color = if (day.bleeding) BLEEDING.copy(alpha = flowAlpha(day.flow)) else colors.cell,
            topLeft = Offset(left + gap / 2, bandTop),
            size = Size(cell - gap, band),
            cornerRadius = corner,
        )
        if (day.spotting) {
            drawCircle(BLEEDING, radius = cell * MARK_SIZE, center = Offset(centre, size.height - cell * MARK_SIZE))
        }
        if (day.mucus) {
            drawLine(
                colors.mucus,
                start = Offset(centre, bandTop + band),
                end = Offset(centre, size.height),
                strokeWidth = gap.coerceAtLeast(1f),
            )
        }
        day.ovulationTest?.let { result -> drawTest(result, centre, cell * MARK_SIZE * 1.4f, colors.test) }
    }

    if (scale != null) drawTemperature(days.take(columns), cell, bandTop, band, scale, colors.temperature)

    // A row longer than the grid is cut, not squeezed: squeezing would put day 30 of one
    // row under day 20 of another and break the column alignment the view exists for.
    if (days.size > columns) {
        val x = size.width - gap
        drawLine(colors.temperature, Offset(x, bandTop), Offset(x, bandTop + band), strokeWidth = gap * 2)
    }
}

/** Filled for a positive or high result, outlined for a test that did not come up positive. */
private fun DrawScope.drawTest(result: Int, centre: Float, half: Float, color: Color) {
    val triangle = Path().apply {
        moveTo(centre - half, 0f)
        lineTo(centre + half, 0f)
        lineTo(centre, half * 1.6f)
        close()
    }
    when (result) {
        OvulationTestRecord.RESULT_POSITIVE -> drawPath(triangle, color)
        OvulationTestRecord.RESULT_HIGH -> drawPath(triangle, color.copy(alpha = 0.5f))
        else -> drawPath(triangle, color, style = Stroke(width = half / 3))
    }
}

/**
 * Basal temperature across the row, on one scale for the whole screen so a shift in one
 * cycle can be compared with the next. Days without a reading break the line rather than
 * being bridged: a straight segment over a week of no data would look measured.
 */
private fun DrawScope.drawTemperature(
    days: List<CycleDay>,
    cell: Float,
    top: Float,
    height: Float,
    scale: ClosedFloatingPointRange<Double>,
    color: Color,
) {
    val spread = (scale.endInclusive - scale.start).takeIf { it > 0 } ?: 1.0
    var previous: Offset? = null
    days.forEachIndexed { index, day ->
        val value = day.temperature
        if (value == null) {
            previous = null
            return@forEachIndexed
        }
        val point = Offset(
            index * cell + cell / 2,
            top + height - ((value - scale.start) / spread).toFloat() * height,
        )
        previous?.let { drawLine(color, it, point, strokeWidth = 1.5.dp.toPx()) }
        drawCircle(color, radius = 1.5.dp.toPx(), center = point)
        previous = point
    }
}

/** Heavier flow is darker; bleeding with no stated flow sits in the middle. */
private fun flowAlpha(flow: Int?): Float = when (flow) {
    MenstruationFlowRecord.FLOW_LIGHT -> 0.35f
    MenstruationFlowRecord.FLOW_MEDIUM -> 0.65f
    MenstruationFlowRecord.FLOW_HEAVY -> 0.95f
    else -> 0.55f
}

/**
 * A fixed rose rather than a theme colour. Dynamic colour can make every scheme colour a
 * shade of the wallpaper, and bleeding drawn in teal would be read as something else.
 */
private val BLEEDING = Color(0xFFC2185B)

private val ROW_HEIGHT = 30.dp
private const val MIN_COLUMNS = 28
private const val MAX_COLUMNS = 45
private const val CELL_GAP = 0.15f
private const val BAND_HEIGHT = 0.5f
private const val MARK_SIZE = 0.22f
