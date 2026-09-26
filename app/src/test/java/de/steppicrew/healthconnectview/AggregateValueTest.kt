package de.steppicrew.healthconnectview

import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.units.Length
import de.steppicrew.healthconnectview.health.numericAggregate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Each aggregate must come back on the scale its type's records are shown in. */
class AggregateValueTest {

    @Test
    fun `distance is in kilometres`() {
        assertEquals(5.2, numericAggregate(Length.meters(5200.0), DistanceRecord.DISTANCE_TOTAL)!!, 1e-9)
    }

    @Test
    fun `elevation is in metres, like its records`() {
        assertEquals(670.0, numericAggregate(Length.meters(670.0), ElevationGainedRecord.ELEVATION_GAINED_TOTAL)!!, 1e-9)
    }

    @Test
    fun `height is in centimetres, like its records`() {
        assertEquals(180.0, numericAggregate(Length.meters(1.8), HeightRecord.HEIGHT_AVG)!!, 1e-9)
    }
}
