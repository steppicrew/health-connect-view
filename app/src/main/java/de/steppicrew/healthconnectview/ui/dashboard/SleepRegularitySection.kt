package de.steppicrew.healthconnectview.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.ClockSpread
import de.steppicrew.healthconnectview.health.MINUTES_PER_DAY
import de.steppicrew.healthconnectview.health.SleepRegularity
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.ui.components.InfoGroup
import de.steppicrew.healthconnectview.ui.components.InfoTitle
import de.steppicrew.healthconnectview.ui.components.rememberExplanation

/**
 * When the window's nights began and ended: a strip of the clock shaded by how many nights
 * were asleep at each time, with the usual start and end marked, and beneath it each as its
 * middle time and the times the middle half of the nights fell between. Describes, never
 * rates: there is no "good" spread to measure against. How the times are read folds out
 * behind an "i", like the other explanations.
 */
@Composable
internal fun SleepRegularitySection(regularity: SleepRegularity) {
    val explanation = rememberExplanation("sleep_regularity")
    InfoGroup {
        InfoTitle(stringResource(R.string.sleep_regularity_title), explanation)
        Text(
            text = pluralStringResource(R.plurals.sleep_regularity_nights, regularity.nights, regularity.nights),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SleepWindowStrip(regularity, Modifier.padding(top = 8.dp))
        SpreadRow(R.string.sleep_regularity_start, regularity.start)
        SpreadRow(R.string.sleep_regularity_end, regularity.end)
        if (explanation.expanded == true) {
            Text(
                text = stringResource(R.string.sleep_regularity_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SpreadRow(@StringRes label: Int, spread: ClockSpread) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(Formatting.clock(spread.usual), style = MaterialTheme.typography.bodyMedium)
            // Every night at one time has no range to name.
            if (spread.early != spread.late) {
                Text(
                    text = stringResource(R.string.sleep_regularity_middle, Formatting.clock(spread.early), Formatting.clock(spread.late)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The clock from a little before most nights began to a little after most ended, each few
 * minutes as dark as the share of nights asleep then. Read on one line through midnight, so a
 * night is one piece. The rows beneath say the same in words, so the strip is left out of what
 * a screen reader reads.
 */
@Composable
private fun SleepWindowStrip(regularity: SleepRegularity, modifier: Modifier = Modifier) {
    val window = remember(regularity) { sleepWindow(regularity) }
    val hue = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val mark = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxWidth()
            .height((STRIP_HEIGHT + LABEL_GAP + LABEL_HEIGHT).dp)
            .clearAndSetSemantics { },
    ) {
        val strip = STRIP_HEIGHT.dp.toPx()
        val span = (window.last - window.first).toFloat()
        fun x(minute: Int) = (minute - window.first) / span * size.width
        drawRoundRect(empty, size = Size(size.width, strip), cornerRadius = CornerRadius(strip * CORNER))
        // Runs of equal bins as one piece: bin by bin left hairline seams between them.
        var i = 0
        while (i < window.asleep.size) {
            val count = window.asleep[i]
            var end = i + 1
            while (end < window.asleep.size && window.asleep[end] == count) end++
            if (count > 0) {
                val from = x(window.first + i * BIN_MINUTES)
                drawRect(
                    color = hue.copy(alpha = count.toFloat() / regularity.nights),
                    topLeft = Offset(from, 0f),
                    size = Size(x(window.first + end * BIN_MINUTES) - from, strip),
                )
            }
            i = end
        }
        // The usual start and end, across the strip.
        listOf(window.usualStart, window.usualEnd).forEach {
            drawLine(mark, Offset(x(it), 0f), Offset(x(it), strip), strokeWidth = MARK_WIDTH.dp.toPx())
        }
        // Round hours beneath, as many as fit without touching.
        var lastEnd = Float.NEGATIVE_INFINITY
        var hour = Math.floorDiv(window.first + window.tickMinutes - 1, window.tickMinutes) * window.tickMinutes
        while (hour <= window.last) {
            val label = measurer.measure(Formatting.clock(hour.mod(MINUTES_PER_DAY)), labelStyle)
            val left = x(hour) - label.size.width / 2
            if (left >= 0 && left + label.size.width <= size.width && left >= lastEnd) {
                drawText(label, topLeft = Offset(left, strip + LABEL_GAP.dp.toPx()))
                lastEnd = left + label.size.width + LABEL_SPACING.dp.toPx()
            }
            hour += window.tickMinutes
        }
    }
}

/**
 * The strip's clock, in minutes counted on through midnight: [first] and [last] on whole
 * hours, [asleep] the nights asleep in each [BIN_MINUTES] from [first].
 */
private class SleepWindow(
    val first: Int,
    val last: Int,
    val asleep: IntArray,
    val usualStart: Int,
    val usualEnd: Int,
    val tickMinutes: Int,
)

private fun sleepWindow(regularity: SleepRegularity): SleepWindow {
    // Every time placed within the day centred on the usual start, so a night never wraps.
    val anchor = regularity.start.usual - MINUTES_PER_DAY / 2
    fun onward(minute: Int) = anchor + (minute - anchor).mod(MINUTES_PER_DAY)
    val nights = regularity.start.all.zip(regularity.end.all) { start, end ->
        val from = onward(start)
        from to from + (end - start).mod(MINUTES_PER_DAY)
    }
    val starts = nights.map { it.first }.sorted()
    val ends = nights.map { it.second }.sorted()
    // Most nights, not all: one night begun at 15:00 would squeeze the rest into a sliver.
    val first = floorHour(starts[(starts.size * EDGE_SHARE).toInt()] - MARGIN_MINUTES)
    val last = ceilHour(ends[(ends.size * (1 - EDGE_SHARE)).toInt().coerceAtMost(ends.lastIndex)] + MARGIN_MINUTES)
    val asleep = IntArray((last - first) / BIN_MINUTES)
    nights.forEach { (from, to) ->
        for (i in asleep.indices) {
            val middle = first + i * BIN_MINUTES + BIN_MINUTES / 2
            if (middle in from until to) asleep[i]++
        }
    }
    val usualStart = onward(regularity.start.usual)
    val usualEnd = usualStart + (regularity.end.usual - regularity.start.usual).mod(MINUTES_PER_DAY)
    val tick = if (last - first > LONG_WINDOW) 3 * HOUR else 2 * HOUR
    return SleepWindow(first, last, asleep, usualStart, usualEnd, tick)
}

private fun floorHour(minute: Int) = Math.floorDiv(minute, HOUR) * HOUR
private fun ceilHour(minute: Int) = -floorHour(-minute)

private const val HOUR = 60
private const val BIN_MINUTES = 5
private const val MARGIN_MINUTES = 30
private const val EDGE_SHARE = 0.05
private const val LONG_WINDOW = 12 * HOUR
private const val STRIP_HEIGHT = 20
private const val LABEL_GAP = 2
private const val LABEL_HEIGHT = 16
private const val LABEL_SPACING = 8
private const val MARK_WIDTH = 2
private const val CORNER = 0.2f
