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
    val details: (T) -> List<Field> = { emptyList() },
    /**
     * Start of the record's time span. Supplied per type because the shape interfaces
     * (InstantaneousRecord / IntervalRecord) are internal to the library in Kotlin.
     */
    val startTime: (T) -> Instant,
    /** Deduplicating metric for totals; null means no total may be shown for this type. */
    val aggregate: AggregateMetric<*>? = null,
) {
    enum class Shape { INSTANT, INTERVAL, SERIES }

    /**
     * Single source of truth for the permission string. Record class names do not map
     * mechanically onto permissions (SleepSessionRecord -> READ_SLEEP, and Steps/StepsCadence
     * share one permission), so this must always be resolved by the library.
     */
    val permission: String get() = HealthPermission.getReadPermission(type)

    val isChartable: Boolean get() = unitRes != null

    // The three casts below are safe by construction: a spec is only ever applied to records
    // read via ReadRecordsRequest(spec.type), so the runtime type always matches T.
    @Suppress("UNCHECKED_CAST")
    fun timeOf(record: Record): Instant = startTime(record as T)

    @Suppress("UNCHECKED_CAST")
    fun pointsOf(record: Record): List<Point> = points(record as T)

    @Suppress("UNCHECKED_CAST")
    fun summaryOf(record: Record): String = summary(record as T)

    @Suppress("UNCHECKED_CAST")
    fun detailsOf(record: Record): List<Field> = details(record as T)
}
