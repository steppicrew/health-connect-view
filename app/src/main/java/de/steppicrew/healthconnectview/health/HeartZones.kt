package de.steppicrew.healthconnectview.health

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import de.steppicrew.healthconnectview.settings.Settings
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
    /** Where [max] came from. */
    val maxSource: MaxSource,
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
    maxSource: MaxSource,
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
    return HeartZones(times, loadOf(times), max, maxSource)
}

/** Where a maximum heart rate came from: set by hand, from the year of birth, or from the data. */
enum class MaxSource { SET, AGE, DATA }

/**
 * The maximum heart rate for someone born in [birthYear]: Tanaka's 208 - 0.7 x age, which
 * fits large studies better than 220 - age, above all past 40. The year alone is enough: a
 * year of age moves it by less than one beat.
 */
fun predictedMaxHeartRate(birthYear: Int, today: LocalDate = LocalDate.now()): Int =
    (TANAKA_BASE - TANAKA_PER_YEAR * (today.year - birthYear)).roundToInt()

/** How the maximum heart rate is found, as chosen in settings. */
enum class MaxRule {
    /** The higher of [AGE] and [DATA], or [DATA] alone without a year of birth. */
    HIGHER,
    AGE,
    DATA,
    SET,
}

/**
 * The maximum heart rate by [rule], and where it came from, or null where the rule has nothing
 * to go on. [observed] is the data's peak, asked for only where the rule needs it: it takes
 * Health Connect seconds.
 *
 * Higher of the two is the default since neither alone fits everyone, both measured
 * 24.11.2026: a customer who never pushed to the limit had a peak far below his maximum and
 * twice the active minutes, and the owner's peak of 201 at 54 -- 31 above the formula,
 * reached on four mountain-bike rides, climbing and falling smoothly -- was real. A peak can
 * only fall short of a maximum, so the higher one is the better guess; a sensor's spikes in
 * three separate months would fool it, and then the person can choose another rule.
 */
suspend fun maxHeartRate(
    rule: MaxRule,
    set: Int?,
    birthYear: Int?,
    observed: suspend () -> Int?,
    today: LocalDate = LocalDate.now(),
): Pair<Int, MaxSource>? {
    val byAge = birthYear?.let { predictedMaxHeartRate(it, today) to MaxSource.AGE }
    return when (rule) {
        MaxRule.SET -> set?.let { it to MaxSource.SET }
        MaxRule.AGE -> byAge
        MaxRule.DATA -> observed()?.let { it to MaxSource.DATA }
        MaxRule.HIGHER -> {
            val byData = observed()?.let { it to MaxSource.DATA }
            listOfNotNull(byAge, byData).maxByOrNull { it.first }
        }
    }
}

/**
 * The maximum heart rate the chosen rule gave last, for the heart-rate tile's default bands,
 * which must not wait seconds for the data's peak. In memory only: where it comes from the
 * data it is derived from readings, and nothing derived from them is kept on disk.
 */
object KnownMaxHeartRate {
    private val known = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = known.asStateFlow()

    fun record(bpm: Int?) {
        known.value = bpm
    }
}

/** [maxHeartRate] by the rule in [settings], reading the data's peak where it needs it, and recorded in [KnownMaxHeartRate]. */
suspend fun HealthRepository.resolveMaxHeartRate(settings: Settings, today: LocalDate = LocalDate.now()): Pair<Int, MaxSource>? =
    maxHeartRate(settings.maxRule, settings.maxHeartRate, settings.birthYear, { observedMaxHeartRate(today) }, today)
        // Only today's: a past window's maximum would set today's bands by a younger age.
        .also { if (today == LocalDate.now()) KnownMaxHeartRate.record(it?.first) }

/** Whether the rule reads the data's peak, which takes Health Connect seconds. */
val MaxRule.needsData: Boolean get() = this == MaxRule.DATA || this == MaxRule.HIGHER

/** Zone 0 to 4 for [bpm] against [max], or null below the first. */
fun zoneOf(bpm: Double, max: Int): Int? {
    val share = bpm / max
    if (share < ZONE_SHARES.first()) return null
    return (ZONE_SHARES.indexOfLast { share >= it }).coerceAtMost(ZONES - 1)
}

/**
 * Each zone's whole minutes times its number, added up. Whole minutes, as the zone rows show
 * them, so the sum written out under the load adds up to it exactly.
 */
fun loadOf(times: List<Duration>): Int =
    times.withIndex().sumOf { (index, time) -> time.toMinutes() * (index + 1) }.toInt()

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

private const val TANAKA_BASE = 208.0
private const val TANAKA_PER_YEAR = 0.7

/** Monthly maxima passed over as possible spikes; see [observedMaxHeartRate]. */
private const val SPIKE_MONTHS = 2

/** Longer than any watch's interval during a workout (1 to 15 s on the phone). */
private val LONGEST_SAMPLE: Duration = Duration.ofMinutes(1)
