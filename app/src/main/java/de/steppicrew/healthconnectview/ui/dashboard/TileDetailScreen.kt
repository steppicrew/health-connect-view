package de.steppicrew.healthconnectview.ui.dashboard

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.billing.Feature
import de.steppicrew.healthconnectview.export.ExportPeriod
import de.steppicrew.healthconnectview.export.Exporter
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.health.counted
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.health.totalDuration
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.compare.CompareTypeDialog
import de.steppicrew.healthconnectview.ui.components.ExpandableChart
import de.steppicrew.healthconnectview.ui.components.ExportAction
import de.steppicrew.healthconnectview.ui.components.LineChart
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.OnResume
import de.steppicrew.healthconnectview.ui.components.RefreshBox
import de.steppicrew.healthconnectview.ui.session.SessionCurve
import de.steppicrew.healthconnectview.ui.components.ShowExportResults
import de.steppicrew.healthconnectview.ui.components.SpanSelector
import de.steppicrew.healthconnectview.ui.components.WindowStepper
import de.steppicrew.healthconnectview.ui.components.chartTitle
import de.steppicrew.healthconnectview.ui.components.periodLabel
import de.steppicrew.healthconnectview.ui.components.swipeToStep
import de.steppicrew.healthconnectview.ui.components.windowLabel
import de.steppicrew.healthconnectview.ui.detail.RecordRow
import de.steppicrew.healthconnectview.ui.record.RecordDetailOverlay
import java.time.Instant
import java.time.LocalDate
import kotlin.math.pow
import de.steppicrew.healthconnectview.util.catching

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
    /** Weight and its parts together, from any of the body-composition types. */
    onOpenBody: () -> Unit = {},
    /** Opens this type beside [second] over the window shown: first, second, span, a day in it. */
    onCompare: (first: String, second: String, span: Span, date: String) -> Unit = { _, _, _, _ -> },
) {
    val requested = openSession
    val state by viewModel.state.collectAsStateWithLifecycle()
    val span by viewModel.span.collectAsStateWithLifecycle()
    val spec by viewModel.spec.collectAsStateWithLifecycle()
    // Saveable: back from the session screen this composes afresh, and a plain flag would
    // open the requested session again at once, trapping the screen in a loop.
    var opened by rememberSaveable { mutableStateOf(false) }
    var openRecord by rememberSaveable { mutableStateOf<String?>(null) }
    // Held here rather than by the list, which leaves the composition while a window loads:
    // coming back from a workout must land where it was left, not at the top.
    val listState = rememberLazyListState()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val end by viewModel.end.collectAsStateWithLifecycle()
    val comparing by viewModel.comparing.collectAsStateWithLifecycle()
    // Another window is another page, so it opens at the top; a return to the same one does
    // not. Saved like the list's position, so a restored screen does not count as a change.
    var listWindow by rememberSaveable { mutableStateOf("") }
    val window = "${span.name}/$end"
    LaunchedEffect(window) {
        if (listWindow.isNotEmpty() && listWindow != window) listState.scrollToItem(0)
        listWindow = window
    }
    LaunchedEffect(state, requested) {
        if (requested.isEmpty() || opened) return@LaunchedEffect
        val sessions = (state as? UiState.Data)?.value?.sessions ?: return@LaunchedEffect
        val at = catching { Instant.parse(requested) }.getOrNull()
        sessions.firstOrNull { if (at != null) at >= it.start && at < it.end else requested == "route" && it.route != null }?.let {
            opened = true
            onOpenSession(it)
        }
    }

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
                    if (spec?.type in BODY_TYPES) BodyCompositionAction(onOpenBody)
                    spec?.takeIf { it.isPinnable }?.let { current ->
                        val typeName = current.type.simpleName.orEmpty()
                        CompareAction(
                            typeName = typeName,
                            load = viewModel::compareCandidates,
                            onPick = { second ->
                                onCompare(typeName, second, span, end.toString())
                            },
                        )
                    }
                    spec?.let { current ->
                        ExportAction(
                            typeName = current.type.simpleName.orEmpty().removeSuffix("Record"),
                            shown = ExportPeriod(span.startDate(0, end), end),
                            historyGranted = historyGranted,
                            // Not for sessions: Health Connect's daily sleep total cuts nights at
                            // midnight, while the app credits a night to the morning it ended --
                            // a file would give a second answer to "how long did I sleep".
                            dailyAvailable = current.aggregate != null &&
                                current.tile.form != TileSpec.Form.SESSIONS,
                            reportAvailable = Exporter.hasReport(current),
                            combinedAvailable = current.type in Exporter.REPORT_TYPES,
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
                .swipeToStep(end.isBefore(LocalDate.now()), viewModel::stepBack, viewModel::stepForward),
        ) {
            SpanSelector(selected = span, onSelect = viewModel::setSpan)
            WindowStepper(
                label = windowLabel(span, 0, end),
                canStepForward = end.isBefore(LocalDate.now()),
                onBack = viewModel::stepBack,
                onForward = viewModel::stepForward,
                onNow = viewModel::stepToNow,
                shownDate = end,
                onPickDate = viewModel::showDate,
            )

            // Read by every note that depends on how far back the app may look.
            CompositionLocalProvider(
                LocalHistoryAccess provides HistoryAccess(historyGranted, onOpenPermissions),
                LocalSourceCompare provides SourceCompare(coverage, viewModel::compareSources, viewModel::selectSource),
                LocalSourceDefault provides SourceDefaultAction(sourceDefault, viewModel::useDefaultSource),
                LocalYearCompare provides YearCompare(comparing, viewModel::setComparing),
                LocalDayJump provides DayJump(
                    shown = end.takeIf { span == Span.DAY },
                    onShow = viewModel::showDay,
                ),
            ) {
                RefreshBox(
                    refreshing = refreshing,
                    onRefresh = viewModel::pullRefresh,
                    modifier = Modifier.fillMaxSize(),
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
                            listState = listState,
                            data = current.value,
                            period = windowLabel(span, 0, end),
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
    }
    val data = (state as? UiState.Data)?.value
    RecordDetailOverlay(data?.spec, data?.records.orEmpty(), openRecord) { openRecord = null }
}

@Composable
private fun SpanContent(
    listState: LazyListState,
    data: TileDetailData,
    period: String,
    onSelectSource: (String?) -> Unit,
    onOpenSession: (Session) -> Unit,
    loadCurve: suspend (Session) -> SessionCurve,
    onVisibleRange: (ClosedRange<Instant>?) -> Unit,
    onOpenRecord: (String) -> Unit,
) {
    LazyColumn(state = listState) {
        item(key = "summary") { SpanSummary(data, period, onSelectSource, onOpenSession, onVisibleRange) }

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
                    // About the rows' heart-rate curves, so only where a row draws one: a
                    // workout's row no longer does (see SessionRow).
                    if (data.sessions.any(::rowShowsCurve)) Text(
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

/** Whether reads reach back past 30 days, and the way to allow it where they do not. */
internal class HistoryAccess(val granted: Boolean, val onGrant: () -> Unit)

internal val LocalHistoryAccess = staticCompositionLocalOf { HistoryAccess(granted = true, onGrant = {}) }

/** The day on screen when one day is shown, else null; and how to open another day. */
internal class DayJump(val shown: LocalDate?, val onShow: (LocalDate) -> Unit)

internal val LocalDayJump = staticCompositionLocalOf { DayJump(shown = null, onShow = {}) }

/** Whether the chart is set against the same window a year earlier, and how to switch it. */
internal class YearCompare(val on: Boolean, val onChange: (Boolean) -> Unit)

internal val LocalYearCompare = staticCompositionLocalOf { YearCompare(on = false, onChange = {}) }

/** The source comparison's state, how to start it, and how to pick the app it names. */
internal class SourceCompare(
    val load: CoverageLoad?,
    val onCompare: () -> Unit,
    val onSelect: (String?) -> Unit,
)

internal val LocalSourceCompare = staticCompositionLocalOf<SourceCompare?> { null }

/** The default source from settings, and how to return this type to it. */
internal class SourceDefaultAction(val state: SourceDefault, val onUseDefault: () -> Unit)

internal val LocalSourceDefault = staticCompositionLocalOf<SourceDefaultAction?> { null }

/**
 * Under an explanation that searches the past year: said only where the search was in fact cut
 * to 30 days, with the way to lift it. With older data allowed, the rule above is the whole
 * story and a clause about a limit that does not apply only made it longer.
 */
@Composable
internal fun HistoryLimitNote() {
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

@Composable
private fun titleFor(spec: RecordTypeSpec<*>?): String =
    spec?.let { stringResource(it.displayNameRes) } ?: ""

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
internal fun ReadingContextStrip(data: TileDetailData, context: ReadingContext) {
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
    fun signed(value: Double): String = Formatting.signed(value, changeDecimals)

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
            val secondChange = if (secondNow != null && secondBefore != null) secondNow.value - secondBefore.value else null
            // Zero as shown, not as stored: two readings of 44 and 44,02 still read "0,0".
            fun shownZero(value: Double) = kotlin.math.abs(value) < 0.5 * 10.0.pow(-changeDecimals)
            val unchanged = shownZero(current.value - previous.value) && (secondChange == null || shownZero(secondChange))
            val since = Formatting.date(previous.time, zone)
            Text(
                // "0,0 mL/kg/min since" read as a measurement of nothing; say it in words.
                text = if (unchanged) {
                    stringResource(R.string.context_unchanged, since)
                } else {
                    val change = signed(current.value - previous.value) +
                        (secondChange?.let { "/" + signed(it) } ?: "") + unit
                    stringResource(R.string.context_change, change, since)
                },
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
            // Named above the chart, not in small print beneath it: with readings weeks apart
            // the last ten span about the year, and unlabelled the strip read as the year's
            // chart repeated on the day.
            Text(
                text = stringResource(R.string.context_caption),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            ExpandableChart(
                chartTitle(
                    stringResource(spec.displayNameRes),
                    spec.displayUnitRes,
                    periodLabel(context.days.first().time, context.days.last().time),
                ),
            ) { expanded, onExpand ->
                LineChart(
                    points = context.days,
                    modifier = if (expanded) Modifier.fillMaxSize() else Modifier,
                    smooth = false,
                    unitRes = spec.displayUnitRes,
                    valueDecimals = spec.valueDecimals,
                    integral = spec.tile.integralValues,
                    markReadings = true,
                    secondaryPoints = context.secondaryDays,
                    // One step per reading with its date beneath, unlike any time axis: the
                    // strip is a sequence of readings, and must not look like a period.
                    evenlySpaced = true,
                    highlight = index.takeIf { it >= 0 },
                    fillHeight = expanded,
                    onExpand = onExpand,
                    holdSelection = expanded,
                )
            }
        }
    }
}

/** The types whose screen offers weight and its parts together. */
private val BODY_TYPES = setOf(
    WeightRecord::class,
    BodyFatRecord::class,
    LeanBodyMassRecord::class,
    BodyWaterMassRecord::class,
    BoneMassRecord::class,
)

/** Opens the body-composition view, or Play's purchase sheet where Pro is not owned. */
@Composable
private fun BodyCompositionAction(onOpen: () -> Unit) {
    val activity = LocalActivity.current
    val pro by AppEntitlements.current.pro.collectAsStateWithLifecycle()
    val unlocked = pro.allows(Feature.BODY_COMPOSITION)
    val label = stringResource(R.string.body_composition)
    IconButton(onClick = { if (unlocked) onOpen() else activity?.let(AppEntitlements.current::buy) }) {
        Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = if (unlocked) label else stringResource(R.string.export_premium, label),
        )
    }
}

/** Opens the type picker for a comparison, or Play's purchase sheet where Pro is not owned. */
@Composable
private fun CompareAction(typeName: String, load: suspend () -> List<RecordTypeSpec<*>>, onPick: (String) -> Unit) {
    val activity = LocalActivity.current
    val pro by AppEntitlements.current.pro.collectAsStateWithLifecycle()
    val unlocked = pro.allows(Feature.COMPARE)
    var picking by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.compare_title)
    IconButton(onClick = { if (unlocked) picking = true else activity?.let(AppEntitlements.current::buy) }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
            contentDescription = if (unlocked) label else stringResource(R.string.export_premium, label),
        )
    }
    if (picking) CompareTypeDialog(current = typeName, load = load, onDismiss = { picking = false }, onPick = onPick)
}
