package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.ui.components.HeatmapLegend
import de.steppicrew.healthconnectview.ui.components.WeekdayRhythm
import de.steppicrew.healthconnectview.ui.components.heatmapValue
import de.steppicrew.healthconnectview.ui.components.heatmapColors
import de.steppicrew.healthconnectview.ui.components.YearHeatmapGrid
import de.steppicrew.healthconnectview.health.YearHeatmap
import de.steppicrew.healthconnectview.ui.components.smoothPath
import de.steppicrew.healthconnectview.ui.components.SessionTimeline
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.dashboard.Tile
import de.steppicrew.healthconnectview.dashboard.offersMean
import de.steppicrew.healthconnectview.dashboard.TileFace
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import kotlin.math.floor
import de.steppicrew.healthconnectview.health.weekdayMeans
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.ui.components.LineChart
import de.steppicrew.healthconnectview.ui.components.colorOf
import de.steppicrew.healthconnectview.ui.components.StageTotals
import de.steppicrew.healthconnectview.ui.components.StripSegment
import de.steppicrew.healthconnectview.ui.components.SessionLine
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.night
import de.steppicrew.healthconnectview.health.counted
import de.steppicrew.healthconnectview.ui.components.Hypnogram
import de.steppicrew.healthconnectview.ui.components.iconFor
import de.steppicrew.healthconnectview.ui.components.ProgressRing
import de.steppicrew.healthconnectview.ui.components.SparkCurve
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// What a tile shows inside its card: the value, the chart, both, the body parts, the sessions
// and the calendar. Split from DashboardScreen.kt.

/**
 * Picks the renderer from the type's declared form. Each form falls back to the plain number
 * when its own requirements are not met -- a ring with no goal, a curve with too few readings
 * -- so a tile always shows something rather than an empty box.
 */
@Composable
internal fun TileBody(
    data: TileData,
    large: Boolean,
    onGrantAccess: () -> Unit,
    onOpenDay: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    onOpenWorkout: ((Session) -> Unit)? = null,
) {
    val progress = data.progress
    val workoutTap = onOpenWorkout?.let { WorkoutTap(it, onLongClick) }
    // The user's bands where they set them; the type's defaults otherwise.
    val zones = data.tile.effectiveZones

    // A day of a one-value-a-day type has no chart to draw, so its tile falls back to the value.
    val chart = data.chart?.takeIf { it.drawsChart || it.sessions.isNotEmpty() }
    when {
        !data.granted -> LockedTile(onGrantAccess)

        // With no part known, the plain weight: half a tile left empty read as broken.
        data.bodyWindow != null && !data.loading -> BodyCurves(data, data.bodyReadings.orEmpty(), data.bodyWindow)

        !data.bodyReadings.isNullOrEmpty() && !data.loading -> BodyFace(data, data.bodyReadings, large)

        data.heatmap != null && data.tile.face == TileFace.CALENDAR -> TileCalendar(data, data.heatmap)

        chart != null && data.tile.face == TileFace.CHART ->
            TileChart(chart, Modifier.fillMaxSize(), compactAxis = data.tile.height == 1, companion = data.companion(), workoutTap = workoutTap, mean = data.meanOf(chart))

        chart != null && data.tile.face == TileFace.BOTH -> TileValueAndChart(data, chart, onOpenDay, onLongClick, workoutTap)

        // Before the loading and null-value checks: a session tile never has a value, and
        // zero sessions is a real answer rather than an absence of data.
        data.spec.tile.form == TileSpec.Form.SESSIONS -> SessionCount(data)

        data.loading || data.failed || data.value == null -> TileValue(data, large)

        data.spec.tile.form == TileSpec.Form.RING && progress != null ->
            ProgressRing(
                progress = progress,
                modifier = Modifier.fillMaxSize(),
                // Total calories' active part at full strength, the passive lighter.
                accent = data.stackedShare(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TileValue(data, large)
                    GoalShare(data)
                }
            }

        data.spec.tile.form == TileSpec.Form.CURVE && zones != null && data.curve.size > 1 ->
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TileValue(data, large)
                // A tall tile gives the curve the room below the number: the day's shape is
                // what the extra height is for, and a larger number would say nothing more.
                SparkCurve(
                    points = data.curve,
                    zones = zones,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (large) Modifier.weight(1f) else Modifier.height(CURVE_HEIGHT.dp))
                        .padding(top = 6.dp),
                )
            }

        else -> TileValue(data, large)
    }
}

/**
 * Weight beside its parts: the weight as the single cell shows it, and body fat, water and
 * bone mass each with its value. A part measured on another day than the weight carries its
 * own date, muted, so a body fat reading from months ago is not read as this morning's.
 */
@Composable
private fun BodyFace(data: TileData, parts: List<BodyReading>, large: Boolean) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { TileValue(data, large) }
        Column(Modifier.weight(BODY_PARTS_WEIGHT), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.forEach { part ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = stringResource(part.spec.displayNameRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = Formatting.number(part.value, part.spec.valueDecimals) +
                                part.spec.displayUnitRes?.let { " " + stringResource(it) }.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        part.date?.let { day ->
                            Text(
                                text = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A 2x2 body face over a window: weight and each part in a row, name and latest value above a
 * curve of its daily means across the window. Each curve on its own scale, fitted to its own
 * range, so a change of half a kilo shows as clearly as one of a percent -- never two scales
 * on one plot, and never weight and bone mass squashed onto one.
 */
@Composable
private fun BodyCurves(data: TileData, parts: List<BodyReading>, window: ClosedRange<Instant>) {
    val rows = buildList {
        data.value?.let { add(BodyRow(data.spec, it, data.valueDate, data.bodyCurves[data.spec.type.simpleName].orEmpty())) }
        parts.forEach { add(BodyRow(it.spec, it.value, it.date, data.bodyCurves[it.spec.type.simpleName].orEmpty())) }
    }
    if (rows.isEmpty()) {
        TileValue(data, large = true)
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = stringResource(row.spec.displayNameRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    row.date?.let { day ->
                        Text(
                            text = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)) + "  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = Formatting.number(row.value, row.spec.valueDecimals) +
                            row.spec.displayUnitRes?.let { " " + stringResource(it) }.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                PartCurve(row.curve, window, Modifier.fillMaxWidth().weight(1f).padding(vertical = 2.dp))
            }
        }
    }
}

private data class BodyRow(val spec: RecordTypeSpec<*>, val value: Double, val date: LocalDate?, val curve: List<Point>)

/**
 * One part's daily means across [window], placed by time so the rows' days line up, on the
 * part's own range. A single day is a dot; none leaves the row its number alone.
 */
@Composable
private fun PartCurve(points: List<Point>, window: ClosedRange<Instant>, modifier: Modifier) {
    if (points.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    val low = points.minOf { it.value }
    val high = points.maxOf { it.value }
    val range = (high - low).takeIf { it > 0.0 } ?: 1.0
    val from = window.start.toEpochMilli()
    val length = (window.endInclusive.toEpochMilli() - from).coerceAtLeast(1L).toFloat()
    Canvas(modifier) {
        val stroke = PART_STROKE.dp.toPx()
        val offsets = points.map { point ->
            Offset(
                x = ((point.time.toEpochMilli() - from) / length).coerceIn(0f, 1f) * size.width,
                // Flat where all readings agree: drawn through the middle rather than at the floor.
                y = if (high > low) size.height - ((point.value - low) / range).toFloat() * size.height else size.height / 2,
            )
        }
        // Smoothed like the detail charts, monotone so the curve never overshoots a reading.
        if (offsets.size > 1) drawPath(smoothPath(offsets), color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        offsets.forEach { drawCircle(color, radius = stroke, center = it) }
    }
}

private const val PART_STROKE = 1.5f

/** The parts' column against the weight's: the names need the room. */
private const val BODY_PARTS_WEIGHT = 1.6f

/**
 * The window's chart filling the tile: the detail screen's chart, with its axis values and
 * goal line, minus the touch readout -- a tap here opens that screen.
 */
@Composable
private fun TileChart(
    chart: TileDetailData,
    modifier: Modifier,
    compactAxis: Boolean = false,
    /** The tile's second curve and its readings; see [Tile.companion]. */
    companion: Pair<SessionLine, List<Pair<Session, List<Point>>>>? = null,
    workoutTap: WorkoutTap? = null,
    /** The window's mean, drawn as a dashed level across the chart; see [Tile.mean]. */
    mean: Double? = null,
) {
    val extent = chart.extent
    // A day of sleep is last night, and a night with stages is drawn as them: the owner's
    // idea, 09.10.2026. A band from 23:02 to 05:15 says when; the stages say how. The night is
    // the day's longest sleep, so a nap with stages does not take its place.
    val night = chart.sessions.night()?.takeIf { it.stages.isNotEmpty() }
    if (companion != null && extent != null) {
        CompanionCurves(companion.first, companion.second, compactAxis, modifier, workoutTap)
    } else if (chart.spec.tile.sessionKind == Session.Kind.SLEEP && extent != null && night != null) {
        Hypnogram(
            stages = night.stages,
            start = night.start,
            end = night.end,
            fillHeight = true,
            totalsShown = !compactAxis,
            modifier = modifier,
        )
    } else if (chart.spec.tile.form == TileSpec.Form.SESSIONS && extent != null) {
        // A session type's day has no series; its sessions on the timeline are the chart.
        SessionTimeline(sessions = chart.sessions, extent = extent, fillHeight = true, modifier = modifier)
    } else {
        // The detail chart's dashed reference line, here level at the window's mean.
        val shown = if (mean != null && chart.points.size > 1) {
            chart.copy(baseline = listOf(Point(chart.points.first().time, mean), Point(chart.points.last().time, mean)))
        } else {
            chart
        }
        DataLineChart(shown, modifier, interactive = false, fillHeight = true, compactAxis = compactAxis, sessionIcons = false)
    }
}

/**
 * The number beside the chart on a wide tile, above it on a tall one: a 2x1 tile is too short
 * to stack both and leave the chart anything to show.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileValueAndChart(
    data: TileData,
    chart: TileDetailData,
    /**
     * Where the tile's tap opens one workout, a tap on the count opens the day's list instead,
     * so a large tile reaches every workout of the day, as a small one does.
     */
    onOpenDay: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    workoutTap: WorkoutTap? = null,
) {
    val figureTap = onOpenDay?.let { Modifier.combinedClickable(onClick = it, onLongClick = onLongClick) } ?: Modifier
    // A session tile's figure as its single cell writes it: "6,22" was a night's hours as a
    // bare decimal, where the cell beside says "6h 13m".
    val mean = data.meanOf(chart)
    @Composable
    fun Figure(stages: StageTotalsAt, inline: Boolean = false) =
        when {
            data.spec.tile.form == TileSpec.Form.SESSIONS -> SessionCount(data, stages, inline)
            data.spec.tile.form == TileSpec.Form.RING && data.progress != null ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TileValue(data)
                    GoalShare(data)
                }
            else -> TileValue(data)
        }
    if (data.tile.height > 1) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The stage chart names its own stages; a curve over the strip does not.
            Box(Modifier.fillMaxWidth().then(figureTap), contentAlignment = Alignment.Center) {
                Figure(if (data.companion() != null) StageTotalsAt.ROW else StageTotalsAt.NONE, inline = true)
            }
            TileChart(chart, Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp), companion = data.companion(), workoutTap = workoutTap, mean = mean)
        }
    } else {
        // The number as wide as it needs, up to half the tile, and the rest to the chart: a
        // fixed share left a short number in a wide margin -- the owner's request, 11.10.2026.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val valueMax = maxWidth * VALUE_MAX_SHARE
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.widthIn(max = valueMax).fillMaxHeight()
                        .padding(end = VALUE_GAP.dp).then(figureTap),
                    contentAlignment = Alignment.Center,
                ) {
                    Figure(StageTotalsAt.COLUMN)
                }
                TileChart(chart, Modifier.weight(1f).fillMaxHeight(), compactAxis = true, companion = data.companion(), workoutTap = workoutTap, mean = mean)
            }
        }
    }
}

/**
 * The day's session count, with everything they covered beneath it.
 *
 * Zero is shown as a word rather than as the missing-data dash: a day with no activities is a
 * fact about the day, not a gap in what was recorded. That is the opposite of the reasoning
 * for a measured type, where a null total genuinely means nothing was written.
 */
@Composable
private fun SessionCount(
    data: TileData,
    stagesAt: StageTotalsAt = StageTotalsAt.NONE,
    /**
     * Count, time and icons on one line, above a tall tile's charts: stacked they took three
     * lines of the height the workouts' curves are there for -- the owner's request.
     */
    inline: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (data.loading) {
            TileLoading()
            return@Column
        }
        // Zero sessions is an answer; a read that threw is not, so it must not say "none".
        if (data.failed) {
            TileFailed()
            return@Column
        }

        if (data.sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.sessions_none),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        // The activities themselves, as far as they fit: two or three icons say "a ride and a
        // walk" where the bare count says only "two".
        @Composable
        fun Icons(modifier: Modifier) = Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
            data.sessions.take(TILE_ICONS).forEach { session ->
                Icon(
                    imageVector = iconFor(session),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TILE_ICON_SIZE.dp),
                )
            }
        }

        val sleep = data.spec.tile.sessionKind == Session.Kind.SLEEP
        if (inline && !sleep) {
            // One line that cannot wrap in any language: a number, a duration and icons.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.sessions_count, data.sessions.size),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = Formatting.duration(data.sessionDuration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Icons(Modifier)
            }
            return@Column
        }

        // A night is asked "how long", never "how many": a large "1" above 6h 13m answered the
        // question nobody had. Workouts keep their count, which is the figure they are asked by.
        // Across days a night's mean, said so, and no stages: one night's beneath a mean would
        // read as the window's.
        val window = data.shownSpan != Span.DAY
        if (sleep) {
            Text(
                text = Formatting.duration(if (window) data.nightlyDuration else data.sessionDuration),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (window) {
                Text(
                    text = stringResource(R.string.span_per_night),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
        } else {
            Text(
                text = stringResource(R.string.sessions_count, data.sessions.size),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = Formatting.duration(data.sessionDuration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A night's stages where there is room: the moon beneath "6h 13m" said only "slept".
        val night = data.sessions.night()?.takeIf { it.stages.isNotEmpty() }
        if (night != null && stagesAt != StageTotalsAt.NONE) {
            StageTotals(night.stages, Modifier.padding(top = 4.dp), stacked = stagesAt == StageTotalsAt.COLUMN)
            return@Column
        }
        Icons(Modifier.padding(top = 4.dp))
    }
}

private const val CURVE_HEIGHT = 28

/** The tile's chosen curve with the sessions it was read over, where one is chosen and was read. */
private fun TileData.companion(): Pair<SessionLine, List<Pair<Session, List<Point>>>>? {
    val line = SessionLine.of(tile.companion) ?: return null
    val curves = companionCurves.filter { (_, points) -> points.size > 1 }.ifEmpty { return null }
    return line to curves
}

/**
 * The chosen reading over each of the day's last sessions, each across its own span: how the
 * two go together, the owner's request. A night's stages run along the bottom, as on its own
 * screen; a workout's breaks are shaded, and above it its icon and times say which one it is
 * -- the timeline it replaces said that by where the band sat.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompanionCurves(
    line: SessionLine,
    curves: List<Pair<Session, List<Point>>>,
    compactAxis: Boolean,
    modifier: Modifier,
    /**
     * Each workout's curve opens that workout, its time line with it: with two on a tall
     * tile, the tile's own tap opens the day's list, and one of them was a tap away.
     */
    workoutTap: WorkoutTap? = null,
) {
    val spec = RecordRegistry.specOrNull(line.typeName)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        curves.forEach { (session, points) ->
            val tap = workoutTap?.takeIf { session.kind == Session.Kind.EXERCISE }
                ?.let { Modifier.combinedClickable(onClick = { it.open(session) }, onLongClick = it.onLongClick) }
                ?: Modifier
            Column(Modifier.fillMaxWidth().weight(1f).then(tap)) {
                if (session.kind != Session.Kind.SLEEP) {
                    // One line that cannot wrap: the icon rule for wrapping text does not apply.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = iconFor(session),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(COMPANION_ICON_SIZE.dp),
                        )
                        Text(
                            text = Formatting.time(session.start) + "–" + Formatting.time(session.end) + " · " + Formatting.duration(session.counted),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
            }
            LineChart(
                points = line.shown(points),
                unitRes = spec?.displayUnitRes,
                integral = spec?.tile?.integralValues ?: false,
                minSpan = line.minSpan,
                lineColorOverride = line.color(),
                dottedLine = line.dots,
                strip = session.stages.map { StripSegment(it.start, it.end, colorOf(it.kind)) },
                breaks = session.breaks.map { it.start..it.end },
                extent = session.start..session.end,
                interactive = false,
                fillHeight = true,
                compactAxis = compactAxis,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            }
        }
    }
}

/**
 * The window's mean where the tile asks for it: the platform's own average over the window --
 * the figure the tile already shows beside its chart, so the line needs no label of its own.
 */
private fun TileData.meanOf(chart: TileDetailData): Double? =
    chart.total?.takeIf { tile.mean && offersMean(tile.typeName) }

/** A tap that opens one workout, and the long press that starts arranging, as on the tile itself. */
private class WorkoutTap(val open: (Session) -> Unit, val onLongClick: () -> Unit)

/** Where a sleep tile lists the time in each stage beside its hours, if anywhere. */
private enum class StageTotalsAt { NONE, ROW, COLUMN }

private const val COMPANION_ICON_SIZE = 14


/**
 * The calendar face: the year's figure above its grid, as on the detail screen but without its
 * taps -- a tap on the tile opens the year there, where a day can be read -- and the legend
 * beneath. Blood pressure's days are coloured by grade, which the shades' legend would misread.
 */
@Composable
private fun TileCalendar(data: TileData, heatmap: YearHeatmap) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val value = data.value
        // Sessions in hours, written as a duration: "7,5" read as a decimal.
        if (data.spec.tile.form == TileSpec.Form.SESSIONS && value != null) {
            Text(
                text = heatmapValue(data.spec)(value),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            TileValue(data)
        }
        YearHeatmapGrid(
            heatmap = heatmap,
            colorOf = heatmapColors(heatmap),
            selected = null,
            onSelect = null,
            description = stringResource(R.string.heatmap_title),
            showEmpty = false,
        )
        // Blood pressure is graded, not shaded: neither the bars nor the legend would read.
        if (heatmap.secondValues.isEmpty()) {
            // The room a 2x2 leaves: which weekdays run long or short.
            if (data.tile.height == 2) {
                val means = remember(heatmap) { weekdayMeans(data.spec, heatmap, LocalDate.now()) }
                if (means.isNotEmpty()) WeekdayRhythm(heatmap, means, data.spec)
            }
            HeatmapLegend(heatmap, data.spec, Modifier.align(Alignment.Start))
        }
    }
}
