package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.registry.Point
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import java.time.Duration
import kotlinx.coroutines.CancellationException
import java.time.Instant

/**
 * A span of time the user was doing something, drawn behind a chart.
 *
 * Health Connect stores no link between a session and the readings taken during it -- there
 * is no session id on a heart rate sample -- so the only available association is the time
 * range. A band therefore says "a session covered this time", which is a true statement about
 * overlap, and deliberately not "these samples belong to that session", which nothing in the
 * data supports.
 */
data class Session(
    val start: Instant,
    val end: Instant,
    /** The session's own name where its writer set one, else null. */
    val title: String?,
    val kind: Kind,
    val origin: String,
    /**
     * The exercise type code, kept so the UI can pick an icon. Null for sleep, which needs no
     * further discrimination.
     */
    val exerciseType: Int? = null,
    /** A night's stages where its writer recorded them; always empty for exercise. */
    val stages: List<SleepStage> = emptyList(),
    /**
     * The record holding this session's route, where one was recorded -- only a pointer: the
     * route itself is read when the session is opened, since a year of tracks held for a list
     * would be tens of megabytes of coordinates nobody asked to see.
     */
    val route: RouteRef? = null,
    /**
     * The id of the record this session was built from -- the copy [dedupeSessions] kept --
     * so a session can be opened again by its id alone, from any screen or from the debug
     * route. Empty only in tests.
     */
    val recordId: String = "",
    /** An exercise session's laps where its writer recorded them; empty otherwise. */
    val laps: List<Lap> = emptyList(),
    /**
     * A workout's breaks, inferred from its movement readings stopping; see [Movement]. Empty
     * until read, which only some screens do, and for anything but exercise.
     */
    val breaks: List<Break> = emptyList(),
    /** The time spent moving, where it was read; null leaves the session counted whole. */
    val moving: Duration? = null,
) {
    enum class Kind { SLEEP, EXERCISE, MINDFULNESS }
}

/** One lap of a workout, with its length where the writer recorded one. */
data class Lap(val start: Instant, val end: Instant, val meters: Double?)

/**
 * Where a session's route is: the exercise record holding it. Whether reading it needs the
 * user's consent first is asked when the session is opened, since that can change meanwhile.
 */
data class RouteRef(val recordId: String)

/**
 * Picks one session per overlapping group, preferring the writer that named it.
 *
 * The same workout arrives from several apps -- on a real device one indoor bike session was
 * written by three -- and they disagree: a Garmin watch recorded it as outdoor biking while
 * the machine's own app recorded it as stationary. Preferring a titled session favours the app
 * specific enough to name the activity, which in practice is the one that knows what it was.
 */
fun dedupeSessions(sessions: List<Session>): List<Session> {
    val sorted = sessions.sortedWith(compareBy({ it.start }, { it.end }))
    val kept = mutableListOf<Session>()

    sorted.forEach { candidate ->
        val overlapping = kept.indexOfFirst { existing ->
            existing.kind == candidate.kind &&
                candidate.start < existing.end &&
                candidate.end > existing.start
        }
        val existing = kept.getOrNull(overlapping)
        if (existing == null) {
            kept += candidate
            return@forEach
        }
        val winner = when {
            existing.title == null && candidate.title != null -> candidate
            // Among equally named copies of a night, the one with more stages: a re-sync can
            // lose detail but never add it. Measured on the phone, Health Sync's copy of a
            // Garmin night once carried one segment fewer than Garmin's own.
            existing.title == candidate.title && candidate.stages.size > existing.stages.size -> candidate
            else -> existing
        }
        // The copy that wins by its name need not be the one with the track: a machine's own
        // app names the workout, the watch recorded where it went. Keep the route either way,
        // and the laps likewise.
        val loser = if (winner === existing) candidate else existing
        kept[overlapping] = winner.copy(
            route = winner.route ?: loser.route,
            laps = winner.laps.ifEmpty { loser.laps },
        )
    }
    return kept
}

/**
 * Builds a [Session] from an exercise record. The title is the writer's own or none: a name
 * from the type is the UI's to give, in the user's language, and [dedupeSessions] prefers the
 * copy a writer named.
 */
fun ExerciseSessionRecord.toSession(): Session = Session(
    start = startTime,
    end = endTime,
    title = title,
    kind = Session.Kind.EXERCISE,
    origin = metadata.dataOrigin.packageName,
    recordId = metadata.id,
    laps = laps.map { Lap(it.startTime, it.endTime, it.length?.inMeters) },
    exerciseType = exerciseType,
    route = when (exerciseRouteResult) {
        is ExerciseRouteResult.Data, is ExerciseRouteResult.ConsentRequired -> RouteRef(metadata.id)
        else -> null
    },
)

/**
 * A mindfulness session: meditation, breathing, a guided track. Named like exercise, from its
 * title or not at all. The record type is marked experimental in the library; its permission
 * resolves to the platform's `READ_MINDFULNESS`, so it can be granted.
 */
@OptIn(ExperimentalMindfulnessSessionApi::class)
fun MindfulnessSessionRecord.toSession(): Session = Session(
    start = startTime,
    end = endTime,
    title = title,
    kind = Session.Kind.MINDFULNESS,
    origin = metadata.dataOrigin.packageName,
    recordId = metadata.id,
)

fun SleepSessionRecord.toSession(): Session = Session(
    start = startTime,
    end = endTime,
    title = title,
    kind = Session.Kind.SLEEP,
    origin = metadata.dataOrigin.packageName,
    recordId = metadata.id,
    stages = stages.mapNotNull { it.toSleepStage() },
)

/** How long a session lasted, start to end. */
val Session.duration: Duration get() = Duration.between(start, end)

/**
 * How long a session counts for in a total: a workout's moving time where it was read, else
 * its length. The owner's choice, 09.10.2026: a ride with five hours at a café in the middle
 * made the day's training "9h 6m" against two hours on the bike.
 */
val Session.counted: Duration get() = moving ?: duration

/**
 * Everything a set of sessions covered, workouts by their moving time where it was read.
 *
 * Safe to add up in a way a metric would not be, because [dedupeSessions] has already
 * collapsed the same workout written by several apps into one -- so these are distinct spans
 * rather than overlapping accounts of the same one.
 */
fun List<Session>.totalDuration(): Duration =
    fold(Duration.ZERO) { total, session -> total + session.counted }

/**
 * Sleep and exercise spans overlapping a window, deduplicated and selected by it.
 *
 * The read is widened by [SESSION_MARGIN] either side because a night's sleep is credited to
 * the morning it ends on but starts the previous evening -- measured on a real device, 22:48
 * to 05:15 -- so a window-bounded query is the wrong question for it whatever the filter's
 * overlap semantics. The margin is then undone by *selecting*, not by trimming: a session
 * that merely happened nearby is dropped, and one that belongs keeps its real start and end.
 * Trimming them to the window is what made every night read as beginning at midnight.
 *
 * Deliberately unfiltered by source. A session written by any app is still a fact about what
 * the user was doing, and the source filter is about which app's *measurements* to trust.
 */
suspend fun HealthRepository.sessionsIn(
    start: Instant,
    end: Instant,
    kinds: Set<Session.Kind>,
): List<Session> {
    val range = TimeRangeFilter.between(start.minus(SESSION_MARGIN), end.plus(SESSION_MARGIN))


    val exercise = if (Session.Kind.EXERCISE in kinds) {
        runCatching {
            read(ExerciseSessionRecord::class, range).map { it.toSession() }
        }.getOrDefault(emptyList())
    } else {
        emptyList()
    }

    // Mindfulness is an optional Health Connect feature; where the device lacks it the read
    // throws and the list simply has none, as with any type the platform does not offer.
    val mindfulness = if (Session.Kind.MINDFULNESS in kinds) {
        runCatching {
            @OptIn(ExperimentalMindfulnessSessionApi::class)
            read(MindfulnessSessionRecord::class, range).map { it.toSession() }
        }.getOrDefault(emptyList())
    } else {
        emptyList()
    }

    val sleep = if (Session.Kind.SLEEP in kinds) {
        runCatching {
            read(SleepSessionRecord::class, range).map { it.toSession() }
        }.getOrDefault(emptyList())
    } else {
        emptyList()
    }

    // Exercise belongs to the window it happened in; sleep belongs to the day it *ended* on.
    //
    // Overlap is the right test for a workout, and the wrong one for a night. A night is
    // named by the morning it ends on -- "how did I sleep last night" is asked the next day --
    // so overlap credited a single night to two days at once: the night ending this morning
    // and the one starting this evening both appeared, and the same night appeared again
    // tomorrow. Measured on the phone for 11.09: 00:27-05:15 and 21:30-08:56 were both shown,
    // the second of which is the 12th's night.
    //
    // The end is tested against the window rather than the start, so a night beginning at
    // 22:48 the previous evening still counts here, which is the whole reason for the margin.
    val (sleepSessions, otherSessions) = dedupeSessions(exercise + mindfulness + sleep)
        .partition { it.kind == Session.Kind.SLEEP }

    val kept = otherSessions.filter { it.start < end && it.end > start } +
        sleepSessions.filter { it.end > start && it.end <= end }

    return kept.sortedBy { it.start }
}

/**
 * One session by the id of its record, as the lists show it: deduplicated against the other
 * writers' copies, so it carries the route or laps another app recorded. Null where the
 * record is gone or cannot be read.
 */
suspend fun HealthRepository.sessionById(kind: Session.Kind, id: String): Session? {
    val single = try {
        when (kind) {
            Session.Kind.EXERCISE -> readOne(ExerciseSessionRecord::class, id).toSession()
            Session.Kind.SLEEP -> readOne(SleepSessionRecord::class, id).toSession()
            Session.Kind.MINDFULNESS -> {
                @OptIn(ExperimentalMindfulnessSessionApi::class)
                readOne(MindfulnessSessionRecord::class, id).toSession()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        return null
    }
    // Its own window, read again for the other copies: [dedupeSessions] keeps the id of the
    // copy it prefers, which is the one the list opened -- so the same id finds it here.
    return sessionsIn(single.start, single.end, setOf(kind)).firstOrNull { it.recordId == id } ?: single
}

/**
 * A type's records for a window, with nights selected the way [sessionsIn] selects them.
 *
 * Health Connect matches an interval record to a window by its start, so a night running
 * 23:29 to 09:24 was listed under the day it began: the sleep view of the 26th showed the night
 * in its session list and "0 records" beneath it, and the 25th listed a night it did not show.
 * Sleep is read widened and kept by its end, like the sessions; every other type is read as
 * before.
 */
suspend fun HealthRepository.recordsIn(
    spec: RecordTypeSpec<*>,
    start: Instant,
    end: Instant,
    origins: Set<DataOrigin> = emptySet(),
    maxRecords: Int = HealthRepository.MAX_RECORDS,
): List<Record> {
    if (spec.type != SleepSessionRecord::class) {
        return read(spec.type, TimeRangeFilter.between(start, end), maxRecords, origins)
    }
    return read(
        spec.type,
        TimeRangeFilter.between(start.minus(SESSION_MARGIN), end.plus(SESSION_MARGIN)),
        maxRecords,
        origins,
    ).filter { record ->
        val ended = spec.endTimeOf(record) ?: spec.timeOf(record)
        ended > start && ended <= end
    }
}

/**
 * How far outside a window sessions are searched. Half a day catches a night that began the
 * previous evening without dragging in the night before that.
 */
val SESSION_MARGIN: Duration = Duration.ofHours(12)

/**
 * A day's plot range, widened backwards to contain any session that began before it.
 *
 * A night is credited to the day it *ends* on but starts the previous evening -- measured on
 * the phone, 22:18 to 08:58. Pinned to midnight, the 1h 42m before it has nowhere to go: the
 * chart clamps anything outside its extent onto the plot edge, so the band was drawn
 * 00:00-08:58 while the headline above it read 10h 40m. One screen, two answers to "how long
 * did I sleep".
 *
 * Widening only backwards is deliberate. A session running past the *end* of the window
 * belongs to the next day -- sleep is selected by its end, so it cannot occur, and an exercise
 * session crossing midnight is shown on the day it began. Widening forward would pull
 * tomorrow's evening onto today's axis.
 *
 * A day whose sessions sit inside it is returned untouched, so the fixed midnight-to-midnight
 * axis is kept everywhere it can be.
 */
fun widenToSessions(
    range: ClosedRange<Instant>,
    sessions: List<Session>,
): ClosedRange<Instant> {
    val earliest = sessions.minOfOrNull { it.start } ?: return range
    return if (earliest < range.start) earliest..range.endInclusive else range
}

/**
 * The one writer's samples to draw across a session: the writer covering most of it.
 *
 * Coverage is counted in [slot]-wide slots of the session holding at least one sample, with
 * the sample count only breaking ties. Picking by count alone lost the start of a night on the
 * phone: Health Sync's copy had more samples overall but none before midnight, while Garmin's
 * own had one every two minutes from 23:30 -- so the curve under a 23:29 night began at 00:00.
 * Merging writers instead is not an option: instantaneous samples from two apps interleave
 * into a zigzag between two accounts of one heart rate.
 */
fun <K> fullestWriter(
    byWriter: Map<K, List<Point>>,
    start: Instant,
    end: Instant,
    slot: Duration = COVERAGE_SLOT,
): List<Point> {
    fun coverage(points: List<Point>): Int = points
        .filter { it.time >= start && it.time <= end }
        .map { Duration.between(start, it.time).toMillis() / slot.toMillis() }
        .distinct()
        .size
    return byWriter.values
        .maxWithOrNull(compareBy<List<Point>>({ coverage(it) }, { it.size }))
        ?.sortedBy { it.time }
        .orEmpty()
}

private val COVERAGE_SLOT: Duration = Duration.ofMinutes(5)

/**
 * One writer's records as a single stream: where its records overlap, only the densest one's
 * samples there.
 *
 * One app can hold two accounts of the same moment. On the phone Health Sync wrote a strength
 * session's heart rate once a second and also carried on an all-day record, begun before the
 * session, with a reading every 15 s through it; merged, the two alternated into a comb of
 * 68 jumps of 15 bpm and more, where each alone had five. Records are taken densest first,
 * and a record's samples count only outside the spans of those already taken, so a sparse
 * record still fills the minutes the dense one does not reach, and back-to-back records --
 * a watch writing one a minute -- join whole.
 */
fun singleStream(records: List<List<Point>>): List<Point> {
    val taken = mutableListOf<ClosedRange<Instant>>()
    val kept = mutableListOf<Point>()
    records.filter { it.isNotEmpty() }
        .sortedByDescending { points ->
            val span = Duration.between(points.minOf { it.time }, points.maxOf { it.time }).toMillis()
            points.size.toDouble() / (span + DENSITY_FLOOR_MS)
        }
        .forEach { points ->
            kept += points.filter { point -> taken.none { point.time in it } }
            taken += points.minOf { it.time }..points.maxOf { it.time }
        }
    return kept.sortedBy { it.time }
}

/** Keeps a record of one or two samples from counting as infinitely dense. */
private const val DENSITY_FLOOR_MS = 1_000L
