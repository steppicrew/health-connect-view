package de.steppicrew.healthconnectview

import androidx.health.connect.client.units.Mass
import de.steppicrew.healthconnectview.health.Nutrient
import de.steppicrew.healthconnectview.health.nutrientTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutrientsTest {
    @Test
    fun `the mean counts only the days a nutrient was recorded`() {
        val days = listOf(
            mapOf(Nutrient.PROTEIN to 80.0, Nutrient.FAT to 60.0),
            emptyMap(),
            mapOf(Nutrient.PROTEIN to 100.0),
        )
        val totals = nutrientTotals(days).associateBy { it.nutrient }

        assertEquals(180.0, totals.getValue(Nutrient.PROTEIN).total, 1e-9)
        assertEquals(90.0, totals.getValue(Nutrient.PROTEIN).perDay, 1e-9)
        assertEquals(1, totals.getValue(Nutrient.FAT).days)
        assertEquals(60.0, totals.getValue(Nutrient.FAT).perDay, 1e-9)
    }

    @Test
    fun `nutrients never recorded are left out, in label order`() {
        val totals = nutrientTotals(listOf(mapOf(Nutrient.CAFFEINE to 95.0, Nutrient.FAT to 10.0)))

        assertEquals(listOf(Nutrient.FAT, Nutrient.CAFFEINE), totals.map { it.nutrient })
        assertTrue(nutrientTotals(listOf(emptyMap(), emptyMap())).isEmpty())
    }

    @Test
    fun `a mass is read in grams or milligrams`() {
        assertEquals(80.0, Nutrient.PROTEIN.of(Mass.grams(80.0)), 1e-9)
        assertEquals(2_300.0, Nutrient.SODIUM.of(Mass.grams(2.3)), 1e-6)
    }
}
