package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.RoutePoint
import de.steppicrew.healthconnectview.health.altitudeRange
import de.steppicrew.healthconnectview.health.cumulativeDistances
import de.steppicrew.healthconnectview.health.heightProfile
import de.steppicrew.healthconnectview.health.indexAt
import de.steppicrew.healthconnectview.health.projectRoute
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Quantity
import java.time.Instant

/**
 * A route as its shape, north up, with a height profile beneath where heights were recorded,
 * and a slider through time that marks where the route was at that moment on both. No map:
 * the app has no network to fetch one, and says so under the drawing. A filled dot marks the
 * start, a ring the end.
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
    val routeShape = remember(points) { ShapeCache() }
    val profileShape = remember(points) { ShapeCache() }

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(ROUTE_HEIGHT.dp)) {
            val pad = MARKER.dp.toPx() * 2
            val (projected, path) = routeShape.forSize(size.width, size.height) {
                val projected = projectRoute(points, size.width - 2 * pad, size.height - 2 * pad)
                    .map { (x, y) -> Offset(x + pad, y + pad) }
                projected to Path().apply {
                    projected.firstOrNull()?.let { moveTo(it.x, it.y) }
                    projected.drop(1).forEach { lineTo(it.x, it.y) }
                }
            }
            if (projected.isEmpty()) return@Canvas
            drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
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
                val (_, path) = profileShape.forSize(size.width, size.height) {
                    emptyList<Offset>() to Path().apply {
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
        Slider(value = fraction, onValueChange = { fraction = it })

        val km = Quantity.DISTANCE
        val m = Quantity.ELEVATION
        val readout = listOfNotNull(
            Formatting.time(here.time),
            Formatting.number(km.convert(distances[index] / 1000)) + " " + km.symbol(),
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
        Text(
            text = stringResource(R.string.route_no_map),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * A drawn shape kept between frames: its points and path, rebuilt only when the canvas size
 * changes. Plain state on purpose -- replacing it must not trigger a recomposition, and it is
 * only ever touched while drawing.
 */
private class ShapeCache {
    private var width = -1f
    private var height = -1f
    private var shape: Pair<List<Offset>, Path>? = null

    fun forSize(width: Float, height: Float, build: () -> Pair<List<Offset>, Path>): Pair<List<Offset>, Path> {
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
