package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.PermissionInfo
import de.steppicrew.healthconnectview.registry.RecordRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every permission on the permission screen explains itself behind its "i". */
class PermissionInfoTest {

    @Test
    fun `every type has a text`() {
        val missing = RecordRegistry.all.filter { PermissionInfo.infoFor(it) == null }.map { it.type.simpleName }
        assertTrue("no permission text for $missing", missing.isEmpty())
    }

    @Test
    fun `types sharing a permission share one text`() {
        RecordRegistry.all.groupBy { it.permission }.values.filter { it.size > 1 }.forEach { siblings ->
            assertEquals(siblings.map { it.type.simpleName }.toString(), 1, siblings.map { PermissionInfo.infoFor(it) }.toSet().size)
        }
    }
}
