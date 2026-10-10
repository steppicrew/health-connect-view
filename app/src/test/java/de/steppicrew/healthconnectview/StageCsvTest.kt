package de.steppicrew.healthconnectview

import androidx.health.connect.client.records.SleepSessionRecord
import de.steppicrew.healthconnectview.health.stageCsv
import org.junit.Assert.assertEquals
import org.junit.Test

class StageCsvTest {

    @Test
    fun `the three waking codes stay apart in an export`() {
        assertEquals("awake", stageCsv(SleepSessionRecord.STAGE_TYPE_AWAKE))
        assertEquals("awake_in_bed", stageCsv(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED))
        assertEquals("out_of_bed", stageCsv(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED))
    }

    @Test
    fun `unclassified sleep is not passed off as light`() {
        assertEquals("sleeping", stageCsv(SleepSessionRecord.STAGE_TYPE_SLEEPING))
        assertEquals("light", stageCsv(SleepSessionRecord.STAGE_TYPE_LIGHT))
    }

    @Test
    fun `a code the platform adds later is unknown, not dropped`() {
        assertEquals("unknown", stageCsv(99))
    }
}
