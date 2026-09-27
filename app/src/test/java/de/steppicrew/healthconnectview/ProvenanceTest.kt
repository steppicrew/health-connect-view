package de.steppicrew.healthconnectview

import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import de.steppicrew.healthconnectview.registry.DeviceKind
import de.steppicrew.healthconnectview.registry.RecordingMethod
import de.steppicrew.healthconnectview.registry.deviceName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How and by what a record was made, as a list row and the CSV name it. */
class ProvenanceTest {

    @Test
    fun `recording methods map from the platform codes`() {
        assertEquals(RecordingMethod.MANUAL, RecordingMethod.of(Metadata.RECORDING_METHOD_MANUAL_ENTRY))
        assertEquals(RecordingMethod.ACTIVE, RecordingMethod.of(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED))
        assertEquals(RecordingMethod.AUTOMATIC, RecordingMethod.of(Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED))
        assertEquals(RecordingMethod.UNKNOWN, RecordingMethod.of(Metadata.RECORDING_METHOD_UNKNOWN))
        assertEquals(RecordingMethod.UNKNOWN, RecordingMethod.of(99))
    }

    @Test
    fun `a list names manual and active recording, not the automatic norm`() {
        assertTrue(RecordingMethod.MANUAL.shownInList)
        assertTrue(RecordingMethod.ACTIVE.shownInList)
        assertFalse(RecordingMethod.AUTOMATIC.shownInList)
        assertFalse(RecordingMethod.UNKNOWN.shownInList)
    }

    @Test
    fun `every device code the platform defines has a kind`() {
        val codes = listOf(
            Device.TYPE_WATCH, Device.TYPE_PHONE, Device.TYPE_SCALE, Device.TYPE_RING,
            Device.TYPE_HEAD_MOUNTED, Device.TYPE_FITNESS_BAND, Device.TYPE_CHEST_STRAP, Device.TYPE_SMART_DISPLAY,
        )
        assertEquals(codes.toSet(), DeviceKind.entries.map { it.code }.toSet())
        assertNull(DeviceKind.of(null))
    }

    @Test
    fun `a device is named once, maker and model`() {
        assertEquals("Garmin Forerunner 265", deviceName("Garmin", "Forerunner 265"))
        assertEquals("Garmin Forerunner 265", deviceName("Garmin", "Garmin Forerunner 265"))
        assertEquals("Pixel 9", deviceName(null, " Pixel 9 "))
        assertEquals("Withings", deviceName("Withings", ""))
        assertNull(deviceName(" ", null))
    }
}
