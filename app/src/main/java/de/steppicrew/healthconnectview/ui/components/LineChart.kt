package de.steppicrew.healthconnectview.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.registry.AxisScale
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.registry.readingGap
import de.steppicrew.healthconnectview.registry.segmentAtGaps
import de.steppicrew.healthconnectview.ui.dashboard.StackedBucket
import de.steppicrew.healthconnectview.ui.dashboard.ValueBand
import java.time.Duration
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
    /** Decimal places for a touched point's value, where the unit fixes them. */
    valueDecimals: Int? = null,
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
     * Stops inside a workout, shaded behind the line in a neutral tone: the readings there are
     * real, but not of the activity, and a session band in its own colour would claim they are.
     */
    breaks: List<ClosedRange<Instant>> = emptyList(),
    /**
     * Draw one bar per point instead of a line through them.
     *
     * For a window bucketed by day, where each point is a whole day's figure rather than a
     * moment: a line between two such points draws a value for the hours in between that
     * nothing measured, and reads as a trend where there is only a sequence.
     */
    bars: Boolean = false,
    /**
     * Whether this quantity is counted in whole units, so the axis is not stepped in fractions.
     *
     * Declared per type in the registry: an axis labelled 2,5 floors claims a precision the
     * measurement does not have, while forcing whole steps onto a body weight would flatten a
     * chart that moves inside one kilogram.
     */
    integral: Boolean = false,
    /**
     * The narrowest range the axis may show, widened around the data's middle. A tight scale
     * keeps a small movement visible, but a night holding at 50 to 52 bpm filled the whole
     * height and each single beat drew as a cliff; a floor on the range lets a steady series
     * read as steady. Null for no floor.
     */
    minSpan: Double? = null,
    /**
     * Per-bucket low/high drawn as a shaded ribbon behind the line.
     *
     * Each point of a multi-day line is a whole day's mean, and a mean is the one number that
     * hides how the day actually went. The ribbon puts the day's range back without pretending
     * to say when within it anything happened.
     */
    rangeBand: List<ValueBand> = emptyList(),
    /** The second line's spread, drawn like [rangeBand]: diastolic's beneath systolic's. */
    secondaryRangeBand: List<ValueBand> = emptyList(),
    /**
     * Per-bucket components of a stacked bar, bottom-up, empty where the type declares none.
     *
     * Where a bucket has a stack, its bar is drawn as those segments instead of one block:
     * the parts answer different questions and their sum hides both.
     */
    stack: List<StackedBucket> = emptyList(),
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
    /**
     * A second line measured together with the first -- diastolic under systolic. Drawn with
     * the same marks, coloured by [secondaryZones], and read out after a slash on touch.
     */
    secondaryPoints: List<Point> = emptyList(),
    secondaryZones: ValueZones? = null,
    /**
     * Whether the chart answers touch: pinch to zoom, drag to read, press to highlight.
     *
     * Off on a dashboard tile, where a tap has to reach the tile and open the detail -- the
     * chart there is a glance, and the gestures belong to the screen it opens.
     */
    interactive: Boolean = true,
    /**
     * Take whatever height the parent gives instead of the fixed plot height, for a tile whose
     * size the grid decides. The parent must bound the height, as a tile cell does.
     */
    fillHeight: Boolean = false,
    /**
     * Half the gridlines, for a plot too short for four: on a one-row tile the labels of
     * neighbouring lines touched and read as one number.
     */
    compactAxis: Boolean = false,
    /**
     * A colour per point, overriding [zones] where set; null keeps the point's usual colour.
     *
     * For a yardstick that moves along the series rather than fixed bands: HRV's usual range
     * is recomputed for every day, so whether a point is unusual is decided per point by the
     * caller, not by a value boundary the chart could apply itself.
     */
    pointColors: List<Color?> = emptyList(),
    /**
     * Loose values drawn as faint dots behind the line, placed by time: the single nights a
     * 7-night mean is made of, so a dip in the line can be traced to the nights that caused
     * it. They take part in the scale, or an unusual night would fall off the chart.
     */
    scatter: List<Point> = emptyList(),
    /**
     * A reference level drawn dashed behind the line, placed by time: a resting heart rate's
     * four-week mean, against which a few beats up stand out from the day-to-day noise. It
     * takes part in the scale, so the level stays on the chart when the days wander off it.
     */
    baseline: List<Point> = emptyList(),
    /**
     * Told the stretch of time on screen whenever the zoom or pan settles on a new one, and
     * null at full width, so a list beside the chart can follow it.
     */
    onVisibleRange: ((ClosedRange<Instant>?) -> Unit)? = null,
    /**
     * A range from outside the data -- oxygen saturation's 95-100 % for adults -- drawn as one
     * full-width band with edges, unlike a per-bucket ribbon of the wearer's own values. It
     * takes part in the scale, so a reading below it is seen to be below it.
     */
    referenceRange: ClosedFloatingPointRange<Double>? = null,
    /**
     * Opens the chart full screen, by a double tap or the mark beside the readout; null where
     * it cannot be. A single tap still reads a value at once: the value shows on touch-down,
     * so waiting to rule out a second tap costs nothing.
     */
    onExpand: (() -> Unit)? = null,
    /**
     * Keep a read value shown after the finger lifts, until the next touch. For the full-screen
     * chart, where a tap is how a value is read; inline it would leave a stale highlight.
     */
    holdSelection: Boolean = false,
    /**
     * How a value is written, on the axis and in the readout, where a plain number would be
     * wrong: a pace is minutes and seconds, and 7,73 reads as nothing a runner knows.
     */
    valueText: ((Double) -> String)? = null,
    /** A unit with no string resource -- "min/km" -- used in place of [unitRes]. */
    unitText: String? = null,
    /**
     * Low values at the top. For a pace, where the smaller number is the faster one: upright,
     * a fast stretch was a dip and the colours, red for fast, ran against the shape.
     */
    invertAxis: Boolean = false,
    /**
     * One even step per reading, its date beneath it, instead of a time axis. For a sequence of
     * readings taken weeks apart -- the recent days a weigh-in is set against -- where a time
     * axis made the strip look like the year's chart and crowded close readings together.
     */
    evenlySpaced: Boolean = false,
    /**
     * The reading being looked at, marked with a rule through it, a larger dot and its date
     * set off on the axis, so it is found among the others without reading every date.
     */
    highlight: Int? = null,
    /**
     * Further lines over this one -- breath rate over heart rate through a night -- placed by
     * time, so only on a chart with a time axis. One that [OverlayLine.sharesScale] joins this
     * line's scale; any other is fitted to its own, unlabelled, and read by touch. Drawn under
     * this line, which owns the axis. See [MultiLineChart] for the chips that switch them.
     */
    overlays: List<OverlayLine> = emptyList(),
    /** This line's colour where it is one of several, so it matches its chip; else the theme's. */
    lineColorOverride: Color? = null,
) {
    if (points.isEmpty()) return

    val values = points.map { it.value }
    // The goal takes part in the scale: a goal above the day's total must stay on the chart,
    // or "not reached yet" looks identical to "reached", which is the whole point of drawing
    // it. A goal already beaten simply sits below the peak.
    // Bars are read by comparing heights, so they must start at zero: on a floating baseline
    // a 7-hour night beside a 9-hour one looks like a third of the sleep rather than a fifth
    // less. A line has no such claim to make and keeps its tight scale, which is what lets a
    // small movement in a resting heart rate stay visible.
    // The band takes part in the scale, for the same reason the goal does: a ribbon clipped
    // at the top would show a day's peak as equal to the highest that happened to fit.
    val bandLow = (rangeBand + secondaryRangeBand).minOfOrNull { it.low }
    val bandHigh = (rangeBand + secondaryRangeBand).maxOfOrNull { it.high }
    // The second line takes part too: diastolic sits well below systolic, and a scale fitted
    // to the upper line alone would cut the lower one off entirely.
    val secondLow = secondaryPoints.minOfOrNull { it.value }
    val secondHigh = secondaryPoints.maxOfOrNull { it.value }
    val scatterLow = scatter.minOfOrNull { it.value } ?: values.min()
    val scatterHigh = scatter.maxOfOrNull { it.value } ?: values.max()
    val baselineLow = baseline.minOfOrNull { it.value } ?: values.min()
    val baselineHigh = baseline.maxOfOrNull { it.value } ?: values.max()
    val referenceLow = referenceRange?.start ?: values.min()
    val referenceHigh = referenceRange?.endInclusive ?: values.max()
    // A line in the same unit is read off the same axis, so it must fit on it.
    val shared = overlays.filter { it.sharesScale }.flatMap { it.points }.map { it.value }
    val sharedLow = shared.minOrNull() ?: values.min()
    val sharedHigh = shared.maxOrNull() ?: values.max()
    val fittedLow = minOf(values.min(), goal ?: values.min(), bandLow ?: values.min(), secondLow ?: values.min(), scatterLow, baselineLow, referenceLow, sharedLow)
    val fittedHigh = maxOf(values.max(), goal ?: values.max(), bandHigh ?: values.max(), secondHigh ?: values.max(), scatterHigh, baselineHigh, referenceHigh, sharedHigh)
    val widen = minSpan?.let { ((it - (fittedHigh - fittedLow)) / 2).coerceAtLeast(0.0) } ?: 0.0
    // Not below zero: a floor on the range must not invent negative heart rates.
    val dataLow = (fittedLow - widen).let { if (fittedLow >= 0.0) it.coerceAtLeast(0.0) else it }
    val dataHigh = fittedHigh + widen

    // The ends are rounded outward onto multiples of a round step, so every gridline lands on
    // a number a reader can use. Taking them straight from the data instead labelled a heart
    // rate of 45..113 as 45, 62, 79, 96, 113 -- five values none of which helps place a sixth.
    // The scale still contains the data, the goal and the band, so nothing that took part in
    // the old range is clipped out of the new one.
    val scale = remember(dataLow, dataHigh, bars, integral, compactAxis) {
        AxisScale.of(
            low = dataLow,
            high = dataHigh,
            targetSteps = if (compactAxis) GUIDE_INTERVALS / 2 else GUIDE_INTERVALS,
            integral = integral,
            includeZero = bars,
        )
    }
    val minValue = scale.min
    val maxValue = scale.max
    // A flat series would divide by zero; give it a nominal span so it draws as a centre line.
    val span = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    val lineColor = lineColorOverride ?: MaterialTheme.colorScheme.primary
    // Each other-unit line's own range, rounded like the axis, so its curve fills the plot the
    // way it would on a chart of its own.
    val overlayScales = remember(overlays) {
        overlays.map { line ->
            if (line.sharesScale || line.points.isEmpty()) {
                null
            } else {
                AxisScale.of(
                    low = line.points.minOf { it.value },
                    high = line.points.maxOf { it.value },
                    targetSteps = GUIDE_INTERVALS,
                    integral = false,
                    includeZero = false,
                )
            }
        }
    }

    /** A point's colour: the caller's where it gave one, else by zone, else the theme's. */
    fun colorAt(index: Int): Color =
        pointColors.getOrNull(index) ?: zones?.colorFor(points[index].value) ?: lineColor
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface
    // Faint enough to read as background. Sleep is a fixed blue rather than a theme colour:
    // it means night, and under dynamic colour a themed hue would drift with the wallpaper
    // until it no longer read as sleep at all. Exercise stays themed, having no such
    // convention to honour.
    val sleepColor = SLEEP_BAND.copy(alpha = BAND_ALPHA)
    val exerciseColor = MaterialTheme.colorScheme.tertiary.copy(alpha = BAND_ALPHA)
    val breakColor = MaterialTheme.colorScheme.onSurface.copy(alpha = BAND_ALPHA)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()

    var selected by remember(points) { mutableStateOf<Int?>(null) }

    // A bar is a slot centred on its day, so across days the plot runs half a slot past the
    // first and last bar. Everything placed by time -- the bars, the tick labels, the touch
    // point, the lines over the bars -- then shares one mapping. The bars used to be inset on
    // their own, so the touched point and the labels sat off their bars, most at the ends.
    val plotExtent = remember(points, extent, bars) { extent ?: if (bars) barExtent(points) else null }

    // The visible slice of the time axis, as a zoom factor and a left edge in fraction units.
    //
    // A whole day at once buries the busy parts: a workout is twenty pixels wide on a
    // 24-hour chart, and the detail that makes it worth looking at is not resolvable. Zoom
    // is a viewport over the fractions rather than a re-query, so everything positioned by
    // fraction -- the line, the bands, the axis icons, the tick labels and the touch
    // handler -- follows from one pair of numbers and cannot disagree.
    var zoom by remember(points, plotExtent) { mutableFloatStateOf(1f) }
    var pan by remember(points, plotExtent) { mutableFloatStateOf(0f) }

    fun visible(fraction: Float): Float = visibleFraction(fraction, zoom, pan)

    // Each point's horizontal position as a fraction of the width. Computed once here so the
    // touch handler and the drawing agree exactly on where a point sits.
    val fractions = remember(points, plotExtent, evenlySpaced) {
        // Half a step in from each edge, so the first and last dates sit under their points
        // rather than being pushed inward off them.
        if (evenlySpaced) points.indices.map { (it + 0.5f) / points.size } else horizontalFractions(points, plotExtent)
    }
    // The plot's own time range, shared with the icon row so an icon lands on the band it
    // names. Null where the series has no elapsed time and the fractions fall back to even
    // spacing, which no time can be mapped onto.
    val timeExtent = remember(points, plotExtent) {
        val start = plotExtent?.start ?: points.first().time
        val end = plotExtent?.endInclusive ?: points.last().time
        (start..end).takeIf { end > start }
    }
    // Runs of the line with data in them. Between runs is a gap -- a day with no value, or a
    // pause several times the readings' usual rhythm -- drawn dotted, and the band stops there.
    val segments = remember(points, emptyBuckets, bars) {
        segmentAtGaps(points, emptyBuckets, if (bars) null else readingGap(points))
    }
    // Each gap as the moments either side of it, for splitting the band at the same places.
    val gaps = remember(segments) {
        segments.zipWithNext { before, after -> before.last().time to after.first().time }
    }
    val maxZoom = remember(fractions) { maxZoomFor(fractions) }
    if (onVisibleRange != null) {
        val report by rememberUpdatedState(onVisibleRange)
        LaunchedEffect(zoom, pan, timeExtent) {
            val whole = timeExtent
            report(
                if (zoom <= 1f || whole == null) {
                    null
                } else {
                    val millis = Duration.between(whole.start, whole.endInclusive).toMillis()
                    val start = whole.start.plusMillis((millis * pan).toLong())
                    start..start.plusMillis((millis / zoom).toLong())
                },
            )
        }
    }

    fun nearestIndex(x: Float, width: Int): Int? {
        if (fractions.isEmpty() || width <= 0) return null
        // Back through the viewport, so a touch lands on the point under the finger at
        // whatever zoom the chart is currently showing.
        val target = ((x / width).coerceIn(0f, 1f) / zoom + pan).coerceIn(0f, 1f)
        return fractions.indices.minByOrNull { kotlin.math.abs(fractions[it] - target) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // The readout occupies a fixed row whether or not anything is selected, so touching
        // the chart does not shift the layout under the finger. Nothing can be selected on a
        // chart that ignores touch, so there it would only be an empty row.
        if (interactive) {
            val selectedPoint = selected?.let(points::getOrNull)
            if (overlays.isNotEmpty()) {
                OverlayReadout(
                    time = selectedPoint?.time,
                    lines = overlays,
                    ownColor = lineColor,
                    ownValue = selectedPoint?.let { point ->
                        (valueText?.invoke(point.value) ?: Formatting.number(point.value, valueDecimals)) +
                            ((unitText ?: unitRes?.let { stringResource(it) })?.let { " $it" } ?: "")
                    },
                )
            }
            SelectionReadout(
                point = selectedPoint,
                unitRes = unitRes,
                decimals = valueDecimals,
                // Matched by time: both values of a reading share its instant, as do both
                // means of a day's bucket.
                secondary = selectedPoint?.let { point -> secondaryPoints.firstOrNull { it.time == point.time } },
                onExpand = onExpand,
                valueText = valueText,
                unitText = unitText,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.weight(1f) else Modifier.height(CHART_HEIGHT.dp))
                // Room below the plot for the bottom gridline label, which is drawn under
                // its own line rather than clamped up on top of the series.
                .padding(top = 8.dp, bottom = AXIS_GAP.dp)
                .then(
                    if (!interactive) {
                        Modifier
                    } else {
                        Modifier
                            .pointerInput(points, plotExtent) {
                                // Pinch to zoom the time axis, drag to pan. Only the horizontal axis
                                // scales: the vertical one already fits the values on screen, and
                                // stretching it would make two charts of the same type incomparable.
                                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                                    val newZoom = (zoom * zoomChange).coerceIn(1f, maxZoom)
                                    // Zoom about the pinch centroid, so the stretch of chart under the
                                    // fingers stays under them rather than sliding away.
                                    val focus = pan + (centroid.x / size.width).coerceIn(0f, 1f) / zoom
                                    val afterZoom = focus - (centroid.x / size.width) / newZoom
                                    // Panning is in screen pixels, so it has to be divided by the zoom to
                                    // become a fraction of the whole series.
                                    zoom = newZoom
                                    pan = clampPan(
                                        afterZoom - panChange.x / size.width / newZoom,
                                        newZoom,
                                    )
                                }
                            }
                            .pointerInput(points) {
                                // Drag as well as tap: reading a series means sweeping along it, and
                                // lifting clears so the chart does not keep a stale highlight.
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset -> selected = nearestIndex(offset.x, size.width) },
                                    onDrag = { change, _ ->
                                        selected = nearestIndex(change.position.x, size.width)
                                    },
                                    onDragEnd = { if (!holdSelection) selected = null },
                                    onDragCancel = { if (!holdSelection) selected = null },
                                )
                            }
                            .pointerInput(points, onExpand) {
                                detectTapGestures(
                                    onDoubleTap = onExpand?.let { expand -> { expand() } },
                                    onPress = { offset ->
                                        selected = nearestIndex(offset.x, size.width)
                                        // Held highlight while the finger is down, cleared on release.
                                        tryAwaitRelease()
                                        if (!holdSelection) selected = null
                                    },
                                )
                            }
                    },
                ),
        ) {
            // The plot's time origin, which is the extent where one is given and the first
            // reading otherwise. Bands, the goal marker and the line all measure from it, so
            // a band cannot drift away from the stretch of line it explains.
            val firstTime = (plotExtent?.start ?: points.first().time).toEpochMilli()
            val lastTime = (plotExtent?.endInclusive ?: points.last().time).toEpochMilli()
            val timeSpan = (lastTime - firstTime).takeIf { it > 0L }

            // The single conversion from "where in the series" to "where on screen". Zoom and
            // pan live here alone, so the line, the bands, the markers and the axis cannot
            // drift apart at any magnification.
            fun xForFraction(fraction: Float): Float = visible(fraction) * size.width

            fun xFor(index: Int): Float = xForFraction(fractions[index])

            /** A moment's position, for anything placed by time rather than by sample. */
            fun xForTime(millis: Long): Float? {
                val span = timeSpan ?: return null
                return xForFraction(((millis - firstTime).toDouble() / span).toFloat())
            }

            fun yFor(value: Double): Float {
                val fraction = (value - minValue) / span
                return (size.height * (if (invertAxis) fraction else 1.0 - fraction)).toFloat()
            }

            val offsets = points.mapIndexed { index, point ->
                Offset(xFor(index), yFor(point.value))
            }
            referenceRange?.let { range ->
                val top = yFor(range.endInclusive)
                val bottom = yFor(range.start)
                drawRect(
                    color = REFERENCE_COLOR.copy(alpha = REFERENCE_ALPHA),
                    topLeft = Offset(0f, top),
                    size = androidx.compose.ui.geometry.Size(size.width, bottom - top),
                )
                listOf(top, bottom).forEach { y ->
                    drawLine(
                        color = REFERENCE_COLOR.copy(alpha = REFERENCE_EDGE_ALPHA),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    )
                }
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
                    // Clamped to the viewport rather than to the whole series, so a band
                    // running off the visible edge is cut at the edge instead of vanishing.
                    val from = (xForTime(session.start.toEpochMilli()) ?: return@forEach)
                        .coerceIn(0f, size.width)
                    val to = (xForTime(session.end.toEpochMilli()) ?: return@forEach)
                        .coerceIn(0f, size.width)
                    if (to <= from) return@forEach
                    drawRect(
                        color = when (session.kind) {
                            Session.Kind.SLEEP -> sleepColor
                            Session.Kind.EXERCISE, Session.Kind.MINDFULNESS -> exerciseColor
                        },
                        topLeft = Offset(from, 0f),
                        size = androidx.compose.ui.geometry.Size(to - from, size.height),
                    )
                }
                breaks.forEach { pause ->
                    val from = (xForTime(pause.start.toEpochMilli()) ?: return@forEach).coerceIn(0f, size.width)
                    val to = (xForTime(pause.endInclusive.toEpochMilli()) ?: return@forEach).coerceIn(0f, size.width)
                    if (to <= from) return@forEach
                    drawRect(
                        color = breakColor,
                        topLeft = Offset(from, 0f),
                        size = androidx.compose.ui.geometry.Size(to - from, size.height),
                    )
                }
            }

            // One bar per bucket, from the baseline up to the day's figure. Drawn instead of
            // the line, never alongside it: the two say the same thing and the line is the
            // one that overstates it.
            //
            // The width comes from the gap between neighbouring points rather than from the
            // point count, so a missing day leaves a space instead of widening its neighbours
            // -- the same reason points are placed by timestamp everywhere else here.
            if (bars && offsets.isNotEmpty()) {
                val baseline = yFor(minValue.coerceAtMost(0.0).let { if (it < 0) it else 0.0 })
                    .coerceAtMost(size.height)
                val slot = if (offsets.size > 1) {
                    offsets.zipWithNext { a, b -> b.x - a.x }.filter { it > 0f }.minOrNull()
                        ?: size.width
                } else {
                    size.width / BAR_LONE_DIVISOR
                }
                val barWidth = (slot * BAR_WIDTH_FRACTION).coerceAtLeast(1f)
                val byTime = stack.associateBy { it.time.toEpochMilli() }

                // Centred on the bucket's own position. The plot already runs half a slot past
                // the first and last bar (`barExtent`), so nothing is clipped at the ends and
                // every gap is the same.
                offsets.forEachIndexed { index, offset ->
                    val left = offset.x - barWidth / 2f
                    val parts = byTime[points[index].time.toEpochMilli()]?.parts

                    if (parts == null) {
                        val top = offset.y.coerceAtMost(baseline)
                        drawRect(
                            color = colorAt(index),
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(
                                barWidth,
                                (baseline - top).coerceAtLeast(1f),
                            ),
                        )
                        return@forEachIndexed
                    }

                    // Stacked from the baseline up, each segment measured on the same scale
                    // as the whole bar, so the parts end exactly where the total does rather
                    // than being drawn as fractions of a height computed separately.
                    var cumulative = 0.0
                    var runningTop = baseline
                    parts.forEachIndexed { partIndex, part ->
                        cumulative += part
                        val segmentTop = yFor(cumulative).coerceIn(0f, baseline)
                        val height = (runningTop - segmentTop).coerceAtLeast(0f)
                        if (height > 0f) {
                            drawRect(
                                color = lineColor.copy(
                                    alpha = stackAlpha(partIndex, parts.size),
                                ),
                                topLeft = Offset(left, segmentTop),
                                size = androidx.compose.ui.geometry.Size(barWidth, height),
                            )
                        }
                        runningTop = segmentTop
                    }
                }
            }

            // The spread behind the line: one filled shape across the low edge and back along
            // the high edge. Drawn before the guides so the axis stays readable over it, and
            // before the line so the mean it explains is never obscured by it.
            fun drawRibbons(band: List<ValueBand>) {
            if (band.size > 1 && timeSpan != null) {
                val lows = band.mapNotNull { band ->
                    xForTime(band.time.toEpochMilli())?.let { Offset(it, yFor(band.low)) }
                }
                val highs = band.mapNotNull { band ->
                    xForTime(band.time.toEpochMilli())?.let { Offset(it, yFor(band.high)) }
                }
                if (lows.size == highs.size && lows.size > 1) {
                    // One ribbon per run between gaps. As a single shape it ran straight
                    // across days nothing was recorded, sloping or pinching through them as
                    // if the spread had been measured there.
                    val runs = mutableListOf(mutableListOf(0))
                    for (index in 1 until band.size) {
                        val from = band[index - 1].time
                        val to = band[index].time
                        val broken = gaps.any { (before, after) -> from <= before && to >= after }
                        if (broken) runs += mutableListOf(index) else runs.last() += index
                    }
                    runs.filter { it.size > 1 }.forEach { run ->
                        val ribbon = Path().apply {
                            moveTo(lows[run.first()].x, lows[run.first()].y)
                            run.drop(1).forEach { lineTo(lows[it].x, lows[it].y) }
                            run.reversed().forEach { lineTo(highs[it].x, highs[it].y) }
                            close()
                        }
                        drawPath(ribbon, color = lineColor.copy(alpha = RANGE_BAND_ALPHA))
                    }
                }
            }
            }
            drawRibbons(rangeBand)
            drawRibbons(secondaryRangeBand)

            // Guides labelled at their own line, so a value can be read off the chart rather
            // than inferred from the endpoints. The count follows from the round step rather
            // than being fixed: four intervals is what the scale aims for, and it lands on
            // three or five where that is what keeps the ends round.
            val guides = scale.guides
            guides.forEach { guide ->
                val y = yFor(guide)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            // Placed by time like every other point, so the marker sits exactly where the
            // line meets the goal rather than at the nearest sample.
            val crossingX = goalCrossing?.let { crossing ->
                xForTime(crossing.toEpochMilli())?.takeIf { it in 0f..size.width }
            }

            // A gap joined by a faint dotted line: the run continues, but nothing was recorded
            // in between. Left blank, a gap read as the chart failing to draw; drawn solid, it
            // claimed readings across hours without one.
            if (!bars) {
                val gapColor = lineColor.copy(alpha = GAP_ALPHA)
                val dotted = PathEffect.dashPathEffect(floatArrayOf(GAP_DOT_ON.dp.toPx(), GAP_DOT_OFF.dp.toPx()))
                var end = 0
                segments.zipWithNext().forEach { (before, _) ->
                    end += before.size
                    drawLine(
                        color = gapColor,
                        start = offsets[end - 1],
                        end = offsets[end],
                        strokeWidth = GAP_WIDTH.dp.toPx(),
                        pathEffect = dotted,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // The other lines first, so the one owning the axis is drawn over them. Each broken
            // where its readings stop for longer than its own gap, like the main line.
            overlays.forEachIndexed { index, line ->
                val scale = overlayScales[index]
                fun yOf(value: Double): Float = if (scale == null) {
                    yFor(value)
                } else {
                    val own = (value - scale.min) / ((scale.max - scale.min).takeIf { it > 0.0 } ?: 1.0)
                    yFor(minValue + own * span)
                }
                var previous: Point? = null
                val path = Path()
                line.points.forEach { point ->
                    val x = xForTime(point.time.toEpochMilli()) ?: return@forEach
                    val y = yOf(point.value)
                    val last = previous
                    if (last == null || (line.maxGap != null && java.time.Duration.between(last.time, point.time) > line.maxGap)) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                    previous = point
                }
                drawPath(path, color = line.color, style = Stroke(width = LINE_WIDTH.dp.toPx(), cap = StrokeCap.Round))

                // The touched moment on this line too, so the readout's value has a place.
                selected?.let { at -> line.nearest(points[at].time) }?.let { point ->
                    val x = xForTime(point.time.toEpochMilli()) ?: return@let
                    drawCircle(color = surfaceColor, radius = 6.dp.toPx(), center = Offset(x, yOf(point.value)))
                    drawCircle(color = line.color, radius = 4.dp.toPx(), center = Offset(x, yOf(point.value)))
                }
            }

            // Each run of consecutive points is stroked on its own, so a gap stays a gap.
            var drawn = 0
            if (!bars) segments.forEach { segment ->
                val start = drawn
                val segmentOffsets = offsets.subList(drawn, drawn + segment.size)
                drawn += segment.size
                if (segmentOffsets.isEmpty()) return@forEach

                when {
                    // A lone point between two gaps has no line to draw, so it is marked
                    // instead -- otherwise a day surrounded by empty days vanishes entirely.
                    segmentOffsets.size == 1 -> drawCircle(
                        color = colorAt(start),
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
                    zones != null || pointColors.isNotEmpty() -> segmentOffsets.zipWithNext()
                        .forEachIndexed { index, (from, to) ->
                            val fromColor = colorAt(start + index)
                            val toColor = colorAt(start + index + 1)
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

            // The second line, straight and coloured by its own bands like the first. It has no
            // gaps to honour: the types that carry one are readings, joined across empty days.
            if (secondaryPoints.isNotEmpty()) {
                val secondFractions = if (evenlySpaced) {
                    // Placed on its partner's step: both values of a day share its instant.
                    secondaryPoints.map { second ->
                        fractions.getOrNull(points.indexOfFirst { it.time == second.time }) ?: 0f
                    }
                } else {
                    horizontalFractions(
                        secondaryPoints,
                        plotExtent ?: (points.first().time..points.last().time),
                    )
                }
                val secondOffsets = secondaryPoints.mapIndexed { index, point ->
                    Offset(xForFraction(secondFractions[index]), yFor(point.value))
                }
                if (!bars) secondOffsets.zipWithNext().forEachIndexed { index, (from, to) ->
                    val fromColor = secondaryZones?.colorFor(secondaryPoints[index].value) ?: lineColor
                    val toColor = secondaryZones?.colorFor(secondaryPoints[index + 1].value) ?: lineColor
                    drawLine(
                        brush = if (fromColor == toColor) {
                            SolidColor(fromColor)
                        } else {
                            Brush.linearGradient(colors = listOf(fromColor, toColor), start = from, end = to)
                        },
                        start = from,
                        end = to,
                        strokeWidth = LINE_WIDTH.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                if ((markReadings || secondOffsets.size == 1) && secondaryPoints.size <= MAX_DOTS) {
                    secondaryPoints.forEachIndexed { index, point ->
                        drawCircle(
                            color = secondaryZones?.colorFor(point.value) ?: lineColor,
                            radius = 3.dp.toPx(),
                            center = secondOffsets[index],
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

            highlight?.takeIf { it in points.indices }?.let { index ->
                val x = xFor(index)
                drawLine(
                    color = goalColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(color = surfaceColor, radius = 7.dp.toPx(), center = Offset(x, yFor(points[index].value)))
                drawCircle(color = goalColor, radius = 5.dp.toPx(), center = Offset(x, yFor(points[index].value)))
                secondaryPoints.firstOrNull { it.time == points[index].time }?.let { second ->
                    drawCircle(color = surfaceColor, radius = 7.dp.toPx(), center = Offset(x, yFor(second.value)))
                    drawCircle(color = goalColor, radius = 5.dp.toPx(), center = Offset(x, yFor(second.value)))
                }
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
                secondaryPoints.firstOrNull { it.time == points[index].time }?.let { second ->
                    val y2 = yFor(second.value)
                    drawCircle(color = surfaceColor, radius = 7.dp.toPx(), center = Offset(x, y2))
                    drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(x, y2))
                }
            }

            // Coloured like the line they sit on: in the theme colour they read as black
            // specks scattered over a coloured curve, which looks like a defect rather than
            // like the measurements the line is made of. The count cap still applies, since
            // even discrete readings crowd once a long span holds enough of them.
            // Behind the line's own dots, and faint, so the line stays the answer and the
            // nights read as what it was made from. Joined by a dotted line so night-to-night
            // swings can be followed, broken where a night is missing: a line through a
            // missing night would draw a value nobody measured.
            // Dashed and in the axis-label colour, under the line: a yardstick to read the line
            // against, not a second series. Broken where a day has no mean, as the line is.
            val baselineDash = PathEffect.dashPathEffect(
                floatArrayOf(GOAL_DASH_ON.dp.toPx(), GOAL_DASH_OFF.dp.toPx()),
            )
            // One path per unbroken run, not a line per day: over a year a day is a pixel or
            // two, shorter than one dash, so segment by segment the dashes never broke.
            val baselinePath = Path()
            var previous: Point? = null
            baseline.forEach { point ->
                val x = xForTime(point.time.toEpochMilli()) ?: return@forEach
                val y = yFor(point.value)
                val joined = previous?.let { Duration.between(it.time, point.time) <= SCATTER_MAX_GAP } == true
                if (joined) baselinePath.lineTo(x, y) else baselinePath.moveTo(x, y)
                previous = point
            }
            drawPath(
                path = baselinePath,
                color = labelColor,
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = baselineDash),
            )

            val scatterColor = lineColor.copy(alpha = SCATTER_ALPHA)
            val dotted = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
            scatter.sortedBy { it.time }.zipWithNext().forEach { (from, to) ->
                if (Duration.between(from.time, to.time) > SCATTER_MAX_GAP) return@forEach
                val x1 = xForTime(from.time.toEpochMilli()) ?: return@forEach
                val x2 = xForTime(to.time.toEpochMilli()) ?: return@forEach
                drawLine(
                    color = scatterColor,
                    start = Offset(x1, yFor(from.value)),
                    end = Offset(x2, yFor(to.value)),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dotted,
                    cap = StrokeCap.Round,
                )
            }
            scatter.forEach { point ->
                val x = xForTime(point.time.toEpochMilli()) ?: return@forEach
                drawCircle(
                    color = scatterColor,
                    radius = SCATTER_RADIUS.dp.toPx(),
                    center = Offset(x, yFor(point.value)),
                )
            }

            if (markReadings && points.size <= MAX_DOTS) {
                points.forEachIndexed { index, point ->
                    drawCircle(
                        color = colorAt(index),
                        radius = 3.dp.toPx(),
                        center = Offset(xFor(index), yFor(point.value)),
                    )
                }
            }

            // The gridline labels last, over everything drawn above. Drawn with their
            // gridlines they sat *under* the series, so a dense line or a bar ran straight
            // through the digits and the halo -- which exists to separate them from what
            // crosses them -- was painted over with them. Reported on respiratory rate.
            guides.forEach { guide ->
                val y = yFor(guide)
                // Measured on a single unwrapped line. Without this the measurer inherits the
                // canvas width as its constraint and a label can come back wrapped or
                // clipped -- on a session chart the guides read "138, 12, 14, 101" where the
                // middle two were 127 and 114 with their last digit cut off. An axis that
                // silently drops digits is worse than no axis.
                val label = textMeasurer.measure(
                    text = valueText?.invoke(guide) ?: Formatting.axisLabel(guide, scale.decimals),
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false,
                )
                // Centred on its line, except the bottom one: clamping that inside the plot
                // puts it on top of the line and whatever the series does there, which on a
                // rising chart is exactly where the data starts. Below the axis it is clear
                // of both, and the padding reserved beneath the canvas leaves room for it.
                val labelY = if (guide == (if (invertAxis) guides.last() else guides.first())) {
                    size.height
                } else {
                    (y - label.size.height / 2f).coerceAtLeast(0f)
                }
                // Haloed rather than sitting on a filled block.
                //
                // A rect the width of the label hid whatever the series did behind it, which
                // on a chart with bars near the axis is a bar's left edge and on a dense line
                // is the part of the curve the reader is trying to follow. The halo separates
                // the digits from whatever crosses them while covering almost nothing.
                //
                // Drawn as eight offset copies *under* the fill, not as a stroke on the glyph
                // itself: a stroke is centred on the outline, so half its width eats inward.
                // Measured at 2dp on this phone that is a 2.8px bite into a ~3px stem, which
                // reads as bold text with the counters of 6, 4 and 0 filled in -- reported
                // from the device. An offset copy only ever adds pixels outside the glyph, so
                // the digits keep their own weight.
                val halo = LABEL_HALO.dp.toPx()
                HALO_DIRECTIONS.forEach { (dx, dy) ->
                    drawText(
                        textLayoutResult = label,
                        color = surfaceColor,
                        topLeft = Offset(dx * halo, labelY + dy * halo),
                    )
                }
                drawText(
                    textLayoutResult = label,
                    color = labelColor,
                    topLeft = Offset(0f, labelY),
                )
            }
        }

        // Above the hour labels rather than among them: a band says *when* something
        // happened but not *what*, and the list below the chart names the sessions without
        // saying which band is which. With two or three bands that is guesswork.
        //
        // Suppressed under bars, where every bar already *is* a session: an icon per day then
        // names what the chart is made of rather than pointing at anything, and four weeks of
        // them run together into a band of their own.
        if (sessions.isNotEmpty() && timeExtent != null && !bars) {
            SessionAxisIcons(sessions = sessions, extent = timeExtent, zoom = zoom, pan = pan)
        }

        if (evenlySpaced) {
            ReadingAxis(points = points, fractions = fractions, highlight = highlight, zoom = zoom, pan = pan)
        } else {
            TimeAxis(points = points, extent = plotExtent, zoom = zoom, pan = pan, datesOnDayChange = extent == null)
        }
    }
}

/**
 * Each reading's date beneath its step, for an [evenlySpaced] chart. The highlighted one is
 * placed first and in the highlight's colour, so it is never the label left out; the others
 * fill in where they do not overprint a label already placed.
 */
@Composable
private fun ReadingAxis(points: List<Point>, fractions: List<Float>, highlight: Int?, zoom: Float, pan: Float) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val marked = MaterialTheme.colorScheme.tertiary
    // Readings weeks apart can cross a new year, and "27 Okt." then "10 Sept." does not say
    // which of the two years each is: the year goes under the first date and each first date
    // of a new year, and only where the readings span more than one.
    val zone = java.time.ZoneId.systemDefault()
    val years = points.map { it.time.atZone(zone).year }
    val spansYears = years.distinct().size > 1
    Layout(
        content = {
            points.forEachIndexed { index, point ->
                val newYear = spansYears && (index == 0 || years[index] != years[index - 1])
                Text(
                    text = Formatting.dayAndMonth(point.time) + if (newYear) "\n" + years[index] else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == highlight) marked else muted,
                    fontWeight = if (index == highlight) FontWeight.Bold else null,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val gap = LABEL_GAP.dp.roundToPx()
        layout(constraints.maxWidth, height) {
            val taken = mutableListOf<IntRange>()
            val order = listOfNotNull(highlight?.takeIf { it in placeables.indices }) +
                placeables.indices.filter { it != highlight }
            order.forEach { index ->
                val placeable = placeables[index]
                val centre = visibleFraction(fractions[index], zoom, pan) * constraints.maxWidth
                if (centre < 0f || centre > constraints.maxWidth) return@forEach
                val x = (centre - placeable.width / 2f).toInt()
                    .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                val span = (x - gap)..(x + placeable.width + gap)
                if (taken.none { it.first < span.last && span.first < it.last }) {
                    placeable.place(x, 0)
                    taken += span
                }
            }
        }
    }
}

private const val CHART_HEIGHT = 200

/** Least space between two time labels, so neighbours read as two dates. */
private const val LABEL_GAP = 6
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
    /** As on [LineChart]: take the parent's bounded height rather than the fixed one. */
    fillHeight: Boolean = false,
) {
    val sleepColor = SLEEP_BAND.copy(alpha = BAND_ALPHA)
    val exerciseColor = MaterialTheme.colorScheme.tertiary.copy(alpha = BAND_ALPHA)
    val breakColor = MaterialTheme.colorScheme.onSurface.copy(alpha = BAND_ALPHA)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val start = extent.start.toEpochMilli()
    val span = (extent.endInclusive.toEpochMilli() - start).toDouble().takeIf { it > 0.0 }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.weight(1f) else Modifier.height(TIMELINE_HEIGHT.dp)),
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
                        Session.Kind.EXERCISE, Session.Kind.MINDFULNESS -> exerciseColor
                    },
                    topLeft = Offset(from, 0f),
                    size = androidx.compose.ui.geometry.Size(to - from, size.height),
                )
                // A workout's breaks in grey inside its band, as on its heart-rate curve: the
                // hours off the bike were not training.
                session.breaks.forEach { pause ->
                    val pauseFrom = ((pause.start.toEpochMilli() - start) / span).toFloat().coerceIn(0f, 1f) * size.width
                    val pauseTo = ((pause.end.toEpochMilli() - start) / span).toFloat().coerceIn(0f, 1f) * size.width
                    if (pauseTo <= pauseFrom) return@forEach
                    drawRect(
                        color = trackColor,
                        topLeft = Offset(pauseFrom, 0f),
                        size = androidx.compose.ui.geometry.Size(pauseTo - pauseFrom, size.height),
                    )
                    drawRect(
                        color = breakColor,
                        topLeft = Offset(pauseFrom, 0f),
                        size = androidx.compose.ui.geometry.Size(pauseTo - pauseFrom, size.height),
                    )
                }
            }
        }

        if (sessions.isNotEmpty()) {
            SessionAxisIcons(sessions = sessions, extent = extent)
        }
        TimeAxis(points = emptyList(), extent = extent)
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
private fun SessionAxisIcons(
    sessions: List<Session>,
    extent: ClosedRange<Instant>,
    zoom: Float = 1f,
    pan: Float = 0f,
) {
    val start = extent.start.toEpochMilli()
    val span = (extent.endInclusive.toEpochMilli() - start).toDouble()

    val placed = remember(sessions, extent, zoom, pan) {
        sessions.mapNotNull { session ->
            val middle = (session.start.toEpochMilli() + session.end.toEpochMilli()) / 2
            // Through the same viewport the plot uses, so an icon stays under its band at
            // every magnification; a session zoomed out of view drops its icon rather than
            // piling up against the edge.
            val fraction = (((middle - start) / span).toFloat() - pan) * zoom
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
    extent: ClosedRange<Instant>? = null,
    zoom: Float = 1f,
    pan: Float = 0f,
    /**
     * A chart of several days zoomed down to hours: the first label and each midnight also
     * name the date, or "00:00" leaves the reader guessing which day it began. A single-day
     * chart has its date in the header and keeps plain times.
     */
    datesOnDayChange: Boolean = false,
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
    // Ticked over the *visible* window, not the whole series. Zooming into an hour otherwise
    // left a single label on screen -- or none -- because the ticks were still spaced for a
    // day. Re-ticking makes the axis get finer as the chart does, which is the point of
    // zooming in the first place.
    val whole = Duration.between(first, last)
    val visibleStart = first.plusMillis((whole.toMillis() * pan).toLong())
    val visibleEnd = visibleStart.plusMillis((whole.toMillis() / zoom).toLong())

    val span = Duration.between(visibleStart, visibleEnd)
    // The threshold is a day *plus the evening a night can reach back into*, not exactly 24
    // hours. A sleep day widens its extent to contain a night that began before midnight
    // (22:18 on the device, so 25.7 hours), and at a hard 24 that window fell through to the
    // multi-day format and labelled a single night with one repeated date -- which read on
    // screen as the axis losing its labels entirely.
    val intraday = !span.isNegative && span <= Duration.ofHours(HOURS_INTRADAY_MAX)

    // Ticks are a ruler over the visible window -- round hours within a day, calendar days,
    // weeks or months across days -- positioned by time. Within a day, snapping to samples
    // gave labels like 06:02 and 16:51. Across days the ticks used to be snapped to points of
    // the whole series and only stretched by the zoom, so zoomed between two of them there was
    // no label at all and no way to tell where the chart was.
    val ticks = remember(intraday, visibleStart, visibleEnd) {
        if (intraday) {
            hourlyTicks(visibleStart, visibleEnd)
        } else {
            calendarTicks(visibleStart, visibleEnd)
        }.filter { it.fraction in 0f..1f }
    }

    Layout(
        content = {
            ticks.forEachIndexed { index, tick ->
                val dated = datesOnDayChange && (index == 0 || tick.time.isLocalMidnight())
                Text(
                    text = when {
                        !intraday -> Formatting.dayAndMonth(tick.time)
                        dated -> Formatting.time(tick.time) + "\n" + Formatting.dayAndMonth(tick.time)
                        else -> Formatting.time(tick.time)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0

        val gap = LABEL_GAP.dp.roundToPx()
        layout(constraints.maxWidth, height) {
            // A label that would overprint the one before it is left out. Ticks snapped to
            // samples can sit a day apart after a long gap -- 15 and 16 Sept on a month of
            // weight, 28 and 31 Aug on a year -- and two dates printed over each other read
            // as neither; on a narrow tile it happened on every chart.
            var taken = Int.MIN_VALUE
            placeables.forEachIndexed { index, placeable ->
                // Centred on the tick, then held inside the chart so the first and last
                // labels are not half off the edge.
                val centre = ticks[index].fraction * constraints.maxWidth
                val x = (centre - placeable.width / 2f).toInt()
                    .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                if (x >= taken) {
                    placeable.place(x, 0)
                    taken = x + placeable.width + gap
                }
            }
        }
    }
}

/** Whether a moment is the start of a day in the device's zone. */
private fun Instant.isLocalMidnight(): Boolean =
    atZone(java.time.ZoneId.systemDefault()).toLocalTime() == java.time.LocalTime.MIDNIGHT

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
internal data class AxisTick(val fraction: Float, val time: Instant)

/**
 * Ticks at round calendar dates across a window of days, positioned by time.
 *
 * Days, then every second or third day, then Mondays, then every other Monday, then the first
 * of each month or every few months -- whichever keeps the window within [AXIS_TICKS] or so
 * labels. Recomputed for the visible window, so zooming into a year ends at single days.
 * Daily points sit at midnight, so a day's tick lands on its point.
 */
internal fun calendarTicks(
    start: Instant,
    end: Instant,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): List<AxisTick> {
    val spanMillis = (end.toEpochMilli() - start.toEpochMilli()).takeIf { it > 0L }
        ?: return emptyList()
    val firstDay = start.atZone(zone).toLocalDate()
    val lastDay = end.atZone(zone).toLocalDate()
    val spanDays = java.time.temporal.ChronoUnit.DAYS.between(firstDay, lastDay).coerceAtLeast(1L)

    val dayStep = TICK_DAY_STEPS.firstOrNull { spanDays / it <= AXIS_TICKS }
    val monthStep = TICK_MONTH_STEPS.firstOrNull { spanDays / (it * DAYS_PER_MONTH) <= AXIS_TICKS }
        ?: TICK_MONTH_STEPS.last()
    val keep: (java.time.LocalDate) -> Boolean = when (dayStep) {
        null -> { day -> day.dayOfMonth == 1 && (day.monthValue - 1) % monthStep == 0L }
        // Weekly and fortnightly marks on Mondays, where a week starts on a German calendar.
        DAYS_PER_WEEK -> { day -> day.dayOfWeek == java.time.DayOfWeek.MONDAY }
        2 * DAYS_PER_WEEK -> { day ->
            day.dayOfWeek == java.time.DayOfWeek.MONDAY &&
                day.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 2 == 0
        }
        else -> { day -> day.toEpochDay() % dayStep == 0L }
    }

    return generateSequence(firstDay) { it.plusDays(1) }
        .takeWhile { !it.isAfter(lastDay) }
        .filter(keep)
        .map { it.atStartOfDay(zone).toInstant() }
        .filter { !it.isBefore(start) && !it.isAfter(end) }
        .map { time ->
            AxisTick(
                fraction = ((time.toEpochMilli() - start.toEpochMilli()).toDouble() / spanMillis).toFloat(),
                time = time,
            )
        }
        .toList()
}

/** Day intervals tried in turn; beyond a fortnight the ticks go by month. */
private val TICK_DAY_STEPS = listOf(1L, 2L, 3L, DAYS_PER_WEEK, 2 * DAYS_PER_WEEK)

/** Month intervals tried in turn once days are too many. */
private val TICK_MONTH_STEPS = listOf(1L, 2L, 3L, 6L, 12L)

private const val DAYS_PER_WEEK = 7L
private const val DAYS_PER_MONTH = 30L

/**
 * Maps a fraction of the whole series onto a fraction of the visible viewport.
 *
 * The single place zoom and pan are applied, so the line, the bands, the markers, the axis
 * and the touch handler cannot drift apart at any magnification.
 */
internal fun visibleFraction(fraction: Float, zoom: Float, pan: Float): Float =
    (fraction - pan) * zoom

/**
 * Keeps the viewport inside the data.
 *
 * Panning past either end would show empty space and leave the reader unsure whether the day
 * really stopped there. At zoom 1 the only valid pan is zero: the whole series is on screen.
 */
internal fun clampPan(value: Float, scale: Float): Float =
    value.coerceIn(0f, (1f - 1f / scale).coerceAtLeast(0f))

/**
 * How far a chart may zoom: until [MIN_VISIBLE_POINTS] points fill the width, and never past
 * [MAX_ZOOM]. Closer than that there is one bar or a pair of dots on screen, which says
 * nothing a tap on it would not -- a week of bars needs barely any zoom, a day of heart rate
 * keeps the full range.
 */
internal fun maxZoomFor(fractions: List<Float>): Float {
    val sorted = fractions.sorted()
    val span = MIN_VISIBLE_POINTS - 1
    val tightest = sorted.indices.drop(span).minOfOrNull { sorted[it] - sorted[it - span] }
        ?.takeIf { it > 0f }
        ?: return 1f
    return (1f / tightest).coerceIn(1f, MAX_ZOOM)
}

/**
 * The time range a bar chart spans: half the narrowest gap between bars beyond the first and
 * last, so each bar's slot lies wholly inside the plot and centred on its time. Null for a
 * single bar, which is centred anyway.
 */
internal fun barExtent(points: List<Point>): ClosedRange<Instant>? {
    val times = points.map { it.time }.sorted()
    val gap = times.zipWithNext { a, b -> Duration.between(a, b) }
        .filter { !it.isZero && !it.isNegative }
        .minOrNull() ?: return null
    val half = gap.dividedBy(2)
    return times.first().minus(half)..times.last().plus(half)
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
 * A further line over a [LineChart]: its readings, its colour, how a value is written, and the
 * longest gap it is drawn across.
 */
data class OverlayLine(
    val points: List<Point>,
    val color: Color,
    /** A value as the readout writes it, number and unit: "14 /min". */
    val format: (Double) -> String,
    /** In the main line's unit, so on its scale; otherwise on a scale of its own. */
    val sharesScale: Boolean = false,
    /** Readings further apart than this are not joined; null joins them all. */
    val maxGap: java.time.Duration? = null,
) {
    /**
     * The reading nearest [time], or null where none is within [maxGap] of it -- a moment the
     * line has no value for must not borrow one from an hour away.
     */
    fun nearest(time: Instant): Point? {
        val best = points.minByOrNull { kotlin.math.abs(it.time.toEpochMilli() - time.toEpochMilli()) } ?: return null
        val limit = maxGap ?: return best
        return best.takeIf { java.time.Duration.between(it.time, time).abs() <= limit }
    }
}

/**
 * Every line's value at the touched moment, each beside a dot of its colour, in a row that is
 * always present like [SelectionReadout]'s. Text stays in the text colour: the dot says which
 * line, the number is read like any other.
 */
@Composable
private fun OverlayReadout(time: Instant?, lines: List<OverlayLine>, ownColor: Color, ownValue: String?) {
    val values = buildList {
        add(ownColor to ownValue)
        lines.forEach { line -> add(line.color to time?.let(line::nearest)?.let { line.format(it.value) }) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(OVERLAY_READOUT_HEIGHT.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (time != null) {
            values.forEach { (color, text) ->
                DotText(
                    color = color,
                    text = text ?: "–",
                    style = MaterialTheme.typography.labelMedium,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    gap = 4.dp,
                )
            }
        }
    }
}

private const val OVERLAY_READOUT_HEIGHT = 20

/**
 * The touched point's value and time, in a row that is always present.
 *
 * Reserving the space keeps the chart from jumping when a touch begins, which on a chart is
 * disorienting: the thing being pointed at moves out from under the finger.
 */
@Composable
private fun SelectionReadout(
    point: Point?,
    @StringRes unitRes: Int?,
    secondary: Point? = null,
    decimals: Int? = null,
    onExpand: (() -> Unit)? = null,
    valueText: ((Double) -> String)? = null,
    unitText: String? = null,
) {
    val unit = unitText ?: unitRes?.let { stringResource(it) }
    fun text(value: Double) = valueText?.invoke(value) ?: Formatting.number(value, decimals)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = point?.let { selected ->
                text(selected.value) +
                    (secondary?.let { "/" + text(it.value) } ?: "") +
                    (unit?.let { " $it" } ?: "")
            }.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = point?.let { Formatting.dateTime(it.time) }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onExpand != null) ExpandButton(onExpand)
    }
}

/**
 * A cubic curve through every point, for series where the underlying quantity varies
 * continuously rather than in steps. Monotone between neighbours ([monotoneControls]): an
 * overshoot would invent readings that were never recorded -- dipping below zero between two
 * step counts, for instance -- which for health data is not a cosmetic problem but a false
 * statement.
 */
private fun smoothPath(offsets: List<Offset>): Path = Path().apply {
    val controls = monotoneControls(
        FloatArray(offsets.size) { offsets[it].x },
        FloatArray(offsets.size) { offsets[it].y },
    )
    moveTo(offsets.first().x, offsets.first().y)
    controls.forEachIndexed { index, c ->
        val next = offsets[index + 1]
        cubicTo(c.x1, c.y1, c.x2, c.y2, next.x, next.y)
    }
}

private const val GOAL_DASH_ON = 6f
private const val GOAL_DASH_OFF = 4f

private const val GOAL_MARKER_RADIUS = 6f
private const val GOAL_MARKER_RING = 2.5f

/**
 * Intervals the vertical scale aims for: four, giving five labels, which stays legible at the
 * height this chart is drawn. A target rather than a count -- rounding the ends onto a round
 * step can land on one fewer or one more, and a round axis is worth the variance.
 */
private const val GUIDE_INTERVALS = 4

/**
 * How far the halo copies of a gridline label are offset from the glyphs.
 *
 * Enough to lift the digits off a line or a bar crossing them, small enough that the label
 * still hides almost nothing -- which was the point of replacing the filled backing rect.
 */
private const val LABEL_HALO = 1.5f

/**
 * The eight directions a halo copy is drawn in, as unit offsets.
 *
 * All eight, rather than the four sides: with only the axis-aligned offsets a diagonal stroke
 * -- the tail of a 4, the waist of a 2 -- meets the background at a corner the halo never
 * covered, and the digit frays exactly where it crosses a gridline.
 */
private val HALO_DIRECTIONS = listOf(
    -1f to -1f, 0f to -1f, 1f to -1f,
    -1f to 0f, 1f to 0f,
    -1f to 1f, 0f to 1f, 1f to 1f,
)

/** Four intervals gives five ticks, which fit without crowding at phone width. */
private const val AXIS_TICKS = 4

/** Space between the plot and its tick labels. */
private const val AXIS_GAP = 14f

/** Bands sit behind the data and must not compete with it. */
private const val BAND_ALPHA = 0.16f

/** A calm night blue, fixed so it keeps meaning "asleep" whatever the wallpaper. */
private val SLEEP_BAND = Color(0xFF5C7CFA)

/**
 * Longest window still labelled as clock times rather than dates.
 *
 * A day, plus the margin a night may push the start back by: a session beginning the previous
 * evening widens the day's extent, and the result is still one night to a reader. Matched to
 * the session margin the sessions themselves are searched over.
 */
private const val HOURS_INTRADAY_MAX = 36L

/** Small enough to read as an axis mark rather than as a control. */
private const val AXIS_ICON = 14

/** Tall enough for a band to be a band rather than a rule. */
private const val TIMELINE_HEIGHT = 40

private const val MAX_DOTS = 60

/** Faint enough to read as background to the line, still findable one by one. */
private const val SCATTER_ALPHA = 0.35f
private const val SCATTER_RADIUS = 2.5f

/** Consecutive nights are a day apart; anything wider is a night with no value. */
private val SCATTER_MAX_GAP: Duration = Duration.ofHours(36)

/**
 * Thin enough that a dense day reads as a trace rather than a ribbon. At full resolution a
 * heart-rate day holds thousands of points, and a heavy stroke merges neighbouring peaks into
 * a solid block.
 */
private const val LINE_WIDTH = 2f

/** Bar width as a fraction of the gap to its neighbour, leaving a gutter between bars. */
private const val BAR_WIDTH_FRACTION = 0.7f

/** Opacity of the min/max ribbon. Faint: it is context for the line, not a second line. */
private const val RANGE_BAND_ALPHA = 0.18f

/**
 * Opacity of one stacked segment, darkening upwards.
 *
 * One hue at different weights rather than separate colours: the segments are parts of one
 * quantity, and giving each its own colour would read as unrelated series sharing a bar. The
 * bottom segment is the lightest because it is the floor the day is built on.
 */
private fun stackAlpha(index: Int, count: Int): Float =
    STACK_ALPHA_MIN + (1f - STACK_ALPHA_MIN) * (index + 1).toFloat() / count.toFloat()

private const val STACK_ALPHA_MIN = 0.35f


/** Width of a lone bar, as a fraction of the plot. A single day should not fill the chart. */
private const val BAR_LONE_DIVISOR = 8f

/**
 * How far the time axis can be stretched. Twenty-four is a whole day down to about an hour,
 * which is as fine as the data usefully resolves; beyond that the chart is showing the gaps
 * between samples rather than the shape.
 */
private const val MAX_ZOOM = 24f

/** A gap's dotted join: faint, thin and round-dotted, so it reads as no data, not as a series. */
private const val GAP_ALPHA = 0.55f
private const val GAP_WIDTH = 1.5f
private const val GAP_DOT_ON = 0.5f
private const val GAP_DOT_OFF = 5f

/** Fewest points a zoomed chart keeps on screen. */
private const val MIN_VISIBLE_POINTS = 4

/**
 * A reference range's colour: fixed rather than themed, and unlike every band of the data, so
 * it is never taken for the day's spread or the wearer's own range lying behind it.
 */
val REFERENCE_COLOR = Color(0xFF4DB6AC)

/** Its fill: present, but quieter than the data drawn over it. */
const val REFERENCE_ALPHA = 0.16f

/** Its dashed edges, so where the range ends reads precisely. */
private const val REFERENCE_EDGE_ALPHA = 0.9f
