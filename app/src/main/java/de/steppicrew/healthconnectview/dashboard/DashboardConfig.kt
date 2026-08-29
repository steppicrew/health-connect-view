package de.steppicrew.healthconnectview.dashboard

import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.registry.ValueZones

/**
 * One tile on the dashboard.
 *
 * [typeName] is the record class's simple name, matching [RecordRegistry.specOrNull] and the
 * navigation argument already used for the detail screen -- a stored KClass would not survive
 * serialisation, and the simple name is already the app's stable identifier for a type.
 *
 * [width] and [height] are grid spans. Only 1x1 is offered today, but they are stored from
 * the start: adding them later would mean migrating every stored config and rewriting the
 * layout geometry at the same time.
 */
data class Tile(
    val typeName: String,
    val width: Int = 1,
    val height: Int = 1,
    /** Overrides the type's [TileSpec.defaultGoal]; null means use the default. */
    val goal: Double? = null,
    /**
     * Overrides the type's [TileSpec.defaultZones]; null means use the default.
     *
     * Beside the goal because it is the same kind of thing: a per-type number the user sets
     * because only they know what it should be. A resting rate of 48 and one of 70 do not
     * share a scale, and maximum heart rate falls with age.
     */
    val zones: ValueZones? = null,
) {
    val spec: RecordTypeSpec<*>? get() = RecordRegistry.specOrNull(typeName)

    /** The goal actually in force: the user's override, else the type's default. */
    val effectiveGoal: Double? get() = goal ?: spec?.tile?.defaultGoal

    /** The value bands actually in force: the user's override, else the type's default. */
    val effectiveZones: ValueZones? get() = zones ?: spec?.tile?.defaultZones
}

/**
 * The dashboard layout. Tile order is list order.
 *
 * Non-health UI state, so it may be persisted; health values themselves never are.
 */
data class DashboardConfig(val tiles: List<Tile> = emptyList()) {

    /** Drops tiles whose type no longer exists, so a removed type cannot break the screen. */
    fun sanitised(): DashboardConfig = DashboardConfig(tiles.filter { it.spec != null })

    fun without(typeName: String): DashboardConfig =
        DashboardConfig(tiles.filterNot { it.typeName == typeName })

    /** Appends unless already present; pinning the same type twice is never intended. */
    fun plus(tile: Tile): DashboardConfig =
        if (tiles.any { it.typeName == tile.typeName }) this else DashboardConfig(tiles + tile)

    /** Sets one tile's goal; null clears the override back to the type's default. */
    fun withGoal(typeName: String, goal: Double?): DashboardConfig = DashboardConfig(
        tiles.map { if (it.typeName == typeName) it.copy(goal = goal) else it },
    )

    /** Sets one tile's value bands; null clears the override back to the type's default. */
    fun withZones(typeName: String, zones: ValueZones?): DashboardConfig = DashboardConfig(
        tiles.map { if (it.typeName == typeName) it.copy(zones = zones) else it },
    )

    /** Moves the tile at [from] to [to], for drag-to-reorder in edit mode. */
    fun moved(from: Int, to: Int): DashboardConfig {
        if (from !in tiles.indices || to !in tiles.indices || from == to) return this
        val reordered = tiles.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return DashboardConfig(reordered)
    }

    companion object {
        /**
         * First-run dashboard: the types most people check daily, in the order they are
         * usually wanted. Only types that are actually granted and hold data will render, so
         * an over-generous default costs nothing but a few empty slots.
         */
        val DEFAULT: DashboardConfig = DashboardConfig(
            listOf(
                Tile("StepsRecord"),
                Tile("HeartRateRecord"),
                Tile("ExerciseSessionRecord"),
                Tile("SleepSessionRecord"),
                Tile("WeightRecord"),
                Tile("TotalCaloriesBurnedRecord"),
                Tile("FloorsClimbedRecord"),
            ),
        )
    }
}
