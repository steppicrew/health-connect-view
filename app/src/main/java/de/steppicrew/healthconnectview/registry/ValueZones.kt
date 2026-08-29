package de.steppicrew.healthconnectview.registry

import androidx.compose.ui.graphics.Color

/**
 * Value bands a reading falls into, and the colour each one carries.
 *
 * A single blue-to-red ramp answered "how high is this" only vaguely: the whole middle of the
 * range came out muddy purple, and two readings a training zone apart looked alike. Named
 * bands with their own colours make the answer categorical -- this was easy, this was hard --
 * which is how heart rate is actually read.
 *
 * The boundaries are the user's, because a resting rate of 48 and one of 70 do not share a
 * scale, and a maximum heart rate falls with age. [DEFAULT_HEART_RATE] is only a starting
 * point.
 */
data class ValueZones(val bounds: List<Double>) {

    /**
     * Colour for [value], interpolated across the band it falls in.
     *
     * Interpolated rather than flat so the line still reads as continuous: a reading just
     * inside a band sits at that band's edge colour, and a hard step at every boundary would
     * make ordinary variation look like a change of state.
     */
    fun colorFor(value: Double): Color {
        if (bounds.isEmpty()) return ZONE_COLORS.first()
        // Below the first boundary and above the last, the end colours hold rather than
        // wrapping: an extreme reading must not cycle back to looking calm.
        if (value <= bounds.first()) return ZONE_COLORS.first()
        if (value >= bounds.last()) return ZONE_COLORS.last()

        val upper = bounds.indexOfFirst { value < it }.coerceAtLeast(1)
        val low = bounds[upper - 1]
        val high = bounds[upper]
        val span = (high - low).takeIf { it > 0.0 } ?: return ZONE_COLORS[upper]
        val fraction = ((value - low) / span).toFloat().coerceIn(0f, 1f)
        return androidx.compose.ui.graphics.lerp(
            ZONE_COLORS[upper - 1],
            ZONE_COLORS[upper],
            fraction,
        )
    }

    /** The zone a value sits in, as an index into the bands. */
    fun zoneOf(value: Double): Int = bounds.count { value >= it }.coerceIn(0, ZONE_COLORS.size - 1)

    /** Bounds are meaningless unless they ascend; a user can type anything into a field. */
    fun sanitised(): ValueZones =
        ValueZones(bounds.filter { it.isFinite() }.sorted().distinct())

    companion object {
        /**
         * Blue through green, yellow and orange to red.
         *
         * Fixed rather than themed, like the sleep band and for the same reason: these encode
         * a value, so they must not drift with the wallpaper under dynamic colour. Chosen to
         * stay distinguishable in both light and dark, and to keep an order that reads as
         * increasing effort without needing a legend.
         */
        val ZONE_COLORS: List<Color> = listOf(
            Color(0xFF2D7FF9), // resting
            Color(0xFF17A34A), // light
            Color(0xFFD5A106), // moderate
            Color(0xFFE8710A), // hard
            Color(0xFFDC2626), // maximum
        )

        /**
         * Starting boundaries for heart rate, as the user described them: 0-50, 50-100,
         * 100-140, 140-160, 160+. Only a default -- the whole point is that they are editable.
         */
        val DEFAULT_HEART_RATE = ValueZones(listOf(50.0, 100.0, 140.0, 160.0))
    }
}
