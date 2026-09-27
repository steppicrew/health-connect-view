package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** A meter shows mg/dL whole and mmol/L to one place; "91,8 mg/dL" claimed precision it lacks. */
class ValueDecimalsTest {

    private val de = Locale.GERMANY

    @Test
    fun `glucose is whole in mg per dL and one place in mmol per L`() {
        assertEquals(0, Quantity.GLUCOSE.decimals(alternate = true))
        assertEquals(1, Quantity.GLUCOSE.decimals(alternate = false))
        assertEquals("92", Formatting.number(5.1 * 18.0, Quantity.GLUCOSE.decimals(alternate = true), de))
        assertEquals("5,4", Formatting.number(5.37, Quantity.GLUCOSE.decimals(alternate = false), de))
        // Fixed places, so a round value keeps its decimal: "6,0", not "6".
        assertEquals("6,0", Formatting.number(6.0, 1, de))
    }

    @Test
    fun `other quantities keep the magnitude rule`() {
        assertNull(Quantity.MASS.decimals())
        assertEquals("83,3", Formatting.number(83.3, null, de))
    }
}
