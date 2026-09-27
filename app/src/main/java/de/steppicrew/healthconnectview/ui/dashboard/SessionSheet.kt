package de.steppicrew.healthconnectview.ui.dashboard

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.billing.Feature
import de.steppicrew.healthconnectview.health.RoutePoint
import de.steppicrew.healthconnectview.health.toPoints
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import de.steppicrew.healthconnectview.ui.components.iconFor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.registry.Formatting

/**
 * Everything recorded during one session.
 *
 * The metrics are not stored on the session: an ExerciseSessionRecord holds only its type,
 * title, notes, segments, laps and route, so distance, power and calories are separate record
 * types written over the same window. This gathers them by time overlap, which is an
 * inference -- a reading taken during the session, not one tagged as belonging to it -- and
 * the footnote says so.
 */
@Composable
fun SessionSheet(
    session: Session,
    loadStats: suspend (Session) -> List<SessionStat>,
    loadRoute: suspend (Session) -> RouteLoad,
    onExportRoute: (List<RoutePoint>, String, Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    var stats by remember(session) { mutableStateOf<List<SessionStat>?>(null) }
    var route by remember(session) { mutableStateOf<RouteLoad?>(null) }
    var declined by remember(session) { mutableStateOf(false) }

    LaunchedEffect(session) { stats = loadStats(session) }
    LaunchedEffect(session) { if (session.route != null) route = loadRoute(session) }

    // The system's own dialog for this one route. It hands the route straight back, so
    // nothing is read again; declining leaves the session as it was.
    val consent = rememberLauncherForActivityResult(ExerciseRouteRequestContract()) { granted ->
        if (granted != null) route = RouteLoad.Shown(granted.toPoints()) else declined = true
    }
    val routeName = session.title ?: stringResource(R.string.route_title)
    val saveGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GPX_MIME)) { uri ->
        val shown = route as? RouteLoad.Shown
        if (uri != null && shown != null) onExportRoute(shown.points, routeName, uri)
    }
    val activity = LocalActivity.current
    val pro by AppEntitlements.current.pro.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = iconFor(session), contentDescription = null)
        },
        title = {
            Text(
                session.title
                    ?: stringResource(R.string.session_sleep).takeIf {
                        session.kind == Session.Kind.SLEEP
                    }
                    ?: stringResource(R.string.session_untitled),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(
                        R.string.session_span,
                        Formatting.time(session.start),
                        Formatting.time(session.end),
                    ) + "  " + Formatting.duration(session.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (val current = stats) {
                    null -> Text(
                        text = stringResource(R.string.session_loading),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )

                    else -> {
                        current.forEach { stat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(stat.spec.displayNameRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = Formatting.number(stat.value) +
                                        (stat.spec.displayUnitRes?.let { " " + stringResource(it) } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (current.isEmpty()) {
                            Text(
                                text = stringResource(R.string.session_no_stats),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.session_overlap_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                session.route?.let { ref ->
                    Text(
                        text = stringResource(R.string.route_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    when (val current = route) {
                        null -> Text(stringResource(R.string.session_loading), style = MaterialTheme.typography.bodySmall)
                        is RouteLoad.Shown -> {
                            RouteView(current.points)
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
                                text = stringResource(if (declined) R.string.route_declined else R.string.route_consent_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { consent.launch(ref.recordId) }) {
                                Text(stringResource(R.string.route_show))
                            }
                        }
                        RouteLoad.Missing, RouteLoad.Failed -> Text(
                            text = stringResource(R.string.route_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) }
        },
    )
}
