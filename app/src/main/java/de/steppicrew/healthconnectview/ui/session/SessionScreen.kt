package de.steppicrew.healthconnectview.ui.session

import android.net.Uri
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
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.Quantity
import de.steppicrew.healthconnectview.ui.UiState
import de.steppicrew.healthconnectview.ui.components.ExpandableChart
import de.steppicrew.healthconnectview.ui.components.Hypnogram
import de.steppicrew.healthconnectview.ui.components.LineChart
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
        Stats(detail.stats)

        Text(
            text = stringResource(R.string.session_overlap_note),
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
        curve != null -> ExpandableChart(
            title = chartTitle(title, detail.heartRateUnitRes, periodLabel(detail.session.start, detail.session.end)) +
                " · " + sessionName(detail.session),
        ) { expanded, onExpand ->
            LineChart(
                points = curve,
                smooth = false,
                unitRes = detail.heartRateUnitRes,
                zones = detail.heartRateZones,
                // A session curve is heart rate at full resolution: a dot per sample buries it.
                markReadings = false,
                integral = true,
                minSpan = CURVE_MIN_SPAN,
                extent = detail.session.start..detail.session.end,
                fillHeight = expanded,
                onExpand = onExpand,
                holdSelection = expanded,
                modifier = if (expanded) Modifier.fillMaxSize() else Modifier,
            )
        }

        else -> Text(
            text = stringResource(
                if (detail.heartRateLocked) R.string.sessions_locked_heart_rate else R.string.sessions_curve_missing,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

@Composable
private fun Stats(stats: List<SessionStat>) {
    if (stats.isEmpty()) {
        Text(
            text = stringResource(R.string.session_no_stats),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    stats.forEach { stat ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(stat.spec.displayNameRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Formatting.number(stat.value) +
                    (stat.spec.displayUnitRes?.let { " " + stringResource(it) } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
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

/** As on the session rows: a floor so a steady session does not draw each beat as a cliff. */
private const val CURVE_MIN_SPAN = 20.0

/** Activities whose speed is read as a pace, minutes per km; the rest stay in km/h. */
private val PACE_TYPES = setOf(
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
)
