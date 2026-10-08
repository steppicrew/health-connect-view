package de.steppicrew.healthconnectview.ui.components

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.registry.Formatting
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * A chart that can be opened full screen, in landscape, with nothing but its [title] beside it.
 *
 * [content] is drawn twice while open: inline with `expanded` false and an `onExpand` to pass
 * on to the chart, and full screen with `expanded` true and no `onExpand`. A chart that is a
 * small strip inline may draw something else full screen -- the route's profiles become line
 * charts with axes there.
 *
 * Landscape is requested rather than waited for: the chart is wide, and the phone's own
 * rotation lock would otherwise keep it narrow. The orientation the screen had is put back on
 * closing. MainActivity handles the turn in place; should the activity be recreated anyway,
 * the orientation is left alone, or the screen would turn back the moment it had turned.
 */
@Composable
fun ExpandableChart(
    title: String,
    content: @Composable (expanded: Boolean, onExpand: (() -> Unit)?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Kept through the recreation the turn causes, which would otherwise read the landscape
    // just requested as the orientation to return to.
    var before by rememberSaveable { mutableStateOf<Int?>(null) }
    val request = LocalExpandRequest.current
    LaunchedEffect(request.title) {
        if (request.title != null && request.title in title) {
            // Claimed only by a chart that stays: a screen still settling draws a chart for a
            // moment and replaces it, and that one would take the request with it.
            delay(SETTLE_MILLIS)
            request.consume()
            expanded = true
        }
    }
    content(false) { expanded = true }
    if (!expanded) return

    val activity = LocalActivity.current
    DisposableEffect(activity) {
        if (activity != null) {
            if (before == null) before = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = before ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                before = null
            }
        }
    }
    Dialog(
        onDismissRequest = { expanded = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The dialog is a window of its own and does not inherit the app's bar tint: its
        // status bar and navigation buttons came out white on the white page. Dark icons on a
        // light surface, light on a dark one -- read from the surface, since the app's theme
        // can differ from the system's.
        val view = LocalView.current
        val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightSurface
                    isAppearanceLightNavigationBars = lightSurface
                }
            }
        }
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                // One line, ellipsised: the row cannot wrap, so the button may sit centred on it.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { expanded = false }) {
                        Icon(Icons.Default.CloseFullscreen, contentDescription = stringResource(R.string.chart_collapse))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { content(true, null) }
            }
        }
    }
}

/**
 * A full-screen chart's title: what it shows, in what unit, over which [period]. Full screen
 * there is nothing else on the page to say it. The unit is left out where it only repeats the
 * name ("Schritte (Schritte)").
 *
 * The period is the one the screen names, not the chart's extent: a day's axis is widened to
 * the night that began the evening before and ends at the next midnight, and titled by it the
 * 16th read "15.08. – 17.08.".
 */
@Composable
fun chartTitle(name: String, @StringRes unitRes: Int?, period: String): String {
    val unit = unitRes?.let { stringResource(it) }
        ?.takeUnless { it.equals(name, ignoreCase = true) }
        ?.let { " ($it)" }.orEmpty()
    return "$name$unit · $period"
}

/** The days from [first] to [last] reading, both included, for a chart with no window of its own. */
fun periodLabel(first: Instant, last: Instant): String {
    val zone = ZoneId.systemDefault()
    return if (first.atZone(zone).toLocalDate() == last.atZone(zone).toLocalDate()) {
        Formatting.date(first)
    } else {
        Formatting.date(first) + " – " + Formatting.date(last)
    }
}

/**
 * A chart to open full screen without a touch: the debug backdoor's way in, since a double tap
 * cannot be sent from the host. [title] is null in release, where nothing can set it.
 */
class ExpandRequest(val title: String?, val consume: () -> Unit)

val LocalExpandRequest = staticCompositionLocalOf { ExpandRequest(null) {} }

private const val SETTLE_MILLIS = 1_500L

/** The mark that opens a chart full screen, beside its readout. */
@Composable
internal fun ExpandButton(onExpand: () -> Unit) {
    IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
        Icon(
            Icons.Default.OpenInFull,
            contentDescription = stringResource(R.string.chart_expand),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
