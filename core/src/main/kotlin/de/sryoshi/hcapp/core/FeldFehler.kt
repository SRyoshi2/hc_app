package de.sryoshi.hcapp.core

/**
 * Macht die Feldfehler des Servers (`error.fields`, z. B. `ingredients[2].name`) lesbar:
 * „Zutat 3 – Name: Dieses Feld ist erforderlich.“
 */
object FeldFehler {
    private val FELDER = mapOf(
        "title" to "Titel", "description" to "Beschreibung", "servings" to "Portionen",
        "prep_minutes" to "Vorbereitungszeit", "cook_minutes" to "Kochzeit", "difficulty" to "Schwierigkeit",
        "source_url" to "Quelle", "video_url" to "Video", "kcal" to "Kalorien", "protein_g" to "Eiweiß",
        "fat_g" to "Fett", "carbs_g" to "Kohlenhydrate", "category_ids" to "Kategorien", "is_public" to "Sichtbarkeit",
        "ingredients" to "Zutaten", "steps" to "Schritte", "items" to "Artikel",
        "name" to "Name", "amount" to "Menge", "unit" to "Einheit", "note" to "Hinweis", "section" to "Abschnitt",
        "store" to "Geschäft", "shopping_category" to "Kategorie", "price" to "Preis",
        "text" to "Text", "heading" to "Überschrift", "image" to "Bild", "caption" to "Bildunterschrift",
        "target_list_key" to "Liste", "new_list_title" to "Name der neuen Liste",
        // OweMe
        "person_name" to "Name", "direction" to "Richtung", "due_date" to "Zurück bis", "date" to "Datum",
        "mode" to "Weg", "start" to "Erste Rate", "interval" to "Rhythmus", "rate" to "Betrag je Rate",
        "count" to "Anzahl der Raten", "end" to "Letzte Rate", "rates" to "Raten",
        // Health
        "birth_date" to "Geburtsdatum", "height_cm" to "Körpergröße", "sex" to "Geschlecht", "activity" to "Aktivitätsgrad",
        "target_weight_kg" to "Zielgewicht", "target_date" to "Ziel bis", "weight_kg" to "Gewicht",
        "kcal_per_serving" to "kcal je Portion", "month" to "Monat", "enabled" to "Health",
        "training_enabled" to "Training",
        "doctor" to "Arzt", "visit_date" to "Datum", "visit_time" to "Beginn", "visit_end_time" to "Ende",
        "duration_hours" to "Dauer", "notes" to "Notizen", "next_due_date" to "Nächster Termin",
        "next_due_amount" to "Nächster Termin in", "next_due_unit" to "Einheit des nächsten Termins",
        "follows_up_id" to "Folgetermin von", "last_date" to "Zuletzt", "file" to "Datei", "category_id" to "Kategorie",
        "value" to "Wert", "ref_from" to "Referenz von", "ref_to" to "Referenz bis",
        "start_time" to "Beginn", "sport" to "Sportart", "minutes" to "Minuten", "intensity" to "Intensität",
        "distance_km" to "Strecke", "weekly" to "Wochenziele", "units" to "Einheiten je Woche",
        "long_term" to "Langzeitziel", "goal_id" to "Ziel", "counts" to "Zählt", "week" to "Woche", "source" to "Quelle",
    )
    /** Beschriftungen, die in OweMe anders lauten als bei Rezepten (`amount`: Menge ↔ Betrag, `note`: Hinweis ↔ Notiz). */
    val OWEME: Map<String, String> = mapOf("title" to "Titel", "amount" to "Betrag", "note" to "Notiz")

    private val BEREICHE = mapOf("ingredients" to "Zutat", "steps" to "Schritt", "items" to "Artikel", "rates" to "Rate", "weekly" to "Wochenziel")
    private val LISTENFELD = Regex("""^(ingredients|steps|items|rates|weekly)\[(\d+)]\.?(.*)$""")

    /**
     * Eine Zeile je Meldung; Reihenfolge wie vom Server. [beschriftung] überschreibt Feldnamen für einen Bereich,
     * in dem sie anders heißen (z. B. [OWEME]).
     */
    fun meldungen(fields: Map<String, List<String>>, beschriftung: Map<String, String> = emptyMap()): List<String> = fields.flatMap { (schluessel, texte) ->
        fun name(feld: String) = beschriftung[feld] ?: FELDER[feld] ?: feld
        val treffer = LISTENFELD.matchEntire(schluessel)
        val kopf = if (treffer != null) {
            val (bereich, index, feld) = treffer.destructured
            val ort = "${BEREICHE.getValue(bereich)} ${index.toInt() + 1}"
            if (feld.isEmpty()) ort else "$ort – ${name(feld)}"
        } else if ('.' in schluessel) {
            schluessel.split('.').joinToString(" – ") { name(it) }
        } else {
            name(schluessel)
        }
        // „non_field“ sind Fehler, die zu keinem einzelnen Feld gehören: nur der Text.
        if (schluessel == "non_field") texte else texte.map { "$kopf: $it" }
    }
}
