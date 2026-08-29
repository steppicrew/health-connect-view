package de.steppicrew.healthconnectview.registry

import androidx.annotation.StringRes
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import java.time.Instant
import kotlin.reflect.KClass

/** A single chartable numeric point extracted from a record. */
data class Point(val time: Instant, val value: Double)

/** A label/value line shown on the record detail screen. */
data class Field(@StringRes val labelRes: Int, val value: String)

/**
 * Describes one Health Connect record type: how to name it, chart it and render it.
 *
 * One generic data class holding lambdas — rather than a sealed hierarchy — so that all
 * types can be iterated as data and served by a single code path in the UI.
 */
data class RecordTypeSpec<T : Record>(
    val type: KClass<T>,
    @param:StringRes val displayNameRes: Int,
    val category: Category,
    /** Unit label resource, or null when the type has no chartable numeric value. */
    @param:StringRes val unitRes: Int?,
    val shape: Shape,
    /**
     * Raw points for display only. Never sum these: overlapping records from multiple
     * writing apps would be double-counted. Totals come from [aggregate].
     */
    val points: (T) -> List<Point>,
    val summary: (T) -> String,
    /**
     * Unit appended to [summary] at render time. Only set where the unit is a word rather
     * than an international symbol -- "steps" needs translating, "kg" does not -- because a
     * summary lambda has no Context and cannot resolve resources itself.
     */
    @param:StringRes val summaryUnitRes: Int? = null,
    val details: (T) -> List<Field> = { emptyList() },
    /**
     * Start of the record's time span. Supplied per type because the shape interfaces
     * (InstantaneousRecord / IntervalRecord) are internal to the library in Kotlin.
     */
    val startTime: (T) -> Instant,
    /**
     * End of the record's time span, or null for an instantaneous type.
     *
     * Supplied per type for the same reason as [startTime]: IntervalRecord is public in
     * bytecode but internal to the library in Kotlin, so `is IntervalRecord` does not compile.
     * Without this a record covering a whole day and one covering a minute are shown
     * identically, which makes a daily-summary row look like a stray midnight entry.
     */
    val endTime: ((T) -> Instant)? = null,
    /** Deduplicating metric for totals; null means no total may be shown for this type. */
    val aggregate: AggregateMetric<*>? = null,
    /**
     * How this type is drawn on the dashboard. Defaults to a plain number, which every
     * chartable type can render, so a new type needs no tile decision to be usable.
     */
    val tile: TileSpec = TileSpec(TileSpec.Form.NUMBER),
) {
    enum class Shape { INSTANT, INTERVAL, SERIES }

    /**
     * Single source of truth for the permission string. Record class names do not map
     * mechanically onto permissions (SleepSessionRecord -> READ_SLEEP, and Steps/StepsCadence
     * share one permission), so this must always be resolved by the library.
     */
    val permission: String get() = HealthPermission.getReadPermission(type)

    /**
     * Whether the type has a numeric value that can be charted or summed. The non-numeric
     * types (cycle events, sexual activity) have none, so they contribute no number anywhere
     * -- including to a session's assembled statistics.
     */
    val isChartable: Boolean get() = unitRes != null

    /**
     * Whether the type can be pinned to the dashboard.
     *
     * Wider than [isChartable]: a session tile shows a count and a list rather than a charted
     * metric, so it is useful with no unit at all -- ExerciseSessionRecord has none, because
     * "an activity" is not measured in anything. [isChartable] keeps its narrower meaning, as
     * it also decides which types can contribute a number to a session's statistics.
     */
    val isPinnable: Boolean get() = isChartable || tile.form == TileSpec.Form.SESSIONS

    // The casts below are safe by construction: a spec is only ever applied to records
    // read via ReadRecordsRequest(spec.type), so the runtime type always matches T.
    @Suppress("UNCHECKED_CAST")
    fun timeOf(record: Record): Instant = startTime(record as T)

    @Suppress("UNCHECKED_CAST")
    fun endTimeOf(record: Record): Instant? = endTime?.invoke(record as T)

    /** Writing app's package name. Every record carries one; several apps may write a type. */
    fun originOf(record: Record): String = record.metadata.dataOrigin.packageName

    @Suppress("UNCHECKED_CAST")
    fun pointsOf(record: Record): List<Point> = points(record as T)

    @Suppress("UNCHECKED_CAST")
    fun summaryOf(record: Record): String = summary(record as T)

    @Suppress("UNCHECKED_CAST")
    fun detailsOf(record: Record): List<Field> = details(record as T)
}
