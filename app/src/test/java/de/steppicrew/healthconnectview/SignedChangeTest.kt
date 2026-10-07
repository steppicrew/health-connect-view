package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.Formatting
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SignedChangeTest {

    private val locale = Locale.GERMANY

    @Test
    fun `a rise carries a plus, a fall its minus`() {
        assertEquals("+0,4", Formatting.signed(0.37, 1, locale))
        assertEquals("-1,2", Formatting.signed(-1.24, 1, locale))
    }

    @Test
    fun `a change that rounds to nothing has no sign`() {
        assertEquals("0,0", Formatting.signed(-0.02, 1, locale))
        assertEquals("0,0", Formatting.signed(0.04, 1, locale))
        assertEquals("0", Formatting.signed(-0.4, 0, locale))
    }
}
