package de.steppicrew.healthconnectview

import androidx.health.connect.client.records.ExerciseSegment
import de.steppicrew.healthconnectview.health.PlanBlock
import de.steppicrew.healthconnectview.health.PlanGoal
import de.steppicrew.healthconnectview.health.PlanStep
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.WorkoutPlan
import de.steppicrew.healthconnectview.health.planFor
import de.steppicrew.healthconnectview.health.totals
import java.time.Duration
import de.steppicrew.healthconnectview.health.WorkoutSegment
import de.steppicrew.healthconnectview.health.dedupeSessions
import de.steppicrew.healthconnectview.health.summariseSegments
import de.steppicrew.healthconnectview.registry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** What a workout carries beside its numbers: notes, segments and the plan it followed. */
class WorkoutDetailsTest {

    private val t0: Instant = Instant.parse("2026-10-25T08:00:00Z")
    private val end: Instant = t0.plusSeconds(3600)

    @Test
    fun `the named copy of a workout keeps the other copy's note`() {
        val watch = Session(t0, end, null, Session.Kind.EXERCISE, "watch", notes = "Knee fine")
        val machine = Session(t0.plusSeconds(60), end, "Indoor bike", Session.Kind.EXERCISE, "machine")
        val kept = dedupeSessions(listOf(watch, machine)).single()
        assertEquals("Indoor bike", kept.title)
        assertEquals("Knee fine", kept.notes)
    }

    private fun segment(from: Long, to: Long, type: Int, reps: Int = 0) =
        WorkoutSegment(t0.plusSeconds(from), t0.plusSeconds(to), type, reps)

    private val squat = ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT
    private val bench = ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS
    private val rest = ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST

    @Test
    fun `segments sum up by kind, in the order the kinds first come, rests last`() {
        val segments = listOf(
            segment(0, 50, squat, 10),
            segment(50, 140, rest),
            segment(140, 190, squat, 8),
            segment(190, 280, rest),
            segment(280, 330, bench, 12),
        )
        val summary = summariseSegments(segments)
        assertEquals(listOf(squat, bench, rest), summary.map { it.type })
        assertEquals(2, summary[0].sets)
        assertEquals(18, summary[0].repetitions)
        assertEquals(100L, summary[0].time.seconds)
        assertEquals(180L, summary[2].time.seconds)
        assertEquals(0, summary[2].repetitions)
    }

    @Test
    fun `heart rate is the mean of the readings inside a kind's segments`() {
        val segments = listOf(segment(0, 50, squat), segment(50, 140, rest), segment(140, 190, squat))
        val beats = listOf(
            Point(t0.plusSeconds(10), 120.0),
            Point(t0.plusSeconds(100), 100.0),
            Point(t0.plusSeconds(150), 140.0),
        )
        val summary = summariseSegments(segments, beats)
        assertEquals(130.0, summary[0].heartRate!!, 0.001)
        assertEquals(100.0, summary[1].heartRate!!, 0.001)
        assertNull(summariseSegments(segments).first().heartRate)
    }

    @Test
    fun `the named copy of a workout keeps the other copy's segments`() {
        val segments = listOf(segment(0, 50, squat, 10))
        val watch = Session(t0, end, null, Session.Kind.EXERCISE, "watch", segments = segments)
        val gym = Session(t0.plusSeconds(60), end, "Legs", Session.Kind.EXERCISE, "gym")
        assertEquals(segments, dedupeSessions(listOf(watch, gym)).single().segments)
    }

    private fun step(goal: PlanGoal) = PlanStep(ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING, 0, null, goal, emptyList())

    private fun plan(id: String, vararg blocks: PlanBlock, completed: String? = null) =
        WorkoutPlan(id, t0, end, null, null, 0, blocks.toList(), completed, "coach")

    @Test
    fun `a plan's totals count each block as often as it repeats`() {
        val intervals = plan(
            "p",
            PlanBlock(1, null, listOf(step(PlanGoal.Distance(2000.0)))),
            PlanBlock(4, null, listOf(step(PlanGoal.Distance(1000.0)), step(PlanGoal.Distance(200.0)))),
        )
        assertEquals(6800.0, intervals.totals().meters!!, 0.001)
        assertNull(intervals.totals().duration)
    }

    @Test
    fun `a step ended otherwise leaves the total unsaid`() {
        val open = plan(
            "p",
            PlanBlock(1, null, listOf(step(PlanGoal.Time(Duration.ofMinutes(10))), step(PlanGoal.Manual))),
        )
        assertNull(open.totals().duration)
        assertNull(open.totals().meters)
    }

    @Test
    fun `a workout finds its plan by its own link or by the plan's, through any copy`() {
        val linked = plan("a")
        val naming = plan("b", completed = "watch-copy")
        assertEquals(linked, planFor(listOf(naming, linked), "a", setOf("kept")))
        assertEquals(naming, planFor(listOf(linked, naming), null, setOf("kept", "watch-copy")))
        assertNull(planFor(listOf(linked, naming), null, setOf("kept")))
    }
}
