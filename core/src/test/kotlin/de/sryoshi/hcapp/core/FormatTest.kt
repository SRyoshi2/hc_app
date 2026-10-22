package de.sryoshi.hcapp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormatTest {
    private val euro = " €"

    @Test
    fun `Geld im deutschen Format`() {
        assertEquals("300,00$euro", Geld.anzeige("300.00"))
        assertEquals("1.234,50$euro", Geld.anzeige("1234.5"))
        assertEquals("0,00$euro", Geld.anzeige("0"))
        assertEquals("-25,10$euro", Geld.anzeige("-25.10"))
        assertEquals("1.000.000,00$euro", Geld.anzeige("1000000"))
    }

    @Test
    fun `Geld rundet auf zwei Stellen und meldet Ungueltiges`() {
        assertEquals("2,01$euro", Geld.anzeige("2.005"))
        assertEquals("–", Geld.anzeige(null))
        assertEquals("–", Geld.anzeige(""))
        assertEquals("–", Geld.anzeige("abc"))
    }

    @Test
    fun `Betrag eingeben`() {
        assertEquals("12.50", Geld.normalisieren("12,5"))
        assertEquals("12.50", Geld.normalisieren(" 12.50 "))
        assertEquals("300.00", Geld.normalisieren("300"))
        assertEquals("0.05", Geld.normalisieren("0,05"))
        assertEquals("99999999.99", Geld.normalisieren("99999999,99"))
    }

    @Test
    fun `ungueltige Betraege werden abgelehnt`() {
        for (eingabe in listOf("", " ", "0", "0,00", "-5", "abc", "1,234", "1.234,50", "12,5,1", "100000000", "1e3", "NaN", "5 €")) {
            assertNull(Geld.normalisieren(eingabe), "„$eingabe“ sollte abgelehnt werden")
        }
    }

    @Test
    fun `negativer Betrag`() {
        assertTrue(Geld.negativ("-0.01"))
        assertFalse(Geld.negativ("0.00"))
        assertFalse(Geld.negativ("5.00"))
        assertFalse(Geld.negativ(null))
        assertFalse(Geld.negativ("x"))
    }

    @Test
    fun `Datum im deutschen Format`() {
        assertEquals("01.10.2026", Datum.anzeige("2026-10-01"))
        assertEquals("", Datum.anzeige(null))
        assertEquals("", Datum.anzeige(" "))
        assertEquals("kaputt", Datum.anzeige("kaputt"))
    }
}
