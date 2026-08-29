package de.steppicrew.healthconnectview.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * All Health Connect access goes through here.
 *
 * Reads and aggregates are deliberately separate entry points. Several apps may write the
 * same metric — a phone and a watch both recording steps, for instance — so raw records can
 * overlap. Summing them double-counts. [dailyTotals] delegates to Health Connect's own
 * aggregation, which applies data-origin priority and deduplicates; [read] is for display
 * only and its output must never be summed.
 */
class HealthRepository(private val context: Context) {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> = withContext(Dispatchers.IO) {
        client.permissionController.getGrantedPermissions()
    }

    suspend fun revokeAll() = withContext(Dispatchers.IO) {
        client.permissionController.revokeAllPermissions()
    }

    /**
     * Raw records, for display and inspection. Duplicates from different writing apps are
     * intentionally preserved — showing exactly what is stored is the point of this app.
     */
    suspend fun <T : Record> read(
        type: KClass<T>,
        range: TimeRangeFilter,
        maxRecords: Int = MAX_RECORDS,
        origins: Set<DataOrigin> = emptySet(),
    ): List<T> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = range,
                    dataOriginFilter = origins,
                    ascendingOrder = false,
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                ),
            )
            collected += response.records
            pageToken = response.pageToken
        } while (pageToken != null && collected.size < maxRecords)
        collected
    }

    /**
     * Every record in the range, thinned to at most [limit] evenly spaced samples.
     *
     * [read] deliberately returns the *newest* records and stops at [MAX_RECORDS], which is
     * right for the record list but wrong for a chart: on a high-frequency type — heart rate
     * writes thousands of readings a week — the cap is reached within days, so a chart drawn
     * from it silently covers the last few days of a year-long range and looks like missing
     * history rather than a truncated read.
     *
     * Types with an aggregate metric never come here; their charts are built from
     * deduplicated daily buckets. This is for the instantaneous types that have no aggregate
     * (blood glucose, SpO2, respiratory rate and similar), where each record is a discrete
     * reading and thinning the series changes its resolution but not its shape or extent.
     *
     * Records are counted but not retained while paging, so memory stays bounded by [limit]
     * rather than by however much the range holds.
     */
    suspend fun <T : Record> readForChart(
        type: KClass<T>,
        range: TimeRangeFilter,
        limit: Int = CHART_POINTS,
        origins: Set<DataOrigin> = emptySet(),
    ): List<T> = withContext(Dispatchers.IO) {
        val kept = ArrayDeque<T>()
        var seen = 0
        // Keep every stride-th record, doubling the stride whenever the buffer fills. That
        // holds the sample evenly spread across the whole range in a single pass, without
        // knowing the total count up front.
        var stride = 1
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = range,
                    dataOriginFilter = origins,
                    ascendingOrder = true,
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                ),
            )
            response.records.forEach { record ->
                if (seen % stride == 0) kept.addLast(record)
                seen++
                if (kept.size > limit) {
                    // Drop every second kept record and sample half as often from here on,
                    // so the retained set stays evenly spaced over what has been read.
                    var index = 0
                    kept.retainAll { (index++) % 2 == 0 }
                    stride *= 2
                }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        kept.toList()
    }

    /**
     * True if this type has anything to show — a cheap catalog probe.
     *
     * Checks aggregation as well as raw records, because some types derive a value without
     * storing records: basal metabolic rate is computed from height and weight, so reading
     * records returns nothing while a daily total exists.
     */
    suspend fun <T : Record> hasData(
        type: KClass<T>,
        range: TimeRangeFilter,
        aggregateRange: TimeRangeFilter? = null,
        metric: AggregateMetric<*>? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val hasRecords = client.readRecords(
            ReadRecordsRequest(recordType = type, timeRangeFilter = range, pageSize = 1),
        ).records.isNotEmpty()

        if (hasRecords || metric == null || aggregateRange == null) {
            hasRecords
        } else {
            runCatching { client.aggregate(AggregateRequest(setOf(metric), aggregateRange)) }
                .map { it.contains(metric) }
                .getOrDefault(false)
        }
    }

    /** Deduplicated per-day buckets. The only legitimate source of totals. */
    suspend fun dailyTotals(
        metric: AggregateMetric<*>,
        range: TimeRangeFilter,
    ): List<AggregationResultGroupedByPeriod> = withContext(Dispatchers.IO) {
        client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(metric),
                timeRangeFilter = range,
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
    }

    /**
     * One deduplicated total for a range. A dashboard tile's headline number.
     *
     * Uses `aggregate()` rather than `aggregateGroupByPeriod()`: a tile needs a single figure,
     * and the grouped call would return one bucket to unwrap for no benefit. Returns null when
     * the range holds nothing, which the caller must keep distinct from a total of zero.
     */
    suspend fun total(
        metric: AggregateMetric<*>,
        range: TimeRangeFilter,
        origins: Set<DataOrigin> = emptySet(),
    ): Double? = withContext(Dispatchers.IO) {
        val result = client.aggregate(AggregateRequest(setOf(metric), range, origins))
        result[metric]?.let(::numericAggregate)
    }

    /**
     * Deduplicated buckets of an arbitrary width, for a span chart.
     *
     * [dailyTotals] is the day-width special case. A year of daily buckets would be 365
     * points on a phone-width chart, so wider spans group more coarsely.
     */
    suspend fun bucketedTotals(
        metric: AggregateMetric<*>,
        range: TimeRangeFilter,
        bucket: Period,
        origins: Set<DataOrigin> = emptySet(),
    ): List<AggregationResultGroupedByPeriod> = withContext(Dispatchers.IO) {
        client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(metric),
                timeRangeFilter = range,
                timeRangeSlicer = bucket,
                dataOriginFilter = origins,
            ),
        )
    }

    /** Which apps contributed to a range, so a total can be explained to the user. */
    suspend fun contributingApps(
        metric: AggregateMetric<*>,
        range: TimeRangeFilter,
    ): Set<String> = withContext(Dispatchers.IO) {
        client.aggregate(AggregateRequest(setOf(metric), range))
            .dataOrigins
            .map { it.packageName }
            .toSet()
    }

    companion object {
        const val PAGE_SIZE = 1000
        const val MAX_RECORDS = 5000

        /** Chart sample size. Far beyond the pixel width of any phone chart. */
        const val CHART_POINTS = 2000
        val DEFAULT_ZONE: ZoneId get() = ZoneId.systemDefault()
    }
}
