package de.steppicrew.healthconnectview.registry

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * A vertical scale whose gridlines fall on round numbers.
 *
 * The chart used to run the axis from the data's own minimum to its own maximum and cut that
 * into four, so a heart rate between 45 and 113 was labelled 45, 62, 79, 96, 113 -- five
 * numbers that are all arbitrary, none of which the eye can use to place a point it did not
 * touch. Worse, the labels then went through a formatter that picks decimals by magnitude
 * alone, so a step of 17 bpm arrived as "78,5" on a quantity nobody measures in halves.
 *
 * The rule here is the usual one for a linear axis: pick a step from 1, 2, 2.5 or 5 times a
 * power of ten, then push the ends outward onto multiples of it. Those multiples are what make
 * the intermediate labels round as well -- a round bottom and a round step cannot produce a
 * ragged one in between.
 */
data class AxisScale(
    val min: Double,
    val max: Double,
    val step: Double,
    /** Decimals the labels need, so a whole-numbered step is never labelled with a fraction. */
    val decimals: Int,
) {
    /** Gridline values, bottom to top, each an exact multiple of [step]. */
    val guides: List<Double>
        get() = (0..steps).map { min + step * it }

    private val steps: Int get() = ((max - min) / step).roundedCount()

    private fun Double.roundedCount(): Int = (this + HALF).toInt().coerceAtLeast(1)

    companion object {

        /**
         * Steps that read as round to a person. 2.5 earns its place on short ranges: a span of
         * 9 with 1, 2 and 5 available jumps from nine lines to five to two, and 2.5 is the only
         * thing between.
         */
        private val NICE_STEPS = doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)

        /** Nudge for the float error that turns 6.999999 into six steps instead of seven. */
        private const val HALF = 0.5

        /**
         * Guard for a scale asked to span a range no floating point can divide sensibly.
         * Beyond this the labels are wider than the plot anyway.
         */
        private const val MAX_GUIDES = 12

        /**
         * A scale covering [low]..[high] with at most [targetSteps] intervals.
         *
         * [integral] forbids a fractional step, for quantities counted in whole units: steps,
         * floors, beats per minute. Without it a range of 3 floors is stepped by 0.75 and the
         * axis claims a precision the data does not have.
         *
         * [includeZero] pins the bottom to zero for a bar chart, whose baseline is drawn there
         * and whose heights are read against it. A line keeps its tight scale instead, which is
         * what lets a small movement in a resting heart rate stay visible.
         */
        fun of(
            low: Double,
            high: Double,
            targetSteps: Int = 4,
            integral: Boolean = false,
            includeZero: Boolean = false,
        ): AxisScale {
            val bottom = if (includeZero) minOf(0.0, low) else low
            val top = if (includeZero) maxOf(0.0, high) else high

            // A flat series has no range to divide: one reading, or a value that never moved.
            // Give it room around the value and scale that like any other range.
            val rawSpan = (top - bottom).takeIf { it > 0.0 }
                ?: return flat(bottom, targetSteps, integral)

            // Nearly flat is flat. Two writers' copies of one weigh-in differed by a millionth of
            // a kilogram, and that sliver spread over the full height labelled every gridline
            // "81,900" and drew the two copies as a vertical stroke. Only differences far below
            // anything shown count: weight moving inside a kilogram is a real range and keeps
            // its fine scale.
            val middle = (bottom + top) / 2
            if (rawSpan <= abs(middle) * NEARLY_FLAT) return flat(middle, targetSteps, integral)

            val step = niceStep(rawSpan / targetSteps, integral)
            var min = floor(bottom / step) * step
            var max = ceil(top / step) * step

            // floor/ceil leave the ends untouched when a value already sits exactly on a
            // multiple, which is right -- but if that collapses the range it is not.
            if (max <= min) max = min + step

            // Rounding outward can add an interval, and on a range that started close to the
            // target that overshoots into a crowded axis. Widen the step rather than drop a
            // line, so the ends stay round.
            var guides = ((max - min) / step + HALF).toInt()
            if (guides > MAX_GUIDES) {
                val wider = niceStep((max - min) / targetSteps, integral)
                min = floor(bottom / wider) * wider
                max = ceil(top / wider) * wider
                guides = ((max - min) / wider + HALF).toInt()
                return AxisScale(min, max, wider, decimalsFor(wider, integral))
            }

            return AxisScale(min, max, step, decimalsFor(step, integral))
        }

        /**
         * A scale for a series that never moves, a little either side of its value.
         *
         * The room is proportional to the value. It used to be one round step of the value's
         * own magnitude, so a single weigh-in of 83.3 kg drew on an axis from -17 to 183: the
         * dot a flat line in the middle, and a negative weight on the scale. A value that
         * cannot be negative never gets a negative axis either.
         */
        private fun flat(value: Double, targetSteps: Int, integral: Boolean): AxisScale {
            val proportional = abs(value) * FLAT_ROOM
            val room = if (integral || value == 0.0) maxOf(proportional, 1.0) else proportional
            val low = if (value >= 0.0) maxOf(0.0, value - room) else value - room
            return of(low, value + room, targetSteps, integral)
        }

        /**
         * How far either side of a flat value the axis reaches, as a share of it. Small enough
         * that a percentage near its ceiling stays under 100 -- an oxygen saturation of 97 is
         * drawn on 95..99 -- and wide enough that the value is not pinned to an edge.
         */
        private const val FLAT_ROOM = 0.02

        /**
         * A range at most this share of its value is drawn as flat: a ten-thousandth, 0.008 kg
         * at 82 kg, far below any decimal the axis shows.
         */
        private const val NEARLY_FLAT = 1e-4

        /**
         * The smallest round step at or above [rough].
         *
         * Rounding *up* rather than to the nearest keeps the interval count at or below the
         * target: a step below the rough one always yields more lines than asked for.
         */
        private fun niceStep(rough: Double, integral: Boolean): Double {
            if (rough <= 0.0) return 1.0
            val magnitude = 10.0.pow(floor(log10(rough)))
            val normalised = rough / magnitude
            val nice = NICE_STEPS.first { it >= normalised - EPSILON }
            val step = nice * magnitude
            // A whole-numbered quantity may not be stepped in fractions. Rounding up rather
            // than to 1 keeps a large range from collapsing to a thousand gridlines.
            return if (integral && step < 1.0) 1.0 else if (integral) ceil(step) else step
        }

        /** Tolerance for a normalised value that lands a hair under a nice step. */
        private const val EPSILON = 1e-9

        /**
         * Decimals a step needs, so a label never states a precision its step does not have.
         *
         * Read off the step rather than off each value: a step of 0.5 needs one decimal on
         * every label including the whole ones, or an axis reads 70, 70,5, 71 as 70, 70,5, 71
         * with the halves looking like a different kind of number from their neighbours.
         */
        private fun decimalsFor(step: Double, integral: Boolean): Int {
            if (integral || step >= 1.0 && step % 1.0 == 0.0) return 0
            var decimals = 0
            var scaled = step
            while (scaled % 1.0 != 0.0 && decimals < MAX_DECIMALS) {
                scaled *= 10
                decimals++
            }
            return decimals
        }

        /** Beyond this a health reading is noise, and the label no longer fits the gutter. */
        private const val MAX_DECIMALS = 3
    }
}
