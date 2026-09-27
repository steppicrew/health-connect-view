package de.steppicrew.healthconnectview.dashboard

import de.steppicrew.healthconnectview.health.Span
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
 * [width] and [height] are grid spans: 1x1, 2x1 or 2x2, in [SIZES] order. They were stored
 * from the start, before any other size was offered, so offering them needed no migration.
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
    /**
     * The window the tile describes: the dashboard's day, or the week, four weeks or year
     * that day falls in -- the same windows the detail screen steps through, so a tap opens
     * on exactly the figure the tile showed.
     *
     * Only offered on a large tile. A single cell has room for one number and nothing to say
     * which window it covers, and a week of steps read at a glance as today's would mislead.
     * Stored whatever the size, like the size itself, so shrinking a tile loses nothing.
     */
    val span: Span = Span.DAY,
    /** What a large tile draws; see [TileFace]. Stored whatever the size, like [span]. */
    val face: TileFace = TileFace.VALUE,
) {
    val spec: RecordTypeSpec<*>? get() = RecordRegistry.specOrNull(typeName)

    /** The next size in [SIZES], wrapping; an unknown stored size starts over at 1x1. */
    fun nextSize(): Tile {
        val next = SIZES[(SIZES.indexOf(width to height) + 1) % SIZES.size]
        return copy(width = next.first, height = next.second)
    }

    /** The goal actually in force: the user's override, else the type's default. */
    val effectiveGoal: Double? get() = goal ?: spec?.tile?.defaultGoal

    /** The value bands actually in force: the user's override, else the type's default. */
    val effectiveZones: ValueZones? get() = zones ?: spec?.tile?.defaultZones

    /** Whether the tile spans more than one cell, which is what the options need room for. */
    val isLarge: Boolean get() = width > 1 || height > 1
}

/**
 * What a large tile draws.
 *
 * [VALUE] is the single-cell face at a larger size: the number, its ring or its curve. [CHART]
 * gives the whole tile to the window's chart, with its axis values and goal line, for someone
 * who reads the shape rather than the figure. [BOTH] puts the number above a smaller chart.
 */
enum class TileFace { VALUE, CHART, BOTH }

/**
 * The sizes a tile cycles through, as width to height. No 1x2: a tall narrow tile has room for
 * nothing a square one lacks -- every face is a number with something under it, and the
 * width is what a curve or a second value needs.
 */
val SIZES: List<Pair<Int, Int>> = listOf(1 to 1, 2 to 1, 2 to 2)

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

    /** Sets one tile's window and face. */
    fun withOptions(typeName: String, span: Span, face: TileFace): DashboardConfig = DashboardConfig(
        tiles.map { if (it.typeName == typeName) it.copy(span = span, face = face) else it },
    )

    /** Steps one tile to its next size. */
    fun resized(typeName: String): DashboardConfig = DashboardConfig(
        tiles.map { if (it.typeName == typeName) it.nextSize() else it },
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
