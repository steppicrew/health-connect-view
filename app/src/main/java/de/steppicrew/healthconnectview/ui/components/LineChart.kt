package de.steppicrew.healthconnectview.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.Layout
import java.time.Duration
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.registry.segmentAtGaps
import java.time.Instant

/**
 * The app's only chart.
 *
 * Hand-drawn on a Compose Canvas rather than pulled from a charting library: the shape here
 * is a single series of timestamped values, which is a few lines of geometry, and this keeps
 * the app free of a dependency whose API churns between majors. Everything renders through
 * this one signature, so swapping the implementation stays a single-file change.
 */
@Composable
fun LineChart(
    points: List<Point>,
    modifier: Modifier = Modifier,
    smooth: Boolean = false,
    /** Drawn as a dashed reference line, and kept within the vertical range so it is visible. */
    goal: Double? = null,
    /** Marked with a dot where the series first reaches [goal]. */
    goalCrossing: Instant? = null,
    /** Unit shown beside a touched point's value; omitted when the type has none. */
    @StringRes unitRes: Int? = null,
    /**
     * Bucket starts that held no data. The line is broken across these rather than drawn
     * through, so a day nothing was recorded does not read as a measured value.
     */
    emptyBuckets: List<Instant> = emptyList(),
    /**
     * Spans the user was asleep or exercising, shaded behind the line. The association is by
     * time overlap only -- Health Connect stores no link between a session and the readings
     * taken during it -- so a band means "a session covered this time", not "these readings
     * belong to it".
     */
    sessions: List<Session> = emptyList(),
    /**
     * Value bands to colour the line by, or null for a single-colour line.
     *
     * Set for the types that declare zones, so the same reading is the same colour on the
     * dashboard and on the chart it opens. The bands are fixed values, never the window's own
     * extent: a window-relative scale would paint every day in the full sweep, making a calm
     * day look identical to an alarming one.
     */
    zones: ValueZones? = null,
    /**
     * Whether each reading is marked with a dot.
     *
     * Declared per type rather than inferred from how many points there are: it is about what
     * a reading *is*. A weight is an event someone performed, so the dot says "measured here"
     * and the line between merely connects two of them; a heart-rate sample is one of
     * thousands, where the shape is the message and a dot per sample buries it.
     */
    markReadings: Boolean = false,
    /**
     * Horizontal extent of the plot, or null to span exactly the readings.
     *
     * Set for a single day, where the axis should mean the same thing all day: without it a
     * chart ends at the last recorded point, so midday sits wherever the data happens to stop
     * and 12:00 is in the middle of the morning at 09:00 and off to the left by 21:00. The
     * *line* still ends at its last real point -- the empty remainder is the honest picture of
     * a day in progress, and drawing to the edge would invent readings that do not exist.
     */
    extent: ClosedRange<Instant>? = null,
) {
    if (points.isEmpty()) return

    val values = points.map { it.value }
    // The goal takes part in the scale: a goal above the day's total must stay on the chart,
    // or "not reached yet" looks identical to "reached", which is the whole point of drawing
    // it. A goal already beaten simply sits below the peak.
    val minValue = minOf(values.min(), goal ?: values.min())
    val maxValue = maxOf(values.max(), goal ?: values.max())
    // A flat series would divide by zero; give it a nominal span so it draws as a centre line.
    val span = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface
    // Faint enough to read as background. Sleep is a fixed blue rather than a theme colour:
    // it means night, and under dynamic colour a themed hue would drift with the wallpaper
    // until it no longer read as sleep at all. Exercise stays themed, having no such
    // convention to honour.
    val sleepColor = SLEEP_BAND.copy(alpha = BAND_ALPHA)
    val exerciseColor = MaterialTheme.colorScheme.tertiary.copy(alpha = BAND_ALPHA)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()

    var selected by remember(points) { mutableStateOf<Int?>(null) }

    // Each point's horizontal position as a fraction of the width. Computed once here so the
    // touch handler and the drawing agree exactly on where a point sits.
    val fractions = remember(points, extent) { horizontalFractions(points, extent) }
    // The plot's own time range, shared with the icon row so an icon lands on the band it
    // names. Null where the series has no elapsed time and the fractions fall back to even
    // spacing, which no time can be mapped onto.
    val timeExtent = remember(points, extent) {
        val start = extent?.start ?: points.first().time
        val end = extent?.endInclusive ?: points.last().time
        (start..end).takeIf { end > start }
    }
    val segments = remember(points, emptyBuckets) { segmentAtGaps(points, emptyBuckets) }

    fun nearestIndex(x: Float, width: Int): Int? {
        if (fractions.isEmpty() || width <= 0) return null
        val target = (x / width).coerceIn(0f, 1f)
        return fractions.indices.minByOrNull { kotlin.math.abs(fractions[it] - target) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // The readout occupies a fixed row whether or not anything is selected, so touching
        // the chart does not shift the layout under the finger.
        SelectionReadout(
            point = selected?.let(points::getOrNull),
            unitRes = unitRes,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT.dp)
                // Room below the plot for the bottom gridline label, which is drawn under
                // its own line rather than clamped up on top of the series.
                .padding(top = 8.dp, bottom = AXIS_GAP.dp)
                .pointerInput(points) {
                    // Drag as well as tap: reading a series means sweeping along it, and
                    // lifting clears so the chart does not keep a stale highlight.
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset -> selected = nearestIndex(offset.x, size.width) },
                        onDrag = { change, _ ->
                            selected = nearestIndex(change.position.x, size.width)
                        },
                        onDragEnd = { selected = null },
                        onDragCancel = { selected = null },
                    )
                }
                .pointerInput(points) {
                    detectTapGestures(
                        onPress = { offset ->
                            selected = nearestIndex(offset.x, size.width)
                            // Held highlight while the finger is down, cleared on release.
                            tryAwaitRelease()
                            selected = null
                        },
                    )
                },
        ) {
            // The plot's time origin, which is the extent where one is given and the first
            // reading otherwise. Bands, the goal marker and the line all measure from it, so
            // a band cannot drift away from the stretch of line it explains.
            val firstTime = (extent?.start ?: points.first().time).toEpochMilli()
            val lastTime = (extent?.endInclusive ?: points.last().time).toEpochMilli()
            val timeSpan = (lastTime - firstTime).takeIf { it > 0L }

            fun xFor(index: Int): Float = fractions[index] * size.width

            fun yFor(value: Double): Float =
                (size.height * (1.0 - (value - minValue) / span)).toFloat()

            val offsets = points.mapIndexed { index, point ->
                Offset(xFor(index), yFor(point.value))
            }
            goal?.let { target ->
                val y = yFor(target)
                drawLine(
                    color = goalColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(GOAL_DASH_ON.dp.toPx(), GOAL_DASH_OFF.dp.toPx()),
                    ),
                )
            }

            // Behind everything: a band is context for the line, not a thing to read on its
            // own, so it must never compete with the data drawn over it.
            if (timeSpan != null) {
                sessions.forEach { session ->
                    val from = ((session.start.toEpochMilli() - firstTime).toDouble() /
                        timeSpan).toFloat().coerceIn(0f, 1f) * size.width
                    val to = ((session.end.toEpochMilli() - firstTime).toDouble() /
                        timeSpan).toFloat().coerceIn(0f, 1f) * size.width
                    if (to <= from) return@forEach
                    drawRect(
                        color = when (session.kind) {
                            Session.Kind.SLEEP -> sleepColor
                            Session.Kind.EXERCISE -> exerciseColor
                        },
                        topLeft = Offset(from, 0f),
                        size = androidx.compose.ui.geometry.Size(to - from, size.height),
                    )
                }
            }

            // Guides labelled at their own line, so a value can be read off the chart
            // rather than inferred from the endpoints. Four intervals gives five labels,
            // which stays legible at the height this chart is drawn.
            val guides = (0..GUIDE_INTERVALS).map { step ->
                minValue + (maxValue - minValue) * step / GUIDE_INTERVALS
            }
            guides.forEach { guide ->
                val y = yFor(guide)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )

                // Measured on a single unwrapped line. Without this the measurer inherits the
                // canvas width as its constraint and a label can come back wrapped or
                // clipped -- on a session chart the guides read "138, 12, 14, 101" where the
                // middle two were 127 and 114 with their last digit cut off. An axis that
                // silently drops digits is worse than no axis.
                val label = textMeasurer.measure(
                    text = Formatting.number(guide),
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false,
                )
                // Centred on its line, except the bottom one: clamping that inside the plot
                // puts it on top of the line and whatever the series does there, which on a
                // rising chart is exactly where the data starts. Below the axis it is clear
                // of both, and the padding reserved beneath the canvas leaves room for it.
                val labelY = if (guide == guides.first()) {
                    size.height
                } else {
                    (y - label.size.height / 2f).coerceAtLeast(0f)
                }
                // The label sits on top of the grid and goal lines, so it is backed out to
                // stay readable where they cross it.
                drawRect(
                    color = surfaceColor,
                    topLeft = Offset(0f, labelY),
                    size = androidx.compose.ui.geometry.Size(
                        width = label.size.width.toFloat() + LABEL_PAD.dp.toPx(),
                        height = label.size.height.toFloat(),
                    ),
                )
                drawText(
                    textLayoutResult = label,
                    color = labelColor,
                    topLeft = Offset(0f, labelY),
                )
            }

            // Placed by time like every other point, so the marker sits exactly where the
            // line meets the goal rather than at the nearest sample.
            val crossingX = goalCrossing?.let { crossing ->
                if (timeSpan == null) null
                else {
                    val offsetMillis = crossing.toEpochMilli() - firstTime
                    (size.width * offsetMillis.toDouble() / timeSpan.toDouble()).toFloat()
                }
            }

            // Each run of consecutive points is stroked on its own, so a gap stays a gap.
            var drawn = 0
            segments.forEach { segment ->
                val segmentOffsets = offsets.subList(drawn, drawn + segment.size)
                drawn += segment.size
                if (segmentOffsets.isEmpty()) return@forEach

                when {
                    // A lone point between two gaps has no line to draw, so it is marked
                    // instead -- otherwise a day surrounded by empty days vanishes entirely.
                    segmentOffsets.size == 1 -> drawCircle(
                        color = zones?.colorFor(segment.first().value) ?: lineColor,
                        radius = 3.dp.toPx(),
                        center = segmentOffsets.first(),
                    )

                    // Each span is drawn as a gradient between its two endpoints' colours,
                    // so the colour tracks the value continuously along the line.
                    //
                    // Colouring a whole span by one endpoint made the colour depend on the
                    // direction of travel: a rise from 100 to 135 came out red while the fall
                    // back from 135 to 95 came out blue, so the same reading wore two colours
                    // depending on which way the line was going. A gradient has no such
                    // asymmetry -- every point on the line carries the colour of the value at
                    // that point.
                    zones != null -> segmentOffsets.zipWithNext()
                        .forEachIndexed { index, (from, to) ->
                            val fromColor = zones.colorFor(segment[index].value)
                            val toColor = zones.colorFor(segment[index + 1].value)
                            drawLine(
                                brush = if (fromColor == toColor) {
                                    SolidColor(fromColor)
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(fromColor, toColor),
                                        start = from,
                                        end = to,
                                    )
                                },
                                start = from,
                                end = to,
                                strokeWidth = LINE_WIDTH.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }

                    else -> {
                        val path = if (smooth && segmentOffsets.size > 2) {
                            smoothPath(segmentOffsets)
                        } else {
                            Path().apply {
                                segmentOffsets.forEachIndexed { index, offset ->
                                    if (index == 0) moveTo(offset.x, offset.y)
                                    else lineTo(offset.x, offset.y)
                                }
                            }
                        }
                        drawPath(
                            path,
                            color = lineColor,
                            style = Stroke(width = LINE_WIDTH.dp.toPx()),
                        )
                    }
                }
            }

            // Drawn after the line so the marker is not overdrawn by it.
            if (crossingX != null && goal != null) {
                val y = yFor(goal)
                drawCircle(
                    color = goalColor,
                    radius = GOAL_MARKER_RADIUS.dp.toPx(),
                    center = Offset(crossingX, y),
                )
                // A ring of background colour separates the marker from the line beneath it,
                // which shares its position by definition.
                drawCircle(
                    color = surfaceColor,
                    radius = (GOAL_MARKER_RADIUS - GOAL_MARKER_RING).dp.toPx(),
                    center = Offset(crossingX, y),
                )
            }

            // The selected point: a full-height rule plus a marker, so the position is
            // readable even where the line is flat and a dot alone would be ambiguous.
            selected?.let { index ->
                val x = xFor(index)
                val y = yFor(points[index].value)
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(color = surfaceColor, radius = 7.dp.toPx(), center = Offset(x, y))
                drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(x, y))
            }

            // Coloured like the line they sit on: in the theme colour they read as black
            // specks scattered over a coloured curve, which looks like a defect rather than
            // like the measurements the line is made of. The count cap still applies, since
            // even discrete readings crowd once a long span holds enough of them.
            if (markReadings && points.size <= MAX_DOTS) {
                points.forEachIndexed { index, point ->
                    drawCircle(
                        color = zones?.colorFor(point.value) ?: lineColor,
                        radius = 3.dp.toPx(),
                        center = Offset(xFor(index), yFor(point.value)),
                    )
                }
            }
        }

        // Above the hour labels rather than among them: a band says *when* something
        // happened but not *what*, and the list below the chart names the sessions without
        // saying which band is which. With two or three bands that is guesswork.
        if (sessions.isNotEmpty() && timeExtent != null) {
            SessionAxisIcons(sessions = sessions, extent = timeExtent)
        }

        TimeAxis(points = points, fractions = fractions, extent = extent)
    }
}

private const val CHART_HEIGHT = 200
/**
 * Tick labels along the time axis.
 *
 * Positioned by the same fractions the plot uses, so a label sits under the moment it names
 * rather than being spread evenly and implying a regularity the data does not have.
 *
 * The format follows the span: clock times read naturally within a day, dates across weeks,
 * and a date on an intraday chart would repeat itself at every tick.
 */
/**
 * The day's sessions as bands on a bare timeline, with no series drawn over them.
 *
 * For the types whose detail *is* the sessions. Their aggregate is a duration, and slicing a
 * duration into hourly buckets smears one night across the whole day -- a line that climbs to
 * 1 and falls to 0.2, which reads as a measurement and is not one. What the chart was actually
 * conveying is when the sessions were and how long they ran, and bands say that exactly.
 */
@Composable
fun SessionTimeline(
    sessions: List<Session>,
    extent: ClosedRange<Instant>,
    modifier: Modifier = Modifier,
) {
    val sleepColor = SLEEP_BAND.copy(alpha = BAND_ALPHA)
    val exerciseColor = MaterialTheme.colorScheme.tertiary.copy(alpha = BAND_ALPHA)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val start = extent.start.toEpochMilli()
    val span = (extent.endInclusive.toEpochMilli() - start).toDouble().takeIf { it > 0.0 }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TIMELINE_HEIGHT.dp),
        ) {
            // The empty track is drawn first, so the hours with nothing in them read as part
            // of the same day rather than as blank page.
            drawRect(
                color = trackColor,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width, size.height),
            )
            if (span == null) return@Canvas

            sessions.forEach { session ->
                val from = ((session.start.toEpochMilli() - start) / span)
                    .toFloat().coerceIn(0f, 1f) * size.width
                val to = ((session.end.toEpochMilli() - start) / span)
                    .toFloat().coerceIn(0f, 1f) * size.width
                if (to <= from) return@forEach
                drawRect(
                    color = when (session.kind) {
                        Session.Kind.SLEEP -> sleepColor
                        Session.Kind.EXERCISE -> exerciseColor
                    },
                    topLeft = Offset(from, 0f),
                    size = androidx.compose.ui.geometry.Size(to - from, size.height),
                )
            }
        }

        if (sessions.isNotEmpty()) {
            SessionAxisIcons(sessions = sessions, extent = extent)
        }
        TimeAxis(points = emptyList(), fractions = emptyList(), extent = extent)
    }
}

/**
 * An icon on the axis at each session's midpoint, matching the band behind the chart.
 *
 * Positioned by the same extent the plot uses, so an icon sits under the stretch of line its
 * session covers rather than being spread evenly and implying a regularity the day did not
 * have. The midpoint rather than the start, because that is where the band is widest and the
 * pairing is most obvious.
 *
 * A session whose midpoint falls outside the plot is skipped: the bands are clipped to the
 * window, so an icon pinned to the edge would name a band that is barely there.
 */
@Composable
private fun SessionAxisIcons(sessions: List<Session>, extent: ClosedRange<Instant>) {
    val start = extent.start.toEpochMilli()
    val span = (extent.endInclusive.toEpochMilli() - start).toDouble()

    val placed = remember(sessions, extent) {
        sessions.mapNotNull { session ->
            val middle = (session.start.toEpochMilli() + session.end.toEpochMilli()) / 2
            val fraction = ((middle - start) / span).toFloat()
            if (fraction in 0f..1f) session to fraction else null
        }
    }
    if (placed.isEmpty()) return

    Layout(
        content = {
            placed.forEach { (session, _) ->
                Icon(
                    imageVector = iconFor(session),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AXIS_ICON.dp),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0

        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                // Centred on the midpoint, then held inside the plot so an icon near an edge
                // is not half cut off.
                val centre = placed[index].second * constraints.maxWidth
                val x = (centre - placeable.width / 2f).toInt()
                    .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

@Composable
private fun TimeAxis(
    points: List<Point>,
    fractions: List<Float>,
    extent: ClosedRange<Instant>? = null,
) {
    // The axis measures the plot, so it follows the extent wherever one is fixed. Reading it
    // off the points instead would label a 24-hour plot with the hours the data happened to
    // cover, which is the mismatch the extent exists to remove.
    // Points may be empty when an extent is given -- a session timeline has bands but no
    // series -- so the fallbacks must never be reached in that case rather than merely
    // happening not to be.
    val first = extent?.start ?: points.firstOrNull()?.time ?: return
    val last = extent?.endInclusive ?: points.lastOrNull()?.time ?: return
    // Anything up to a day reads as clock times, including a span shorter than an hour: a
    // 31-minute workout was labelled "28 Aug" five times over, because toHours() floored to
    // zero and the axis fell through to the multi-day format. A date repeated across a chart
    // that fits inside one afternoon tells the reader nothing.
    val span = Duration.between(first, last)
    val intraday = !span.isNegative && span <= Duration.ofHours(HOURS_IN_DAY)

    // Within a day, ticks are placed at round hours rather than snapped to samples: a
    // record-built series has points at whatever minute activity happened, so snapping gave
    // labels like 06:02 and 16:51, which read as arbitrary rather than as an axis.
    val ticks = remember(points, intraday, first, last) {
        if (intraday) hourlyTicks(first, last) else axisTicks(points, fractions)
    }

    Layout(
        content = {
            ticks.forEach { tick ->
                Text(
                    text = if (intraday) {
                        Formatting.time(tick.time)
                    } else {
                        Formatting.dayAndMonth(tick.time)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0

        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                // Centred on the tick, then held inside the chart so the first and last
                // labels are not half off the edge.
                val centre = ticks[index].fraction * constraints.maxWidth
                val x = (centre - placeable.width / 2f).toInt()
                    .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

/**
 * Ticks at round hours across the window, positioned by time.
 *
 * Unlike [axisTicks] these are not snapped to samples: an axis is a ruler, and a ruler marked
 * at 06:02 and 16:51 reads as arbitrary. The interval is chosen so the labels stay legible on
 * a phone-width chart.
 */
private fun hourlyTicks(start: Instant, end: Instant): List<AxisTick> {
    val spanMillis = (end.toEpochMilli() - start.toEpochMilli()).takeIf { it > 0L }
        ?: return emptyList()

    // A span shorter than a couple of hours has too few whole hours to mark -- a 31-minute
    // workout may contain none at all -- so it is ticked at round minutes instead. Without
    // this such a chart got no ticks and fell back to showing its date over and over.
    if (Duration.between(start, end) < Duration.ofHours(MINUTE_TICK_BELOW_HOURS)) {
        return minuteTicks(start, end, spanMillis)
    }

    val spanHours = Duration.between(start, end).toHours().coerceAtLeast(1L)
    val stepHours = TICK_HOUR_STEPS.firstOrNull { spanHours / it <= AXIS_TICKS } ?: spanHours

    val zone = java.time.ZoneId.systemDefault()
    var tick = start.atZone(zone)
        .truncatedTo(java.time.temporal.ChronoUnit.HOURS)
        .let { if (it.toInstant() < start) it.plusHours(1) else it }

    val ticks = mutableListOf<AxisTick>()
    while (!tick.toInstant().isAfter(end)) {
        if (tick.hour.toLong() % stepHours == 0L) {
            val fraction =
                (tick.toInstant().toEpochMilli() - start.toEpochMilli()).toDouble() / spanMillis
            ticks += AxisTick(fraction = fraction.toFloat(), time = tick.toInstant())
        }
        tick = tick.plusHours(1)
    }
    return ticks
}

/**
 * Ticks at round minutes, for a window too short to contain enough whole hours.
 *
 * The step is chosen so the labels stay legible rather than crowding: a half-hour session
 * gets five-minute marks, a two-hour one gets fifteen.
 */
private fun minuteTicks(start: Instant, end: Instant, spanMillis: Long): List<AxisTick> {
    val spanMinutes = Duration.between(start, end).toMinutes().coerceAtLeast(1L)
    val step = TICK_MINUTE_STEPS.firstOrNull { spanMinutes / it <= AXIS_TICKS } ?: spanMinutes

    val zone = java.time.ZoneId.systemDefault()
    var tick = start.atZone(zone).truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
    if (tick.toInstant() < start) tick = tick.plusMinutes(1)

    val ticks = mutableListOf<AxisTick>()
    while (!tick.toInstant().isAfter(end)) {
        if (tick.minute.toLong() % step == 0L) {
            val fraction =
                (tick.toInstant().toEpochMilli() - start.toEpochMilli()).toDouble() / spanMillis
            ticks += AxisTick(fraction = fraction.toFloat(), time = tick.toInstant())
        }
        tick = tick.plusMinutes(1)
    }
    return ticks
}

/** Minute intervals tried in turn, mirroring TICK_HOUR_STEPS. */
private val TICK_MINUTE_STEPS = listOf(1L, 2L, 5L, 10L, 15L, 30L)

/** Below this a window is ticked by minutes rather than by hours. */
private const val MINUTE_TICK_BELOW_HOURS = 3L

/** Hour intervals tried in turn until the whole span fits within AXIS_TICKS labels. */
private val TICK_HOUR_STEPS = listOf(1L, 2L, 3L, 4L, 6L, 8L, 12L)

/** One tick: where it sits across the width, and the moment it names. */
private data class AxisTick(val fraction: Float, val time: Instant)

/**
 * Evenly spaced ticks across the elapsed time, each snapped to the nearest real point.
 *
 * Snapping matters: a label reading a time no sample was taken at invites the reader to
 * believe the series was measured there. Duplicate snaps are dropped, so a sparse series shows
 * fewer labels rather than the same one repeated.
 */
private fun axisTicks(points: List<Point>, fractions: List<Float>): List<AxisTick> {
    if (points.size < 2 || fractions.size != points.size) return emptyList()

    return (0..AXIS_TICKS).map { step ->
        val target = step / AXIS_TICKS.toFloat()
        val index = fractions.indices.minByOrNull { kotlin.math.abs(fractions[it] - target) }
            ?: 0
        AxisTick(fraction = fractions[index], time = points[index].time)
    }.distinctBy { it.time }
}

/**
 * Each point's horizontal position as a fraction of the plot width, from its timestamp.
 *
 * Shared by the drawing and the touch handler so both agree on where a point is: computing it
 * twice invites them to drift, and a highlight that lands beside the line it names is worse
 * than no highlight. Internal rather than private so the extent behaviour can be pinned by a
 * test: it decides where every band, marker and label lands.
 */
internal fun horizontalFractions(
    points: List<Point>,
    extent: ClosedRange<Instant>? = null,
): List<Float> {
    if (points.isEmpty()) return emptyList()
    val first = (extent?.start ?: points.first().time).toEpochMilli()
    val span = (extent?.endInclusive ?: points.last().time).toEpochMilli() - first

    // A series with no elapsed time (one point, or several sharing an instant) has no
    // meaningful time axis, so fall back to even spacing. An extent always has width, so this
    // is only ever reached without one.
    if (span <= 0L) {
        if (points.size == 1) return listOf(0.5f)
        return points.indices.map { it / (points.size - 1).toFloat() }
    }
    // Clamped, because a reading can fall outside a fixed extent -- a sleep session running
    // past midnight, most obviously -- and a fraction outside 0..1 would draw off the plot.
    return points.map {
        ((it.time.toEpochMilli() - first).toDouble() / span).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * The touched point's value and time, in a row that is always present.
 *
 * Reserving the space keeps the chart from jumping when a touch begins, which on a chart is
 * disorienting: the thing being pointed at moves out from under the finger.
 */
@Composable
private fun SelectionReadout(point: Point?, @StringRes unitRes: Int?) {
    val unit = unitRes?.let { stringResource(it) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = point?.let { selected ->
                Formatting.number(selected.value) + (unit?.let { " $it" } ?: "")
            }.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = point?.let { Formatting.dateTime(it.time) }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A cubic curve through every point, for series where the underlying quantity varies
 * continuously rather than in steps.
 *
 * Control points are placed from each neighbour pair (a Catmull-Rom spline converted to
 * Bezier), then clamped so a segment can never leave the range of the two values it joins.
 * Without that clamp an overshoot invents readings that were never recorded -- dipping below
 * zero between two step counts, for instance -- which for health data is not a cosmetic
 * problem but a false statement.
 */
private fun smoothPath(offsets: List<Offset>): Path = Path().apply {
    moveTo(offsets.first().x, offsets.first().y)

    offsets.zipWithNext().forEachIndexed { index, (current, next) ->
        val previous = offsets.getOrElse(index - 1) { current }
        val following = offsets.getOrElse(index + 2) { next }

        val lowY = minOf(current.y, next.y)
        val highY = maxOf(current.y, next.y)

        // Control points stay inside the segment horizontally as well as vertically. With
        // points placed by timestamp the neighbours can be far apart in x, and an unclamped
        // control point would reach past the segment and double the line back on itself.
        val lowX = minOf(current.x, next.x)
        val highX = maxOf(current.x, next.x)

        val control1 = Offset(
            x = (current.x + (next.x - previous.x) / CATMULL_ROM_TENSION).coerceIn(lowX, highX),
            y = (current.y + (next.y - previous.y) / CATMULL_ROM_TENSION).coerceIn(lowY, highY),
        )
        val control2 = Offset(
            x = (next.x - (following.x - current.x) / CATMULL_ROM_TENSION).coerceIn(lowX, highX),
            y = (next.y - (following.y - current.y) / CATMULL_ROM_TENSION).coerceIn(lowY, highY),
        )

        cubicTo(control1.x, control1.y, control2.x, control2.y, next.x, next.y)
    }
}

/** Standard Catmull-Rom conversion factor; larger values give a tighter curve. */
private const val CATMULL_ROM_TENSION = 6f

private const val GOAL_DASH_ON = 6f
private const val GOAL_DASH_OFF = 4f

private const val GOAL_MARKER_RADIUS = 6f
private const val GOAL_MARKER_RING = 2.5f

/** Four intervals gives five labelled gridlines, legible at this chart's height. */
private const val GUIDE_INTERVALS = 4

/** Breathing room right of a gridline label, so the line does not touch the glyphs. */
private const val LABEL_PAD = 4f

/** Four intervals gives five ticks, which fit without crowding at phone width. */
private const val AXIS_TICKS = 4

/** Space between the plot and its tick labels. */
private const val AXIS_GAP = 14f

/** Bands sit behind the data and must not compete with it. */
private const val BAND_ALPHA = 0.16f

/** A calm night blue, fixed so it keeps meaning "asleep" whatever the wallpaper. */
private val SLEEP_BAND = Color(0xFF5C7CFA)

private const val HOURS_IN_DAY = 24L

/** Small enough to read as an axis mark rather than as a control. */
private const val AXIS_ICON = 14

/** Tall enough for a band to be a band rather than a rule. */
private const val TIMELINE_HEIGHT = 40

private const val MAX_DOTS = 60

/**
 * Thin enough that a dense day reads as a trace rather than a ribbon. At full resolution a
 * heart-rate day holds thousands of points, and a heavy stroke merges neighbouring peaks into
 * a solid block.
 */
private const val LINE_WIDTH = 2f
