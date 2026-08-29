package de.steppicrew.healthconnectview.ui.dashboard

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
import de.steppicrew.healthconnectview.registry.Formatting
import java.time.Duration

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
    onDismiss: () -> Unit,
) {
    var stats by remember(session) { mutableStateOf<List<SessionStat>?>(null) }

    LaunchedEffect(session) { stats = loadStats(session) }

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
            Column {
                Text(
                    text = stringResource(
                        R.string.session_span,
                        Formatting.time(session.start),
                        Formatting.time(session.end),
                    ) + "  " + Formatting.duration(
                        Duration.between(session.start, session.end),
                    ),
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
                                        (stat.spec.unitRes?.let { " " + stringResource(it) } ?: ""),
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) }
        },
    )
}
