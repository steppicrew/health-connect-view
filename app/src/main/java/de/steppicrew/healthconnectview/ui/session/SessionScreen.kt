package de.steppicrew.healthconnectview.ui.session

import androidx.compose.foundation.layout.height
import de.steppicrew.healthconnectview.health.HeartZones
import de.steppicrew.healthconnectview.ui.components.DotText
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import android.net.Uri
import androidx.annotation.StringRes
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.billing.Feature
import de.steppicrew.healthconnectview.health.Lap
import de.steppicrew.healthconnectview.health.RoutePoint
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.heightProfile
import de.steppicrew.healthconnectview.health.profileOf
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.Quantity
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.components.BreakLegend
import de.steppicrew.healthconnectview.ui.components.ExpandableChart
import de.steppicrew.healthconnectview.ui.components.RefreshBox
import de.steppicrew.healthconnectview.ui.components.Hypnogram
import de.steppicrew.healthconnectview.ui.components.ChartSeries
import de.steppicrew.healthconnectview.ui.components.SessionLine
import de.steppicrew.healthconnectview.ui.components.StripSegment
import de.steppicrew.healthconnectview.ui.components.colorOf
import de.steppicrew.healthconnectview.ui.components.labelOf
import de.steppicrew.healthconnectview.health.StageKind
import de.steppicrew.healthconnectview.registry.RecordRegistry
import androidx.compose.ui.graphics.Color
import de.steppicrew.healthconnectview.ui.components.MultiLineChart
import de.steppicrew.healthconnectview.ui.components.SeriesColors
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.ShowExportResults
import de.steppicrew.healthconnectview.ui.components.chartTitle
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.firstLineTextInset
import de.steppicrew.healthconnectview.ui.components.iconFor
import de.steppicrew.healthconnectview.ui.components.periodLabel
import de.steppicrew.healthconnectview.ui.components.sessionName
import de.steppicrew.healthconnectview.ui.dashboard.heartRateSpec
import de.steppicrew.healthconnectview.util.appLabelFor
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One session, full screen: when and how long, who wrote it, the heart rate through it, its
 * route, and what else was recorded over the same window.
 *
 * It replaces a dialog that had no room for a heart-rate chart and grew as its parts arrived.
 * Everything is read before anything is drawn, so nothing moves once it is on screen; the
 * statistics come last because they are the longest part and the least looked at.
 *
 * The metrics are not stored on the session: an ExerciseSessionRecord holds only its type,
 * title, notes, segments, laps and route, so distance, power and calories are separate record
 * types written over the same window. They are gathered by time overlap, which is an
 * inference -- a reading taken during the session, not one tagged as belonging to it -- and
 * the footnote says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(viewModel: SessionViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ShowExportResults(viewModel.exportResults, snackbar)
    val session = (state as? UiState.Data)?.value?.session

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                // The kind only: a writer's title can run to "Rüdersdorf bei Berlin
                // Mountainbiken", which the bar cut off. The header below wraps it whole.
                title = {
                    session?.let {
                        Text(
                            text = stringResource(
                                when (it.kind) {
                                    Session.Kind.EXERCISE -> R.string.type_exercise_session
                                    Session.Kind.SLEEP -> R.string.session_sleep
                                    Session.Kind.MINDFULNESS -> R.string.type_mindfulness_session
                                },
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
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
        val inner = Modifier.padding(padding)
        RefreshBox(
            refreshing = refreshing,
            onRefresh = viewModel::pullRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val current = state) {
                UiState.Loading -> LoadingView(inner)
                UiState.Empty, UiState.NoPermission -> MessageView(
                    icon = Icons.Default.SearchOff,
                    title = stringResource(R.string.session_gone),
                    body = "",
                    modifier = inner,
                )
                is UiState.Error -> MessageView(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.detail_error_title),
                    body = current.message,
                    modifier = inner,
                )
                is UiState.Data -> SessionContent(
                    detail = current.value,
                    onRouteGranted = viewModel::routeGranted,
                    onExportRoute = viewModel::exportRoute,
                    modifier = inner,
                )
            }
        }
    }
}

@Composable
private fun SessionContent(
    detail: SessionDetail,
    onRouteGranted: (ExerciseRoute) -> Unit,
    onExportRoute: (List<RoutePoint>, String, Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = detail.session
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Header(session)

        // A night's stages first, where the writer recorded them: for a night they are what
        // is being asked about, and the heart rate below is the context.
        if (session.stages.isNotEmpty()) {
            Hypnogram(
                stages = session.stages,
                start = session.start,
                end = session.end,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        HeartRate(detail)

        if (session.route != null) {
            Section(stringResource(R.string.route_title))
            Route(session, detail.route, detail.speed, onRouteGranted, onExportRoute)
        }

        if (session.laps.isNotEmpty()) {
            Section(stringResource(R.string.session_laps))
            Laps(session.laps)
        }

        Section(stringResource(R.string.session_statistics))
        Stats(detail)
        detail.heartZones?.let { zones ->
            // Among the figures, last, rather than under the five zones where it was missed:
            // the zones arrive after the rest, so a row added here moves nothing above it.
            StatRow(stringResource(R.string.session_load), Formatting.number(zones.load.toDouble()), strong = true)
        }
        detail.heartZones?.let { zones ->
            Section(stringResource(R.string.session_zones_title))
            Zones(zones, detail.heartRateUnitRes)
        }

        Text(
            text = stringResource(R.string.session_overlap_note) +
                if (detail.movement != null) " " + stringResource(R.string.session_breaks_note) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
        )
    }
}

/** What it was, whole however long its title; on which day and when, how long, and which app wrote it. */
@Composable
private fun Header(session: Session) {
    val style = MaterialTheme.typography.titleMedium
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = iconFor(session),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = firstLineInset(style, HEADER_ICON.dp)).size(HEADER_ICON.dp),
        )
        Column(Modifier.padding(top = firstLineTextInset(style, HEADER_ICON.dp))) {
            Text(text = sessionName(session), style = style)
            Text(
                text = Formatting.date(session.start) + "  " + stringResource(
                    R.string.session_span,
                    Formatting.time(session.start),
                    Formatting.time(session.end),
                ) + "  " + Formatting.duration(session.duration),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.detail_written_by, LocalContext.current.appLabelFor(session.origin)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeartRate(detail: SessionDetail) {
    val spec = heartRateSpec() ?: return
    val title = stringResource(spec.displayNameRes)
    Section(title)
    val curve = detail.heartRate
    when {
        curve != null -> {
            val speedUnit = Quantity.SPEED
            val heightUnit = Quantity.ELEVATION
            // Speed beside heart rate where the watch recorded one, as a chip: a climb reads
            // as speed falling while the heart rate rises.
            val series = listOfNotNull(
                ChartSeries(
                    key = LINE_HEART_RATE,
                    label = title,
                    points = curve,
                    color = SeriesColors.orange(),
                    unitKey = "bpm",
                    unitRes = detail.heartRateUnitRes,
                    integral = true,
                    minSpan = CURVE_MIN_SPAN,
                    zones = detail.heartRateZones,
                    maxGap = LINE_GAP,
                ),
                // The session's own lines beside heart rate, as chips; colours follow the measurement.
                nightSeries(detail, SessionLine.BREATH),
                nightSeries(detail, SessionLine.OXYGEN),
                nightSeries(detail, SessionLine.HRV),
                detail.speed?.let { speed ->
                    ChartSeries(
                        key = LINE_SPEED,
                        label = stringResource(R.string.type_speed),
                        // Sliced like the route's speed profile below, so the two agree.
                        points = profileOf(speed.map { Point(it.time, speedUnit.convert(it.value * MS_TO_KMH)) }),
                        color = SeriesColors.blue(),
                        unitKey = "speed",
                        unitText = speedUnit.symbol(),
                        valueDecimals = 1,
                        maxGap = LINE_GAP,
                    )
                },
                // Height from the route, where it is shown: a climb is what a rising heart rate
                // and falling speed are both about. Sliced like the route's own profile. Grey,
                // the colour of terrain, and apart from the three measurement colours.
                (detail.route as? RouteLoad.Shown)?.points?.let(::heightProfile)?.takeIf { it.size > 1 }?.let { heights ->
                    ChartSeries(
                        key = LINE_HEIGHT,
                        label = stringResource(R.string.chart_short_height),
                        points = heights.map { (time, metres) -> Point(time, heightUnit.convert(metres)) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        unitKey = "height",
                        unitText = heightUnit.symbol(),
                        integral = true,
                        minSpan = HEIGHT_MIN_SPAN,
                        maxGap = LINE_GAP,
                    )
                },
            )
            val stageNames = StageKind.entries.associateWith { stringResource(labelOf(it)) }
            val laps = detail.session.laps.sortedBy { it.start }
            val lapNames = laps.indices.map { stringResource(R.string.session_lap, it + 1) }
            ExpandableChart(
                title = periodLabel(detail.session.start, detail.session.end) + " · " + sessionName(detail.session),
            ) { expanded, onExpand ->
                MultiLineChart(
                    chartId = "session_" + detail.session.kind.name.lowercase(),
                    series = series,
                    defaultShown = listOf(LINE_HEART_RATE),
                    extent = detail.session.start..detail.session.end,
                    breaks = detail.movement?.breaks.orEmpty().map { it.start..it.end },
                    // A night's stages along the bottom, so a rise in heart rate can be laid
                    // against the REM it fell in.
                    strip = detail.session.stages.map { StripSegment(it.start, it.end, colorOf(it.kind)) },
                    // Where the laps meet, the last one's end being the workout's own.
                    markers = laps.dropLast(1).map { it.end },
                    // What the readout names at a moment: the night's stage, or the lap.
                    stripLabel = when {
                        detail.session.stages.isNotEmpty() -> { time ->
                            detail.session.stages.firstOrNull { time >= it.start && time < it.end }?.let { stageNames[it.kind] }
                        }
                        laps.size > 1 -> { time ->
                            laps.indexOfFirst { time >= it.start && time < it.end }.takeIf { it >= 0 }?.let(lapNames::get)
                        }
                        else -> null
                    },
                    fillHeight = expanded,
                    onExpand = onExpand,
                    holdSelection = expanded,
                    modifier = if (expanded) Modifier.fillMaxSize() else Modifier,
                )
            }
        }

        else -> Text(
            text = stringResource(
                if (detail.heartRateLocked) R.string.sessions_locked_heart_rate else R.string.sessions_curve_missing,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Said under the chart, where the band is: the footnote is a screen away.
    if (curve != null && detail.movement?.breaks.orEmpty().isNotEmpty()) {
        BreakLegend(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Route(
    session: Session,
    route: RouteLoad,
    speed: List<Point>?,
    onRouteGranted: (ExerciseRoute) -> Unit,
    onExportRoute: (List<RoutePoint>, String, Uri) -> Unit,
) {
    val ref = session.route ?: return
    var declined by rememberSaveable(session.recordId) { mutableStateOf(false) }
    // The system's own dialog for this one route. It hands the route straight back, so
    // nothing is read again; declining leaves the session as it was.
    val consent = rememberLauncherForActivityResult(ExerciseRouteRequestContract()) { granted ->
        if (granted != null) onRouteGranted(granted) else declined = true
    }
    val routeName = sessionName(session)
    val saveGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GPX_MIME)) { uri ->
        val shown = route as? RouteLoad.Shown
        if (uri != null && shown != null) onExportRoute(shown.points, routeName, uri)
    }
    val activity = LocalActivity.current
    val pro by AppEntitlements.current.pro.collectAsStateWithLifecycle()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    when (route) {
        is RouteLoad.Shown -> {
            RouteView(route.points, speed, pace = session.exerciseType in PACE_TYPES)
            val unlocked = pro.allows(Feature.ROUTE_EXPORT)
            TextButton(
                onClick = {
                    if (unlocked) {
                        val stamp = session.start.atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
                        saveGpx.launch("Route_$stamp.gpx")
                    } else {
                        activity?.let(AppEntitlements.current::buy)
                    }
                },
            ) {
                if (!unlocked) Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                val label = stringResource(R.string.route_export)
                Text(if (unlocked) label else stringResource(R.string.export_premium, label))
            }
        }

        RouteLoad.NeedsConsent -> {
            Text(
                text = stringResource(
                    if (declined) R.string.route_declined else R.string.route_consent_body,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            TextButton(onClick = { consent.launch(ref.recordId) }) {
                Text(stringResource(R.string.route_show))
            }
        }

        RouteLoad.Missing, RouteLoad.Failed -> Text(
            text = stringResource(R.string.route_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

/** Each lap's number, time and length, as its writer recorded them. */
@Composable
private fun Laps(laps: List<Lap>) {
    val km = Quantity.DISTANCE
    laps.forEachIndexed { index, lap ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.session_lap, index + 1),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            lap.meters?.let {
                Text(
                    text = Formatting.number(km.convert(it / 1000)) + " " + km.symbol(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = Formatting.duration(Duration.between(lap.start, lap.end)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What was recorded during the session. Where it was not all spent moving, its total and
 * moving time come first, then each break, so the figures below read as the time spent moving.
 */
@Composable
private fun Stats(detail: SessionDetail) {
    val session = detail.session
    val movement = detail.movement
    // Shown only where the two would read differently: "7h 49m" and "7h 49m" says nothing.
    val total = Formatting.duration(session.duration)
    val moving = movement?.let { Formatting.duration(it.moving) }
    if (moving != null && moving != total) {
        StatRow(stringResource(R.string.session_time_total), total)
        StatRow(stringResource(R.string.session_time_moving), moving)
    }
    movement?.breaks.orEmpty().forEach { pause ->
        StatRow(
            label = stringResource(R.string.session_break),
            value = Formatting.duration(pause.duration),
            detail = stringResource(R.string.session_span, Formatting.time(pause.start), Formatting.time(pause.end)),
        )
    }
    if (detail.stats.isEmpty()) {
        Text(
            text = stringResource(R.string.session_no_stats),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    detail.stats.forEach { stat ->
        val unit = stat.spec.displayUnitRes?.let { " " + stringResource(it) } ?: ""
        StatRow(
            label = stringResource(stat.spec.displayNameRes),
            value = Formatting.number(stat.value) + unit,
            // The spread under the mean: an average heart rate says little about a
            // session without its peak.
            detail = when {
                stat.low != null && stat.high != null -> stringResource(
                    R.string.session_stat_range,
                    Formatting.number(stat.low),
                    Formatting.number(stat.high) + unit,
                )
                stat.high != null -> stringResource(R.string.session_stat_max, Formatting.number(stat.high) + unit)
                else -> null
            },
        )
    }
}

/**
 * Time in each heart-rate zone as one bar of five shades, deepening with effort, then a row per
 * zone with its beats and its time, and the load. The note under them says how both are made
 * and where the maximum comes from; no verdict.
 */
@Composable
private fun Zones(zones: HeartZones, @StringRes unitRes: Int?) {
    val unit = unitRes?.let { " " + stringResource(it) } ?: ""
    val total = zones.times.sumOf { it.toMillis() }
    if (total > 0) {
        val gap = MaterialTheme.colorScheme.surface
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(ZONE_BAR_HEIGHT.dp)
                .padding(vertical = 4.dp),
        ) {
            var x = 0f
            zones.times.forEachIndexed { index, time ->
                val width = size.width * time.toMillis() / total
                if (width <= 0f) return@forEachIndexed
                drawRect(ZONE_COLORS[index], topLeft = Offset(x, 0f), size = Size(width, size.height))
                // A 2 dp surface gap between neighbours, as between any two fills.
                if (x > 0f) drawRect(gap, topLeft = Offset(x - 1.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height))
                x += width
            }
        }
    }
    zones.times.forEachIndexed { index, time ->
        val (low, high) = zones.boundsOf(index)
        StatRow(
            label = stringResource(R.string.session_zone, index + 1),
            value = Formatting.duration(time),
            detail = Formatting.number(low.toDouble()) + "–" + Formatting.number(high.toDouble()) + unit,
            swatch = ZONE_COLORS[index],
        )
    }
    Text(
        text = stringResource(
            if (zones.maxFromSettings) R.string.session_zones_note_set else R.string.session_zones_note_data,
            Formatting.number(zones.max.toDouble()) + unit,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * Heart rate's orange, light to dark: one hue for a quantity that only grows, so the order
 * reads without a legend.
 */
private val ZONE_COLORS = listOf(
    Color(0xFFFAD3C0),
    Color(0xFFF5A882),
    Color(0xFFEB6834),
    Color(0xFFC04A1C),
    Color(0xFF8A3211),
)

private const val ZONE_BAR_HEIGHT = 20

/**
 * One figure: its name, its value, and a smaller line under the value where there is one.
 * [strong] sets it in bold, for the one figure that sums up the session.
 */
@Composable
private fun StatRow(label: String, value: String, detail: String? = null, swatch: Color? = null, strong: Boolean = false) {
    val weight = if (strong) FontWeight.Bold else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (swatch != null) {
            // The colour the row stands for in the bar above, on the label's first line.
            DotText(
                color = swatch,
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = weight,
                modifier = Modifier.weight(1f),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

private const val HEADER_ICON = 28

/** Keys the remembered choice of lines by; see ChartLinesStore. */
private val LINE_HEART_RATE = SessionLine.HEART_RATE.key
private const val LINE_SPEED = "speed"


/** One of a night's readings as a chart line, named and measured as its type is; null where none. */
@Composable
private fun nightSeries(detail: SessionDetail, line: SessionLine): ChartSeries? {
    val points = detail.nightLines[line.typeName] ?: return null
    val spec = RecordRegistry.specOrNull(line.typeName) ?: return null
    return ChartSeries(
        key = line.key,
        label = stringResource(line.shortLabel ?: spec.displayNameRes),
        points = line.shown(points),
        color = line.color(),
        unitKey = line.typeName,
        minSpan = line.minSpan,
        unitRes = spec.displayUnitRes,
        integral = spec.tile.integralValues,
        maxGap = if (line.dots) null else LINE_GAP,
        dots = line.dots,
        reference = spec.tile.referenceRange,
    )
}

/** A line is broken where its readings stop for longer: a watch out of range, not a value. */
private val LINE_GAP: Duration = Duration.ofMinutes(5)

private const val MS_TO_KMH = 3.6

private const val LINE_HEIGHT = "height"

/** A flat ride's few metres must not fill the height as if it were a pass. */
private const val HEIGHT_MIN_SPAN = 50.0

/** As on the session rows: a floor so a steady session does not draw each beat as a cliff. */
private const val CURVE_MIN_SPAN = 20.0

/** Activities whose speed is read as a pace, minutes per km; the rest stay in km/h. */
private val PACE_TYPES = setOf(
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
)
