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

/** The fallback when an aggregate comes back empty must combine like the aggregate would. */
class CombineTest {

    private fun spec(name: String) = de.steppicrew.healthconnectview.registry.RecordRegistry.specOrNull(name)!!

    @Test
    fun `an averaged type is averaged, not summed`() {
        assertEquals(51.0, spec("HeartRateRecord").combine(listOf(50.0, 52.0, 51.0))!!, 1e-9)
    }

    @Test
    fun `a total is summed`() {
        assertEquals(300.0, spec("StepsRecord").combine(listOf(100.0, 200.0))!!, 1e-9)
    }

    @Test
    fun `nothing stays nothing rather than zero`() {
        assertEquals(null, spec("StepsRecord").combine(emptyList()))
    }
}
