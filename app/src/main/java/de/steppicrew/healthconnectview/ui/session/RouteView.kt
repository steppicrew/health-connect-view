package de.steppicrew.healthconnectview.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.RoutePoint
import de.steppicrew.healthconnectview.health.altitudeRange
import de.steppicrew.healthconnectview.health.cumulativeDistances
import de.steppicrew.healthconnectview.health.heightProfile
import de.steppicrew.healthconnectview.health.indexAt
import de.steppicrew.healthconnectview.health.projectRoute
import de.steppicrew.healthconnectview.health.routeSpeeds
import de.steppicrew.healthconnectview.health.speedProfile
import de.steppicrew.healthconnectview.health.speedScale
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Quantity
import java.time.Instant

/**
 * A route as its shape, north up, with a height profile beneath where heights were recorded,
 * and a slider through time that marks where the route was at that moment on both. No map:
 * the app has no network to fetch one, and says so under the drawing. A filled dot marks the
 * start, a ring the end.
 *
 * Where the route moved through time, it is coloured by speed, blue through green and yellow
 * to red, with the speed profile beneath in the same colours. The speed is worked out from the
 * positions -- a route stores none -- and the drawing says so.
 */
@Composable
fun RouteView(points: List<RoutePoint>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val marker = MaterialTheme.colorScheme.tertiary
    val distances = remember(points) { cumulativeDistances(points) }
    val profile = remember(points) { heightProfile(points) }
    val heights = remember(points) { altitudeRange(points) }
    val speeds = remember(points) { routeSpeeds(points) }
    val scale = remember(speeds) { speeds?.let(::speedScale) }
    val speedCurve = remember(speeds) { speeds?.let { speedProfile(points, it) }.orEmpty() }
    // The slider's place in the session's time, 0 to 1; at the end until moved, so the
    // readout starts with the whole route's length.
    var fraction by remember(points) { mutableFloatStateOf(1f) }
    val start = points.first().time.toEpochMilli()
    val span = (points.last().time.toEpochMilli() - start).coerceAtLeast(1)
    val at = Instant.ofEpochMilli(start + (span * fraction).toLong())
    val index = indexAt(points, at)
    val here = points[index]
    // The route and the profile only change with the route or the canvas size, but they were
    // projected and rebuilt point by point on every step of the slider -- hundreds of points
    // per frame, and the marker lagged behind the finger. Built once per size, kept here; each
    // step now draws only the marker on top.
    val routeShape = remember(points) { ShapeCache<Pair<List<Offset>, List<Pair<Color, Path>>>>() }
    val profileShape = remember(points) { ShapeCache<Path>() }
    val speedShape = remember(points) { ShapeCache<List<Pair<Color, Path>>>() }

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(ROUTE_HEIGHT.dp)) {
            val pad = MARKER.dp.toPx() * 2
            val (projected, paths) = routeShape.forSize(size.width, size.height) {
                val projected = projectRoute(points, size.width - 2 * pad, size.height - 2 * pad)
                    .map { (x, y) -> Offset(x + pad, y + pad) }
                projected to colouredPaths(projected, speeds, scale, line)
            }
            if (projected.isEmpty()) return@Canvas
            drawColoured(paths, 2.5.dp.toPx())
            drawCircle(line, MARKER.dp.toPx(), projected.first())
            drawCircle(surface, (MARKER - 2).dp.toPx(), projected.last())
            drawCircle(line, MARKER.dp.toPx(), projected.last(), style = Stroke(width = 2.dp.toPx()))
            // Where the slider is: a ring around a dot, in a colour of its own.
            drawCircle(surface, (MARKER + 2).dp.toPx(), projected[index])
            drawCircle(marker, MARKER.dp.toPx(), projected[index])
        }
        if (heights != null && profile.size >= 2) {
            Canvas(Modifier.fillMaxWidth().height(PROFILE_HEIGHT.dp).padding(top = 8.dp)) {
                val range = (heights.endInclusive - heights.start).takeIf { it > 0 } ?: 1.0
                fun x(time: Instant) = (time.toEpochMilli() - start).toFloat() / span * size.width
                fun y(height: Double) = (size.height - (height - heights.start) / range * size.height).toFloat()
                val path = profileShape.forSize(size.width, size.height) {
                    Path().apply {
                        profile.forEachIndexed { i, (time, height) ->
                            if (i == 0) moveTo(x(time), y(height)) else lineTo(x(time), y(height))
                        }
                    }
                }
                drawPath(path, muted, style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
                val nowX = x(at)
                drawLine(marker, Offset(nowX, 0f), Offset(nowX, size.height), strokeWidth = 1.5.dp.toPx())
            }
        }
        if (speeds != null && scale != null && speedCurve.size >= 2) {
            Canvas(Modifier.fillMaxWidth().height(PROFILE_HEIGHT.dp).padding(top = 8.dp)) {
                // Drawn to the top of the colour scale, not the fastest moment: a GPS jump would
                // otherwise flatten every real change of pace into the floor of the strip.
                val top = scale.endInclusive.takeIf { it > 0 } ?: 1.0
                fun x(time: Instant) = (time.toEpochMilli() - start).toFloat() / span * size.width
                fun y(speed: Double) = (size.height - (speed / top).coerceAtMost(1.0) * size.height).toFloat()
                val paths = speedShape.forSize(size.width, size.height) {
                    val curve = speedCurve.map { (time, speed) -> Offset(x(time), y(speed)) }
                    colouredPaths(curve, speedCurve.map { it.second }.toDoubleArray(), scale, line)
                }
                drawColoured(paths, 1.5.dp.toPx())
                val nowX = x(at)
                drawLine(marker, Offset(nowX, 0f), Offset(nowX, size.height), strokeWidth = 1.5.dp.toPx())
            }
        }
        Slider(value = fraction, onValueChange = { fraction = it })

        val km = Quantity.DISTANCE
        val m = Quantity.ELEVATION
        val kmh = Quantity.SPEED
        val readout = listOfNotNull(
            Formatting.time(here.time),
            Formatting.number(km.convert(distances[index] / 1000)) + " " + km.symbol(),
            speeds?.let { Formatting.number(kmh.convert(it[index] * MS_TO_KMH)) + " " + kmh.symbol() },
            here.altitude?.let { Formatting.number(m.convert(it)) + " " + m.symbol() },
        ).joinToString(" · ")
        Text(text = readout, style = MaterialTheme.typography.bodyMedium)
        heights?.let {
            Text(
                text = stringResource(
                    R.string.route_heights,
                    Formatting.number(m.convert(it.start)),
                    Formatting.number(m.convert(it.endInclusive)) + " " + m.symbol(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        if (scale != null) SpeedLegend(scale, muted)
        Text(
            text = stringResource(R.string.route_no_map),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * The colour scale beneath the drawing: its slow end, a strip running through the colours, and
 * its fast end, then a line saying where the speed comes from.
 */
@Composable
private fun SpeedLegend(scale: ClosedFloatingPointRange<Double>, muted: Color) {
    val kmh = Quantity.SPEED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(stringResource(R.string.route_speed), style = MaterialTheme.typography.bodySmall, color = muted)
        Text(Formatting.number(kmh.convert(scale.start * MS_TO_KMH)), style = MaterialTheme.typography.bodySmall, color = muted)
        Box(
            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(SPEED_COLOURS)),
        )
        Text(
            Formatting.number(kmh.convert(scale.endInclusive * MS_TO_KMH)) + " " + kmh.symbol(),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
    Text(
        text = stringResource(R.string.route_speed_source),
        style = MaterialTheme.typography.labelSmall,
        color = muted,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/**
 * A line through [points] split by colour: each stretch takes the colour of its two ends' mean
 * speed on [scale], in [SPEED_STEPS] steps, and each step is one path. Hundreds of one-segment
 * strokes per frame would undo the point of caching the shape. Without speeds, one path in
 * [plain].
 */
private fun colouredPaths(
    points: List<Offset>,
    speeds: DoubleArray?,
    scale: ClosedFloatingPointRange<Double>?,
    plain: Color,
): List<Pair<Color, Path>> {
    if (speeds == null || scale == null) {
        return listOf(plain to Path().apply {
            points.firstOrNull()?.let { moveTo(it.x, it.y) }
            points.drop(1).forEach { lineTo(it.x, it.y) }
        })
    }
    val range = (scale.endInclusive - scale.start).takeIf { it > 0 } ?: 1.0
    val paths = arrayOfNulls<Path>(SPEED_STEPS)
    for (i in 0 until points.lastIndex) {
        val fraction = (((speeds[i] + speeds[i + 1]) / 2 - scale.start) / range).coerceIn(0.0, 1.0)
        val step = (fraction * (SPEED_STEPS - 1) + 0.5).toInt()
        val path = paths[step] ?: Path().also { paths[step] = it }
        path.moveTo(points[i].x, points[i].y)
        path.lineTo(points[i + 1].x, points[i + 1].y)
    }
    return paths.withIndex().mapNotNull { (step, path) ->
        path?.let { speedColour(step / (SPEED_STEPS - 1f)) to it }
    }
}

private fun DrawScope.drawColoured(paths: List<Pair<Color, Path>>, width: Float) {
    val stroke = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    paths.forEach { (colour, path) -> drawPath(path, colour, style = stroke) }
}

/** Slow to fast, [fraction] 0 to 1: blue, green, yellow, red, blended between. */
private fun speedColour(fraction: Float): Color {
    val at = fraction.coerceIn(0f, 1f) * (SPEED_COLOURS.size - 1)
    val low = at.toInt().coerceAtMost(SPEED_COLOURS.size - 2)
    return lerp(SPEED_COLOURS[low], SPEED_COLOURS[low + 1], at - low)
}

private val SPEED_COLOURS = listOf(Color(0xFF2F6FDB), Color(0xFF2FA84F), Color(0xFFEBC12B), Color(0xFFDB3B2F))
private const val SPEED_STEPS = 24
private const val MS_TO_KMH = 3.6

/**
 * A drawn shape kept between frames, rebuilt only when the canvas size
 * changes. Plain state on purpose -- replacing it must not trigger a recomposition, and it is
 * only ever touched while drawing.
 */
private class ShapeCache<T : Any> {
    private var width = -1f
    private var height = -1f
    private var shape: T? = null

    fun forSize(width: Float, height: Float, build: () -> T): T {
        val kept = shape
        if (kept != null && width == this.width && height == this.height) return kept
        return build().also {
            shape = it
            this.width = width
            this.height = height
        }
    }
}

private const val ROUTE_HEIGHT = 200
private const val PROFILE_HEIGHT = 56
private const val MARKER = 5
