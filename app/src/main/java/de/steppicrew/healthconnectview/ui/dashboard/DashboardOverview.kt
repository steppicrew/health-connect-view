package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.totalDuration
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.iconFor
import de.steppicrew.healthconnectview.ui.insights.InsightStrip
import de.steppicrew.healthconnectview.ui.insights.colourAt

// The overview tile: several types' figures for the day, each against its usual range.

/**
 * The overview's figures as a grid -- the owner's pick of two forms, 11.11.2026. A wide row
 * holds three figures, each with its type's icon and a dot where the day is unusual for it; a
 * 2x2 tile holds two a row, each over the insights tile's strip, and names beneath what is
 * unusual, with the reader's usual range. A single cell lists them one a row.
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
            val tall = height > 1
            val columns = when {
                width == 1 -> 1
                tall -> 2
                else -> 3
            }
            OverviewGrid(
                rows = rows,
                columns = columns,
                strips = tall,
                unusual = if (tall) rows.filter { it.unusual && !it.data.loading } else emptyList(),
                enabled = !editing,
                onOpen = onOpen,
                onLongClick = onLongClick,
            )
        }
    }
}

/**
 * The figures as large as the tile lets every one of them stand whole, then spread over its
 * height: on the owner's phone (10.11.2026) six figures at one fixed size filled a third of a
 * 2x2 tile. The size is chosen by measuring, since the widest figure -- a mean with its unit --
 * decides it, and that depends on the day's values, the language and the reader's font size.
 *
 * Where even the smallest size holds too few rows, the last gives way to "+N weitere", as on the
 * insights tile; the unusual ones are named beneath only once every figure is shown.
 */
@Composable
private fun OverviewGrid(
    rows: List<OverviewRow>,
    columns: Int,
    strips: Boolean,
    unusual: List<OverviewRow>,
    enabled: Boolean,
    onOpen: (String) -> Unit,
    onLongClick: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val styles = with(MaterialTheme.typography) { listOf(headlineMedium, headlineSmall, titleLarge, titleMedium, titleSmall) }
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gap = OVERVIEW_GAP.dp.roundToPx()
        val notesGap = OVERVIEW_NOTES_GAP.dp.roundToPx()
        val columnGap = OVERVIEW_COLUMN_GAP.dp.roundToPx()
        val cellWidth = ((width - columnGap * (columns - 1)) / columns).coerceAtLeast(0)
        val loose = Constraints(maxWidth = width, maxHeight = height)
        val notes = unusual.mapIndexed { index, row -> subcompose("note$index") { UnusualNote(row) }.single().measure(loose) }
        val notesHeight = notes.sumOf { it.height + gap } - gap + notesGap

        // The cells at one size, or null where one of them would be cut short.
        fun linesAt(level: Int): List<List<Placeable>>? {
            val measurables = rows.mapIndexed { index, row ->
                subcompose("cell$level-$index") { OverviewCell(row, styles[level], strips, enabled, onOpen, onLongClick) }.single()
            }
            if (level < styles.lastIndex && measurables.any { it.maxIntrinsicWidth(height) > cellWidth }) return null
            return measurables
                .map { it.measure(Constraints(minWidth = cellWidth, maxWidth = cellWidth, maxHeight = height)) }
                .chunked(columns)
        }
        fun heightOf(lines: List<List<Placeable>>) = lines.sumOf { line -> line.maxOf { it.height } + gap } - gap

        // The largest size at which every figure fits with the notes beneath; failing that, the
        // largest at which the figures do; failing that, the smallest.
        var lines: List<List<Placeable>> = emptyList()
        var withoutNotes: List<List<Placeable>>? = null
        for (level in styles.indices) {
            val candidate = linesAt(level) ?: continue
            val figures = heightOf(candidate)
            if (figures + (if (notes.isEmpty()) 0 else notesHeight) <= height) {
                lines = candidate
                withoutNotes = null
                break
            }
            if (withoutNotes == null && figures <= height) withoutNotes = candidate
            lines = candidate
        }
        withoutNotes?.let { lines = it }

        var used = 0
        var shown = 0
        while (shown < lines.size && used + lines[shown].maxOf { it.height } <= height) {
            used += lines[shown].maxOf { it.height } + gap
            shown++
        }
        val hidden = { lines.drop(shown).sumOf { it.size } }
        fun moreLine(count: Int) = subcompose("more$count") {
            Text(pluralStringResource(R.plurals.insights_tile_more, count, count), style = MaterialTheme.typography.labelSmall, color = muted)
        }.single().measure(loose)
        val more = if (shown < lines.size) {
            var line = moreLine(hidden())
            while (shown > 0 && used + line.height > height) {
                shown--
                used -= lines[shown].maxOf { it.height } + gap
                line = moreLine(hidden())
            }
            line
        } else {
            null
        }
        more?.let { used += it.height + gap }
        val placed = mutableListOf<Placeable>()
        if (more == null) {
            notes.forEach { note ->
                val extra = if (placed.isEmpty()) notesGap else gap
                if (used - gap + extra + note.height <= height) {
                    used += extra - gap + note.height + gap
                    placed += note
                }
            }
        }
        // What room is left goes between the rows, up to a point, and the rest above and below.
        val content = (used - gap).coerceAtLeast(0)
        val spaces = shown - 1 + (if (more != null) 1 else 0) + (if (placed.isNotEmpty()) 1 else 0)
        val spread = if (spaces > 0) ((height - content) / (spaces + 1)).coerceIn(0, OVERVIEW_SPREAD_MAX.dp.roundToPx()) else 0
        layout(width, height) {
            var y = ((height - content - spread * spaces) / 2).coerceAtLeast(0)
            lines.take(shown).forEach { line ->
                line.forEachIndexed { column, cell -> cell.place(column * (cellWidth + columnGap), y) }
                y += line.maxOf { it.height } + gap + spread
            }
            more?.let {
                it.place(0, y)
                y += it.height + gap + spread
            }
            placed.forEachIndexed { index, note ->
                if (index == 0) y += notesGap - gap
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
    style: TextStyle,
    strip: Boolean,
    enabled: Boolean,
    onOpen: (String) -> Unit,
    onLongClick: () -> Unit,
) {
    val spec = row.data.spec
    val name = stringResource(spec.displayNameRes)
    // The icon grows with the figure, a little smaller than its line.
    val icon = with(LocalDensity.current) { style.fontSize.toDp() } * OVERVIEW_ICON_SCALE
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
                    .padding(end = 4.dp, top = firstLineInset(style, icon))
                    .size(icon),
            )
            CellValue(row, style, icon, withUnit = strip, modifier = Modifier.weight(1f, fill = false))
            // On a 2x1 tile the dot alone, in the strip's colour where the day sits; a 2x2 one
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
    }
}

/**
 * The day's figure as the type's own single cell writes it, one line: a carried reading muted,
 * a pair as "128/82", a night's sleep as its length and workouts as their count -- with their
 * time where a 2x2 tile has room -- and breathing marked as the day's mean. The unit and a
 * workout's time are small and muted beside the number, as on the type's own tile, so the
 * number can be large in the room a tile has.
 */
@Composable
private fun CellValue(row: OverviewRow, style: TextStyle, icon: Dp, withUnit: Boolean, modifier: Modifier = Modifier) {
    val data = row.data
    val spec = data.spec
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val small = SpanStyle(fontSize = MaterialTheme.typography.labelLarge.fontSize, color = muted)
    val iconAt = Modifier.padding(top = firstLineInset(style, icon)).size(icon)
    when {
        !data.granted -> Icon(Icons.Default.Lock, stringResource(R.string.tile_locked), modifier = iconAt, tint = muted)
        data.loading -> CircularProgressIndicator(iconAt.padding(2.dp), strokeWidth = 1.5.dp)
        data.failed -> Icon(Icons.Default.ErrorOutline, stringResource(R.string.tile_failed), modifier = iconAt, tint = muted)
        else -> {
            val sessions = spec.tile.form == TileSpec.Form.SESSIONS
            val unit = spec.displayUnitRes?.takeIf { withUnit && !sessions && data.secondaryValue == null }
                ?.let { Formatting.unitSpace(it) + stringResource(it) }.orEmpty()
            // The figure, and what is set small beside it.
            val (text, after) = when {
                sessions && spec.tile.sessionKind == Session.Kind.SLEEP ->
                    data.sessions.takeIf { it.isNotEmpty() }?.let { Formatting.duration(it.totalDuration()) } to ""
                sessions -> when {
                    data.sessions.isEmpty() -> stringResource(R.string.sessions_none) to ""
                    withUnit -> stringResource(R.string.sessions_count, data.sessions.size) to " · " + Formatting.duration(data.sessionDuration)
                    else -> stringResource(R.string.sessions_count, data.sessions.size) to ""
                }
                else -> data.value?.let { value ->
                    val figure = tileValueText(value, data.secondaryValue, spec.valueDecimals)
                    if (row.dayMean) stringResource(R.string.overview_mean, figure) else figure
                } to unit
            }
            Text(
                text = buildAnnotatedString {
                    append(text ?: stringResource(R.string.tile_no_data))
                    if (text != null) withStyle(small) { append(after) }
                },
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

/** Above the unusual ones named beneath the figures, to set them apart from the grid. */
private const val OVERVIEW_NOTES_GAP = 10

/** The most room added between the figures' rows, so a few on a tall tile still read as a block. */
private const val OVERVIEW_SPREAD_MAX = 40

/** The icon beside a figure, against the figure's font size. */
private const val OVERVIEW_ICON_SCALE = 1.1f
private const val OVERVIEW_DOT = 8
