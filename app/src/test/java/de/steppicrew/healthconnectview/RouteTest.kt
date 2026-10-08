package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.export.Gpx
import de.steppicrew.healthconnectview.health.RoutePoint
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.health.RouteRef
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.altitudeRange
import de.steppicrew.healthconnectview.health.cumulativeDistances
import de.steppicrew.healthconnectview.health.heightProfile
import de.steppicrew.healthconnectview.health.indexAt
import de.steppicrew.healthconnectview.health.dedupeSessions
import de.steppicrew.healthconnectview.health.projectRoute
import de.steppicrew.healthconnectview.health.routeLength
import de.steppicrew.healthconnectview.health.routeSpeeds
import de.steppicrew.healthconnectview.health.speedScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.time.Instant

/** Exercise routes: drawn without a map, exported as GPX, kept through the session dedupe. */
class RouteTest {

    private val t0 = Instant.parse("2026-09-20T08:00:00Z")
    private fun point(i: Int, lat: Double, lon: Double, alt: Double? = null) = RoutePoint(t0.plusSeconds(i * 10L), lat, lon, alt)

    @Test
    fun `a degree of latitude is about 111 km`() {
        val length = routeLength(listOf(point(0, 50.0, 8.0), point(1, 51.0, 8.0)))
        assertEquals(111_195.0, length, 50.0)
    }

    @Test
    fun `the shape keeps its proportions and is centred`() {
        // A square at 60°N: a degree of longitude is half a degree of latitude there.
        val points = listOf(point(0, 60.0, 10.0), point(1, 61.0, 10.0), point(2, 61.0, 12.0), point(3, 60.0, 12.0))
        val projected = projectRoute(points, 200f, 100f)
        val width = projected.maxOf { it.first } - projected.minOf { it.first }
        val height = projected.maxOf { it.second } - projected.minOf { it.second }
        assertEquals(1.0, (width / height).toDouble(), 0.02)
        assertEquals(100f, height, 0.01f)
        assertEquals("centred across the 200-wide box", 200f, projected.minOf { it.first } + projected.maxOf { it.first }, 0.01f)
        // North is up: the northern points have the smaller y.
        assertTrue(projected[1].second < projected[0].second)
    }

    @Test
    fun `a straight north-south route does not divide by zero`() {
        val projected = projectRoute(listOf(point(0, 50.0, 8.0), point(1, 50.01, 8.0)), 200f, 100f)
        assertTrue(projected.all { it.first.isFinite() && it.second.isFinite() })
        assertEquals(100f, projected[0].first, 0.01f)
    }

    @Test
    fun `heights need two points that carry one`() {
        assertNull(altitudeRange(listOf(point(0, 50.0, 8.0, 100.0), point(1, 50.0, 8.1))))
        assertEquals(90.0..120.0, altitudeRange(listOf(point(0, 50.0, 8.0, 120.0), point(1, 50.0, 8.1, 90.0))))
    }

    @Test
    fun `GPX writes dots whatever the locale and escapes the name`() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        try {
            val out = StringWriter()
            Gpx.write(listOf(point(0, 52.5, 13.4, 34.25)), "Lauf & Rad <1>", out)
            val gpx = out.toString()
            assertTrue(gpx, gpx.contains("""<trkpt lat="52.5000000" lon="13.4000000"><ele>34.3</ele><time>2026-09-20T08:00:00Z</time></trkpt>"""))
            assertTrue(gpx.contains("<name>Lauf &amp; Rad &lt;1&gt;</name>"))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `the named copy of a session keeps the other copy's route`() {
        val end = t0.plusSeconds(3600)
        val watch = Session(t0, end, null, Session.Kind.EXERCISE, "watch", route = RouteRef("r1"))
        val machine = Session(t0.plusSeconds(60), end, "Indoor bike", Session.Kind.EXERCISE, "machine")
        val kept = dedupeSessions(listOf(watch, machine)).single()
        assertEquals("Indoor bike", kept.title)
        assertEquals(RouteRef("r1"), kept.route)
    }

    @Test
    fun `distance so far grows along the route`() {
        val d = cumulativeDistances(listOf(point(0, 50.0, 8.0), point(1, 50.001, 8.0), point(2, 50.002, 8.0)))
        assertEquals(0.0, d[0], 0.0)
        assertEquals(2 * d[1], d[2], 0.01)
    }

    @Test
    fun `the slider finds the nearest point in time`() {
        val points = listOf(point(0, 50.0, 8.0), point(1, 50.0, 8.0), point(3, 50.0, 8.0))
        assertEquals(1, indexAt(points, t0.plusSeconds(12)))
        assertEquals(2, indexAt(points, t0.plusSeconds(26)))
        assertEquals(2, indexAt(points, t0.plusSeconds(99)))
        assertEquals(0, indexAt(points, t0.minusSeconds(5)))
    }

    @Test
    fun `a single height reading metres off does not reach the profile`() {
        // 600 points at 40 m with one at 48 m: the median of its slice ignores it.
        val points = (0 until 600).map { point(it, 50.0, 8.0, if (it == 300) 48.0 else 40.0) }
        assertTrue(heightProfile(points).all { it.second == 40.0 })
    }

    @Test
    fun `a real climb survives the profile`() {
        val points = (0 until 600).map { point(it, 50.0, 8.0, if (it < 300) 40.0 else 60.0) }
        val profile = heightProfile(points)
        assertEquals(40.0, profile.first().second, 0.0)
        assertEquals(60.0, profile.last().second, 0.0)
    }

    @Test
    fun `speed is distance over time, steady along a steady route`() {
        // 0.001° of latitude every 10 s: 111.2 m per 10 s, 11.1 m/s.
        val points = (0..30).map { point(it, 50.0 + it * 0.001, 8.0) }
        val speeds = routeSpeeds(points)!!
        assertTrue(speeds.all { kotlin.math.abs(it - 11.12) < 0.05 })
    }

    @Test
    fun `GPS scatter between one-second fixes does not read as a sprint`() {
        // Walking 1.4 m/s north, with every other fix 5 m off to the east.
        val points = (0..120).map { i ->
            RoutePoint(t0.plusSeconds(i.toLong()), 50.0 + i * 1.4 / 111_195.0, 8.0 + (i % 2) * 5 / 71_500.0, null)
        }
        val speeds = routeSpeeds(points)!!
        // Neighbour to neighbour this is over 5 m/s; over the window it stays near walking pace.
        assertTrue(speeds.drop(10).dropLast(10).all { it < 2.0 })
    }

    @Test
    fun `fixes sharing an instant do not divide by zero`() {
        val points = listOf(point(0, 50.0, 8.0), point(0, 50.0001, 8.0), point(1, 50.001, 8.0))
        assertTrue(routeSpeeds(points)!!.all { it.isFinite() })
        assertNull(routeSpeeds(listOf(point(0, 50.0, 8.0), point(0, 50.1, 8.0))))
    }

    @Test
    fun `the colour scale leaves out a single GPS jump`() {
        val speeds = DoubleArray(100) { 3.0 + it * 0.01 }.also { it[50] = 80.0 }
        assertTrue(speedScale(speeds)!!.endInclusive < 5.0)
    }

    @Test
    fun `a pace is minutes and seconds per km or mile, and none for a standstill`() {
        assertEquals("5:00 min/km", Formatting.pace(1000.0 / 300, imperial = false))
        assertEquals("8:03 min/mi", Formatting.pace(1000.0 / 300, imperial = true))
        assertNull(Formatting.pace(0.0, imperial = false))
        assertNull(Formatting.pace(0.1, imperial = false))
    }
}
