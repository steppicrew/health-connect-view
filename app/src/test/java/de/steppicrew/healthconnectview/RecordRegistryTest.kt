package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.RecordRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
