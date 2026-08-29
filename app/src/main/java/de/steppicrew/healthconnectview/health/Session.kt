package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.time.TimeRangeFilter
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
        if (overlapping < 0) {
            kept += candidate
        } else if (kept[overlapping].title == null && candidate.title != null) {
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
 * Sleep and exercise spans overlapping a window, deduplicated and clipped to it.
 *
 * The read is widened by [SESSION_MARGIN] either side because a night's sleep is credited to
 * the morning it ends on but starts the previous evening -- measured on a real device, 22:48
 * to 05:15 -- so a window-bounded query is the wrong question for it whatever the filter's
 * overlap semantics. The margin is then undone by clipping: a session that merely happened
 * nearby is not part of the window.
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

    return dedupeSessions(exercise + sleep)
        .filter { it.start < end && it.end > start }
        .sortedBy { it.start }
}

/**
 * How far outside a window sessions are searched. Half a day catches a night that began the
 * previous evening without dragging in the night before that.
 */
val SESSION_MARGIN: Duration = Duration.ofHours(12)
