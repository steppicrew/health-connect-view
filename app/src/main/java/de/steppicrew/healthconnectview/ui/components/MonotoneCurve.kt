package de.steppicrew.healthconnectview.ui.components

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/** The two Bezier control points of the cubic from one point to the next. */
internal data class CubicControls(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/**
 * Control points for a smooth curve through ([xs], [ys]) that never leaves the range of the two
 * values each piece joins: Steffen's monotone cubic, on the real horizontal spacing.
 *
 * It replaced a Catmull-Rom spline whose control points were clamped into each segment. The
 * clamp kept the curve inside, but it bent the two sides of a point differently, so the
 * direction jumped at the dot -- weight a day apart after a long gap drew flat, a sudden drop,
 * flat again. Here each point has one slope, taken from both neighbours by their distance and
 * capped so no piece can overshoot, and a point next to a level stretch is flat.
 *
 * No slope is more than twice the gentler secant beside its point, so a control point sits at
 * most two-thirds of the way up its piece: all four lie within the piece's two values, and the
 * curve, inside their hull, cannot leave them.
 *
 * [xs] must not decrease; a repeated x gives a straight piece (both controls on the ends).
 */
internal fun monotoneControls(xs: FloatArray, ys: FloatArray): List<CubicControls> {
    val n = xs.size
    if (n < 2) return emptyList()
    val widths = FloatArray(n - 1) { xs[it + 1] - xs[it] }
    val secants = FloatArray(n - 1) { if (widths[it] > 0f) (ys[it + 1] - ys[it]) / widths[it] else 0f }

    val slopes = FloatArray(n) { i ->
        when (i) {
            0 -> secants[0]
            n - 1 -> secants[n - 2]
            else -> {
                val before = secants[i - 1]
                val after = secants[i]
                val hBefore = widths[i - 1]
                val hAfter = widths[i]
                if (hBefore <= 0f || hAfter <= 0f) 0f
                else {
                    val weighted = (before * hAfter + after * hBefore) / (hBefore + hAfter)
                    (sign(before) + sign(after)) * min(min(abs(before), abs(after)), abs(weighted) / 2f)
                }
            }
        }
    }
    return List(n - 1) { i ->
        val third = widths[i] / 3f
        CubicControls(
            x1 = xs[i] + third,
            y1 = ys[i] + slopes[i] * third,
            x2 = xs[i + 1] - third,
            y2 = ys[i + 1] - slopes[i + 1] * third,
        )
    }
}

