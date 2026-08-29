package de.steppicrew.healthconnectview.registry

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
