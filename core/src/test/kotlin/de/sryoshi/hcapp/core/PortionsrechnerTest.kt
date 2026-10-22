package de.sryoshi.hcapp.core

import de.sryoshi.hcapp.core.Portionsrechner.Naehrwerte
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Dieselben Regeln wie der Rechner im Kalorientracker des Webs (`calorie_day.html`). */
class PortionsrechnerTest {
    @Test
    fun `kcal der Portion`() {
        assertEquals(209, Portionsrechner.kcal(348.0, 60.0))   // 208,8
        assertEquals(500, Portionsrechner.kcal(250.0, 200.0))
        assertEquals(0, Portionsrechner.kcal(0.0, 50.0))
        assertEquals(2, Portionsrechner.kcal(1.0, 150.0))      // 1,5 rundet auf, wie Math.round
        assertEquals(1, Portionsrechner.kcal(1.0, 50.0))       // 0,5 rundet auf
    }

    @Test
    fun `ungueltige Eingaben ergeben keine kcal`() {
        assertNull(Portionsrechner.kcal(null, 50.0))
        assertNull(Portionsrechner.kcal(100.0, null))
        assertNull(Portionsrechner.kcal(-1.0, 50.0))
        assertNull(Portionsrechner.kcal(100.0, 0.0))
        assertNull(Portionsrechner.kcal(100.0, -5.0))
        assertNull(Portionsrechner.kcal(Double.NaN, 50.0))
    }

    @Test
    fun `Naehrwerte der Portion mit einer Nachkommastelle`() {
        val n = Portionsrechner.naehrwerte(13.2, 6.7, 53.3, 60.0)
        assertEquals(Naehrwerte.Voll(eiweiss = 7.9, fett = 4.0, kohlenhydrate = 32.0), n)
    }

    @Test
    fun `Kohlenhydrate sind freiwillig`() {
        assertEquals(Naehrwerte.Voll(eiweiss = 10.0, fett = 5.0, kohlenhydrate = null), Portionsrechner.naehrwerte(20.0, 10.0, null, 50.0))
    }

    @Test
    fun `null ist nicht null Gramm`() {
        // Ein Lebensmittel ohne Fett hat 0 g Fett; eines ohne Angabe nichts. Nur die Angabe wird übernommen.
        assertEquals(Naehrwerte.Voll(eiweiss = 5.0, fett = 0.0, kohlenhydrate = 10.0), Portionsrechner.naehrwerte(10.0, 0.0, 20.0, 50.0))
    }

    @Test
    fun `Eiweiss und Fett gehoeren zusammen`() {
        assertEquals(Naehrwerte.Unvollstaendig, Portionsrechner.naehrwerte(10.0, null, 20.0, 50.0))
        assertEquals(Naehrwerte.Unvollstaendig, Portionsrechner.naehrwerte(null, 10.0, null, 50.0))
        assertEquals(Naehrwerte.Unvollstaendig, Portionsrechner.naehrwerte(null, null, 20.0, 50.0))
    }

    @Test
    fun `ohne Angaben oder Gramm bleibt es leer`() {
        assertEquals(Naehrwerte.Leer, Portionsrechner.naehrwerte(null, null, null, 50.0))
        assertEquals(Naehrwerte.Leer, Portionsrechner.naehrwerte(10.0, 5.0, 3.0, null))
        assertEquals(Naehrwerte.Leer, Portionsrechner.naehrwerte(10.0, 5.0, 3.0, 0.0))
    }

    @Test
    fun `Eingabe lesen`() {
        assertEquals(12.5, Portionsrechner.zahl("12,5"))
        assertEquals(12.5, Portionsrechner.zahl(" 12.5 "))
        assertEquals(0.0, Portionsrechner.zahl("0"))
        assertNull(Portionsrechner.zahl(""))
        assertNull(Portionsrechner.zahl(null))
        assertNull(Portionsrechner.zahl("-3"))
        assertNull(Portionsrechner.zahl("abc"))
        assertNull(Portionsrechner.zahl("NaN"))
        assertNull(Portionsrechner.zahl("Infinity"))
    }

    @Test
    fun `Zahl als Text fuer den Server`() {
        assertEquals("12", Portionsrechner.text(12.0))
        assertEquals("12.5", Portionsrechner.text(12.5))
        assertEquals("0.1", Portionsrechner.text(0.1))
        assertEquals("100", Portionsrechner.text(100.0))
    }
}
