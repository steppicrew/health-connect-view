package de.steppicrew.healthconnectview.ui.dashboard

import androidx.annotation.StringRes
import de.steppicrew.healthconnectview.ui.components.SourceMark
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.produceState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.LaunchedEffect
import de.steppicrew.healthconnectview.export.ExportResult
import de.steppicrew.healthconnectview.ui.components.ExportAction
import de.steppicrew.healthconnectview.ui.components.Hypnogram
import de.steppicrew.healthconnectview.ui.components.InfoToggle
import de.steppicrew.healthconnectview.ui.components.rememberExplanation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Trend
import de.steppicrew.healthconnectview.health.TrendResult
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.health.totalDuration
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.detail.RecordRow
import de.steppicrew.healthconnectview.ui.components.iconFor
import de.steppicrew.healthconnectview.ui.components.AppIcon
import de.steppicrew.healthconnectview.ui.components.rememberAppIcon
import de.steppicrew.healthconnectview.ui.components.LineChart
import de.steppicrew.healthconnectview.ui.components.SessionTimeline
import de.steppicrew.healthconnectview.ui.components.SparkCurve
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.SpanSelector
import de.steppicrew.healthconnectview.ui.components.WindowStepper
import de.steppicrew.healthconnectview.ui.components.swipeToStep
import de.steppicrew.healthconnectview.ui.components.windowLabel
import de.steppicrew.healthconnectview.util.appLabelFor
import java.time.Duration

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
    val spec by viewModel.spec.collectAsStateWithLifecycle()
    var openSession by remember { mutableStateOf<Session?>(null) }

    openSession?.let { session ->
        SessionSheet(
            session = session,
            loadStats = { viewModel.statisticsFor(it) },
            onDismiss = { openSession = null },
        )
    }
    val offset by viewModel.offset.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.exportResults.collect { result ->
            snackbar.showSnackbar(
                when (result) {
                    is ExportResult.Written ->
                        resources.getQuantityString(R.plurals.export_done, result.rows, result.rows)
                    ExportResult.Failed -> resources.getString(R.string.export_failed)
                },
            )
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(titleFor(spec)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    spec?.let { current ->
                        ExportAction(
                            fileBase = exportFileBase(current, span, offset),
                            // Not for sessions: Health Connect's daily sleep total cuts nights at
                            // midnight, while the app credits a night to the morning it ended --
                            // a file would give a second answer to "how long did I sleep".
                            dailyAvailable = current.aggregate != null &&
                                current.tile.form != TileSpec.Form.SESSIONS,
                            onExport = viewModel::export,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .swipeToStep(offset > 0, viewModel::stepBack, viewModel::stepForward),
        ) {
            SpanSelector(selected = span, onSelect = viewModel::setSpan)
            WindowStepper(
                label = windowLabel(span, offset),
                canStepForward = offset > 0,
                onBack = viewModel::stepBack,
                onForward = viewModel::stepForward,
            )

            when (val current = state) {
                is UiState.Loading -> LoadingView(progress = progress)

                is UiState.NoPermission -> MessageView(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.detail_no_permission_title),
                    body = stringResource(R.string.detail_no_permission_body),
                )

                // Deliberately not the padlock: "nothing was recorded" and "not allowed to
                // look" are the distinction UiState draws, and sharing an icon collapses it
                // on the one screen where the difference is actionable.
                is UiState.Empty -> MessageView(
                    icon = Icons.Default.EventBusy,
                    title = stringResource(R.string.detail_empty_title),
                    body = stringResource(R.string.detail_empty_body),
                )

                is UiState.Error -> MessageView(
                    icon = Icons.Default.ErrorOutline,
                    title = stringResource(R.string.detail_error_title),
                    body = current.message,
                )

                is UiState.Data -> SpanContent(
                    data = current.value,
                    onSelectSource = viewModel::selectSource,
                    onOpenSession = { openSession = it },
                    loadCurve = viewModel::curveFor,
                )
            }
        }
    }
}

@Composable
private fun SpanContent(
    data: TileDetailData,
    onSelectSource: (String?) -> Unit,
    onOpenSession: (Session) -> Unit,
    loadCurve: suspend (Session) -> List<Point>?,
) {
    LazyColumn {
        item(key = "summary") { SpanSummary(data, onSelectSource, onOpenSession) }

        // A session type's own screen: the sessions are the content, not context behind a
        // chart, so they get a row each with the heart rate recorded during them.
        if (data.spec.tile.form == TileSpec.Form.SESSIONS) {
            item(key = "sessions_header") {
                Column {
                    Text(
                        text = pluralStringResource(
                            R.plurals.sessions_detail_header,
                            data.sessions.size,
                            data.sessions.size,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    // The count answers "how many"; the sum answers "how much of the day",
                    // and on a week or a month that is the figure being looked for.
                    if (data.sessions.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.sessions_total_duration,
                                Formatting.duration(data.sessions.totalDuration()),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    Text(
                        text = stringResource(
                            if (data.heartRateLocked) {
                                R.string.sessions_locked_heart_rate
                            } else {
                                R.string.sessions_curve_note
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            items(data.sessions, key = { it.start.toString() }) { session ->
                SessionRow(
                    session = session,
                    loadCurve = loadCurve,
                    zones = data.sessionCurveZones,
                    heartRateUnitRes = data.sessionCurveUnitRes,
                    heartRateLocked = data.heartRateLocked,
                    onClick = { onOpenSession(session) },
                )
            }
        }

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

        if (data.listPending) {
            item(key = "records_pending") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.detail_records_loading),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        } else item(key = "records_header") {
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

/**
 * What the tile's arrow means, with the numbers behind it.
 *
 * On the phone the arrow on floors pointed up on a day with 4 climbed after a day with 10,
 * and read as wrong: it compares a week with a month, not today with yesterday, and leaves
 * today out altogether. Saying so, with both averages, is what makes the arrow checkable.
 */
@Composable
private fun TrendExplanation(trend: TrendResult, @StringRes unitRes: Int?) {
    val unit = unitRes?.let { " " + stringResource(it) }.orEmpty()
    val explanation = rememberExplanation("trend")

    Column(Modifier.padding(top = 8.dp)) {
        // The headline and the averages always show: they are the data. The rule is behind the
        // "i", closed until asked for, so the numbers are what is seen at first glance.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    when (trend.direction) {
                        Trend.UP -> R.string.trend_title_up
                        Trend.FLAT -> R.string.trend_title_flat
                        Trend.DOWN -> R.string.trend_title_down
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            InfoToggle(explanation)
        }
        Text(
            text = stringResource(
                R.string.trend_numbers,
                Formatting.number(trend.recent) + unit,
                Formatting.number(trend.baseline) + unit,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (explanation.expanded == true) {
            Text(
                text = stringResource(R.string.trend_rule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Steps_2026-09-20_2026-09-26": type and the window's first and last day. */
private fun exportFileBase(spec: RecordTypeSpec<*>, span: Span, offset: Int): String =
    spec.type.simpleName.orEmpty().removeSuffix("Record") + "_" + span.startDate(offset) + "_" +
        span.endDate(offset).minusDays(1)

@Composable
private fun SpanSummary(
    data: TileDetailData,
    onSelectSource: (String?) -> Unit,
    onOpenSession: (Session) -> Unit,
) {
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
                // A session type's total is a duration in hours, and a quarter-hour session
                // read as a bare "0,25" -- a fraction of nothing. Everything else keeps the
                // number, with its unit appended: "1.792" alone was equally unlabelled, just
                // less obviously so.
                text = if (data.spec.tile.form == TileSpec.Form.SESSIONS) {
                    Formatting.duration(Duration.ofMinutes((total * MINUTES_PER_HOUR).toLong()))
                } else {
                    Formatting.number(total) +
                        (data.spec.unitRes?.let { " " + stringResource(it) } ?: "")
                },
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        // Reading a dashed line against a curve is fiddly; say the answer in words too.
        data.goal?.let { goal ->
            val reached = (data.total ?: 0.0) >= goal
            Text(
                text = if (reached) {
                    // The time is the point of the badge: that the goal was met is already
                    // visible from the curve crossing the line.
                    data.goalCrossing?.let { crossing ->
                        stringResource(
                            R.string.chart_goal_reached_at,
                            Formatting.number(goal),
                            Formatting.time(crossing),
                        )
                    } ?: stringResource(R.string.chart_goal_reached, Formatting.number(goal))
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

        // Beside the day's own total, which is what the tile's arrow was misread against.
        data.trend?.let { TrendExplanation(it, data.spec.unitRes) }

        if (data.listPending || data.contributingApps.isNotEmpty()) {
            SourceSection(data = data, onSelectSource = onSelectSource)
        }

        // A session type draws its sessions rather than a series: see SessionTimeline for why
        // the aggregate makes no chart worth showing.
        if (data.spec.tile.form == TileSpec.Form.SESSIONS && data.extent != null) {
            SessionTimeline(
                sessions = data.sessions,
                extent = data.extent,
                modifier = Modifier.padding(top = 16.dp),
            )
            // The timeline is a chart with no series, so it falls outside the legend below --
            // which is drawn from `points`, and a session type's day has none. This is the
            // screen the shaded bands were actually reported on, so it is the last one that
            // should go unlabelled.
            ChartLegend(data = data)
        }

        if (data.points.isNotEmpty()) {
            LineChart(
                points = data.points,
                // Safe to smooth now that each rise spans the interval the record actually
                // covered: the curve rounds the corners of a real ramp rather than inventing
                // a slope where the data says a vertical jump. The clamp keeps every segment
                // within the two values it joins, so a plateau cannot bulge.
                smooth = data.spec.tile.smoothChart && !data.bars,
                bars = data.bars,
                rangeBand = data.rangeBand,
                stack = data.stack,
                goal = data.goal,
                goalCrossing = data.goalCrossing,
                unitRes = data.spec.unitRes,
                emptyBuckets = data.emptyBuckets,
                sessions = data.sessions,
                zones = data.lineZones,
                markReadings = data.spec.tile.markReadings,
                integral = data.spec.tile.integralValues,
                extent = data.extent,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(
                    when {
                        // Counting sessions is not summing a metric, so "daily totals" would
                        // name the wrong operation.
                        data.sessionCounts -> R.string.chart_source_sessions_per_day
                        data.approximated -> R.string.chart_source_cumulative_scaled
                        data.cumulative -> R.string.chart_source_cumulative
                        data.aggregated && data.weeklyBuckets ->
                            R.string.chart_source_aggregated_weekly
                        // "Totals" is wrong for a mean, and doubly so with a spread drawn
                        // behind it: the line is the day's average, not its sum.
                        data.aggregated && data.rangeBand.isNotEmpty() ->
                            R.string.chart_source_aggregated_range
                        data.aggregated && data.stack.isNotEmpty() ->
                            R.string.chart_source_aggregated_split
                        data.aggregated -> R.string.chart_source_aggregated
                        else -> R.string.chart_source_raw
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Everything drawn on the chart, named. The caption above says which *operation*
            // produced the series; this says which *mark* is which, which is a different
            // question and the one a shaded band raises -- the sleep ribbon behind a heart
            // rate was reported as simply unexplained.
            ChartLegend(data = data)

            // The bands are context; naming them is what turns a shaded region into
            // "that peak was the bike ride". Only where the sessions sit *behind* a chart --
            // on a session type's own screen they are the content, listed in full below.
            if (data.spec.tile.form != TileSpec.Form.SESSIONS) {
                data.sessions.forEach { session ->
                    SessionCaption(session = session, onClick = { onOpenSession(session) })
                }
            }

            // Naming the writer the shape came from: the total is everyone's, the path is
            // one device's, and leaving that unsaid would be a silent substitution.
            data.shapeSource?.let { writer ->
                Text(
                    text = stringResource(
                        R.string.chart_shape_from,
                        LocalContext.current.appLabelFor(writer),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // A break in the line is only unambiguous once it is named; without this it reads
            // as a rendering artefact rather than as an absence of data.
            if (data.emptyBuckets.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        R.plurals.chart_gaps,
                        data.emptyBuckets.size,
                        data.emptyBuckets.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One session named on a single line, under the chart it explains. */
/**
 * Names every mark on the chart: the series itself, and whatever is drawn behind or across it.
 *
 * The caption above the legend says which *operation* produced the numbers ("daily totals,
 * deduplicated"); this says what each *shape* is. They answer different questions, and the
 * second one went unanswered: the pale blue sleep ribbon behind a chart was reported as
 * unexplained, since nothing on screen connected it to sleep.
 *
 * Only what is actually drawn is listed. A legend naming a mark the chart does not have is
 * worse than none -- it sends the reader looking for something that is not there.
 */
@Composable
private fun ChartLegend(data: TileDetailData) {
    val sleepShown = data.sessions.any { it.kind == Session.Kind.SLEEP }
    val exerciseShown = data.sessions.any { it.kind == Session.Kind.EXERCISE }
    val stacked = data.stack.isNotEmpty() && data.stackLabels.isNotEmpty()

    // The series' own name depends on what a point means, which is exactly what the mark
    // already encodes: a bar is a bucket's total, a line is a reading.
    val seriesLabel = when {
        // No series to name: a session type's day is the timeline alone, whose bands are
        // named below like any other.
        data.points.isEmpty() -> null
        stacked -> null // The stack's own segments are named below; a total above them is noise.
        data.sessionCounts -> R.string.legend_value_sessions
        data.spec.tile.form == TileSpec.Form.SESSIONS && data.bars ->
            R.string.legend_value_sleep_hours
        data.bars && data.weeklyBuckets -> R.string.legend_value_bars_weekly
        data.bars -> R.string.legend_value_bars
        data.cumulative -> R.string.legend_value_cumulative
        data.rangeBand.isNotEmpty() -> R.string.legend_value_mean
        else -> R.string.legend_value_line
    }

    // Nothing worth naming: a plain line with no goal, no band and no sessions behind it is
    // already self-explanatory, and a legend of one entry repeating the caption is clutter.
    //
    // A shaded band is the exception, and always names itself even when it is the only entry.
    // It is the one mark with no other explanation on screen -- a line has an axis and a
    // caption, a bar has both plus its own label, but a pale rectangle behind them has
    // nothing. The sleep timeline is exactly that case: a single band, reported as
    // unexplained, which the "more than one entry" rule would have gone on suppressing.
    val bandShown = sleepShown || exerciseShown || data.rangeBand.isNotEmpty()
    val entries = (if (seriesLabel != null) 1 else 0) +
        (if (stacked) data.stackLabels.size else 0) +
        listOf(
            data.rangeBand.isNotEmpty(),
            sleepShown,
            exerciseShown,
            data.goal != null,
            data.spec.tile.markReadings,
        ).count { it }
    if (entries < 2 && !bandShown) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        seriesLabel?.let { LegendEntry(color = MaterialTheme.colorScheme.primary, label = it) }

        // Named in the same order and weight as the segments they stand for: a stack is one
        // hue at rising weights, deliberately not separate colours, so nothing else on screen
        // says which weight is which.
        if (stacked) {
            data.stackLabels.forEachIndexed { index, label ->
                LegendEntry(
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = LEGEND_ALPHA_MIN + (1f - LEGEND_ALPHA_MIN) *
                            (index + 1).toFloat() / data.stackLabels.size.toFloat(),
                    ),
                    label = label,
                )
            }
        }

        if (data.rangeBand.isNotEmpty()) {
            LegendEntry(
                color = MaterialTheme.colorScheme.primary.copy(alpha = LEGEND_BAND_ALPHA),
                label = R.string.legend_range,
            )
        }
        if (sleepShown) {
            LegendEntry(color = LEGEND_SLEEP, label = R.string.legend_sleep)
        }
        if (exerciseShown) {
            LegendEntry(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = LEGEND_BAND_ALPHA),
                label = R.string.legend_exercise,
            )
        }
        if (data.goal != null) {
            LegendEntry(color = MaterialTheme.colorScheme.tertiary, label = R.string.legend_goal)
        }
        if (data.spec.tile.markReadings) {
            LegendEntry(
                color = MaterialTheme.colorScheme.primary,
                label = R.string.legend_readings,
            )
        }
    }
}

/** One swatch and its name. */
@Composable
private fun LegendEntry(color: Color, @StringRes label: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Alpha for a band's swatch.
 *
 * Deliberately stronger than the chart's own 0.16: a band covers a wide stretch of plot and
 * reads clearly at that weight, while the same alpha in a 10dp square is a tint barely
 * distinguishable from the surface -- (227,231,254) against (253,251,255). The swatch has to
 * be identifiable as a colour first and an exact match second, so it is the same hue at a
 * weight that survives being small.
 */
private const val LEGEND_BAND_ALPHA = 0.45f

/**
 * The chart's fixed sleep blue. Not a theme colour, for the same reason the band is not: it
 * means night, and under dynamic colour a themed hue drifts with the wallpaper.
 */
private val LEGEND_SLEEP = Color(0xFF5C7CFA).copy(alpha = LEGEND_BAND_ALPHA)

/** Matches the chart's own stack weighting, so the swatch reads as the segment it names. */
private const val LEGEND_ALPHA_MIN = 0.35f

@Composable
private fun SessionCaption(session: Session, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = iconFor(session),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SESSION_ICON.dp),
        )
        Text(
            text = listOfNotNull(
                sessionTitle(session),
                stringResource(
                    R.string.session_span,
                    Formatting.time(session.start),
                    Formatting.time(session.end),
                ),
            ).joinToString("  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One session as a row on a session type's own screen: what it was, when, how long, and the
 * heart rate recorded during it.
 *
 * The curve is the reason this is a row rather than a caption. A session is a span with no
 * value of its own -- the readings that describe it are separate types over the same window --
 * so showing them here is what turns "a 53-minute activity" into something you can read.
 */
private sealed interface CurveLoad {
    data object Loading : CurveLoad
    data class Done(val points: List<Point>?) : CurveLoad
}

@Composable
private fun SessionRow(
    session: Session,
    loadCurve: suspend (Session) -> List<Point>?,
    zones: ValueZones?,
    @StringRes heartRateUnitRes: Int?,
    heartRateLocked: Boolean,
    onClick: () -> Unit,
) {
    // Deliberately not clickable as a whole any more: the chart below needs the touches for
    // its own readout, and a row that both scrubs a curve and opens a dialog would do the
    // wrong one about half the time. The statistics moved onto the info button instead.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = iconFor(session),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = sessionTitle(session) ?: stringResource(R.string.session_untitled),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(
                        R.string.session_span,
                        Formatting.time(session.start),
                        Formatting.time(session.end),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = Formatting.duration(session.duration),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.session_statistics),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Stages first, where the writer recorded them: for a night they are what is being
        // asked about, and the heart rate below is the context.
        if (session.stages.isNotEmpty()) {
            Hypnogram(
                stages = session.stages,
                start = session.start,
                end = session.end,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        // Read when the row is shown rather than for every session up front; see curveFor.
        // Nothing is drawn until it arrives, so a row never claims "no heart rate recorded"
        // for a curve that is merely still loading.
        val curveLoad by produceState<CurveLoad>(CurveLoad.Loading, session, heartRateLocked) {
            value = CurveLoad.Done(if (heartRateLocked) null else loadCurve(session))
        }
        val curve = (curveLoad as? CurveLoad.Done)?.points

        if (curveLoad is CurveLoad.Done) when {
            // The full chart rather than a spark line: at this size the samples are dense
            // enough to be worth reading individually, and the readout answers "what was my
            // rate at that dip" -- which a curve you cannot touch only poses.
            curve != null -> LineChart(
                points = curve,
                smooth = false,
                unitRes = heartRateUnitRes,
                zones = zones,
                // Never on a session curve: it is heart rate at full resolution by
                // definition, so every sample would carry a dot.
                markReadings = false,
                // The curve is heart rate whatever type opened the session.
                integral = true,
                extent = session.start..session.end,
            )

            // Distinct explanations for the same blank space: nothing was recorded, versus
            // the app is not allowed to look. The locked case is said once for the whole
            // list rather than repeated on every row.
            !heartRateLocked -> Text(
                text = stringResource(R.string.sessions_curve_missing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** A session's own name, falling back to "Sleep" for a night no app bothered to title. */
@Composable
private fun sessionTitle(session: Session): String? =
    session.title
        ?: stringResource(R.string.session_sleep).takeIf { session.kind == Session.Kind.SLEEP }

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
/**
 * A compact selectable chip for the source picker.
 *
 * Material's filter chip pads its content by 16dp a side, which for a 20dp app icon made each
 * chip three times the width of what it shows, and five writers ran to two lines. Here the
 * padding is 8dp; the touch target stays at the accessible minimum, and the selected state is
 * announced as with any chip.
 */
@Composable
private fun SourceChip(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Box(
            modifier = Modifier
                .height(SOURCE_CHIP_HEIGHT.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun SourceSection(data: TileDetailData, onSelectSource: (String?) -> Unit) {
    val context = LocalContext.current
    val sources = data.contributingApps.sortedBy { context.appLabelFor(it) }

    val explanation = rememberExplanation("sources")

    // One outlined box for chips, "i" and explanation, so they read as one control -- and so
    // the space is already taken while the writers are still being read, and the chart below
    // does not jump when they arrive.
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (data.listPending) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SOURCE_PLACEHOLDER_HEIGHT.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                return@Column
            }
            if (sources.size > 1) {
                Row(verticalAlignment = Alignment.Top) {
                    // Wraps rather than scrolls: with four or five writers a scrolling row hid the
                    // last ones off the edge, and nothing said there were more.
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SourceChip(
                            selected = data.selectedSource == null,
                            onClick = { onSelectSource(null) },
                        ) {
                            Text(stringResource(R.string.source_all), style = MaterialTheme.typography.labelLarge)
                        }
                        sources.forEach { packageName ->
                            SourceChip(
                                selected = data.selectedSource == packageName,
                                onClick = { onSelectSource(packageName) },
                            ) {
                                // The app's own icon where it has one, which fits several sources
                                // on screen at once where "Garmin Connect" and "Health Sync"
                                // already ran off the edge. The label stays as the icon's content
                                // description, and as the visible text wherever no icon can be had.
                                SourceMark(packageName, SOURCE_ICON, SOURCE_ICON_PX) {
                                    Text(context.appLabelFor(packageName), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    // The chips are the data; what choosing one means is the explanation.
                    InfoToggle(explanation)
                }
            }

            // With a single writer there is nothing to choose and nothing to explain: naming it
            // is the data itself, so it always shows.
            if (sources.size == 1 || explanation.expanded == true) {
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
            }

            // The overlap winner is Health Connect's own priority setting, not ours to define.
            if (sources.size > 1 && explanation.expanded == true) {
                Text(
                    text = stringResource(R.string.source_priority_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private const val SESSION_ICON = 16
private const val SESSION_CURVE_HEIGHT = 40

/** Big enough to recognise a brand mark, small enough that several chips fit a phone width. */
private const val SOURCE_ICON = 20
private const val SOURCE_CHIP_HEIGHT = 32

/** Roughly one row of chips, so the box keeps its size while the writers load. */
private const val SOURCE_PLACEHOLDER_HEIGHT = 48
private const val SOURCE_ICON_PX = 64

/** The aggregate for a session type comes back in hours; durations format from minutes. */
private const val MINUTES_PER_HOUR = 60

@Composable
private fun titleFor(spec: RecordTypeSpec<*>?): String =
    spec?.let { stringResource(it.displayNameRes) } ?: ""
