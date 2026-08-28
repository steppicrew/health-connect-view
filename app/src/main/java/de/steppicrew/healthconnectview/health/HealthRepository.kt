package de.steppicrew.healthconnectview.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.Record
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
    ): List<T> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = range,
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
        val DEFAULT_ZONE: ZoneId get() = ZoneId.systemDefault()
    }
}
