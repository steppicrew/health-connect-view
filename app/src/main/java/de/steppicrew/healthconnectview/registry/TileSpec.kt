package de.steppicrew.healthconnectview.registry

import de.steppicrew.healthconnectview.health.Session

/**
 * How a type is drawn on the dashboard.
 *
 * This is per-type presentation knowledge -- steps read naturally as progress towards a goal,
 * heart rate as a curve -- so it lives in the registry beside the rest of the type's
 * description. Putting it in the dashboard instead would mean branching on the record type in
 * the UI, which is the 40-way branch the registry exists to avoid: one renderer per [Form]
 * serves every type that declares it.
 */
data class TileSpec(
    val form: Form,
    /**
     * Default goal, in the same unit the spec's aggregate is converted to. Only meaningful
     * for [Form.RING] -- a ring with nothing to fill has no meaning -- and only a starting
     * point, since the user can change it. Null means the type has no sensible default.
     */
    val defaultGoal: Double? = null,
    /** Value range for [Form.CURVE] colouring, low to high. */
    val colorScale: ClosedFloatingPointRange<Double>? = null,
    /**
     * Whether a chart of this type is drawn as a smooth curve rather than straight segments.
     *
     * On by default: a curve reads as a trend, which is what a chart is usually being asked
     * for. The curve is clamped so it cannot leave the range between the two values it joins,
     * so it never invents a reading below zero or above a peak.
     *
     * It is still an interpolation. For counted totals -- steps, floors -- the line between
     * two buckets is drawn rather than measured, which is why the individual points stay
     * marked wherever few enough of them fit.
     */
    val smoothChart: Boolean = true,
    /**
     * Whether an intraday chart accumulates through the day rather than showing each bucket
     * on its own.
     *
     * Right for quantities that add up -- steps, floors, distance, calories -- where the
     * question is "how far through the day am I", and the rising line can be read against a
     * goal. Wrong for anything measured rather than counted: cumulative weight or heart rate
     * is meaningless.
     *
     * Only affects the within-a-day view. Across days each bucket is already a daily total,
     * and accumulating those would answer a different question.
     */
    val cumulativeIntraday: Boolean = false,
    /**
     * Session kinds shaded behind this type's intraday chart.
     *
     * Sleep behind heart rate explains an overnight trough; an exercise band explains a spike
     * that would otherwise look like stress. Only meaningful within a day: across weeks the
     * bands would be thinner than the line and say nothing.
     *
     * Empty by default -- a band is context, and context that is not wanted is clutter.
     */
    val overlaySessions: Set<Session.Kind> = emptySet(),
) {
    enum class Form {
        /** The day's total or latest reading, as a number. The fallback any type can use. */
        NUMBER,

        /** A ring filled to the day's total as a fraction of the goal. Needs an aggregate. */
        RING,

        /** A short curve of recent readings, coloured across [colorScale]. */
        CURVE,
    }
}
