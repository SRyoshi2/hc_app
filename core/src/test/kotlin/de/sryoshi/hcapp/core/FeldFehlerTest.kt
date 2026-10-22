package de.sryoshi.hcapp.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FeldFehlerTest {
    @Test
    fun `einfache Felder`() {
        assertEquals(listOf("Portionen: Muss eine Zahl sein."), FeldFehler.meldungen(mapOf("servings" to listOf("Muss eine Zahl sein."))))
    }

    @Test
    fun `Listenstellen werden ab 1 gezaehlt`() {
        val m = FeldFehler.meldungen(mapOf(
            "ingredients[0].amount" to listOf("Muss eine Zahl ab 0 sein."),
            "steps[2].text" to listOf("Dieses Feld ist erforderlich."),
            "items[0]" to listOf("Muss ein Objekt sein."),
        ))
        assertEquals(listOf("Zutat 1 – Menge: Muss eine Zahl ab 0 sein.", "Schritt 3 – Text: Dieses Feld ist erforderlich.", "Artikel 1: Muss ein Objekt sein."), m)
    }

    @Test
    fun `mehrere Meldungen je Feld und unbekannte Felder`() {
        assertEquals(listOf("Titel: a", "Titel: b", "unbekannt: c"),
            FeldFehler.meldungen(mapOf("title" to listOf("a", "b"), "unbekannt" to listOf("c"))))
    }

    @Test
    fun `ohne Fehler keine Zeilen`() {
        assertEquals(emptyList(), FeldFehler.meldungen(emptyMap()))
    }
}
