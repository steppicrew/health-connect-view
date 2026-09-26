package de.steppicrew.healthconnectview

import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.metadata.Metadata
import de.steppicrew.healthconnectview.registry.Category
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Plain JVM tests — no Robolectric. The registry is pure data, and the platform
 * permission constants are read straight out of android.jar via reflection, which keeps
 * this test fast and independent of any emulated Android runtime.
 */
class RecordRegistryTest {

    @Test
    fun `registry holds every supported record type exactly once`() {
        assertEquals(40, RecordRegistry.all.size)
        assertEquals(40, RecordRegistry.all.map { it.type }.toSet().size)
    }

    /**
     * The critical guard. Record class names do not map mechanically onto permission
     * strings, and a permission the platform does not define is never granted — it fails
     * silently at runtime and merely looks like missing data. Catch it at build time.
     */
    @Test
    fun `every permission is a real platform constant`() {
        val platform = Class.forName("android.health.connect.HealthPermissions").fields
            .filter { it.type == String::class.java }
            .mapNotNull { it.get(null) as? String }
            .toSet()

        val unknown = RecordRegistry.allReadPermissions.filterNot { it in platform }
        assertTrue("Permissions unknown to the platform: $unknown", unknown.isEmpty())
    }

    @Test
    fun `permissions are deduplicated because some types share one`() {
        // Steps/StepsCadence, MenstruationFlow/MenstruationPeriod and
        // ExerciseSession/CyclingPedalingCadence each share a permission.
        assertTrue(RecordRegistry.allReadPermissions.size < RecordRegistry.all.size)
    }

    @Test
    fun `only read permissions are ever requested`() {
        val writes = RecordRegistry.allReadPermissions.filter { "WRITE" in it }
        assertTrue("App must stay read-only, found: $writes", writes.isEmpty())
    }

    @Test
    fun `every category is populated`() {
        RecordRegistry.byCategory.forEach { (category, specs) ->
            assertTrue("$category is empty", specs.isNotEmpty())
        }
    }

    /**
     * A row showing only a start time cannot distinguish a whole-day summary record from a
     * one-minute one, which is how a legitimate daily total ends up looking like a stray
     * midnight entry. Interval types must therefore expose their end.
     */
    @Test
    fun `every interval type exposes an end time`() {
        RecordRegistry.all
            .filter { it.shape == RecordTypeSpec.Shape.INTERVAL }
            .forEach { spec ->
                assertNotNull(
                    "${spec.type.simpleName} is an interval type with no endTime",
                    spec.endTime,
                )
            }
    }

    @Test
    fun `instantaneous types do not claim an end time`() {
        RecordRegistry.all
            .filter { it.shape == RecordTypeSpec.Shape.INSTANT }
            .forEach { spec ->
                assertNull(
                    "${spec.type.simpleName} is instantaneous but declares an endTime",
                    spec.endTime,
                )
            }
    }

    /**
     * Two chart decisions read these flags as opposites, so a type setting both would be
     * asked to be a counted quantity and a taken reading at once.
     *
     * `cumulativeIntraday` draws the multi-day chart as bars from zero, because each bucket
     * is a total. `markReadings` connects that chart through days with nothing recorded,
     * because a day without a weigh-in says nothing rather than zero. A type with both would
     * draw bars through days it has no value for, which invents a total nobody wrote.
     */
    @Test
    fun `a type is either a counted quantity or a taken reading, never both`() {
        val both = RecordRegistry.all
            .filter { it.tile.cumulativeIntraday && it.tile.markReadings }
            .map { it.type.simpleName }

        assertTrue("Types claiming to be counted and measured at once: $both", both.isEmpty())
    }

    @Test
    fun `unrecorded protection is its own state, not unprotected`() {
        val spec = RecordRegistry.spec(SexualActivityRecord::class)
        fun wordsFor(value: Int) = spec.summaryResOf(
            SexualActivityRecord(Instant.EPOCH, null, Metadata.manualEntry(), value),
        )

        assertEquals(listOf(R.string.protection_unspecified), wordsFor(SexualActivityRecord.PROTECTION_USED_UNKNOWN))
        assertEquals(listOf(R.string.protection_used), wordsFor(SexualActivityRecord.PROTECTION_USED_PROTECTED))
        assertEquals(listOf(R.string.protection_not_used), wordsFor(SexualActivityRecord.PROTECTION_USED_UNPROTECTED))
    }

    @Test
    fun `no cycle type shows a raw integer`() {
        val raw = RecordRegistry.all
            .filter { it.category == Category.CYCLE }
            // Measured types (basal temperature) have a number to show; the rest are enums.
            .filter { !it.isChartable && it.summaryRes == null && it.shape != RecordTypeSpec.Shape.INTERVAL }
            .map { it.type.simpleName }

        assertTrue("Cycle types without worded values: $raw", raw.isEmpty())
    }
}
