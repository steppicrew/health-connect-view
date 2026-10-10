package de.steppicrew.healthconnectview.dashboard

import de.steppicrew.healthconnectview.health.readsHeatmap
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
 * [id] tells tiles apart once a type may be pinned more than once -- today's steps as a ring
 * beside a year of weekly bars. The first tile of a type keeps the type name as its id, so a
 * layout stored before ids existed reads back unchanged; later ones get "name#2" and up.
 *
 * [width] and [height] are grid spans: 1x1, 2x1 or 2x2, in [SIZES] order. They were stored
 * from the start, before any other size was offered, so offering them needed no migration.
 */
data class Tile(
    val typeName: String,
    val width: Int = 1,
    val height: Int = 1,
    /**
     * Overrides the type's [TileSpec.defaultGoal]; null means use the default.
     *
     * Per type, not per tile, though stored on each: a step goal is the person's, and the
     * detail screen draws one goal line whichever of two step tiles opened it. Every tile of
     * a type carries the same value; [DashboardConfig.withGoal] and
     * [DashboardConfig.sanitised] keep it so.
     */
    val goal: Double? = null,
    /**
     * Overrides the type's [TileSpec.defaultZones]; null means use the default. Per type,
     * like [goal].
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
    val id: String = typeName,
    /**
     * The card's colour, per tile: with a type on several tiles, colour is what tells them
     * apart at a glance. Stored without Pro like the size, and drawn only with it.
     */
    val color: TileColor = TileColor.DEFAULT,
    /**
     * A second curve a large tile draws with its own, by type name: a night's heart rate over
     * its stages. Only what [companionsOf] offers for the type; stored whatever the size.
     */
    val companion: String? = null,
    /**
     * The calendar face over the calendar year so far (January to December, the days to come
     * left blank) rather than the last 365 days. Stored whatever the face, like [span].
     */
    val calendarYear: Boolean = false,
) {
    val spec: RecordTypeSpec<*>? get() = RecordRegistry.specOrNull(typeName)

    /** The one tile that is not a type: what moved this week, across all of them. */
    val isInsights: Boolean get() = typeName == INSIGHTS

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

    companion object {
        /** No record class has this simple name, so it cannot collide with a type's tile. */
        const val INSIGHTS = "Insights"
    }
}

/**
 * What a large tile draws.
 *
 * [VALUE] is the single-cell face at a larger size: the number, its ring or its curve. [CHART]
 * gives the whole tile to the window's chart, with its axis values and goal line, for someone
 * who reads the shape rather than the figure. [BOTH] puts the number above a smaller chart.
 */
enum class TileFace {
    VALUE,
    CHART,
    BOTH,

    /**
     * Weight with body fat, water and bone mass beside it, each the latest known -- the owner's
     * idea, 09.10.2026. Weight tiles only ([facesFor]); a tap opens the body composition screen.
     */
    BODY,

    /**
     * The year as a calendar, one shade per day, as under a type's year chart -- the owner's
     * idea, 10.10.2026. Over the last 365 days or the calendar year ([Tile.calendarYear]).
     * Offered where the year can be read without the chart's own read ([readsHeatmap]):
     * readings with only daily means took a minute, which a dashboard cannot wait for.
     */
    CALENDAR,
}

/** The faces a large tile of [typeName] offers: the body face only on weight, the calendar where it reads quickly. */
fun facesFor(typeName: String): List<TileFace> =
    TileFace.entries.filter { face ->
        when (face) {
            TileFace.BODY -> typeName == WEIGHT_TYPE
            TileFace.CALENDAR -> RecordRegistry.specOrNull(typeName)?.let(::readsHeatmap) == true
            else -> true
        }
    }

/** The type whose tile may show the body face. */
const val WEIGHT_TYPE = "WeightRecord"

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

    /**
     * Drops tiles whose type no longer exists, so a removed type cannot break the screen, and
     * any insights tile after the first;
     * gives a repeated id a fresh one, and each type's goal and zones to all its tiles, so a
     * hand-edited or merged layout cannot break the rules the edits keep.
     */
    fun sanitised(): DashboardConfig {
        // One insights tile at most, one cell: a second would show the same list, and a larger
        // face is still to be designed.
        val insightsAt = tiles.indexOfFirst { it.isInsights }
        val known = tiles.withIndex()
            .filter { (index, tile) -> tile.spec != null || index == insightsAt }
            .map { (_, tile) -> if (tile.isInsights) tile.copy(width = 1, height = 1) else tile }
        val first = known.groupBy { it.typeName }.mapValues { it.value.first() }
        val seen = mutableSetOf<String>()
        val fixed = known.map { tile ->
            val lead = first.getValue(tile.typeName)
            val id = if (seen.add(tile.id)) tile.id else freeId(tile.typeName, seen).also { seen += it }
            tile.copy(id = id, goal = lead.goal, zones = lead.zones)
        }
        return DashboardConfig(fixed)
    }

    fun without(id: String): DashboardConfig = DashboardConfig(tiles.filterNot { it.id == id })

    /**
     * Adds a tile of [typeName] in front of the tile [before], or at the end where that is null
     * or gone: the dashboard passes the first tile in view, so a new tile appears where the
     * user is looking rather than below everything. A type already pinned gets another tile
     * with an id of its own and the type's goal and zones; whether that is allowed is the
     * caller's to decide.
     */
    fun adding(typeName: String, before: String? = null): DashboardConfig {
        val sibling = tiles.firstOrNull { it.typeName == typeName }
        val tile = Tile(
            typeName = typeName,
            goal = sibling?.goal,
            zones = sibling?.zones,
            id = freeId(typeName, tiles.map { it.id }.toSet()),
        )
        val at = tiles.indexOfFirst { it.id == before }.takeIf { it >= 0 } ?: tiles.size
        return DashboardConfig(tiles.take(at) + tile + tiles.drop(at))
    }

    fun has(typeName: String): Boolean = tiles.any { it.typeName == typeName }

    /** Sets the goal of every tile of a type; null clears it back to the type's default. */
    fun withGoal(typeName: String, goal: Double?): DashboardConfig = DashboardConfig(
        tiles.map { if (it.typeName == typeName) it.copy(goal = goal) else it },
    )

    /** Sets the value bands of every tile of a type; null restores the type's default. */
    fun withZones(typeName: String, zones: ValueZones?): DashboardConfig = DashboardConfig(
        tiles.map { if (it.typeName == typeName) it.copy(zones = zones) else it },
    )

    /** Sets one tile's window and face. */
    fun withOptions(
        id: String,
        span: Span,
        face: TileFace,
        companion: String? = null,
        calendarYear: Boolean = false,
    ): DashboardConfig = DashboardConfig(
        tiles.map { if (it.id == id) it.copy(span = span, face = face, companion = companion, calendarYear = calendarYear) else it },
    )

    /** Sets one tile's colour. */
    fun withColor(id: String, color: TileColor): DashboardConfig = DashboardConfig(
        tiles.map { if (it.id == id) it.copy(color = color) else it },
    )

    /** Steps one tile to its next size. */
    fun resized(id: String): DashboardConfig = DashboardConfig(
        tiles.map { if (it.id == id) it.nextSize() else it },
    )

    /** Moves the tile at [from] to [to], for drag-to-reorder in edit mode. */
    fun moved(from: Int, to: Int): DashboardConfig {
        if (from !in tiles.indices || to !in tiles.indices || from == to) return this
        val reordered = tiles.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return DashboardConfig(reordered)
    }

    companion object {
        /** The type name itself if unused, else the first free "name#2", "name#3" ... */
        private fun freeId(typeName: String, taken: Set<String>): String =
            generateSequence(1) { it + 1 }
                .map { if (it == 1) typeName else "$typeName#$it" }
                .first { it !in taken }

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

/**
 * The curves a large tile of [typeName] may draw over its sessions -- the owner's idea,
 * 09.10.2026: a night's readings over its stages, which the phone holds every night, and a
 * workout's heart or breath rate in place of the day's timeline. Weight's body composition is
 * the combined weight tile, step 64 of the roadmap. Elsewhere the tile already has its context
 * (workouts behind heart rate) or draws bars a line would clash with.
 */
fun companionsOf(typeName: String): List<String> = when (typeName) {
    "SleepSessionRecord" -> listOf(
        "HeartRateRecord",
        "RespiratoryRateRecord",
        "OxygenSaturationRecord",
        "HeartRateVariabilityRmssdRecord",
    )
    "ExerciseSessionRecord" -> listOf(
        "HeartRateRecord",
        "RespiratoryRateRecord",
    )
    else -> emptyList()
}
