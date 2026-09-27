package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.ui.components.CubicControls
import de.steppicrew.healthconnectview.ui.components.monotoneControls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * The smoothed chart line. Weight a day apart after a long gap drew flat, a sudden drop and
 * flat again: clamped Catmull-Rom control points bent the two sides of a dot differently.
 */
class MonotoneCurveTest {

    /** The weight case: level-ish over a week, then one day and a small drop, then a climb. */
    private val xs = floatArrayOf(0f, 275f, 510f, 548f, 823f)
    private val ys = floatArrayOf(1114f, 1314f, 1308f, 1323f, 1022f)

    private fun bezier(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        val u = 1 - t
        return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d
    }

    private fun samples(i: Int, c: CubicControls) =
        (0..50).map { bezier(ys[i], c.y1, c.y2, ys[i + 1], it / 50f) }

    @Test
    fun `no piece leaves the range of the two values it joins`() {
        monotoneControls(xs, ys).forEachIndexed { i, c ->
            val low = min(ys[i], ys[i + 1]) - 1e-3f
            val high = max(ys[i], ys[i + 1]) + 1e-3f
            samples(i, c).forEach { assertTrue("piece $i: $it", it in low..high) }
        }
    }

    @Test
    fun `each piece runs one way, with no wiggle`() {
        monotoneControls(xs, ys).forEachIndexed { i, c ->
            val steps = samples(i, c).zipWithNext { a, b -> b - a }
            assertTrue("piece $i", steps.all { it >= -1e-3f } || steps.all { it <= 1e-3f })
        }
    }

    @Test
    fun `the direction is the same on both sides of a point`() {
        val controls = monotoneControls(xs, ys)
        for (i in 1 until xs.size - 1) {
            val inSlope = (ys[i] - controls[i - 1].y2) / (xs[i] - controls[i - 1].x2)
            val outSlope = (controls[i].y1 - ys[i]) / (controls[i].x1 - xs[i])
            assertEquals("point $i", inSlope, outSlope, 1e-4f)
        }
    }

    @Test
    fun `a point beside a level stretch is flat`() {
        val controls = monotoneControls(floatArrayOf(0f, 1f, 2f), floatArrayOf(5f, 5f, 9f))
        assertEquals(5f, controls[0].y2, 0f)
        assertEquals(5f, controls[1].y1, 0f)
    }

    @Test
    fun `a peak is flat at its top`() {
        val controls = monotoneControls(floatArrayOf(0f, 1f, 2f), floatArrayOf(0f, 3f, 1f))
        assertEquals(3f, controls[0].y2, 0f)
        assertEquals(3f, controls[1].y1, 0f)
    }

    @Test
    fun `a straight run stays straight`() {
        val controls = monotoneControls(floatArrayOf(0f, 3f, 9f), floatArrayOf(0f, 3f, 9f))
        controls.forEach { assertEquals(it.x1, it.y1, 1e-5f); assertEquals(it.x2, it.y2, 1e-5f) }
    }
}
