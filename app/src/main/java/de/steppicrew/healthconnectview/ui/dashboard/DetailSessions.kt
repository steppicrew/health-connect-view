package de.steppicrew.healthconnectview.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.counted
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.ui.components.ExpandableChart
import de.steppicrew.healthconnectview.ui.components.Hypnogram
import de.steppicrew.healthconnectview.ui.components.LineChart
import de.steppicrew.healthconnectview.ui.components.BreakLegend
import de.steppicrew.healthconnectview.ui.session.SessionCurve
import de.steppicrew.healthconnectview.ui.components.chartTitle
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.firstLineTextInset
import de.steppicrew.healthconnectview.ui.components.iconFor
import de.steppicrew.healthconnectview.ui.components.periodLabel
import de.steppicrew.healthconnectview.ui.components.sessionName
import kotlinx.coroutines.flow.map

// The detail screen's sessions: a row per workout or night with its curve. Split from
// TileDetailScreen.kt.

@Composable
internal fun SessionCaption(session: Session, onClick: () -> Unit) {
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

/** Material's minimum touch target, which a text button is laid out at. */
private const val TOUCH_TARGET = 48

/**
 * One session as a row on a session type's own screen: what it was, when, how long, and the
 * heart rate recorded during it.
 *
 * The curve is the reason this is a row rather than a caption. A session is a span with no
 * value of its own -- the readings that describe it are separate types over the same window --
 * so showing them here is what turns "a 53-minute activity" into something you can read.
 */
/**
 * Whether a session's row draws its heart rate. Not a workout's or a night's: theirs is on
 * their own page with the other lines, and a chart per row only pushed later ones a screen
 * further down -- the owner's report, 09.10.2026, for workouts and then for nights. A night
 * keeps its stages, which are what its list is read for.
 */
internal fun rowShowsCurve(session: Session): Boolean =
    session.kind != Session.Kind.EXERCISE && session.kind != Session.Kind.SLEEP

private sealed interface CurveLoad {
    data object Loading : CurveLoad
    data class Done(val curve: SessionCurve?) : CurveLoad
}

@Composable
internal fun SessionRow(
    session: Session,
    loadCurve: suspend (Session) -> SessionCurve,
    zones: ValueZones?,
    @StringRes heartRateUnitRes: Int?,
    heartRateLocked: Boolean,
    onClick: () -> Unit,
) {
    val curveShown = rowShowsCurve(session)
    val statistics = stringResource(R.string.session_statistics)
    // Clickable as a whole only without a chart: a chart needs the touches for its readout,
    // and a row that both scrubs a curve and opens a page would do the wrong one half the time.
    Column(
        modifier = Modifier
            .then(if (curveShown) Modifier else Modifier.clickable(onClick = onClick))
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
            // Moving time where it was read, matching the total above the list.
            Text(
                text = Formatting.duration(session.counted),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Said in a word: an "i" read as "explain this" and a bare chevron as decoration,
            // both reported by the owner, 09.10.2026.
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(start = 8.dp, end = 0.dp),
                // Lifted so its label sits on the name's line, not between the two: the touch
                // target is taller than a line (see FirstLine.kt).
                modifier = Modifier
                    .offset(y = -firstLineTextInset(MaterialTheme.typography.bodyLarge, TOUCH_TARGET.dp))
                    .semantics { contentDescription = statistics },
            ) {
                Text(stringResource(R.string.session_details), maxLines = 1)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
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
            value = CurveLoad.Done(if (heartRateLocked || !curveShown) null else loadCurve(session))
        }
        val curve = (curveLoad as? CurveLoad.Done)?.curve?.points
        val breaks = (curveLoad as? CurveLoad.Done)?.curve?.breaks.orEmpty()

        if (curveLoad is CurveLoad.Done) when {
            // The full chart rather than a spark line: at this size the samples are dense
            // enough to be worth reading individually, and the readout answers "what was my
            // rate at that dip" -- which a curve you cannot touch only poses.
            curve != null -> ExpandableChart(
                chartTitle(stringResource(R.string.type_heart_rate), heartRateUnitRes, periodLabel(session.start, session.end)) +
                    " · " + sessionName(session),
            ) { expanded, onExpand ->
                LineChart(
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
                    breaks = breaks.map { it.start..it.end },
                    fillHeight = expanded,
                    onExpand = onExpand,
                    holdSelection = expanded,
                    modifier = if (expanded) Modifier.fillMaxSize() else Modifier,
                )
            }

            // Distinct explanations for the same blank space: nothing was recorded, versus
            // the app is not allowed to look. The locked case is said once for the whole
            // list rather than repeated on every row.
            !heartRateLocked && curveShown -> Text(
                text = stringResource(R.string.sessions_curve_missing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (curve != null && breaks.isNotEmpty()) BreakLegend(Modifier.padding(top = 4.dp))
    }
}

private const val SESSION_ICON = 16

private const val SESSION_CURVE_HEIGHT = 40

/** A session's heart-rate axis spans at least this many bpm, so a steady night reads flat. */
private const val SESSION_CURVE_MIN_SPAN = 20.0
