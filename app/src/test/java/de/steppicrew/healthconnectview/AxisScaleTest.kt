package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.AxisScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * The y-axis scale. Every case here is a chart someone could open tomorrow, because the defect
 * this replaces was visible on all of them: the axis ran from the data's own minimum to its own
 * maximum, so the labels were five arbitrary reals and the reader could place nothing against
 * them.
 */
class AxisScaleTest {

    private fun assertClose(expected: Double, actual: Double, what: String) {
        assertTrue("$what: expected $expected but was $actual", abs(expected - actual) < 1e-9)
    }

    /** The reported case: a heart rate running 45 to 113. */
    @Test
    fun `a heart rate range becomes round numbers`() {
        val scale = AxisScale.of(45.0, 113.0, integral = true)

        assertClose(40.0, scale.min, "min")
        assertClose(120.0, scale.max, "max")
        assertClose(20.0, scale.step, "step")
        assertEquals(listOf(40.0, 60.0, 80.0, 100.0, 120.0), scale.guides)
        assertEquals(0, scale.decimals)
    }

    @Test
    fun `the range always contains the data`() {
        listOf(
            45.0 to 113.0,
            0.3 to 0.7,
            980.0 to 10_400.0,
            -5.0 to 5.0,
            36.1 to 37.9,
        ).forEach { (low, high) ->
            val scale = AxisScale.of(low, high)
            assertTrue("$low..$high: min ${scale.min} above the data", scale.min <= low)
            assertTrue("$low..$high: max ${scale.max} below the data", scale.max >= high)
        }
    }

    /**
     * The intermediate labels are the point of the exercise: a round bottom with a ragged step
     * still gives an axis nobody can read a value off.
     */
    @Test
    fun `every gridline is a multiple of the step`() {
        listOf(45.0 to 113.0, 0.0 to 9.0, 980.0 to 10_400.0, 36.1 to 37.9).forEach { (lo, hi) ->
            val scale = AxisScale.of(lo, hi)
            scale.guides.forEach { guide ->
                val multiples = guide / scale.step
                assertTrue(
                    "$lo..$hi: guide $guide is not a multiple of ${scale.step}",
                    abs(multiples - Math.round(multiples)) < 1e-6,
                )
            }
        }
    }

    /**
     * A step is round when its mantissa is, at whatever magnitude: 0.25, 2.5 and 25 are all
     * the same step seen through different units, and a chart of litres has as much right to
     * 0.25 as a chart of steps has to 2500.
     */
    private fun mantissaOf(step: Double): Double {
        val magnitude = 10.0.pow(floor(log10(step)))
        return step / magnitude
    }

    @Test
    fun `steps are round numbers`() {
        val allowed = setOf(1.0, 2.0, 2.5, 5.0)
        listOf(0.4, 1.0, 9.0, 60.0, 400.0, 12_000.0).forEach { high ->
            val scale = AxisScale.of(0.0, high)
            val mantissa = mantissaOf(scale.step)
            assertTrue(
                "step ${scale.step} for 0..$high is not round (mantissa $mantissa)",
                allowed.any { abs(it - mantissa) < 1e-6 },
            )
        }
        (1..400).forEach { high ->
            val scale = AxisScale.of(0.0, high.toDouble())
            val mantissa = mantissaOf(scale.step)
            assertTrue(
                "step ${scale.step} for 0..$high is not round (mantissa $mantissa)",
                allowed.any { abs(it - mantissa) < 1e-6 },
            )
        }
    }

    /** Steps, floors and beats are counted in whole units; an axis must not imply halves. */
    @Test
    fun `a counted quantity never gets a fractional step`() {
        listOf(0.0 to 3.0, 0.0 to 7.0, 2.0 to 5.0, 0.0 to 1.0).forEach { (lo, hi) ->
            val scale = AxisScale.of(lo, hi, integral = true)
            assertClose(scale.step, Math.round(scale.step).toDouble(), "step for $lo..$hi")
            assertEquals("decimals for $lo..$hi", 0, scale.decimals)
            scale.guides.forEach { guide ->
                assertClose(guide, Math.round(guide).toDouble(), "guide $guide for $lo..$hi")
            }
        }
    }

    @Test
    fun `a measured quantity may use a fractional step`() {
        // Body weight moving inside a kilogram is exactly where whole steps would flatten the
        // chart to a single line.
        val scale = AxisScale.of(72.4, 73.1)
        assertTrue("step ${scale.step} should be fractional here", scale.step < 1.0)
        assertTrue("labels need a decimal", scale.decimals >= 1)
    }

    /**
     * Decimals come off the step, not off each value, so a label never states a precision its
     * neighbours do not share.
     */
    @Test
    fun `decimals match the step`() {
        assertEquals(0, AxisScale.of(0.0, 100.0).decimals)
        assertEquals(1, AxisScale.of(0.0, 2.0, targetSteps = 4).decimals)
        assertTrue(AxisScale.of(0.0, 0.2).decimals >= 2)
    }

    @Test
    fun `bars keep zero on the scale`() {
        val scale = AxisScale.of(2_400.0, 11_800.0, integral = true, includeZero = true)
        assertClose(0.0, scale.min, "bar baseline")
        assertTrue("zero must be a gridline", scale.guides.any { abs(it) < 1e-9 })
    }

    @Test
    fun `a line does not force zero onto the scale`() {
        // The reason a resting heart rate stays readable: from zero, a move from 58 to 62 is
        // invisible.
        val scale = AxisScale.of(58.0, 62.0, integral = true)
        assertTrue("a line should not be pinned to zero", scale.min > 0.0)
    }

    @Test
    fun `a flat series still draws`() {
        val scale = AxisScale.of(70.0, 70.0, integral = true)
        assertTrue("a flat series needs a range", scale.max > scale.min)
        assertTrue("the value should sit inside", scale.min < 70.0 && scale.max > 70.0)
        assertTrue("and be labelled", scale.guides.any { abs(it - 70.0) < 1e-9 })
    }

    @Test
    fun `a flat zero series still draws`() {
        val scale = AxisScale.of(0.0, 0.0, integral = true, includeZero = true)
        assertTrue("a flat zero needs a range", scale.max > scale.min)
    }

    @Test
    fun `negative ranges keep round ends`() {
        // Temperature deltas and elevation changes can go below zero.
        val scale = AxisScale.of(-12.0, 34.0)
        assertTrue(scale.min <= -12.0)
        assertTrue(scale.max >= 34.0)
        scale.guides.forEach { guide ->
            val multiples = guide / scale.step
            assertTrue("$guide off-step", abs(multiples - Math.round(multiples)) < 1e-6)
        }
    }

    @Test
    fun `the axis stays legible at every range`() {
        var value = 0.05
        while (value < 100_000) {
            val scale = AxisScale.of(0.0, value)
            assertTrue(
                "too many gridlines for 0..$value: ${scale.guides.size}",
                scale.guides.size in 2..13,
            )
            value *= 1.7
        }
    }

    /** Sweep for the float-error cases that produce an off-by-one interval count. */
    @Test
    fun `guides always span exactly min to max`() {
        var low = -500.0
        while (low < 500) {
            val scale = AxisScale.of(low, low + 137.4)
            assertClose(scale.min, scale.guides.first(), "first guide at $low")
            assertClose(scale.max, scale.guides.last(), "last guide at $low")
            low += 13.3
        }
    }
}
