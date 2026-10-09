package de.steppicrew.healthconnectview.ui.session

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.health.Break
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Movement
import de.steppicrew.healthconnectview.health.AFTER_END
import de.steppicrew.healthconnectview.health.RECOVERY_TOLERANCE
import de.steppicrew.healthconnectview.health.RoutePoint
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.activePieces
import de.steppicrew.healthconnectview.health.combinePieces
import de.steppicrew.healthconnectview.health.movementIn
import de.steppicrew.healthconnectview.health.fullestWriter
import de.steppicrew.healthconnectview.health.toPoints
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.ui.dashboard.heartRateSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Duration
import java.time.Instant

/**
 * One metric measured over a session's window, for the session screen: its total or mean,
 * and for a type with a spread -- heart rate, power, speed, cadence -- the lowest and highest
 * reading as well, where the platform could give both.
 */
data class SessionStat(
    val spec: RecordTypeSpec<*>,
    val value: Double,
    val low: Double? = null,
    val high: Double? = null,
)

/** A session's heart-rate curve and the breaks to shade behind it, for a row in a list. */
data class SessionCurve(val points: List<Point>?, val breaks: List<Break>)

/** An open session's route: its points, consent needed first, none recorded, or a failed read. */
sealed interface RouteLoad {
    data class Shown(val points: List<RoutePoint>) : RouteLoad
    data object NeedsConsent : RouteLoad
    data object Missing : RouteLoad
    data object Failed : RouteLoad
}

/** GPX's registered type; save dialogs and track apps both know it. */
const val GPX_MIME = "application/gpx+xml"

/**
 * Everything recorded during one session, assembled by time overlap.
 *
 * ExerciseSessionRecord itself carries no distance, power or calories -- only its type,
 * title, notes, segments, laps and route. Those metrics are separate record types written
 * over the same window, so a session's statistics exist but have to be gathered rather
 * than read. On a real indoor bike session this found 647 kcal active, 25.5 km and a mean
 * of 138 bpm across 54 heart-rate records.
 *
 * With [breaks], only the time spent moving counts: each piece between them is aggregated on
 * its own and the pieces combined, totals added and means weighted by time. A ride with five
 * hours' rest in the middle otherwise reported the resting heart rate as its lowest and pulled
 * its mean far below anything ridden.
 */
suspend fun HealthRepository.statisticsFor(
    session: Session,
    breaks: List<Break> = emptyList(),
    /** The session's heart rate as [heartRateDuring] read it, if the caller has it. */
    heartRate: List<Point>? = null,
): List<SessionStat> = coroutineScope {
    val pieces = activePieces(session.start, session.end, breaks)
    val granted = grantedPermissions()
    val heartSpec = heartRateSpec()
    val heartStat = heartSpec?.let { spec -> heartRate?.let { heartRateStat(spec, it, pieces) } }
    val specs = RecordRegistry.all
        .filter { it.permission in granted && it.aggregate != null && it.isChartable }
        // Taken from the readings already read for the curve, where there are any: Health
        // Connect took 1.4 s to aggregate a 53-minute workout's heart rate, every other type
        // well under a third of that.
        .filter { heartStat == null || it.type != heartSpec.type }
        // Derived from height and weight, not measured during anything: a workout's "157
        // kcal/day" basal rate on the phone was a figure about the person, not the ride.
        .filter { it.type != BasalMetabolicRateRecord::class }
    // In the same request as the mean, so all three come from one deduplication of the same
    // records.
    val metricsOf = specs.associateWith { spec ->
        setOfNotNull(spec.aggregate, spec.rangeAggregates?.first, spec.rangeAggregates?.second)
    }

    // Every type's metrics in one request per piece: asked type by type, about thirty types
    // over a few pieces made a hundred requests and 2.4 s on the phone. One type the platform
    // refuses fails such a request whole, so then each type is asked alone again.
    val together = try {
        pieces.map { (from, to) -> totals(metricsOf.values.flatten().toSet(), TimeRangeFilter.between(from, to)) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    val gate = Semaphore(MAX_CONCURRENT_READS)
    specs.map { spec ->
        async {
            val metrics = metricsOf.getValue(spec)
            val values = together?.map { all -> all.filterKeys { it in metrics } } ?: gate.withPermit {
                try {
                    pieces.map { (from, to) -> totals(metrics, TimeRangeFilter.between(from, to)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            } ?: return@async null
            statOf(spec, values.zip(pieces.map { (from, to) -> Duration.between(from, to) }))
        }
    }
        .awaitAll()
        .filterNotNull()
        // In the registry's order, as the others come, rather than heart rate last.
        .let { stats -> if (heartStat == null) stats else (stats + heartStat).sortedBy { RecordRegistry.all.indexOf(it.spec) } }
}

/**
 * Heart rate's mean, lowest and highest from one writer's readings within [pieces]: the
 * figures the aggregate gives, from the readings the curve draws. Safe where a total would
 * not be -- one writer cannot overlap itself, and a mean or a peak is no sum. The mean is of
 * the readings, as the platform's is. Null where no reading falls within a piece.
 */
internal fun heartRateStat(spec: RecordTypeSpec<*>, points: List<Point>, pieces: List<Pair<Instant, Instant>>): SessionStat? {
    val values = points.filter { p -> pieces.any { (from, to) -> p.time >= from && p.time < to } }.map { it.value }
    if (values.isEmpty()) return null
    return SessionStat(spec, values.average(), low = values.min(), high = values.max())
}

/** One type's figure from its totals over each piece of a session, with each piece's length. */
private fun statOf(spec: RecordTypeSpec<*>, perPiece: List<Pair<Map<AggregateMetric<*>, Double>, Duration>>): SessionStat? {
    val metric = spec.aggregate ?: return null
    val range = spec.rangeAggregates
    val value = combinePieces(
        perPiece.mapNotNull { (values, length) -> values[metric]?.let { it to length } },
        averaged = spec.isAveraged,
    ) ?: return null
    return SessionStat(
        spec = spec,
        // A counted total in whole units. A writer's whole-day record is shared out by time,
        // so the window held "3,24 floors" on the phone -- a fraction nobody climbed.
        value = if (spec.tile.integralValues && !spec.isAveraged) Math.round(value).toDouble() else value,
        // A ride's slowest moment is a near-stop on every ride (0,21 km/h on the phone), so
        // speed shows its top alone.
        low = range?.takeIf { spec.type != SpeedRecord::class }
            ?.let { (low, _) -> perPiece.mapNotNull { it.first[low] }.minOrNull() },
        high = range?.let { (_, high) -> perPiece.mapNotNull { it.first[high] }.maxOrNull() },
    )
}


/**
 * The route of [session], read now that the session is open: its points, a note that the
 * user must consent to this one first, or nothing.
 *
 * Read now rather than trusting the list's note: consent given for this session earlier, or
 * the standing permission granted since, makes the route readable.
 */
suspend fun HealthRepository.routeFor(session: Session): RouteLoad {
    val ref = session.route ?: return RouteLoad.Missing
    val result = try {
        routeOf(ref.recordId)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        return RouteLoad.Failed
    }
    return when (result) {
        is ExerciseRouteResult.Data -> RouteLoad.Shown(result.exerciseRoute.toPoints())
        is ExerciseRouteResult.ConsentRequired -> RouteLoad.NeedsConsent
        else -> RouteLoad.Missing
    }
}

/**
 * Heart rate through a session's own window, or null where fewer than two readings were taken.
 *
 * Read raw rather than aggregated, and that is safe here in a way it would not be for a total:
 * heart rate is instantaneous, so two apps writing the same beat duplicate a point on the
 * curve rather than inflating a sum. One writer's samples rather than everyone's merged: two
 * apps mirroring the same session sample at slightly different instants and values would
 * interleave into a zigzag between two accounts of one heart rate.
 *
 * The association is by time, like every other session statistic in this app: Health Connect
 * stores no session id on a sample, so these are the readings taken during the session and
 * deliberately not readings tagged as belonging to it.
 */
suspend fun HealthRepository.heartRateDuring(session: Session): List<Point>? {
    val spec = heartRateSpec() ?: return null
    val records = try {
        readForChart(spec.type, TimeRangeFilter.between(session.start, session.end))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
    return fullestWriter(
        records.groupBy { spec.originOf(it) }
            .mapValues { (_, group) -> group.flatMap { spec.pointsOf(it) } },
        session.start,
        session.end,
    ).takeIf { it.size > 1 }
}

/**
 * Heart rate in the minutes after a workout, and a little before its end, from the writer
 * with the densest readings there: mostly the watch's all-day readings, since the workout's
 * own recording has stopped. Null where nothing was read after the end.
 */
suspend fun HealthRepository.heartRateAfter(session: Session): List<Point>? {
    val spec = heartRateSpec() ?: return null
    val from = session.end.minus(RECOVERY_TOLERANCE)
    val to = session.end.plus(AFTER_END)
    val records = try {
        // From well before: Health Connect returns a series record only where it starts within
        // the range, and on the phone the one holding the minutes after a workout began 61 s
        // before its end. Read from the end itself, it was missed and the sparse all-day
        // readings drawn instead. The points are cut to the window below.
        readForChart(spec.type, TimeRangeFilter.between(from.minus(SERIES_LEAD), to))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
    return fullestWriter(
        records.groupBy { spec.originOf(it) }
            .mapValues { (_, group) -> group.flatMap { spec.pointsOf(it) }.filter { it.time >= from && it.time <= to } },
        from,
        to,
        // Slots far finer than a whole session's: over five minutes, the writer reading every
        // 15 s must win against one reading every two minutes.
        slot = AFTER_SLOT,
    ).takeIf { points -> points.any { it.time > session.end } }
}

private val AFTER_SLOT: Duration = Duration.ofSeconds(30)

/** Longer than any heart-rate series record seen spanning a workout's end (10 min). */
private val SERIES_LEAD: Duration = Duration.ofMinutes(30)

/**
 * Speed through a session's window as one writer recorded it, in metres per second, or null
 * where fewer than two readings were taken. A route stores no speed, but a watch that records
 * one writes a speed record over the same window; on the phone each of 30 routed workouts had
 * one, about 900 readings long. One writer's, for the reason [heartRateDuring] gives.
 */
suspend fun HealthRepository.speedDuring(session: Session): List<Point>? {
    val spec = RecordRegistry.specOrNull(SPEED) ?: return null
    if (spec.permission !in grantedPermissions()) return null
    val records = try {
        readForChart(spec.type, TimeRangeFilter.between(session.start, session.end))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
    return fullestWriter(
        records.groupBy { spec.originOf(it) }
            .mapValues { (_, group) -> group.flatMap { spec.pointsOf(it) } },
        session.start,
        session.end,
    ).map { Point(it.time, it.value / MS_TO_KMH) }.takeIf { it.size > 1 }
}

/**
 * One type's readings through a session's window, from the writer covering most of it, or null
 * where it is not granted or fewer than two were taken. For the lines beside heart rate through
 * a night: measured on the phone, every recent night held breath rate and oxygen once a minute
 * and HRV every five, all from one writer. One writer's, for the reason [heartRateDuring] gives.
 */
suspend fun HealthRepository.readingsDuring(session: Session, typeName: String): List<Point>? {
    val spec = RecordRegistry.specOrNull(typeName) ?: return null
    if (spec.permission !in grantedPermissions()) return null
    val records = try {
        readForChart(spec.type, TimeRangeFilter.between(session.start, session.end))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
    return fullestWriter(
        records.groupBy { spec.originOf(it) }
            .mapValues { (_, group) -> group.flatMap { spec.pointsOf(it) } },
        session.start,
        session.end,
    ).takeIf { it.size > 1 }
}

private const val SPEED = "SpeedRecord"
private const val MS_TO_KMH = 3.6

/** How many of a session's reads run at once: Health Connect serves an app largely in turn. */
const val MAX_CONCURRENT_READS = 4
