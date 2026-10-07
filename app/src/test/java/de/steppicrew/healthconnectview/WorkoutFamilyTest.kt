package de.steppicrew.healthconnectview

import androidx.health.connect.client.records.ExerciseSessionRecord
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.WorkoutFamily
import de.steppicrew.healthconnectview.health.familiesIn
import de.steppicrew.healthconnectview.health.familyOf
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutFamilyTest {

    private val t0 = Instant.parse("2026-10-01T08:00:00Z")

    private fun workout(type: Int?, kind: Session.Kind = Session.Kind.EXERCISE) =
        Session(t0, t0.plusSeconds(3600), null, kind, "app", exerciseType = type)

    @Test
    fun `a ride indoors and outdoors is one kind`() {
        // The same session arrives as either from different writers; filtering must not split it.
        assertEquals(WorkoutFamily.CYCLING, familyOf(ExerciseSessionRecord.EXERCISE_TYPE_BIKING))
        assertEquals(WorkoutFamily.CYCLING, familyOf(ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY))
    }

    @Test
    fun `an ungrouped or missing type is other`() {
        assertEquals(WorkoutFamily.OTHER, familyOf(ExerciseSessionRecord.EXERCISE_TYPE_GOLF))
        assertEquals(WorkoutFamily.OTHER, familyOf(null))
    }

    @Test
    fun `only the kinds that occur are offered, in a fixed order with other last`() {
        val sessions = listOf(
            workout(ExerciseSessionRecord.EXERCISE_TYPE_GOLF),
            workout(ExerciseSessionRecord.EXERCISE_TYPE_WALKING),
            workout(ExerciseSessionRecord.EXERCISE_TYPE_BIKING),
            workout(ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY),
            workout(null, Session.Kind.SLEEP),
        )
        assertEquals(
            listOf(WorkoutFamily.CYCLING, WorkoutFamily.WALKING, WorkoutFamily.OTHER),
            familiesIn(sessions),
        )
    }
}
