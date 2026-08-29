package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventBusy
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.health.totalDuration
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.detail.RecordRow
import de.steppicrew.healthconnectview.ui.components.iconFor
import de.steppicrew.healthconnectview.ui.components.LineChart
import de.steppicrew.healthconnectview.ui.components.SessionTimeline
import de.steppicrew.healthconnectview.ui.components.SparkCurve
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.util.appLabelFor
import java.time.Duration
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

    Scaffold(
        modifier = modifier,
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
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SpanSelector(selected = span, onSelect = viewModel::setSpan)
            WindowStepper(
                label = windowLabel(span, offset),
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
                    curve = data.sessionCurves[session.start],
                    scale = data.sessionCurveScale,
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

        if (data.contributingApps.isNotEmpty()) {
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
                goalCrossing = data.goalCrossing,
                unitRes = data.spec.unitRes,
                emptyBuckets = data.emptyBuckets,
                sessions = data.sessions,
                colorScale = data.lineColorScale,
                extent = data.extent,
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
@Composable
private fun SessionRow(
    session: Session,
    curve: List<Point>?,
    scale: ClosedFloatingPointRange<Double>?,
    heartRateLocked: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        }

        when {
            curve != null && scale != null -> SparkCurve(
                points = curve,
                scale = scale,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SESSION_CURVE_HEIGHT.dp)
                    .padding(top = 8.dp),
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

private const val SESSION_ICON = 16
private const val SESSION_CURVE_HEIGHT = 40

/** The aggregate for a session type comes back in hours; durations format from minutes. */
private const val MINUTES_PER_HOUR = 60

@Composable
private fun titleFor(spec: RecordTypeSpec<*>?): String =
    spec?.let { stringResource(it.displayNameRes) } ?: ""

/**
 * The window being shown, derived from the span rather than from the loaded data.
 *
 * A day with nothing recorded still *is* a day, and naming it is what lets the user step to
 * another one: taking the label from the data left an empty screen with no date at all, and
 * therefore no clue which day had nothing in it.
 */
@Composable
private fun windowLabel(span: Span, offset: Int): String {
    val start = span.startDate(offset)
    val end = span.endDate(offset).minusDays(1)
    return if (start == end) {
        formatDate(start)
    } else {
        formatDate(start) + " – " + formatDate(end)
    }
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
