package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.ExerciseRoute
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** One recorded position of a route. [altitude] in metres where the writer stored one. */
data class RoutePoint(val time: Instant, val latitude: Double, val longitude: Double, val altitude: Double?)

fun ExerciseRoute.toPoints(): List<RoutePoint> =
    route.map { RoutePoint(it.time, it.latitude, it.longitude, it.altitude?.inMeters) }.sortedBy { it.time }

/** The route's length along its points in metres, great-circle between neighbours. */
fun routeLength(points: List<RoutePoint>): Double =
    points.zipWithNext { a, b -> haversine(a, b) }.sum()

/** Lowest and highest altitude, or null where fewer than two points carry one. */
fun altitudeRange(points: List<RoutePoint>): ClosedFloatingPointRange<Double>? {
    val altitudes = points.mapNotNull { it.altitude }
    return if (altitudes.size < 2) null else altitudes.min()..altitudes.max()
}

/** Distance along the route up to each point, in metres; the first is 0. */
fun cumulativeDistances(points: List<RoutePoint>): DoubleArray {
    val out = DoubleArray(points.size)
    for (i in 1 until points.size) out[i] = out[i - 1] + haversine(points[i - 1], points[i])
    return out
}

/** The point closest in time to [time], by index; points are in time order. */
fun indexAt(points: List<RoutePoint>, time: Instant): Int {
    if (points.isEmpty()) return -1
    var low = 0
    var high = points.lastIndex
    while (low < high) {
        val mid = (low + high) / 2
        if (points[mid].time < time) low = mid + 1 else high = mid
    }
    return if (low > 0 && Duration.between(points[low - 1].time, time) < Duration.between(time, points[low].time)) low - 1 else low
}

/**
 * Heights over time for a profile, as the median of each of [slices] equal stretches of time.
 *
 * Drawn point by point, the profile grew a comb of one-point strokes: a single reading metres
 * off its neighbours, a pixel away in time, is a vertical line. The median of a slice drops
 * such a reading and keeps every real climb, which lasts far longer than a slice.
 */
fun heightProfile(points: List<RoutePoint>, slices: Int = PROFILE_SLICES): List<Pair<Instant, Double>> {
    val withHeight = points.filter { it.altitude != null }
    return sliceMedians(withHeight.map { it.time }, withHeight.map { it.altitude!! }, slices)
}

/** Speeds over time for a profile, sliced like [heightProfile]; [speeds] as [routeSpeeds] gives them. */
fun speedProfile(points: List<RoutePoint>, speeds: DoubleArray, slices: Int = PROFILE_SLICES): List<Pair<Instant, Double>> =
    sliceMedians(points.map { it.time }, speeds.toList(), slices)

/** The median of each of [slices] equal stretches of time; [times] in order, one per value. */
private fun sliceMedians(times: List<Instant>, values: List<Double>, slices: Int): List<Pair<Instant, Double>> {
    if (times.size < 2) return emptyList()
    val start = times.first().toEpochMilli()
    val span = (times.last().toEpochMilli() - start).coerceAtLeast(1)
    return times.indices.groupBy { ((times[it].toEpochMilli() - start) * slices / (span + 1)).toInt() }
        .toSortedMap()
        .map { (_, slice) ->
            val sorted = slice.map { values[it] }.sorted()
            times[slice[slice.size / 2]] to sorted[sorted.size / 2]
        }
}

/**
 * Speed at each point in metres per second, worked out from the positions: no route carries
 * one, a location holds only time, place and height.
 *
 * The straight distance between the ends of [window] centred on the point, over its time. At one
 * fix a second, a few metres of GPS scatter reads as a sprint -- and it does so just as well
 * summed along the path inside a window, since every zigzag adds its length. Measured end to
 * end across twenty seconds the scatter is a fraction of a km/h; the price is a shortened
 * hairpin, which a twenty-second window rarely contains. Where fixes are sparser than the
 * window, the nearest neighbour on each side is used instead. Null where the
 * route has fewer than two moments in time, so there is no speed to speak of.
 */
fun routeSpeeds(points: List<RoutePoint>, window: Duration = SPEED_WINDOW): DoubleArray? {
    if (points.size < 2 || points.first().time == points.last().time) return null
    val millis = LongArray(points.size) { points[it].time.toEpochMilli() }
    val half = window.toMillis() / 2
    var from = 0
    var to = 0
    return DoubleArray(points.size) { i ->
        while (millis[from] < millis[i] - half) from++
        while (to < points.lastIndex && millis[to + 1] <= millis[i] + half) to++
        var a = minOf(from, (i - 1).coerceAtLeast(0))
        var b = maxOf(to, (i + 1).coerceAtMost(points.lastIndex))
        // Fixes sharing one instant: widen until time has passed, or a speed would divide by zero.
        while (millis[b] == millis[a] && a > 0) a--
        while (millis[b] == millis[a] && b < points.lastIndex) b++
        haversine(points[a], points[b]) / ((millis[b] - millis[a]) / 1000.0)
    }
}

/**
 * The speeds a colour scale runs between: the 5th to the 95th percentile, not the extremes.
 * One GPS jump of a hundred metres is a moment at 300 km/h, and scaled to that everything real
 * would be the same blue. Null where the route has no speed.
 */
fun speedScale(speeds: DoubleArray): ClosedFloatingPointRange<Double>? {
    if (speeds.isEmpty()) return null
    val sorted = speeds.sorted()
    return sorted[(sorted.lastIndex * 5) / 100]..sorted[(sorted.lastIndex * 95) / 100]
}

private val SPEED_WINDOW: Duration = Duration.ofSeconds(20)

private const val PROFILE_SLICES = 120

/**
 * The route flattened onto a box [width] by [height], centred, north up, keeping its shape:
 * longitude is shrunk by the cosine of the route's middle latitude, as a map at that latitude
 * shows it. There is no map beneath -- the app has no network to fetch one -- so the shape is
 * the whole picture, and it must not be stretched to fill the box.
 */
fun projectRoute(points: List<RoutePoint>, width: Float, height: Float): List<Pair<Float, Float>> {
    if (points.isEmpty()) return emptyList()
    val midLat = (points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2
    val scaleX = cos(midLat * PI / 180)
    val xs = points.map { it.longitude * scaleX }
    val ys = points.map { it.latitude }
    val spanX = xs.max() - xs.min()
    val spanY = ys.max() - ys.min()
    val scale = max(spanX, spanY).takeIf { it > 0.0 }?.let { minOf(width / spanX.orTiny(), height / spanY.orTiny()) } ?: 0.0
    val offsetX = (width - spanX * scale) / 2
    val offsetY = (height - spanY * scale) / 2
    return xs.indices.map { i ->
        ((offsetX + (xs[i] - xs.min()) * scale).toFloat()) to ((offsetY + (ys.max() - ys[i]) * scale).toFloat())
    }
}

/** A straight north-south or east-west route has no span on one axis; do not divide by it. */
private fun Double.orTiny(): Double = if (this > 0.0) this else Double.MIN_VALUE

private fun haversine(a: RoutePoint, b: RoutePoint): Double {
    val lat1 = a.latitude * PI / 180
    val lat2 = b.latitude * PI / 180
    val dLat = lat2 - lat1
    val dLon = (b.longitude - a.longitude) * PI / 180
    val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

private const val EARTH_RADIUS_M = 6_371_008.8
