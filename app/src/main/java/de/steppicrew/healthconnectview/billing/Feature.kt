package de.steppicrew.healthconnectview.billing

/**
 * Features that may be gated behind a purchase.
 *
 * The free tier has to stay genuinely useful -- seeing your own data is the whole point of
 * the app -- so browsing every type and charting it is free, and paid features are depth and
 * convenience on top.
 */
enum class Feature(val isPremium: Boolean) {
    BROWSE_ALL_TYPES(isPremium = false),
    BASIC_CHART(isPremium = false),

    /** Ranges beyond 30 days, which also need READ_HEALTH_DATA_HISTORY. */
    LONG_RANGE_HISTORY(isPremium = true),
    EXPORT_CSV(isPremium = true),
    ADVANCED_STATS(isPremium = true),

    /** Dashboard tiles larger than one cell. */
    TILE_SIZES(isPremium = true),

    /**
     * A type on more than one tile, each with its own window and face. Beside [TILE_SIZES]:
     * without a large tile's window two tiles of a type would show the same number.
     */
    TILE_REPEAT(isPremium = true),

    /** A tile's own colour from the palette in [de.steppicrew.healthconnectview.dashboard.TileColor]. */
    TILE_COLORS(isPremium = true),

    /** PDF logs for a doctor: blood pressure, weight, resting heart rate, blood glucose. */
    PDF_REPORTS(isPremium = true),

    /** An exercise route as a GPX file, to open in a map or training app. */
    ROUTE_EXPORT(isPremium = true),

    /** Weight with fat, lean mass, water and bone, a chart each on one time axis. */
    BODY_COMPOSITION(isPremium = true),

    /** Two types' charts stacked on one time axis. */
    COMPARE(isPremium = true),

    /** Every type's last week against its month, the unusual moves first. */
    INSIGHTS(isPremium = true),
}
