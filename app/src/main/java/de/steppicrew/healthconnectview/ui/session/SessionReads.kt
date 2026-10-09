package de.steppicrew.healthconnectview.ui.session

import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.health.Break
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Movement
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
): List<SessionStat> = coroutineScope {
    val pieces = activePieces(session.start, session.end, breaks)
    val granted = grantedPermissions()
    val gate = Semaphore(MAX_CONCURRENT_READS)

    RecordRegistry.all
        .filter { it.permission in granted && it.aggregate != null && it.isChartable }
        // Derived from height and weight, not measured during anything: a workout's "157
        // kcal/day" basal rate on the phone was a figure about the person, not the ride.
        .filter { it.type != BasalMetabolicRateRecord::class }
        .map { spec ->
            async {
                gate.withPermit {
                    val metric = spec.aggregate ?: return@withPermit null
                    // In the same request as the mean, so all three come from one
                    // deduplication of the same records.
                    val range = spec.rangeAggregates
                    val metrics = setOfNotNull(metric, range?.first, range?.second)
                    val perPiece = try {
                        pieces.map { (from, to) ->
                            totals(metrics, TimeRangeFilter.between(from, to)) to Duration.between(from, to)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        return@withPermit null
                    }
                    val value = combinePieces(
                        perPiece.mapNotNull { (values, length) -> values[metric]?.let { it to length } },
                        averaged = spec.isAveraged,
                    ) ?: return@withPermit null
                    SessionStat(
                        spec = spec,
                        // A counted total in whole units. A writer's whole-day record is shared
                        // out by time, so the window held "3,24 floors" on the phone -- a
                        // fraction nobody climbed.
                        value = if (spec.tile.integralValues && !spec.isAveraged) Math.round(value).toDouble() else value,
                        // A ride's slowest moment is a near-stop on every ride (0,21 km/h on the
                        // phone), so speed shows its top alone.
                        low = range?.takeIf { spec.type != SpeedRecord::class }
                            ?.let { (low, _) -> perPiece.mapNotNull { it.first[low] }.minOrNull() },
                        high = range?.let { (_, high) -> perPiece.mapNotNull { it.first[high] }.maxOrNull() },
                    )
                }
            }
        }
        .awaitAll()
        .filterNotNull()
}

/**
 * How an exercise session divides into moving, stopped and breaks, read from the movement data
 * recorded during it.
 *
 * Speed and distance are only written while moving, so a hole in them is a stop; heart rate is
 * not, since a watch goes on measuring it all day. The session's own writer is asked first --
 * its readings belong to the same recording -- and otherwise the writer with the most. Garmin's
 * own copy of the 18.09 ride, measured on the phone, was 121 minutes long from the right start:
 * its active time laid end to end, with its readings packed into that span. Mixing it with
 * Health Sync's real timeline would fill the hole.
 *
 * Null for anything but exercise, where a type is not granted, or where nothing was recorded.
 */
suspend fun HealthRepository.movementDuring(session: Session): Movement? {
    if (session.kind != Session.Kind.EXERCISE) return null
    val granted = grantedPermissions()
    val window = TimeRangeFilter.between(session.start, session.end)
    // Speed first and distance only where it has none: one speed record holds a ride's
    // thousands of samples, where distance came as 4,224 records -- five pages to read
    // instead of one, for every workout on a page. The session's own writer comes before any
    // other in either type, for the reason above; another writer is the last resort.
    var fallback: List<Instant>? = null
    MOVEMENT_TYPES.mapNotNull(RecordRegistry::specOrNull)
        .filter { it.permission in granted }
        .forEach { spec ->
            val records = try {
                readForChart(spec.type, window)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            val byWriter = records.groupBy { spec.originOf(it) }.mapValues { (_, group) ->
                group.flatMap { record -> spec.pointsOf(record).map { it.time }.ifEmpty { listOf(spec.timeOf(record)) } }
                    .filter { it >= session.start && it <= session.end }
            }
            byWriter[session.origin]?.let { own -> movementIn(session.start, session.end, own)?.let { return it } }
            if (fallback == null) fallback = byWriter.values.maxByOrNull { it.size }
        }
    fallback?.let { return movementIn(session.start, session.end, it) }
    return null
}

/**
 * [sessions] with each workout's breaks and moving time read, so totals count time on the
 * move and bands leave the breaks out. Other kinds pass through as they are.
 */
suspend fun HealthRepository.withMovement(sessions: List<Session>): List<Session> = coroutineScope {
    val gate = Semaphore(MAX_CONCURRENT_READS)
    sessions.map { session ->
        async {
            if (session.kind != Session.Kind.EXERCISE) return@async session
            val movement = gate.withPermit { movementDuring(session) } ?: return@async session
            session.copy(breaks = movement.breaks, moving = movement.moving)
        }
    }.awaitAll()
}

/** Written only while moving: their absence inside a workout is a stop. In the order asked. */
private val MOVEMENT_TYPES = listOf("SpeedRecord", "DistanceRecord")

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

private const val SPEED = "SpeedRecord"
private const val MS_TO_KMH = 3.6

/** How many of a session's reads run at once: Health Connect serves an app largely in turn. */
const val MAX_CONCURRENT_READS = 4
