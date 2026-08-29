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
    /**
     * Default value bands for colouring this type's curve, low to high.
     *
     * A default only: the boundaries that matter are the user's, since a resting heart rate
     * of 48 and one of 70 do not share a scale. The override lives on the tile beside the
     * goal, which is the other per-type number the user sets.
     */
    val defaultZones: ValueZones? = null,
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
     * Sleep behind heart rate explains an overnight trough; an exercise band explains a climb
     * in steps or floors that would otherwise look like an ordinary afternoon. The band is the
     * answer to "why does the line do that", so it belongs on any type whose day has a shape
     * worth explaining. Only meaningful within a day: across weeks the bands would be thinner
     * than the line and say nothing.
     *
     * Empty by default, because a type with no intraday shape gains nothing from a band --
     * see [ACTIVITY_CONTEXT] for the set the movement types share.
     */
    val overlaySessions: Set<Session.Kind> = emptySet(),
    /**
     * Which kind of session this tile counts, for [Form.SESSIONS]. Null for every other form.
     *
     * A session tile is a count and a list rather than one metric charted, so it is a
     * genuinely second shape of tile -- but it stays in the registry rather than becoming a
     * dashboard concept of its own, so the dashboard still never branches on record type.
     */
    val sessionKind: Session.Kind? = null,
) {
    enum class Form {
        /** The day's total or latest reading, as a number. The fallback any type can use. */
        NUMBER,

        /** A ring filled to the day's total as a fraction of the goal. Needs an aggregate. */
        RING,

        /** A short curve of recent readings, coloured by its value bands. */
        CURVE,

        /**
         * The day's count of sessions of [sessionKind], with their total duration beneath.
         *
         * A session is a span rather than a reading, so neither a total nor a latest value
         * describes it: "three activities, 1h 40m" is the honest summary of a day's exercise,
         * where a summed duration alone loses that it was three separate things.
         */
        SESSIONS,
    }

    companion object {
        /**
         * Both kinds of session, for the types whose day is shaped by what the user was doing.
         *
         * Named once rather than repeated per type: the reasoning is the same for steps,
         * floors, distance and calories -- a rise in any of them is explained by the activity
         * it happened during, and a flat stretch by the night it happened in.
         */
        val ACTIVITY_CONTEXT: Set<Session.Kind> = setOf(Session.Kind.SLEEP, Session.Kind.EXERCISE)
    }
}
