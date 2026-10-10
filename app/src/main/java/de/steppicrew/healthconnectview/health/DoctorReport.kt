package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.records.Record
import java.time.LocalDate
import kotlin.reflect.KClass

/**
 * The four reports for one period in one document, for a doctor who asks for "everything you
 * measure". A part is null where its type had no reading in the period: an empty log tells a
 * doctor nothing and costs a page.
 */
data class DoctorReport(
    val first: LocalDate,
    val last: LocalDate,
    val pressure: PressureReport?,
    val weight: WeightReport?,
    val resting: RestingReport?,
    val glucose: GlucoseReport?,
    /** The app each type was read from, by name, where one was chosen; null for all apps. */
    val sources: Map<KClass<out Record>, String?> = emptyMap(),
) {
    val parts: Int get() = listOfNotNull(pressure, weight, resting, glucose).size
}

/**
 * The readings with the lowest and the highest systolic value, as whole readings. The lowest
 * systolic and the lowest diastolic of a period usually come from different readings, and
 * printed as one pair they would make up a reading nobody took.
 */
fun pressureExtremes(readings: List<PressureReading>): Pair<PressureReading, PressureReading>? {
    val low = readings.minByOrNull { it.systolic } ?: return null
    val high = readings.maxByOrNull { it.systolic } ?: return null
    return low to high
}
