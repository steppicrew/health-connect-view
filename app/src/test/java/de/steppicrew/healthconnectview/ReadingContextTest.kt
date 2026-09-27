package de.steppicrew.healthconnectview

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Pressure
import de.steppicrew.healthconnectview.registry.csv
import de.steppicrew.healthconnectview.registry.readingContext
import de.steppicrew.healthconnectview.registry.tally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReadingContextTest {

    private val now = Instant.parse("2026-09-27T07:00:00Z")

    private fun glucose(relation: Int, meal: Int, specimen: Int) = BloodGlucoseRecord(
        time = now,
        zoneOffset = null,
        metadata = Metadata.manualEntry(),
        level = BloodGlucose.millimolesPerLiter(5.4),
        specimenSource = specimen,
        mealType = meal,
        relationToMeal = relation,
    )

    private fun pressure(position: Int, location: Int) = BloodPressureRecord(
        time = now,
        zoneOffset = null,
        metadata = Metadata.manualEntry(),
        systolic = Pressure.millimetersOfMercury(124.0),
        diastolic = Pressure.millimetersOfMercury(81.0),
        bodyPosition = position,
        measurementLocation = location,
    )

    @Test
    fun `glucose names relation, meal and specimen, in that order`() {
        val items = readingContext(
            glucose(BloodGlucoseRecord.RELATION_TO_MEAL_FASTING, MealType.MEAL_TYPE_BREAKFAST, BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD),
        )
        assertEquals(listOf(R.string.glucose_relation_fasting, R.string.meal_breakfast, R.string.specimen_capillary), items.map { it.valueRes })
        assertEquals("relation_to_meal=fasting;meal=breakfast;specimen=capillary_blood", items.csv())
    }

    @Test
    fun `unknown values are left out, not shown as unknown`() {
        assertTrue(readingContext(glucose(BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN, MealType.MEAL_TYPE_UNKNOWN, BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN)).isEmpty())
        assertTrue(readingContext(pressure(BloodPressureRecord.BODY_POSITION_UNKNOWN, BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN)).isEmpty())
        assertEquals("", emptyList<de.steppicrew.healthconnectview.registry.ContextItem>().csv())
    }

    @Test
    fun `blood pressure names position and where it was measured`() {
        val items = readingContext(pressure(BloodPressureRecord.BODY_POSITION_SITTING_DOWN, BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM))
        assertEquals(listOf(R.string.context_position, R.string.context_location), items.map { it.labelRes })
        assertEquals("body_position=sitting;location=left_upper_arm", items.csv())
    }

    @Test
    fun `a report counts each item's values, most frequent first`() {
        val seated = readingContext(pressure(BloodPressureRecord.BODY_POSITION_SITTING_DOWN, BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM))
        val standing = readingContext(pressure(BloodPressureRecord.BODY_POSITION_STANDING_UP, BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN))
        val tallies = tally(listOf(standing, seated, seated, emptyList()))
        assertEquals(listOf(R.string.context_position, R.string.context_location), tallies.map { it.labelRes })
        assertEquals(listOf(R.string.position_sitting to 2, R.string.position_standing to 1), tallies[0].counts)
        assertEquals(listOf(R.string.location_left_upper_arm to 2), tallies[1].counts)
    }
}
