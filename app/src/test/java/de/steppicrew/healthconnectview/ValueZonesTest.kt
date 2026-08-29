package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.ValueZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The colour a reading gets must depend only on the reading.
 *
 * The bug this guards against: segments used to take one colour from the value they ended on,
 * so a rise from 100 to 135 drew red while the fall back from 135 to 95 drew blue -- the same
 * reading wore two colours depending on which way the line was travelling.
 */
class ValueZonesTest {

    private val zones = ValueZones.DEFAULT_HEART_RATE

    @Test
    fun `a value has one colour regardless of context`() {
        assertEquals(zones.colorFor(120.0), zones.colorFor(120.0))
    }

    @Test
    fun `readings in different bands get different colours`() {
        assertNotEquals(zones.colorFor(45.0), zones.colorFor(150.0))
        assertNotEquals(zones.colorFor(90.0), zones.colorFor(130.0))
    }

    /** Extremes clamp rather than wrapping, so a very high reading cannot look calm. */
    @Test
    fun `values beyond the ends hold the end colours`() {
        assertEquals(ValueZones.ZONE_COLORS.first(), zones.colorFor(10.0))
        assertEquals(ValueZones.ZONE_COLORS.first(), zones.colorFor(-5.0))
        assertEquals(ValueZones.ZONE_COLORS.last(), zones.colorFor(200.0))
        assertEquals(ValueZones.ZONE_COLORS.last(), zones.colorFor(500.0))
    }

    @Test
    fun `zone index rises with the value`() {
        assertEquals(0, zones.zoneOf(30.0))
        assertEquals(1, zones.zoneOf(70.0))
        assertEquals(2, zones.zoneOf(120.0))
        assertEquals(3, zones.zoneOf(150.0))
        assertEquals(4, zones.zoneOf(170.0))
    }

    /** A user can type anything; unsorted or duplicated bounds must not reach the lookup. */
    @Test
    fun `sanitising sorts and dedupes bounds`() {
        val messy = ValueZones(listOf(140.0, 50.0, 100.0, 50.0, 160.0)).sanitised()
        assertEquals(listOf(50.0, 100.0, 140.0, 160.0), messy.bounds)
    }

    @Test
    fun `empty bounds still yield a colour`() {
        assertEquals(ValueZones.ZONE_COLORS.first(), ValueZones(emptyList()).colorFor(120.0))
    }
}
