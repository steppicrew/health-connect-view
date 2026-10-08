package de.steppicrew.healthconnectview.ui.session

import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.RoutePoint
import de.steppicrew.healthconnectview.health.Session
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

/** One metric measured over a session's window, for the session screen. */
data class SessionStat(val spec: RecordTypeSpec<*>, val value: Double)

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
 */
suspend fun HealthRepository.statisticsFor(session: Session): List<SessionStat> = coroutineScope {
    val window = TimeRangeFilter.between(session.start, session.end)
    val granted = grantedPermissions()
    val gate = Semaphore(MAX_CONCURRENT_READS)

    RecordRegistry.all
        .filter { it.permission in granted && it.aggregate != null && it.isChartable }
        .map { spec ->
            async {
                gate.withPermit {
                    val metric = spec.aggregate ?: return@withPermit null
                    val value = try {
                        total(metric, window)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    value?.let { SessionStat(spec = spec, value = it) }
                }
            }
        }
        .awaitAll()
        .filterNotNull()
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
