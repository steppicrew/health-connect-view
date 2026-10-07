package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.firstLineTextInset
import de.steppicrew.healthconnectview.ui.record.RecordDetailOverlay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Route
import androidx.health.connect.client.records.Record
import de.steppicrew.healthconnectview.registry.deviceName
import de.steppicrew.healthconnectview.registry.DeviceKind
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import android.content.Intent
import android.content.ActivityNotFoundException
import java.time.Instant
import de.steppicrew.healthconnectview.ui.components.DotText
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import de.steppicrew.healthconnectview.settings.SettingsStore
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import de.steppicrew.healthconnectview.health.HrvSummary
import de.steppicrew.healthconnectview.health.HrvStanding
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import de.steppicrew.healthconnectview.export.ExportPeriod
import de.steppicrew.healthconnectview.export.ExportResult
import de.steppicrew.healthconnectview.export.Exporter
import de.steppicrew.healthconnectview.ui.components.ExportAction
import de.steppicrew.healthconnectview.ui.components.Hypnogram
import de.steppicrew.healthconnectview.ui.components.InfoToggle
import de.steppicrew.healthconnectview.ui.components.rememberExplanation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.EmojiEvents
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
import de.steppicrew.healthconnectview.health.pressureCategory
import de.steppicrew.healthconnectview.health.labelRes
import de.steppicrew.healthconnectview.health.PressureCategory
import kotlin.math.roundToInt
import de.steppicrew.healthconnectview.health.PartAverage
import de.steppicrew.healthconnectview.health.DayPartSplit
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.StreakSummary
import de.steppicrew.healthconnectview.health.Trend
import de.steppicrew.healthconnectview.health.TrendResult
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.health.totalDuration
import de.steppicrew.healthconnectview.health.Span
import java.time.LocalDate
import de.steppicrew.healthconnectview.registry.readingGap
import de.steppicrew.healthconnectview.registry.segmentAtGaps
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Quantity
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.detail.RecordRow
import de.steppicrew.healthconnectview.ui.components.iconFor
import de.steppicrew.healthconnectview.ui.components.sessionName
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Star
import androidx.health.connect.client.records.ExerciseSessionRecord
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
import androidx.compose.material.icons.filled.MilitaryTech
import de.steppicrew.healthconnectview.health.PersonalRecord
import de.steppicrew.healthconnectview.health.RecordKind
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import de.steppicrew.healthconnectview.ui.components.OnResume
import de.steppicrew.healthconnectview.ui.components.InfoGroup
import de.steppicrew.healthconnectview.ui.components.REFERENCE_COLOR
import de.steppicrew.healthconnectview.health.Suggestion
import de.steppicrew.healthconnectview.ui.components.ShowExportResults
import androidx.compose.material3.TextButton

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
    /** The permission screen, where access to data older than 30 days is allowed. */
    onOpenPermissions: () -> Unit = {},
    /** A session to open once loaded: an ISO instant it runs at, or "route"; see the nav route. */
    openSession: String = "",
    /** The session screen, for a session tapped anywhere on this one. */
    onOpenSession: (Session) -> Unit = {},
    /** The year's workouts by kind, offered on the workouts type only. */
    onOpenWorkouts: () -> Unit = {},
) {
    val requested = openSession
    val state by viewModel.state.collectAsStateWithLifecycle()
    val span by viewModel.span.collectAsStateWithLifecycle()
    val spec by viewModel.spec.collectAsStateWithLifecycle()
    // Saveable: back from the session screen this composes afresh, and a plain flag would
    // open the requested session again at once, trapping the screen in a loop.
    var opened by rememberSaveable { mutableStateOf(false) }
    var openRecord by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state, requested) {
        if (requested.isEmpty() || opened) return@LaunchedEffect
        val sessions = (state as? UiState.Data)?.value?.sessions ?: return@LaunchedEffect
        val at = runCatching { Instant.parse(requested) }.getOrNull()
        sessions.firstOrNull { if (at != null) at >= it.start && at < it.end else requested == "route" && it.route != null }?.let {
            opened = true
            onOpenSession(it)
        }
    }

    val offset by viewModel.offset.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val emptyRecord by viewModel.emptyRecord.collectAsStateWithLifecycle()
    val latestBefore by viewModel.latestBefore.collectAsStateWithLifecycle()
    // Back from the permission screen with older data allowed, the capped window is re-read.
    OnResume { viewModel.onResume() }
    val historyGranted by viewModel.historyGranted.collectAsStateWithLifecycle()
    val coverage by viewModel.coverage.collectAsStateWithLifecycle()
    val sourceDefault by viewModel.sourceDefault.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ShowExportResults(viewModel.exportResults, snackbar)

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
                    if (spec?.type == ExerciseSessionRecord::class) {
                        IconButton(onClick = onOpenWorkouts) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.workouts_by_kind),
                            )
                        }
                    }
                    spec?.let { current ->
                        ExportAction(
                            typeName = current.type.simpleName.orEmpty().removeSuffix("Record"),
                            shown = ExportPeriod(span.startDate(offset), span.endDate(offset).minusDays(1)),
                            historyGranted = historyGranted,
                            // Not for sessions: Health Connect's daily sleep total cuts nights at
                            // midnight, while the app credits a night to the morning it ended --
                            // a file would give a second answer to "how long did I sleep".
                            dailyAvailable = current.aggregate != null &&
                                current.tile.form != TileSpec.Form.SESSIONS,
                            reportAvailable = current.type in Exporter.REPORT_TYPES,
                            canExport = viewModel::canExport,
                            onExport = viewModel::export,
                            renderReport = viewModel::renderReport,
                            onNoViewer = viewModel::reportNoViewer,
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
                onNow = viewModel::stepToNow,
            )

            // Read by every note that depends on how far back the app may look.
            CompositionLocalProvider(
                LocalHistoryAccess provides HistoryAccess(historyGranted, onOpenPermissions),
                LocalSourceCompare provides SourceCompare(coverage, viewModel::compareSources, viewModel::selectSource),
                LocalSourceDefault provides SourceDefaultAction(sourceDefault, viewModel::useDefaultSource),
                LocalDayJump provides DayJump(
                    shown = span.startDate(offset).takeIf { span == Span.DAY },
                    onShow = viewModel::showDay,
                ),
            ) {
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
                    is UiState.Empty -> Column(Modifier.fillMaxSize()) {
                        val latest = latestBefore
                        MessageView(
                            icon = Icons.Default.EventBusy,
                            title = stringResource(R.string.detail_empty_title),
                            body = stringResource(R.string.detail_empty_body),
                            modifier = Modifier.weight(1f),
                            // Not by skipping empty windows on the arrows, which must stay one
                            // window per tap: an offer to go where the data is.
                            actionLabel = latest?.let {
                                val zone = HealthRepository.DEFAULT_ZONE
                                stringResource(R.string.detail_latest_value, Formatting.date(it.atStartOfDay(zone).toInstant(), zone))
                            },
                            onAction = latest?.let { date -> { viewModel.showDate(date) } },
                        )
                        val shownSpec = spec
                        val record = emptyRecord
                        if (record != null && shownSpec != null) {
                            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                                RecordExplanation(record, shownSpec)
                            }
                        }
                    }

                    is UiState.Error -> MessageView(
                        icon = Icons.Default.ErrorOutline,
                        title = stringResource(R.string.detail_error_title),
                        body = current.message,
                    )

                    is UiState.Data -> SpanContent(
                        data = current.value,
                        onSelectSource = viewModel::selectSource,
                        onOpenSession = onOpenSession,
                        loadCurve = viewModel::curveFor,
                        onVisibleRange = viewModel::showListFor,
                        onOpenRecord = { openRecord = it },
                    )
                }
            }
        }
    }
    val data = (state as? UiState.Data)?.value
    RecordDetailOverlay(data?.spec, data?.records.orEmpty(), openRecord) { openRecord = null }
}

@Composable
private fun SpanContent(
    data: TileDetailData,
    onSelectSource: (String?) -> Unit,
    onOpenSession: (Session) -> Unit,
    loadCurve: suspend (Session) -> List<Point>?,
    onVisibleRange: (ClosedRange<Instant>?) -> Unit,
    onOpenRecord: (String) -> Unit,
) {
    LazyColumn {
        item(key = "summary") { SpanSummary(data, onSelectSource, onOpenSession, onVisibleRange) }

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

        // Only over the whole window: zoomed, the header already says the list is the stretch.
        if (data.truncated && data.listRange == null) {
            item(key = "truncated") {
                // The header below says how many; this says how to reach the rest.
                Text(
                    text = stringResource(R.string.detail_list_zoom_hint),
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
            val shown = data.records.size
            val total = data.recordCount
            Text(
                text = when {
                    data.listRange != null && data.truncated ->
                        pluralStringResource(R.plurals.detail_records_in_range_capped, shown, shown)
                    data.listRange != null ->
                        pluralStringResource(R.plurals.detail_records_in_range, shown, shown)
                    // The exact count where the chart's read counted the window anyway.
                    data.truncated && total != null && total > shown -> pluralStringResource(
                        R.plurals.detail_records_of,
                        total,
                        Formatting.integer(shown.toLong()),
                        Formatting.integer(total.toLong()),
                    )
                    data.truncated -> pluralStringResource(R.plurals.detail_records_of_more, shown, shown)
                    else -> pluralStringResource(R.plurals.detail_records_header, shown, shown)
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        items(data.records, key = { it.metadata.id }) { record ->
            RecordRow(spec = data.spec, record = record, onClick = { onOpenRecord(record.metadata.id) })
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
private fun TrendExplanation(trend: TrendResult, @StringRes unitRes: Int?, decimals: Int? = null) {
    val unit = unitRes?.let { " " + stringResource(it) }.orEmpty()
    val explanation = rememberExplanation("trend")

    InfoGroup {
        // The headline and the averages always show: they are the data. The rule is behind the
        // "i", closed until asked for, so the numbers are what is seen at first glance.
        Row(verticalAlignment = Alignment.Top) {
            // The tile's arrow, so the direction reads at a glance before the words do. The
            // title beside it says the same, so the icon needs no description of its own.
            Icon(
                imageVector = trend.direction.icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp, top = firstLineInset(MaterialTheme.typography.titleSmall, 24.dp)),
            )
            Text(
                text = stringResource(
                    when (trend.direction) {
                        Trend.UP -> R.string.trend_title_up
                        Trend.FLAT -> R.string.trend_title_flat
                        Trend.DOWN -> R.string.trend_title_down
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
                // The 24 dp icon is taller than the line, so the text comes down to its middle.
                modifier = Modifier.weight(1f).padding(top = firstLineTextInset(MaterialTheme.typography.titleSmall, 24.dp)),
            )
            InfoToggle(
                explanation,
                Modifier.padding(top = firstLineTextInset(MaterialTheme.typography.titleSmall, 24.dp)),
                firstLine = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            text = stringResource(
                R.string.trend_numbers,
                Formatting.number(trend.recent, decimals) + unit,
                Formatting.number(trend.baseline, decimals) + unit,
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

/**
 * The tile's streak in words, with the year's longest run and the rule behind the "i": that a
 * day without data bridges a goal's run rather than breaking it is what a reader cannot guess,
 * and what makes a count after a forgotten watch believable. Nothing where neither run reaches
 * two days.
 */
@Composable
private fun StreakExplanation(summary: StreakSummary, active: Boolean) {
    val longest = summary.longest?.takeIf { it.count >= MIN_STREAK }
    val current = summary.current.takeIf { it >= MIN_STREAK }
    val title = when {
        current != null -> pluralStringResource(streakPlural(active), current, current)
        longest != null -> pluralStringResource(R.plurals.streak_longest_title, longest.count, longest.count)
        else -> return
    }
    val explanation = rememberExplanation("streak")
    val zone = HealthRepository.DEFAULT_ZONE
    val range = longest?.let {
        stringResource(
            R.string.streak_range,
            Formatting.date(it.first.atStartOfDay(zone).toInstant(), zone),
            Formatting.date(it.last.atStartOfDay(zone).toInstant(), zone),
        )
    }
    InfoGroup {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp, top = firstLineInset(MaterialTheme.typography.titleSmall, 24.dp)),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                // The 24 dp icon is taller than the line, so the text comes down to its middle.
                modifier = Modifier.weight(1f).padding(top = firstLineTextInset(MaterialTheme.typography.titleSmall, 24.dp)),
            )
            InfoToggle(
                explanation,
                Modifier.padding(top = firstLineTextInset(MaterialTheme.typography.titleSmall, 24.dp)),
                firstLine = MaterialTheme.typography.titleSmall,
            )
        }
        when {
            // The current run is the year's longest: saying both would repeat the number.
            current != null && (longest == null || current >= longest.count) ->
                stringResource(R.string.streak_is_longest)
            current != null && longest != null ->
                pluralStringResource(R.plurals.streak_longest, longest.count, longest.count, range.orEmpty())
            else -> range
        }?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        if (explanation.expanded == true) {
            Text(
                text = stringResource(if (active) R.string.streak_active_rule else R.string.streak_rule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HistoryLimitNote()
        }
    }
}

/** Whether reads reach back past 30 days, and the way to allow it where they do not. */
private class HistoryAccess(val granted: Boolean, val onGrant: () -> Unit)

private val LocalHistoryAccess = staticCompositionLocalOf { HistoryAccess(granted = true, onGrant = {}) }

/** The day on screen when one day is shown, else null; and how to open another day. */
private class DayJump(val shown: LocalDate?, val onShow: (LocalDate) -> Unit)

private val LocalDayJump = staticCompositionLocalOf { DayJump(shown = null, onShow = {}) }

/** The source comparison's state, how to start it, and how to pick the app it names. */
private class SourceCompare(
    val load: CoverageLoad?,
    val onCompare: () -> Unit,
    val onSelect: (String?) -> Unit,
)

private val LocalSourceCompare = staticCompositionLocalOf<SourceCompare?> { null }

/** The default source from settings, and how to return this type to it. */
private class SourceDefaultAction(val state: SourceDefault, val onUseDefault: () -> Unit)

private val LocalSourceDefault = staticCompositionLocalOf<SourceDefaultAction?> { null }

/**
 * Under an explanation that searches the past year: said only where the search was in fact cut
 * to 30 days, with the way to lift it. With older data allowed, the rule above is the whole
 * story and a clause about a limit that does not apply only made it longer.
 */
@Composable
private fun HistoryLimitNote() {
    val access = LocalHistoryAccess.current
    if (access.granted) return
    Text(
        text = stringResource(R.string.history_limit_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    TextButton(onClick = access.onGrant) {
        Text(stringResource(R.string.history_grant))
    }
}

/**
 * The type's personal best over the past year: which kind of best, its value, its day.
 *
 * Worded as what the data holds, never as praise or a verdict -- a "lowest" resting rate is a
 * position, not a diagnosis. The rule behind it, including the 30 days without the history
 * permission, is behind the "i", as for the streak.
 */
@Composable
private fun RecordExplanation(record: PersonalRecord, spec: RecordTypeSpec<*>) {
    val explanation = rememberExplanation("record")
    val zone = HealthRepository.DEFAULT_ZONE
    val title = stringResource(
        when (record.kind) {
            RecordKind.MOST -> R.string.record_title_most
            RecordKind.LOWEST -> R.string.record_title_lowest
            RecordKind.HIGHEST -> R.string.record_title_highest
            RecordKind.LONGEST -> R.string.record_title_longest
        },
    )
    val value = if (record.kind == RecordKind.LONGEST) {
        Formatting.duration(Duration.ofSeconds(record.value.toLong()))
    } else {
        Formatting.number(record.value, spec.valueDecimals) +
            (spec.displayUnitRes?.let { " " + stringResource(it) } ?: "")
    }
    InfoGroup {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.MilitaryTech,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp, top = firstLineInset(MaterialTheme.typography.titleSmall, 24.dp)),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                // The 24 dp icon is taller than the line, so the text comes down to its middle.
                modifier = Modifier.weight(1f).padding(top = firstLineTextInset(MaterialTheme.typography.titleSmall, 24.dp)),
            )
            InfoToggle(
                explanation,
                Modifier.padding(top = firstLineTextInset(MaterialTheme.typography.titleSmall, 24.dp)),
                firstLine = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            text = stringResource(
                R.string.record_value_on,
                value,
                Formatting.date(record.date.atStartOfDay(zone).toInstant(), zone),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        // Not on the record's own day: there the button would open what is already shown.
        val jump = LocalDayJump.current
        if (jump.shown != record.date) {
            TextButton(onClick = { jump.onShow(record.date) }) {
                Text(stringResource(R.string.record_show))
            }
        }
        if (explanation.expanded == true) {
            Text(
                text = stringResource(R.string.record_rule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HistoryLimitNote()
        }
    }
}

/** "132/85" where a second value exists, whole numbers as a cuff shows them; else the value. */
private fun pressureText(first: Double, second: Double?, decimals: Int? = null): String =
    if (second == null) Formatting.number(first, decimals) else "${first.roundToInt()}/${second.roundToInt()}"

private val PressureCategory.color: Color get() = ValueZones.ZONE_COLORS[ordinal]

/** The grade in words beside its colour, so the colour is never the only signal. */
@Composable
private fun CategoryBadge(category: PressureCategory) {
    DotText(
        color = category.color,
        text = stringResource(category.labelRes()),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun PressureLegend() {
    Column(Modifier.padding(top = 6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PressureCategory.entries.forEach { category ->
                LegendEntry(color = category.color, label = category.labelRes())
            }
        }
        Text(
            text = stringResource(R.string.bp_legend_lines),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Morning and evening blood pressure side by side, rather than one blended average that hides
 * a morning surge. The rule for where the day splits is behind the "i".
 */
@Composable
private fun DayPartsSection(split: DayPartSplit) {
    val explanation = rememberExplanation("dayparts")

    InfoGroup {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(R.string.bp_parts_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            InfoToggle(explanation, firstLine = MaterialTheme.typography.titleSmall)
        }
        DayPartRow(stringResource(R.string.bp_part_morning), split.morning)
        DayPartRow(stringResource(R.string.bp_part_evening), split.evening)
        if (explanation.expanded == true) {
            Text(
                text = stringResource(R.string.bp_parts_rule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayPartRow(label: String, average: PartAverage?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (average == null) {
            Text(
                text = stringResource(R.string.bp_part_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DotText(
                color = pressureCategory(average.systolic, average.diastolic).color,
                text = pluralStringResource(
                    R.plurals.bp_part_value,
                    average.count,
                    // Whole numbers, as every cuff and every doctor gives them.
                    "${average.systolic.roundToInt()}/${average.diastolic.roundToInt()}",
                    average.count,
                ),
                style = MaterialTheme.typography.bodyMedium,
                textColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** "Steps_2026-09-20_2026-09-26": type and the window's first and last day. */
@Composable
private fun SpanSummary(
    data: TileDetailData,
    onSelectSource: (String?) -> Unit,
    onOpenSession: (Session) -> Unit,
    onVisibleRange: (ClosedRange<Instant>?) -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        // First: it changes how a short chart should be read -- not missing data, but data
        // the app is not allowed to see.
        if (data.historyCapped) {
            Text(
                text = stringResource(R.string.detail_history_capped),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = LocalHistoryAccess.current.onGrant) {
                Text(stringResource(R.string.history_grant))
            }
        }

        data.total?.let { total ->
            Text(
                // A mean is not a total: "Total 129 mmHg" read as blood pressures added up.
                text = stringResource(
                    when {
                        // HRV's figure is computed, and says which: a night, or a week of them.
                        data.hrv != null && data.extent == null -> R.string.hrv_week_label
                        data.hrv != null -> R.string.hrv_night_label
                        data.perNight -> R.string.span_per_night
                        // One value a day: a day of it has nothing to average.
                        data.spec.tile.dailyValue && data.extent != null -> R.string.span_daily_value
                        data.spec.isAveraged || data.dailyFromReadings -> R.string.span_average
                        else -> R.string.span_total
                    },
                ),
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
                    // A change is signed, as in the record list: "+0,01 °C" says above the
                    // wearer's baseline where "0,01 ± °C" read like a tolerance.
                    (if (data.spec.quantity == Quantity.TEMPERATURE_CHANGE && total > 0) "+" else "") +
                        pressureText(total, data.secondaryTotal, data.spec.valueDecimals) +
                        (data.spec.displayUnitRes?.let { " " + stringResource(it) } ?: "")
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            data.secondaryTotal?.let { second ->
                if (data.secondaryZones != null) CategoryBadge(pressureCategory(total, second))
            }
        }

        data.hrv?.let { HrvStatusLine(it, dayView = data.extent != null) }

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

        data.dayParts?.let { DayPartsSection(it) }

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

        data.readingContext?.let { ReadingContextStrip(data, it) }

        if (data.drawsChart) {
            // The single nights are optional: some want the week's line alone, some want to
            // see which nights moved it. Remembered, as a display choice, not per screen.
            val context = LocalContext.current
            val store = remember(context) { SettingsStore(context) }
            val scope = rememberCoroutineScope()
            val showNights by remember(store) { store.settings.map { it.showSingleNights } }
                .collectAsStateWithLifecycle(initialValue = true)
            // Likewise the four-week mean, off by default: a second line is clutter to anyone
            // not looking for their own level.
            val showMean by remember(store) { store.settings.map { it.showRollingMean } }
                .collectAsStateWithLifecycle(initialValue = false)
            val shown = data.copy(
                nightPoints = if (showNights) data.nightPoints else emptyList(),
                baseline = if (showMean) data.baseline else emptyList(),
                // The wearer's usual range replaces the day's spread where the type has one:
                // two bands behind one line would be two answers to "is this normal".
                rangeBand = data.usualBand.ifEmpty { data.rangeBand },
            )
            // Zoomed, the list below follows the stretch on screen.
            DataLineChart(shown, Modifier.padding(top = 16.dp), onVisibleRange = onVisibleRange)
            Text(
                text = stringResource(
                    when {
                        // Counting sessions is not summing a metric, so "daily totals" would
                        // name the wrong operation.
                        // Bars built from the sessions, not from an aggregate: say what each is.
                        data.sessionCaption != null -> data.sessionCaption
                        shown.nightPoints.isNotEmpty() -> R.string.chart_source_hrv_nights
                        data.hrv != null && data.extent == null -> R.string.chart_source_hrv
                        data.approximated -> R.string.chart_source_cumulative_scaled
                        data.cumulative -> R.string.chart_source_cumulative
                        data.aggregated && data.weeklyBuckets && data.spec.isAveraged ->
                            R.string.chart_source_aggregated_weekly_mean
                        data.aggregated && data.weeklyBuckets ->
                            R.string.chart_source_aggregated_weekly
                        // "Totals" is wrong for a mean, and doubly so with a spread drawn
                        // behind it: the line is the day's average, not its sum.
                        data.aggregated && data.rangeBand.isNotEmpty() ->
                            R.string.chart_source_aggregated_range
                        data.aggregated && data.stack.isNotEmpty() ->
                            R.string.chart_source_aggregated_split
                        data.aggregated && data.spec.isAveraged -> R.string.chart_source_aggregated_mean
                        data.aggregated -> R.string.chart_source_aggregated
                        data.dailyFromReadings && data.weeklyBuckets -> R.string.chart_source_readings_weekly
                        data.dailyFromReadings -> R.string.chart_source_readings_daily
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
            ChartLegend(data = shown)
            if (data.nightPoints.isNotEmpty()) {
                ChartToggle(showNights, R.string.hrv_show_nights) {
                    scope.launch { store.setShowSingleNights(it) }
                }
            }
            if (data.baseline.isNotEmpty()) {
                ChartToggle(showMean, R.string.chart_show_rolling_mean) {
                    scope.launch { store.setShowRollingMean(it) }
                }
            }

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

            // A gap is only unambiguous once it is named; without this a dotted stretch reads as
            // a style, and a missing bar as a rendering artefact, rather than as no data.
            val readingGaps = remember(data.points, data.bars) {
                if (data.bars) 0 else segmentAtGaps(data.points, emptyList(), readingGap(data.points)).size - 1
            }
            when {
                data.emptyBuckets.isNotEmpty() -> Text(
                    text = pluralStringResource(
                        if (data.bars) R.plurals.chart_gaps else R.plurals.chart_gaps_dotted,
                        data.emptyBuckets.size,
                        data.emptyBuckets.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                readingGaps > 0 -> Text(
                    text = stringResource(R.string.chart_reading_gaps),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Under the chart, not beside the total: they are read after it (a streak reaching
        // back a year took 12.5 s), and arriving above it pushed the chart a quarter of the
        // screen down while it was being looked at.
        data.streak?.let { StreakExplanation(it, active = data.spec.tile.form == TileSpec.Form.SESSIONS) }
        data.trend?.let { TrendExplanation(it, data.spec.displayUnitRes, data.spec.valueDecimals) }
        data.record?.let { RecordExplanation(it, data.spec) }
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
 *
 * The dots on a marked-readings line are that line's own points, not a second series, so
 * they get no entry of their own. They used to, as "Measurement" beside "Reading" with an
 * identical swatch: two names for one thing, which read as a mark the chart did not have.
 */
@Composable
private fun ChartLegend(data: TileDetailData) {
    // A classified pair of lines: the colours are the message, so the legend names the grades
    // instead of repeating "line" and "reading" in a colour neither line has.
    if (data.secondaryZones != null && data.points.isNotEmpty()) {
        PressureLegend()
        return
    }
    val sleepShown = data.bandSessions.any { it.kind == Session.Kind.SLEEP }
    val exerciseShown = data.bandSessions.any { it.kind == Session.Kind.EXERCISE }
    val stacked = data.stack.isNotEmpty() && data.stackLabels.isNotEmpty()

    // The series' own name depends on what a point means, which is exactly what the mark
    // already encodes: a bar is a bucket's total, a line is a reading.
    val seriesLabel = when {
        // No series to name: a session type's day is the timeline alone, whose bands are
        // named below like any other.
        data.points.isEmpty() -> null
        stacked -> null // The stack's own segments are named below; a total above them is noise.
        data.sessionCounts && data.weeklyBuckets -> R.string.chart_source_sessions_per_week
        data.sessionCounts -> R.string.legend_value_sessions
        data.spec.tile.form == TileSpec.Form.SESSIONS && data.bars &&
            data.spec.tile.sessionKind == Session.Kind.MINDFULNESS ->
            if (data.weeklyBuckets) R.string.chart_source_session_time_weekly else R.string.chart_source_session_time_daily
        data.spec.tile.form == TileSpec.Form.SESSIONS && data.bars ->
            R.string.legend_value_sleep_hours
        data.bars && data.weeklyBuckets -> R.string.legend_value_bars_weekly
        data.bars -> R.string.legend_value_bars
        data.cumulative -> R.string.legend_value_cumulative
        data.hrv != null && data.extent == null -> R.string.legend_hrv_week
        // A year is bucketed by week, as the caption says; "daily" here contradicted it.
        data.rangeBand.isNotEmpty() && data.weeklyBuckets -> R.string.legend_value_mean_weekly
        data.rangeBand.isNotEmpty() -> R.string.legend_value_mean
        // Named as the caption names it: across days a weight point is that day's mean, not a
        // reading, and calling it "Reading" contradicted "Daily averages" printed above.
        data.aggregated && data.weeklyBuckets && data.spec.isAveraged -> R.string.legend_value_mean_weekly
        data.aggregated && data.spec.isAveraged -> R.string.legend_value_mean
        data.dailyFromReadings && data.weeklyBuckets -> R.string.legend_value_mean_weekly
        data.dailyFromReadings -> R.string.legend_value_mean
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
    val reference = data.spec.tile.referenceRange
    val bandShown = sleepShown || exerciseShown || data.rangeBand.isNotEmpty() || reference != null
    val entries = (if (seriesLabel != null) 1 else 0) +
        (if (stacked) data.stackLabels.size else 0) +
        listOf(
            data.rangeBand.isNotEmpty(),
            reference != null,
            sleepShown,
            exerciseShown,
            data.goal != null,
            data.nightPoints.isNotEmpty(),
            data.baseline.isNotEmpty(),
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
                label = when {
                    data.hrv != null || data.usualBand.isNotEmpty() -> R.string.legend_hrv_usual
                    data.weeklyBuckets -> R.string.legend_range_weekly
                    else -> R.string.legend_range
                },
            )
        }
        // Named with where it comes from, so it is never read as the wearer's own range.
        reference?.let {
            LegendEntry(
                color = REFERENCE_COLOR.copy(alpha = LEGEND_BAND_ALPHA),
                label = it.labelRes,
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
        if (data.nightPoints.isNotEmpty()) {
            LegendEntry(
                color = MaterialTheme.colorScheme.primary.copy(alpha = LEGEND_BAND_ALPHA),
                label = R.string.legend_hrv_night,
                round = true,
            )
        }
        if (data.baseline.isNotEmpty()) {
            LegendEntry(color = MaterialTheme.colorScheme.onSurfaceVariant, label = R.string.legend_rolling_mean)
        }
        if (data.goal != null) {
            LegendEntry(color = MaterialTheme.colorScheme.tertiary, label = R.string.legend_goal)
        }
    }
}

/**
 * The window's series as a chart, drawn the same wherever it appears.
 *
 * Shared by the detail screen and a large dashboard tile, so a tile's chart is the chart its
 * tap opens -- same marks, same goal line, same bands -- only without the touch readout.
 */
@Composable
internal fun DataLineChart(
    data: TileDetailData,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    fillHeight: Boolean = false,
    compactAxis: Boolean = false,
    onVisibleRange: ((ClosedRange<Instant>?) -> Unit)? = null,
) {
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
        unitRes = data.spec.displayUnitRes,
        valueDecimals = data.spec.valueDecimals,
        emptyBuckets = data.emptyBuckets,
        sessions = data.bandSessions,
        referenceRange = data.spec.tile.referenceRange?.let { it.low..it.high },
        zones = data.lineZones,
        secondaryPoints = data.secondaryPoints,
        secondaryZones = data.secondaryZones,
        markReadings = data.spec.tile.markReadings,
        integral = data.spec.tile.integralValues,
        extent = data.extent,
        interactive = interactive,
        fillHeight = fillHeight,
        compactAxis = compactAxis,
        // Only the departures are coloured: a week inside its range keeps the line's own
        // colour, so an orange stretch is the one thing that stands out.
        pointColors = data.pointStandings.map { standing ->
            standing?.takeIf { it != HrvStanding.WITHIN }?.color()
        },
        scatter = data.nightPoints,
        baseline = data.baseline,
        onVisibleRange = onVisibleRange,
        modifier = modifier,
    )
}

/**
 * The week's HRV against the usual range, in words and with a coloured dot.
 *
 * Worded as a position, never a verdict -- "below your usual range", not "poor recovery": the
 * range is this app's own arithmetic on the device's readings, and the explanation beneath says
 * so -- without naming a brand, since the readings may come from any wearable.
 */
@Composable
private fun HrvStatusLine(summary: HrvSummary, dayView: Boolean) {
    val day = summary.day
    val explanation = rememberExplanation("hrv")
    Column(Modifier.padding(top = 4.dp)) {
        // On a day the headline is the night; the week it is judged by is named here.
        if (dayView) {
            day?.weekMean?.let { mean ->
                Text(
                    text = stringResource(R.string.hrv_week_mean, Formatting.number(mean)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        val low = day?.usualLow
        val high = day?.usualHigh
        val standing = day?.standing
        // The standing always shows: it is the data. How it is worked out is behind the "i",
        // closed until asked for, as the trend's rule is.
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                if (low != null && high != null && standing != null) {
                    DotText(
                        color = standing.color(),
                        text = stringResource(
                            R.string.hrv_usual,
                            Formatting.number(low),
                            Formatting.number(high),
                            stringResource(standing.labelRes()),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.hrv_no_range),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            InfoToggle(explanation, firstLine = MaterialTheme.typography.bodyMedium)
        }
        if (explanation.expanded == true) {
            Text(
                text = stringResource(R.string.hrv_explained),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Green inside the usual range, orange outside it on either side -- as the watch colours it. */
internal fun HrvStanding.color(): Color = when (this) {
    HrvStanding.WITHIN -> ValueZones.ZONE_COLORS[1]
    HrvStanding.BELOW, HrvStanding.ABOVE -> ValueZones.ZONE_COLORS[3]
}

private fun HrvStanding.labelRes(): Int = when (this) {
    HrvStanding.WITHIN -> R.string.hrv_within
    HrvStanding.BELOW -> R.string.hrv_below
    HrvStanding.ABOVE -> R.string.hrv_above
}

/** One swatch and its name; a round swatch for a mark drawn as dots. */
/** A remembered on/off for an optional mark on the chart, the whole row tappable. */
@Composable
private fun ChartToggle(checked: Boolean, @StringRes label: Int, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        // On the first line, should a long label wrap.
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.padding(top = firstLineInset(MaterialTheme.typography.bodyMedium, TOGGLE_SIZE.dp)),
        )
        // The box has no touch target of its own here -- the row is the target -- so nothing
        // keeps the label off it but this gap, as beside the export dialog's radio buttons.
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(
                start = 12.dp,
                top = firstLineTextInset(MaterialTheme.typography.bodyMedium, TOGGLE_SIZE.dp),
            ),
        )
    }
}

@Composable
private fun LegendEntry(color: Color, @StringRes label: Int, round: Boolean = false) {
    DotText(
        color = color,
        text = stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
        gap = 4.dp,
        shape = if (round) CircleShape else RoundedCornerShape(2.dp),
    )
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
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = iconFor(session),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = firstLineInset(MaterialTheme.typography.labelSmall, SESSION_ICON.dp))
                .size(SESSION_ICON.dp),
        )
        Text(
            text = listOfNotNull(
                sessionName(session),
                stringResource(
                    R.string.session_span,
                    Formatting.time(session.start),
                    Formatting.time(session.end),
                ),
            ).joinToString("  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RouteMark(session, size = SESSION_ICON)
    }
}

/**
 * Says a session has a recorded route, so it can be found in a list before opening it -- the
 * route itself is read, and asked for, only when the session is opened.
 */
@Composable
private fun RouteMark(session: Session, size: Int = ROUTE_MARK) {
    if (session.route == null) return
    Icon(
        imageVector = Icons.Default.Route,
        contentDescription = stringResource(R.string.route_available),
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(size.dp),
    )
}

private const val ROUTE_MARK = 20

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
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = iconFor(session),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = firstLineInset(MaterialTheme.typography.bodyLarge, 24.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = sessionName(session),
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
            RouteMark(session)
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
                minSpan = SESSION_CURVE_MIN_SPAN,
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
 * Every writer's mark, overlapping, as the "all sources" chip: the choice is the union of the
 * icons beside it, and a picture of that says so without words. Writers with no mark at all
 * are left out of the picture; the chip's label still names the choice for a screen reader.
 */
@Composable
private fun StackedSources(sources: List<String>) {
    val label = stringResource(R.string.source_all)
    val ring = MaterialTheme.colorScheme.surface
    Box(Modifier.semantics(mergeDescendants = true) { contentDescription = label }) {
        sources.forEachIndexed { index, packageName ->
            Box(
                Modifier
                    .padding(start = (index * STACK_STEP).dp)
                    .clip(CircleShape)
                    .background(ring)
                    .padding(1.dp)
                    .clearAndSetSemantics { },
            ) {
                SourceMark(packageName, SOURCE_ICON, SOURCE_ICON_PX, Modifier.clip(CircleShape)) {}
            }
        }
    }
}

/**
 * A compact selectable chip for the source picker.
 *
 * Material's filter chip pads its content by 16dp a side, which for a 20dp app icon made each
 * chip three times the width of what it shows, and five writers ran to two lines. Here the
 * padding is 8dp; the touch target stays at the accessible minimum, and the selected state is
 * announced as with any chip.
 */
@Composable
private fun SourceChip(
    selected: Boolean,
    onClick: () -> Unit,
    /** The app chosen in settings as the default: marked with a star, named for TalkBack. */
    isDefault: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(
            modifier = Modifier
                .height(SOURCE_CHIP_HEIGHT.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
            if (isDefault) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = stringResource(R.string.source_default),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(DEFAULT_STAR.dp),
                )
            }
        }
    }
}

private const val DEFAULT_STAR = 14

@Composable
private fun SourceSection(data: TileDetailData, onSelectSource: (String?) -> Unit) {
    val context = LocalContext.current
    val sources = data.contributingApps.sortedBy { context.appLabelFor(it) }

    val explanation = rememberExplanation("sources")
    val defaultAction = LocalSourceDefault.current

    // One outlined box for chips, "i" and explanation, so they read as one control -- and so
    // the space is already taken while the writers are still being read, and the chart below
    // does not jump when they arrive.
    //
    // The label sits in the top border, as on a form's fieldset: it names the box once, which
    // is what lets the "all" chip be a picture of every writer rather than the words.
    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        OutlinedCard(Modifier.fillMaxWidth().padding(top = SOURCE_LEGEND_OFFSET.dp)) {
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
                                StackedSources(sources)
                            }
                            sources.forEach { packageName ->
                                SourceChip(
                                    selected = data.selectedSource == packageName,
                                    onClick = { onSelectSource(packageName) },
                                    isDefault = packageName == defaultAction?.state?.preferred,
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
                    // Back to the app chosen in settings, named, wherever this type shows something
                    // else by a choice of its own -- "Alle" included, which no longer means
                    // "the default" since it is remembered as a choice.
                    val preferred = defaultAction?.state?.preferred
                    if (preferred != null && preferred in sources && defaultAction.state.ownChoice &&
                        data.selectedSource != preferred
                    ) {
                        TextButton(onClick = defaultAction.onUseDefault) {
                            Text(stringResource(R.string.source_use_default, context.appLabelFor(preferred)))
                        }
                    }
                }

                // With a single writer there is nothing to choose and nothing to explain: naming it
                // is the data itself, so it always shows -- with its icon, as the chips would. Even
                // when that app is the preferred source: "only this app's value, not the combined
                // one" was wrong there, since one writer's value is the combined one.
                if (sources.size == 1) {
                    Row(verticalAlignment = Alignment.Top) {
                        SourceMark(
                            sources.first(),
                            SOURCE_ICON,
                            SOURCE_ICON_PX,
                            Modifier.padding(top = firstLineInset(MaterialTheme.typography.bodySmall, SOURCE_ICON.dp)),
                        ) {}
                        Text(
                            text = stringResource(R.string.detail_written_by, context.appLabelFor(sources.first())),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = firstLineTextInset(MaterialTheme.typography.bodySmall, SOURCE_ICON.dp)),
                        )
                    }
                } else if (explanation.expanded == true) {
                    Text(
                        text = data.selectedSource?.let {
                            stringResource(R.string.source_showing_one, context.appLabelFor(it))
                        } ?: stringResource(R.string.source_all_explained),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                // Expanded, each chip is spelled out: the app's name, and the devices its records
                // say they came from -- a watch, the phone, "Garmin Forerunner 265" where the writer
                // stored one. Too long for a chip; worth reading once the box is opened.
                if (sources.size > 1 && explanation.expanded == true) {
                    val devices = remember(data.records) { devicesBySource(data.records) }
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sources.forEach { packageName ->
                            val deviceText = devices[packageName].orEmpty().map { (kind, name) ->
                                val kindText = kind?.let { stringResource(it.labelRes) }
                                when {
                                    kindText != null && name != null -> "$kindText ($name)"
                                    else -> kindText ?: name.orEmpty()
                                }
                            }.distinct().joinToString(", ")
                            Row(verticalAlignment = Alignment.Top) {
                                SourceMark(
                                    packageName,
                                    SOURCE_ICON,
                                    SOURCE_ICON_PX,
                                    Modifier.padding(top = firstLineInset(MaterialTheme.typography.bodySmall, SOURCE_ICON.dp)),
                                ) {}
                                Text(
                                    text = listOf(context.appLabelFor(packageName), deviceText)
                                        .filter { it.isNotEmpty() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp, top = firstLineTextInset(MaterialTheme.typography.bodySmall, SOURCE_ICON.dp)),
                                )
                            }
                        }
                    }
                }

                if (sources.size > 1 && explanation.expanded == true) {
                    LocalSourceCompare.current?.let { SourceComparison(it, data.selectedSource) }
                }

                // The overlap winner is Health Connect's own priority setting, not ours to define.
                // Only for the combined view: with one app selected nothing is deduplicated, so
                // which app would win an overlap says nothing about the figure shown.
                if (sources.size > 1 && explanation.expanded == true && data.selectedSource == null) {
                    Text(
                        text = stringResource(R.string.source_priority_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.source_section_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 12.dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 4.dp),
        )
    }
}

/**
 * Per writing app, the devices its records name: kind and, where stored, maker and model. From
 * the records the list holds, so a source whose records all lie beyond the list's cap names
 * none -- the line then shows the app alone.
 */
private fun devicesBySource(records: List<Record>): Map<String, List<Pair<DeviceKind?, String?>>> =
    records.groupBy { it.metadata.dataOrigin.packageName }.mapValues { (_, ofSource) ->
        ofSource.mapNotNull { record ->
            val device = record.metadata.device
            val kind = DeviceKind.of(device)
            val name = deviceName(device)
            if (kind == null && name == null) null else kind to name
        }.distinct()
    }

private const val SESSION_ICON = 16

/** A checkbox without its own touch target: the 20 dp box and its padding. */
private const val TOGGLE_SIZE = 24
private const val SESSION_CURVE_HEIGHT = 40

/** Big enough to recognise a brand mark, small enough that several chips fit a phone width. */
private const val SOURCE_ICON = 20
private const val SOURCE_CHIP_HEIGHT = 32

/** Half the label's line height, so the border runs through its middle. */
private const val SOURCE_LEGEND_OFFSET = 8

/** How far each icon in the "all sources" stack sits from the one before it. */
private const val STACK_STEP = 12

/** Roughly one row of chips, so the box keeps its size while the writers load. */
private const val SOURCE_PLACEHOLDER_HEIGHT = 48
private const val SOURCE_ICON_PX = 64

/** The aggregate for a session type comes back in hours; durations format from minutes. */
private const val MINUTES_PER_HOUR = 60

@Composable
private fun titleFor(spec: RecordTypeSpec<*>?): String =
    spec?.let { stringResource(it.displayNameRes) } ?: ""

/** A session's heart-rate axis spans at least this many bpm, so a steady night reads flat. */
private const val SESSION_CURVE_MIN_SPAN = 20.0

/**
 * Which app covers this type best, worded as what the data shows: per writer, on how many of
 * the last 30 days it wrote anything, how many entries a day, since when, and for sleep how
 * many nights carried stages. Never a verdict on a device or an app -- "covers the most days"
 * is a count -- and only on request, since it reads every record of the type.
 *
 * The offer is the per-type source filter, the same as tapping the app's chip. It cannot make
 * the app win overlaps in the combined total: that is Health Connect's priority list.
 */
@Composable
private fun SourceComparison(compare: SourceCompare, selected: String?) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (val load = compare.load) {
        null -> TextButton(onClick = compare.onCompare) {
            Text(stringResource(R.string.sources_compare))
        }

        CoverageLoad.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))

        CoverageLoad.Failed -> Text(
            text = stringResource(R.string.sources_compare_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )

        is CoverageLoad.Done -> {
            val coverage = load.coverage
            val suggestion = coverage.suggestion
            val suggested = suggestion?.packageName
            Text(
                text = stringResource(R.string.sources_compare_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                coverage.writers.forEach { writer ->
                    Row(verticalAlignment = Alignment.Top) {
                        SourceMark(
                            writer.packageName,
                            SOURCE_ICON,
                            SOURCE_ICON_PX,
                            Modifier.padding(top = firstLineInset(MaterialTheme.typography.bodyMedium, SOURCE_ICON.dp)),
                        ) {}
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                text = context.appLabelFor(writer.packageName),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (suggestion != null && writer.packageName == suggested) {
                                Text(
                                    text = stringResource(
                                        when (suggestion.reason) {
                                            Suggestion.Reason.MOST_DAYS -> R.string.sources_most_days
                                            Suggestion.Reason.MOST_ENTRIES -> R.string.sources_most_entries
                                            Suggestion.Reason.LONGEST_HISTORY -> R.string.sources_longest_history
                                        },
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = listOfNotNull(
                                    stringResource(R.string.sources_days, writer.days, coverage.days),
                                    stringResource(R.string.sources_per_day, writer.perDay),
                                    writer.nightsWithStages?.let {
                                        stringResource(R.string.sources_stages, it, writer.records)
                                    },
                                    writer.since?.let {
                                        val zone = HealthRepository.DEFAULT_ZONE
                                        stringResource(R.string.sources_since, Formatting.date(it.atStartOfDay(zone).toInstant(), zone))
                                    },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                            )
                        }
                    }
                }
            }
            if (suggested != null && suggested != selected) {
                TextButton(onClick = { compare.onSelect(suggested) }) {
                    Text(stringResource(R.string.sources_show_only, context.appLabelFor(suggested)))
                }
            }
        }
    }
}

/**
 * A day of a type measured now and then, without a 24-hour axis: a single weigh-in on one was
 * a lone dot, and its time of day is the least interesting thing about it. Instead what a
 * reading is looked at for -- up or down since the last one, and against what -- with the
 * recent days as dots and this one highlighted.
 *
 * The change names the earlier reading's date: over an uneven gap, two days or three weeks,
 * a bare "-0,4 kg" would read as a rate.
 */
@Composable
private fun ReadingContextStrip(data: TileDetailData, context: ReadingContext) {
    val spec = data.spec
    val unit = spec.displayUnitRes?.let { " " + stringResource(it) }.orEmpty()
    val zone = HealthRepository.DEFAULT_ZONE
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shownDay = data.readingContextDay
    val index = context.days.indexOfLast { it.time.atZone(zone).toLocalDate() == shownDay }
    val current = context.days.getOrNull(index)
    val previous = context.days.getOrNull(index - 1)

    // As precise as the readings are shown, no more: "+0,37 kg" beside a weight of "81,9 kg"
    // claimed a precision the headline itself does not.
    val changeDecimals = spec.valueDecimals ?: if (spec.tile.integralValues) 0 else 1
    fun signed(value: Double): String =
        (if (value > 0) "+" else "") + Formatting.number(value, changeDecimals)

    Column(Modifier.padding(top = 8.dp)) {
        if (context.dayHigh > context.dayLow) {
            Text(
                text = stringResource(
                    R.string.context_day_range,
                    Formatting.number(context.dayLow, spec.valueDecimals),
                    Formatting.number(context.dayHigh, spec.valueDecimals) + unit,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (current != null && previous != null) {
            val second = context.secondaryDays
            val secondNow = second.firstOrNull { it.time == current.time }
            val secondBefore = second.firstOrNull { it.time == previous.time }
            val change = signed(current.value - previous.value) +
                (if (secondNow != null && secondBefore != null) "/" + signed(secondNow.value - secondBefore.value) else "") +
                unit
            Text(
                text = stringResource(R.string.context_change, change, Formatting.date(previous.time, zone)),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (current != null) {
            Text(
                text = stringResource(R.string.context_first),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
        }
        if (context.days.size > 1) {
            val highlight = MaterialTheme.colorScheme.tertiary
            LineChart(
                points = context.days,
                modifier = Modifier.padding(top = 12.dp),
                smooth = false,
                unitRes = spec.displayUnitRes,
                valueDecimals = spec.valueDecimals,
                integral = spec.tile.integralValues,
                markReadings = true,
                secondaryPoints = context.secondaryDays,
                pointColors = context.days.mapIndexed { i, _ -> highlight.takeIf { i == index } },
            )
            Text(
                text = stringResource(R.string.context_caption),
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
        }
    }
}
