package de.steppicrew.healthconnectview

import androidx.health.connect.client.records.SleepSessionRecord
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.SleepStage
import de.steppicrew.healthconnectview.health.StageKind
import de.steppicrew.healthconnectview.health.dedupeSessions
import de.steppicrew.healthconnectview.health.fullestWriter
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.health.stageKindOf
import de.steppicrew.healthconnectview.health.stageTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SleepStagesTest {

    private val night = Instant.parse("2026-09-25T22:00:00Z")

    private fun stage(fromMin: Long, toMin: Long, kind: StageKind) =
        SleepStage(night.plusSeconds(fromMin * 60), night.plusSeconds(toMin * 60), kind)

    @Test
    fun `the three waking codes are one stage`() {
        listOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        ).forEach { assertEquals(StageKind.AWAKE, stageKindOf(it)) }
    }

    @Test
    fun `unclassified sleep is not passed off as light, and unknown is a gap`() {
        assertEquals(StageKind.ASLEEP, stageKindOf(SleepSessionRecord.STAGE_TYPE_SLEEPING))
        assertNull(stageKindOf(SleepSessionRecord.STAGE_TYPE_UNKNOWN))
    }

    @Test
    fun `totals come in hypnogram order and skip absent stages`() {
        val totals = stageTotals(
            listOf(
                stage(0, 30, StageKind.LIGHT),
                stage(30, 90, StageKind.DEEP),
                stage(90, 100, StageKind.AWAKE),
                stage(100, 160, StageKind.LIGHT),
            ),
        )
        assertEquals(
            listOf(
                StageKind.AWAKE to Duration.ofMinutes(10),
                StageKind.LIGHT to Duration.ofMinutes(90),
                StageKind.DEEP to Duration.ofMinutes(60),
            ),
            totals,
        )
    }

    @Test
    fun `of two untitled copies of a night the one with more stages is kept`() {
        fun copy(origin: String, stages: Int) = Session(
            start = night,
            end = night.plusSeconds(8 * 3600),
            title = null,
            kind = Session.Kind.SLEEP,
            origin = origin,
            stages = List(stages) { stage(it * 10L, it * 10L + 10, StageKind.LIGHT) },
        )
        val kept = dedupeSessions(listOf(copy("health.sync", 18), copy("garmin", 19)))
        assertEquals("garmin", kept.single().origin)
    }

    @Test
    fun `the curve comes from the writer covering the night, not the one with most samples`() {
        val end = night.plusSeconds(8 * 3600)
        val midnight = night.plusSeconds(3600)
        // Every two minutes from the start of the night.
        val own = List(240) { Point(night.plusSeconds(it * 120L), 55.0) }
        // Every minute, but only from midnight on: more samples, less of the night.
        val copy = List(420) { Point(midnight.plusSeconds(it * 60L), 55.0) }

        val drawn = fullestWriter(mapOf("garmin" to own, "health.sync" to copy), night, end)
        assertEquals(night, drawn.first().time)
    }

    @Test
    fun `equal coverage falls back to the denser writer`() {
        val end = night.plusSeconds(3600)
        val sparse = List(12) { Point(night.plusSeconds(it * 300L), 55.0) }
        val dense = List(60) { Point(night.plusSeconds(it * 60L), 56.0) }
        assertEquals(60, fullestWriter(mapOf("a" to sparse, "b" to dense), night, end).size)
    }
}
