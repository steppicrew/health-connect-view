package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.export.Csv
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class CsvTest {

    @Test
    fun `plain fields are left alone and lines end in CRLF`() {
        assertEquals("a,b,3\r\n", Csv.line(listOf("a", "b", "3")))
    }

    @Test
    fun `commas, quotes and line breaks are quoted and quotes doubled`() {
        assertEquals("\"Stärke deinen Rücken, erweitert\"", Csv.escape("Stärke deinen Rücken, erweitert"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
        assertEquals("\"two\nlines\"", Csv.escape("two\nlines"))
    }

    @Test
    fun `numbers use a dot, no grouping and no exponent`() {
        assertEquals("8.42", Csv.number(8.42))
        assertEquals("10161", Csv.number(10161.0))
        assertEquals("0.00001", Csv.number(0.00001))
        assertEquals("1686", Csv.number(1686.0))
    }

    @Test
    fun `a missing number is an empty field, not zero`() {
        assertEquals("", Csv.number(Double.NaN))
    }

    @Test
    fun `times carry their offset`() {
        assertEquals(
            "2026-09-25T23:29:46+02:00",
            Csv.time(Instant.parse("2026-09-25T21:29:46Z"), ZoneOffset.ofHours(2)),
        )
    }
}
