package de.steppicrew.healthconnectview.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import kotlinx.coroutines.delay

@Composable
fun LoadingView(modifier: Modifier = Modifier, progress: Float? = null) {
    // Nothing for a moment: a load that finishes quickly flashed a bar for a frame or two.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_SHOW_AFTER_MS)
        visible = true
    }
    // Glides between the values the steps report. Reads arrive in uneven pieces -- a quarter
    // of a year at a time, or a total after a long chart -- and drawn as they came the bar
    // jumped instead of moving.
    val shown by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(LOADING_GLIDE_MS, easing = LinearEasing),
        label = "loading progress",
    )
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!visible) return@Column
        // A bar where the steps are known, so a long load shows it is moving; the spinner
        // otherwise, rather than a bar that would have to guess.
        if (progress == null) {
            CircularProgressIndicator()
        } else {
            LinearProgressIndicator(
                progress = { shown },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        SlowLoadingNote()
    }
}

/**
 * Words under a load that runs long. A year of readings can take Health Connect a minute, and
 * a spinner alone then looked like an app hung after waking: people closed it. The line
 * changes every few seconds, which a frozen screen would not.
 */
@Composable
private fun SlowLoadingNote() {
    val shown = rememberSlowLoadingNote(active = true, after = LOADING_SLOW_AFTER_MS - LOADING_SHOW_AFTER_MS)
    Crossfade(targetState = shown, label = "slow loading note") { note ->
        Text(
            text = note?.let { stringResource(it) }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Room for the longest line from the start, so the spinner does not move.
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
        )
    }
}

/**
 * The same words over a screen that stays usable while parts of it load, such as the
 * dashboard, whose tiles each spin on their own: a pill at the bottom, over the content rather
 * than above it, so nothing moves when it comes or goes.
 */
@Composable
fun BoxScope.SlowLoadingPill(active: Boolean, modifier: Modifier = Modifier) {
    val shown = rememberSlowLoadingNote(active)
    AnimatedVisibility(
        visible = shown != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 4.dp,
        ) {
            val style = MaterialTheme.typography.bodyMedium
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = firstLineInset(style, PILL_SPINNER))
                        .size(PILL_SPINNER),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                // The last line stays while the pill fades out, rather than an empty pill.
                var last by remember { mutableIntStateOf(SLOW_NOTES.first()) }
                if (shown != null) last = shown
                Crossfade(targetState = last, label = "slow loading pill") { note ->
                    Text(
                        text = stringResource(note),
                        style = style,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}

/**
 * The line to show while a load has run longer than [after], changing every few seconds; null
 * before that and once [active] turns false.
 */
@Composable
private fun rememberSlowLoadingNote(active: Boolean, after: Long = LOADING_SLOW_AFTER_MS): Int? {
    var shown by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(active) {
        shown = null
        if (!active) return@LaunchedEffect
        delay(after)
        var index = 0
        while (true) {
            shown = SLOW_NOTES[index % SLOW_NOTES.size]
            delay(LOADING_NOTE_EVERY_MS)
            index++
        }
    }
    return shown
}

private val SLOW_NOTES = listOf(R.string.loading_slow_1, R.string.loading_slow_2, R.string.loading_slow_3)

/** How long a load runs before it says that it is still running. */
private const val LOADING_SLOW_AFTER_MS = 5_000L

private val PILL_SPINNER = 16.dp

/** How long each of those lines stays before the next. */
private const val LOADING_NOTE_EVERY_MS = 2_500L

/** How long a load may run before anything is drawn for it. */
private const val LOADING_SHOW_AFTER_MS = 300L

/** How long the bar takes to glide to a newly reported value. */
private const val LOADING_GLIDE_MS = 600

/** Shared empty/error/no-permission presentation, optionally with a single action. */
@Composable
fun MessageView(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 24.dp)) {
                Text(actionLabel)
            }
        }
    }
}
