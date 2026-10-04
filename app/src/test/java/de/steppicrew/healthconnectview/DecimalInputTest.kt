package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.ui.components.parseDecimalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecimalInputTest {

    @Test
    fun `a point and a comma both mark the decimals`() {
        assertEquals(7.5, parseDecimalInput("7.5")!!, 0.0)
        assertEquals(7.5, parseDecimalInput(" 7,5 ")!!, 0.0)
    }

    @Test
    fun `digits typed on an Arabic keyboard parse`() {
        assertEquals(8000.0, parseDecimalInput("٨٠٠٠")!!, 0.0)
        assertEquals(7.5, parseDecimalInput("٧٫٥")!!, 0.0)
    }

    @Test
    fun `digits typed on a Devanagari keyboard parse`() {
        assertEquals(120.0, parseDecimalInput("१२०")!!, 0.0)
    }

    @Test
    fun `text that is not a number is rejected`() {
        assertNull(parseDecimalInput(""))
        assertNull(parseDecimalInput("abc"))
        assertNull(parseDecimalInput("1.2.3"))
    }
}
