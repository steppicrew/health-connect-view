package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.countsFromZero
import de.steppicrew.healthconnectview.health.totalDuration
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.iconFor
import de.steppicrew.healthconnectview.ui.insights.InsightStrip
import de.steppicrew.healthconnectview.ui.insights.colourAt

// The overview tile: several types' figures for the day, each against its usual range.

/**
 * The overview's figures as a grid. A wide tile holds two a row, each with its type's icon and
 * unit over the insights tile's strip, and names beneath what is unusual, with the reader's
 * usual range, where there is room; a 2x2 tile adds the week behind each figure as a small
 * chart (owner's choice, 11.11.2026). A single cell lists them one a row, with a dot where the
 * day is unusual. At the text's own size, not grown to fill a taller tile: larger figures
 * showed nothing more (owner, 11.11.2026).
 *
 * A tap on a figure opens its type on the day, as its own tile would; a long press starts
 * arranging, as anywhere on a tile. In edit mode the figures take no taps, so the tile's own
 * controls do.
 */
@Composable
internal fun OverviewTileBody(
    data: OverviewTileData,
    width: Int,
    height: Int,
    editing: Boolean,
    onOpen: (String) -> Unit,
    onLongClick: () -> Unit,
) {
    val rows = data.rows
    when {
        data.locked -> LockableIcon(Icons.Default.Dashboard, stringResource(R.string.settings_pro), locked = true)
        rows == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else -> {
            val wide = width > 1
            OverviewGrid(
                rows = rows,
                columns = if (wide) 2 else 1,
                strips = wide,
                weeks = wide && height > 1,
                unusual = if (wide) rows.filter { it.unusual && !it.data.loading } else emptyList(),
                enabled = !editing,
                onOpen = onOpen,
                onLongClick = onLongClick,
            )
        }
    }
}

/**
 * As many rows of figures as the tile holds at the reader's font size, the last giving way to
 * "+N weitere" where there are more, then as many of the unusual ones named as still fit:
 * measured, as the insights tile's rows are, since an icon and a strip make a row taller than
 * its text.
 */
@Composable
private fun OverviewGrid(
    rows: List<OverviewRow>,
    columns: Int,
    strips: Boolean,
    weeks: Boolean,
    unusual: List<OverviewRow>,
    enabled: Boolean,
    onOpen: (String) -> Unit,
    onLongClick: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val width = constraints.maxWidth
        // Wider between rows with a week under each, or a chart reads as the next row's.
        val gap = (if (weeks) OVERVIEW_WEEK_GAP else OVERVIEW_GAP).dp.roundToPx()
        val columnGap = OVERVIEW_COLUMN_GAP.dp.roundToPx()
        val cellWidth = ((width - columnGap * (columns - 1)) / columns).coerceAtLeast(0)
        val cells = rows.mapIndexed { index, row ->
            subcompose("cell$index") { OverviewCell(row, strips, weeks, enabled, onOpen, onLongClick) }
                .single().measure(Constraints(minWidth = cellWidth, maxWidth = cellWidth, maxHeight = constraints.maxHeight))
        }
        val lines = cells.chunked(columns)
        val loose = Constraints(maxWidth = width, maxHeight = constraints.maxHeight)
        fun moreLine(count: Int) = subcompose("more$count") {
            Text(pluralStringResource(R.plurals.insights_tile_more, count, count), style = MaterialTheme.typography.labelSmall, color = muted)
        }.single().measure(loose)

        var used = 0
        var shown = 0
        while (shown < lines.size && used + lines[shown].maxOf { it.height } <= constraints.maxHeight) {
            used += lines[shown].maxOf { it.height } + gap
            shown++
        }
        val hidden = { lines.drop(shown).sumOf { it.size } }
        val more = if (shown < lines.size) {
            var line = moreLine(hidden())
            while (shown > 0 && used + line.height > constraints.maxHeight) {
                shown--
                used -= lines[shown].maxOf { it.height } + gap
                line = moreLine(hidden())
            }
            line
        } else {
            null
        }
        more?.let { used += it.height + gap }
        // The unusual named beneath only once every figure is shown: a note about one that is
        // not would point at nothing.
        val notes = if (more == null) {
            val placed = mutableListOf<androidx.compose.ui.layout.Placeable>()
            unusual.forEachIndexed { index, row ->
                val note = subcompose("note$index") { UnusualNote(row) }.single().measure(loose)
                val extra = if (placed.isEmpty()) OVERVIEW_NOTES_GAP.dp.roundToPx() else gap
                if (used + extra + note.height <= constraints.maxHeight) {
                    used += extra + note.height
                    placed += note
                }
            }
            placed
        } else {
            emptyList()
        }
        val total = (used - gap).coerceAtLeast(0)
        layout(width, constraints.maxHeight) {
            var y = ((constraints.maxHeight - total) / 2).coerceAtLeast(0)
            lines.take(shown).forEach { line ->
                line.forEachIndexed { column, cell -> cell.place(column * (cellWidth + columnGap), y) }
                y += line.maxOf { it.height } + gap
            }
            more?.let {
                it.place(0, y)
                y += it.height + gap
            }
            notes.forEachIndexed { index, note ->
                if (index == 0) y += OVERVIEW_NOTES_GAP.dp.roundToPx() - gap
                note.place(0, y)
                y += note.height + gap
            }
        }
    }
}

/** One type's figure: its icon, the day's value, and the day's place against the usual. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OverviewCell(
    row: OverviewRow,
    strip: Boolean,
    week: Boolean,
    enabled: Boolean,
    onOpen: (String) -> Unit,
    onLongClick: () -> Unit,
) {
    val spec = row.data.spec
    val name = stringResource(spec.displayNameRes)
    val style = MaterialTheme.typography.titleSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .combinedClickable(
                enabled = enabled,
                onClickLabel = name,
                onClick = { onOpen(spec.type.simpleName.orEmpty()) },
                onLongClick = onLongClick,
            )
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = iconFor(spec),
                contentDescription = name,
                tint = muted,
                modifier = Modifier
                    .padding(end = 4.dp, top = firstLineInset(style, OVERVIEW_ICON.dp))
                    .size(OVERVIEW_ICON.dp),
            )
            CellValue(row, style, withUnit = strip, modifier = Modifier.weight(1f, fill = false))
            // In a single cell the dot alone, in the strip's colour where the day sits; a wide tile
            // draws the strip and names the type beneath.
            val spreads = row.spreads
            if (!strip && row.unusual && spreads != null) {
                Box(
                    Modifier
                        .padding(start = 4.dp, top = firstLineInset(style, OVERVIEW_DOT.dp))
                        .size(OVERVIEW_DOT.dp)
                        .background(colourAt(spreads), CircleShape),
                )
            }
        }
        val usual = row.usual
        if (strip && usual != null) InsightStrip(usual, Modifier.padding(top = 2.dp))
        // Its room kept while the week is read, so the grid does not jump when it arrives.
        if (week) {
            WeekChart(
                row.week.takeIf { row.data.granted && !row.data.failed },
                fromZero = countsFromZero(row.data.spec),
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(OVERVIEW_WEEK_HEIGHT.dp),
            )
        }
    }
}

/**
 * The day shown and the six before it, the day itself in the accent colour: bars from zero for
 * what adds up -- steps, a day's training -- and a line across the week's own range for a
 * level, where from zero 50 and 57 bpm would stand the same height. A day without a value is a
 * gap. Drawn only, like the strip: the figure above is what a screen reader says.
 */
@Composable
private fun WeekChart(week: List<Double?>?, fromZero: Boolean, modifier: Modifier) {
    val past = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = WEEK_PAST_ALPHA)
    val shown = MaterialTheme.colorScheme.primary
    Canvas(modifier.clearAndSetSemantics { }) {
        val values = week ?: return@Canvas
        val known = values.filterNotNull()
        if (known.isEmpty()) return@Canvas
        val slot = size.width / values.size
        val radius = WEEK_DOT_RADIUS.dp.toPx()
        if (fromZero) {
            val top = known.max().takeIf { it > 0.0 } ?: return@Canvas
            val barWidth = slot * WEEK_BAR_SHARE
            values.forEachIndexed { at, value ->
                if (value == null || value <= 0.0) return@forEachIndexed
                val barHeight = (value / top).toFloat() * size.height
                drawRoundRect(
                    color = if (at == values.lastIndex) shown else past,
                    topLeft = Offset(at * slot + (slot - barWidth) / 2, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 4),
                )
            }
        } else {
            val low = known.min()
            val range = known.max() - low
            fun y(value: Double) = if (range > 0.0) radius + ((1 - (value - low) / range).toFloat()) * (size.height - 2 * radius) else size.height / 2
            val points = values.mapIndexed { at, value -> value?.let { Offset(at * slot + slot / 2, y(it)) } }
            points.zipWithNext().forEach { (from, to) ->
                if (from != null && to != null) drawLine(past, from, to, strokeWidth = WEEK_LINE_WIDTH.dp.toPx())
            }
            points.forEachIndexed { at, point ->
                if (point != null) drawCircle(if (at == points.lastIndex) shown else past, radius, point)
            }
        }
    }
}

/**
 * The day's figure as the type's own single cell writes it, one line: a carried reading muted,
 * a pair as "128/82", a night's sleep as its length and workouts as their count -- with their
 * time where a wide tile has room -- and breathing marked as the day's mean.
 */
@Composable
private fun CellValue(row: OverviewRow, style: TextStyle, withUnit: Boolean, modifier: Modifier = Modifier) {
    val data = row.data
    val spec = data.spec
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val iconAt = Modifier.padding(top = firstLineInset(style, OVERVIEW_ICON.dp)).size(OVERVIEW_ICON.dp)
    when {
        !data.granted -> Icon(Icons.Default.Lock, stringResource(R.string.tile_locked), modifier = iconAt, tint = muted)
        data.loading -> CircularProgressIndicator(iconAt.padding(2.dp), strokeWidth = 1.5.dp)
        data.failed -> Icon(Icons.Default.ErrorOutline, stringResource(R.string.tile_failed), modifier = iconAt, tint = muted)
        else -> {
            val sessions = spec.tile.form == TileSpec.Form.SESSIONS
            val unit = spec.displayUnitRes?.takeIf { withUnit && !sessions && data.secondaryValue == null }
                ?.let { Formatting.unitSpace(it) + stringResource(it) }.orEmpty()
            val text = when {
                sessions && spec.tile.sessionKind == Session.Kind.SLEEP ->
                    data.sessions.takeIf { it.isNotEmpty() }?.let { Formatting.duration(it.totalDuration()) }
                sessions -> when {
                    data.sessions.isEmpty() -> stringResource(R.string.sessions_none)
                    withUnit -> stringResource(R.string.sessions_count, data.sessions.size) + " · " + Formatting.duration(data.sessionDuration)
                    else -> stringResource(R.string.sessions_count, data.sessions.size)
                }
                else -> data.value?.let { value ->
                    val figure = tileValueText(value, data.secondaryValue, spec.valueDecimals) + unit
                    if (row.dayMean) stringResource(R.string.overview_mean, figure) else figure
                }
            }
            Text(
                text = text ?: stringResource(R.string.tile_no_data),
                style = style,
                color = if (text == null || data.valueDate != null || data.sessions.isEmpty() && sessions) muted else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier,
            )
        }
    }
}

/** "Atemfrequenz -- über Ihrem Üblichen / 14,2, sonst 12,1-13,4": the day and the reader's own range. */
@Composable
private fun UnusualNote(row: OverviewRow) {
    val usual = row.usual ?: return
    val spreads = row.spreads ?: return
    val spec = row.data.spec
    val style = MaterialTheme.typography.bodySmall
    val decimals = spec.valueDecimals ?: if (spec.tile.integralValues) 0 else null
    val unit = spec.displayUnitRes?.let { Formatting.unitSpace(it) + stringResource(it) }.orEmpty()
    val day = stringResource(
        R.string.overview_usual_range,
        Formatting.number(usual.recent, decimals) + unit,
        Formatting.number(usual.baseline - usual.spread, decimals),
        Formatting.number(usual.baseline + usual.spread, decimals) + unit,
    )
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(end = 6.dp, top = firstLineInset(style, OVERVIEW_DOT.dp))
                .size(OVERVIEW_DOT.dp)
                .background(colourAt(spreads), CircleShape),
        )
        Column {
            Text(
                text = stringResource(spec.displayNameRes) + " · " +
                    stringResource(if (spreads > 0) R.string.overview_unusual_high else R.string.overview_unusual_low),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = day,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Between the figures' rows: tight, since every line is more of the day shown. */
private const val OVERVIEW_GAP = 6

private const val OVERVIEW_COLUMN_GAP = 8

private const val OVERVIEW_WEEK_GAP = 16

/** Above the unusual ones named beneath the figures, to set them apart from the grid. */
private const val OVERVIEW_NOTES_GAP = 10

private const val OVERVIEW_ICON = 16
private const val OVERVIEW_DOT = 8

/** The week under a figure on a 2x2 tile: about two lines of text high. */
private const val OVERVIEW_WEEK_HEIGHT = 28
private const val WEEK_BAR_SHARE = 0.6f
private const val WEEK_PAST_ALPHA = 0.5f
private const val WEEK_DOT_RADIUS = 2.5f
private const val WEEK_LINE_WIDTH = 1.5f
