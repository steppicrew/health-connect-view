package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.SubcomposeLayout
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import androidx.compose.material.icons.filled.Insights
import de.steppicrew.healthconnectview.ui.insights.notable
import de.steppicrew.healthconnectview.ui.insights.insightAmount
import de.steppicrew.healthconnectview.ui.insights.Insight
import de.steppicrew.healthconnectview.ui.insights.InsightStrip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R

// The insights tile: the week's most unusual changes, a row each. Split from DashboardScreen.kt.

@Composable
internal fun InsightsTileBody(data: InsightsTileData, width: Int = 1, height: Int = 1) {
    val insights = data.insights
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // A wide tile sets each change on a strip of its usual range: how unusual the week was for
    // that type, which is what the list is ordered by -- the owner, 11.10.2026.
    val wide = width > 1
    when {
        data.locked -> LockableIcon(Icons.Default.Insights, stringResource(R.string.settings_pro), locked = true)
        insights == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        insights.isEmpty() -> Text(stringResource(R.string.insights_tile_few), style = MaterialTheme.typography.bodySmall, color = muted)
        else -> {
            val picks = data.tile.picks
            val notable = insights.notable()
            // Chosen types in their order, moved or not; else the week's most unusual changes. A
            // wide tile fills the rows the changes leave free with the level types nearest their
            // edge, each in its band -- one change above empty rows looked unfinished, the owner
            // found -- and "all level" says nothing about how close a type came.
            val level = picks.isEmpty() && notable.isEmpty()
            val shown = when {
                picks.isNotEmpty() -> picks.mapNotNull { name -> insights.firstOrNull { it.spec.type.simpleName == name } }
                wide -> notable + insights.filter { it !in notable }.sortedByDescending { it.trend.weight }
                else -> notable
            }
            if (level && !wide) {
                Text(stringResource(R.string.insights_tile_level), style = MaterialTheme.typography.bodyMedium)
                return
            }
            InsightRows(shown, header = if (level) stringResource(R.string.insights_tile_level) else null, strip = wide)
        }
    }
}

/**
 * As many rows as the tile's height holds at the reader's font size, the last giving way to
 * "+N weitere" where there are more: a fixed count either left half a 2x1 tile empty or, with
 * large text, ran out of it. The rows are measured, not estimated from the line height: the
 * icon and the strip make a row taller than its text.
 */
@Composable
private fun InsightRows(shown: List<Insight>, header: String?, strip: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val headerStyle = MaterialTheme.typography.bodyMedium
    val moreStyle = MaterialTheme.typography.labelSmall
    // One width for every row's amount, the widest, so the strips beside them line up.
    val amountStyle = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val amounts = shown.map { insightAmount(it) }
    val amountWidth = with(LocalDensity.current) {
        (amounts.maxOfOrNull { measurer.measure(it, amountStyle).size.width } ?: 0).toDp()
    }
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width, maxHeight = constraints.maxHeight)
        val gap = INSIGHT_GAP.dp.roundToPx()
        val headerPlaced = header?.let {
            subcompose("header") { Text(it, style = headerStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                .single().measure(loose)
        }
        val rows = shown.mapIndexed { index, insight ->
            subcompose(index) { InsightRow(insight, strip, amounts[index], amountWidth) }.single().measure(loose)
        }
        fun moreLine(count: Int) = subcompose("more$count") {
            Text(
                text = pluralStringResource(R.plurals.insights_tile_more, count, count),
                style = moreStyle,
                color = muted,
            )
        }.single().measure(loose)
        var used = headerPlaced?.let { it.height + gap } ?: 0
        var count = 0
        while (count < rows.size && used + rows[count].height <= constraints.maxHeight) {
            used += rows[count].height + gap
            count++
        }
        // Room for the "+N" line, given up by the last row if need be.
        val more = if (count < rows.size) {
            var line = moreLine(rows.size - count)
            while (count > 0 && used + line.height > constraints.maxHeight) {
                count--
                used -= rows[count].height + gap
                line = moreLine(rows.size - count)
            }
            line
        } else {
            null
        }
        val placed = listOfNotNull(headerPlaced) + rows.take(count) + listOfNotNull(more)
        val total = placed.sumOf { it.height } + gap * (placed.size - 1).coerceAtLeast(0)
        layout(width, constraints.maxHeight) {
            var y = ((constraints.maxHeight - total) / 2).coerceAtLeast(0)
            placed.forEach {
                it.place(0, y)
                y += it.height + gap
            }
        }
    }
}

/** One type on the insights tile: arrow, name, on a wide tile its strip, and how far it moved. */
@Composable
private fun InsightRow(insight: Insight, strip: Boolean, amount: String, amountWidth: Dp) {
    val style = MaterialTheme.typography.bodySmall
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = insight.trend.direction.icon,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 4.dp, top = firstLineInset(style, INSIGHT_ICON.dp))
                .size(INSIGHT_ICON.dp),
        )
        Text(
            text = stringResource(insight.spec.displayNameRes),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (strip) {
            InsightStrip(
                insight.trend,
                Modifier.weight(1f).padding(start = 8.dp, top = firstLineInset(style, INSIGHT_STRIP.dp)),
            )
        }
        Text(
            text = amount,
            style = style,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 4.dp).width(amountWidth),
        )
    }
}

/** Between the insights tile's rows: tight, since every line is another type shown. */
private const val INSIGHT_GAP = 2

/** The strip's height, to set it on the row's line; see [InsightStrip]. */
private const val INSIGHT_STRIP = 12

private const val INSIGHT_ICON = 16
