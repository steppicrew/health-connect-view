package de.steppicrew.healthconnectview.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Span
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Span chips and the window stepper, shared by every screen that shows a movable window.
 *
 * Both the tile's full-screen view and the catalog's type detail need the same three
 * controls. They were private to the tile screen while it was the only one with an offset;
 * a second copy would be a second place for "which window is this" to drift.
 *
 * [spans] is a parameter because not every screen offers every span: a screen charting from
 * day-wide buckets cannot offer [Span.DAY], which has no Period-expressible bucket.
 */
@Composable
fun SpanSelector(
    selected: Span,
    onSelect: (Span) -> Unit,
    spans: List<Span> = Span.entries,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        spans.forEach { span ->
            FilterChip(
                selected = span == selected,
                onClick = { onSelect(span) },
                label = { Text(stringResource(span.labelRes)) },
            )
        }
    }
}

@Composable
fun WindowStepper(
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

/**
 * Steps the window by a horizontal swipe, as the [WindowStepper] arrows do: a swipe to the
 * right reveals the previous window, as on a calendar, and to the left the next one.
 *
 * Put on the screen's content rather than on the chart. The chart pans and reads values by
 * dragging and consumes those gestures, which a parent's drag detector then ignores -- so a
 * drag that starts on the chart stays the chart's, and the rest of the screen swipes.
 *
 * Keyed on its callbacks as well as on [canStepForward]; bound references such as
 * `viewModel::stepBack` compare equal across recompositions, so this does not restart.
 */
fun Modifier.swipeToStep(
    canStepForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
): Modifier = pointerInput(canStepForward, onBack, onForward) {
    val threshold = SWIPE_THRESHOLD_DP.dp.toPx()
    var travelled = 0f
    detectHorizontalDragGestures(
        onDragStart = { travelled = 0f },
        onDragEnd = {
            when {
                travelled > threshold -> onBack()
                travelled < -threshold && canStepForward -> onForward()
            }
        },
        onHorizontalDrag = { change, amount ->
            change.consume()
            travelled += amount
        },
    )
}

/** Far enough that a sloppy vertical scroll does not step the window by accident. */
private const val SWIPE_THRESHOLD_DP = 64

/**
 * The window being shown, derived from the span rather than from the loaded data.
 *
 * A day with nothing recorded still *is* a day, and naming it is what lets the user step to
 * another one: taking the label from the data left an empty screen with no date at all, and
 * therefore no clue which day had nothing in it.
 */
@Composable
fun windowLabel(span: Span, offset: Int): String {
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
