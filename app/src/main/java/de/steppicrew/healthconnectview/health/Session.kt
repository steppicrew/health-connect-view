package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.time.TimeRangeFilter
import de.steppicrew.healthconnectview.registry.Point
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import java.time.Duration
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
) {
    enum class Kind { SLEEP, EXERCISE }
}

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
        when {
            existing == null -> kept += candidate
            existing.title == null && candidate.title != null -> kept[overlapping] = candidate
            // Among equally named copies of a night, the one with more stages: a re-sync can
            // lose detail but never add it. Measured on the phone, Health Sync's copy of a
            // Garmin night once carried one segment fewer than Garmin's own.
            existing.title == candidate.title && candidate.stages.size > existing.stages.size ->
                kept[overlapping] = candidate
        }
    }
    return kept
}

/** Maps a session to a readable activity name, or null where the type says nothing useful. */
fun exerciseTypeName(type: Int): String? = EXERCISE_TYPE_NAMES[type]

// Only the types that carry a meaning worth showing; anything else falls back to the session's
// own title, and failing that to no label at all rather than a guess.
private val EXERCISE_TYPE_NAMES: Map<Int, String> = mapOf(
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING to "Biking",
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY to "Indoor bike",
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to "Running",
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING to "Walking",
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING to "Hiking",
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to "Swimming",
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING to "Strength",
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES to "Pilates",
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA to "Yoga",
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE to "Rowing",
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL to "Elliptical",
)

/** Builds a [Session] from an exercise record, naming it from its title or its type. */
fun ExerciseSessionRecord.toSession(): Session = Session(
    start = startTime,
    end = endTime,
    title = title ?: exerciseTypeName(exerciseType),
    kind = Session.Kind.EXERCISE,
    origin = metadata.dataOrigin.packageName,
    exerciseType = exerciseType,
)

fun SleepSessionRecord.toSession(): Session = Session(
    start = startTime,
    end = endTime,
    title = title,
    kind = Session.Kind.SLEEP,
    origin = metadata.dataOrigin.packageName,
    stages = stages.mapNotNull { it.toSleepStage() },
)

/** How long a session lasted. */
val Session.duration: Duration get() = Duration.between(start, end)

/**
 * Everything a set of sessions covered.
 *
 * Safe to add up in a way a metric would not be, because [dedupeSessions] has already
 * collapsed the same workout written by several apps into one -- so these are distinct spans
 * rather than overlapping accounts of the same one.
 */
fun List<Session>.totalDuration(): Duration =
    fold(Duration.ZERO) { total, session -> total + session.duration }

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
    val (sleepSessions, otherSessions) = dedupeSessions(exercise + sleep)
        .partition { it.kind == Session.Kind.SLEEP }

    val kept = otherSessions.filter { it.start < end && it.end > start } +
        sleepSessions.filter { it.end > start && it.end <= end }

    return kept.sortedBy { it.start }
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
): List<Record> {
    if (spec.type != SleepSessionRecord::class) {
        return read(spec.type, TimeRangeFilter.between(start, end), origins = origins)
    }
    return read(
        spec.type,
        TimeRangeFilter.between(start.minus(SESSION_MARGIN), end.plus(SESSION_MARGIN)),
        origins = origins,
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
