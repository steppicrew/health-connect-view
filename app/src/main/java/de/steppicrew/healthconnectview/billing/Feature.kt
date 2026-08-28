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
}
