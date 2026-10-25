package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.Formatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.NumberFormat
import java.time.Duration
import java.util.Locale

/** Compact durations: "7h 32m" where the letters read, the locale's own units in Arabic. */
class DurationFormatTest {

    private val nightAndMinutes: Duration = Duration.ofHours(7).plusMinutes(32)

    @Test
    fun `latin scripts keep the compact letters`() {
        assertEquals("7h 32m", Formatting.duration(nightAndMinutes, Locale.GERMANY))
        assertEquals("45m", Formatting.duration(Duration.ofMinutes(45), Locale.US))
        assertEquals("30s", Formatting.duration(Duration.ofSeconds(30), Locale.US))
        assertEquals("30h", Formatting.hours(30, Locale.US))
    }

    @Test
    fun `arabic writes its own units and the locale's digits`() {
        val arabic = Locale.forLanguageTag("ar")
        val text = Formatting.duration(nightAndMinutes, arabic)
        assertFalse(text, text.contains('h') || text.contains('m'))
        assertTrue(text, text.contains("س") && text.contains("د"))
        // The digits the rest of the app writes numbers in, whichever the platform picks.
        val digits = NumberFormat.getIntegerInstance(arabic)
        assertTrue(text, text.startsWith(digits.format(7)) && text.contains(digits.format(32)))
    }
}
