package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.registry.Point
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

/**
 * A workout's heart rate as time in five zones of a maximum heart rate, and one number for
 * the whole of it -- the owner's choice, 09.10.2026, over an average that hides intervals.
 *
 * The zones are the usual shares of the maximum: 50-60 %, 60-70 %, 70-80 %, 80-90 % and
 * 90-100 %. Time below the first counts in none. The load is each zone's minutes times its
 * number, added up, so it grows with both how long and how hard. Neither carries a verdict:
 * the screen says how they are made, never whether a workout was too much.
 */
data class HeartZones(
    /** Time in zone 1 to 5, in that order. */
    val times: List<Duration>,
    val load: Int,
    /** The maximum the zones are shares of, in beats per minute. */
    val max: Int,
    /** True where the maximum was set in settings rather than read from the data. */
    val maxFromSettings: Boolean,
) {
    /** The lower and upper bound of zone [index] (0 to 4) in beats per minute. */
    fun boundsOf(index: Int): Pair<Int, Int> =
        (max * ZONE_SHARES[index]).roundToInt() to (max * ZONE_SHARES[index + 1]).roundToInt()
}

/**
 * Time in each zone from [points], each reading counted until the next one, but never for more
 * than [longest]: a watch that drops out for ten minutes has not measured those ten minutes.
 * Readings inside [breaks] count in none, as a workout's other figures leave breaks out.
 *
 * Null where the maximum is unknown or the readings are too few to say anything.
 */
fun heartZones(
    points: List<Point>,
    breaks: List<Break>,
    max: Int?,
    maxFromSettings: Boolean,
    longest: Duration = LONGEST_SAMPLE,
): HeartZones? {
    if (max == null || max <= 0 || points.size < 2) return null
    val times = MutableList(ZONES) { Duration.ZERO }
    points.sortedBy { it.time }.zipWithNext { a, b ->
        if (breaks.any { a.time >= it.start && a.time < it.end }) return@zipWithNext
        val zone = zoneOf(a.value, max) ?: return@zipWithNext
        val span = Duration.between(a.time, b.time)
        times[zone] = times[zone] + if (span > longest) longest else span
    }
    return HeartZones(times, loadOf(times), max, maxFromSettings)
}

/** Zone 0 to 4 for [bpm] against [max], or null below the first. */
fun zoneOf(bpm: Double, max: Int): Int? {
    val share = bpm / max
    if (share < ZONE_SHARES.first()) return null
    return (ZONE_SHARES.indexOfLast { share >= it }).coerceAtMost(ZONES - 1)
}

/** Each zone's minutes times its number, added up. */
fun loadOf(times: List<Duration>): Int =
    times.withIndex().sumOf { (index, time) -> time.toMillis() / MILLIS_PER_MINUTE * (index + 1) }.roundToInt()

/**
 * The maximum heart rate the data shows: the third-highest monthly maximum of the past year,
 * so a month or two with a sensor's spike -- a strap slipping, a watch pressed against a door
 * -- do not set every zone too high. Null with fewer than three months, which say too little.
 *
 * One grouped aggregate for the whole year rather than a read of its readings: a year of heart
 * rate every 15 seconds is two million samples. Monthly, not daily: Health Connect's cost
 * grows with the buckets, and on the phone 365 days took 55 s, 12 months 5 s. Kept for the
 * day, so the next workout opened does not ask again.
 */
suspend fun HealthRepository.observedMaxHeartRate(today: LocalDate = LocalDate.now()): Int? =
    ObservedMax.lock.withLock {
        ObservedMax.known?.takeIf { it.first == today }?.let { return@withLock it.second }
        val start = today.minusYears(1).withDayOfMonth(1).atStartOfDay()
        val end = today.plusDays(1).atStartOfDay()
        val months = try {
            bucketedTotals(HeartRateRecord.BPM_MAX, TimeRangeFilter.between(start, end), Period.ofMonths(1))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Not kept: the next workout opened tries again.
            return@withLock null
        }
        val maxima = months.mapNotNull { it.result[HeartRateRecord.BPM_MAX] }.sortedDescending()
        maxima.getOrNull(SPIKE_MONTHS)?.toInt().also { ObservedMax.known = today to it }
    }

/** The last [observedMaxHeartRate], in memory only, and the day it was read for. */
private object ObservedMax {
    val lock = Mutex()
    var known: Pair<LocalDate, Int?>? = null
}

private val ZONE_SHARES = listOf(0.5, 0.6, 0.7, 0.8, 0.9, 1.0)
private const val ZONES = 5
private const val MILLIS_PER_MINUTE = 60_000.0

/** Monthly maxima passed over as possible spikes; see [observedMaxHeartRate]. */
private const val SPIKE_MONTHS = 2

/** Longer than any watch's interval during a workout (1 to 15 s on the phone). */
private val LONGEST_SAMPLE: Duration = Duration.ofMinutes(1)
