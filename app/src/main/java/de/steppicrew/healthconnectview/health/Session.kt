package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
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
