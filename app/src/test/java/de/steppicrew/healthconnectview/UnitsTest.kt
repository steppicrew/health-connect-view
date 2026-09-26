package de.steppicrew.healthconnectview

import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import de.steppicrew.healthconnectview.health.numericAggregate
import de.steppicrew.healthconnectview.registry.Quantity
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.UnitSystem
import de.steppicrew.healthconnectview.registry.Units
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class UnitsTest {

    private val before = Units.system

    @After
    fun restore() {
        Units.system = before
    }

    @Test
    fun `conversions match the textbook values`() {
        val imperial = UnitSystem.IMPERIAL
        assertEquals(176.37, Quantity.MASS.convert(80.0, imperial), 0.01)
        assertEquals(6.214, Quantity.DISTANCE.convert(10.0, imperial), 0.001)
        assertEquals(98.6, Quantity.TEMPERATURE.convert(37.0, imperial), 0.001)
        // A change of half a degree is 0.9 °F, not 32.9.
        assertEquals(0.9, Quantity.TEMPERATURE_CHANGE.convert(0.5, imperial), 0.001)
        assertEquals(70.87, Quantity.BODY_HEIGHT.convert(180.0, imperial), 0.01)
    }

    @Test
    fun `metric shows values unchanged`() {
        Quantity.entries.forEach { assertEquals(12.5, it.convert(12.5, UnitSystem.METRIC), 0.0) }
    }

    @Test
    fun `a typed goal converts back to what was shown`() {
        Quantity.entries.forEach { quantity ->
            val shown = quantity.convert(42.0, UnitSystem.IMPERIAL)
            assertEquals(42.0, quantity.toMetric(shown, UnitSystem.IMPERIAL), 1e-9)
        }
    }

    @Test
    fun `the region decides the default`() {
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.forLocale(Locale.US))
        assertEquals(UnitSystem.METRIC, UnitSystem.forLocale(Locale.GERMANY))
        assertEquals(UnitSystem.METRIC, UnitSystem.forLocale(Locale.UK))
    }

    @Test
    fun `readings and totals convert alike`() {
        Units.system = UnitSystem.IMPERIAL
        val spec = RecordRegistry.specOrNull("WeightRecord")!!
        val record = WeightRecord(
            time = Instant.EPOCH,
            zoneOffset = ZoneOffset.UTC,
            weight = Mass.kilograms(80.0),
            metadata = Metadata.manualEntry(),
        )
        assertEquals(176.37, spec.pointsOf(record).single().value, 0.01)
        assertEquals(176.37, numericAggregate(Mass.kilograms(80.0), WeightRecord.WEIGHT_AVG)!!, 0.01)
        assertEquals(6.214, numericAggregate(Length.kilometers(10.0), DistanceRecord.DISTANCE_TOTAL)!!, 0.001)
        assertEquals("176 lb", Units.format(Quantity.MASS, 80.0))
    }
}
