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
    )
    private val BEREICHE = mapOf("ingredients" to "Zutat", "steps" to "Schritt", "items" to "Artikel")
    private val LISTENFELD = Regex("""^(ingredients|steps|items)\[(\d+)]\.?(.*)$""")

    /** Eine Zeile je Meldung; Reihenfolge wie vom Server. */
    fun meldungen(fields: Map<String, List<String>>): List<String> = fields.flatMap { (schluessel, texte) ->
        val treffer = LISTENFELD.matchEntire(schluessel)
        val kopf = if (treffer != null) {
            val (bereich, index, feld) = treffer.destructured
            val ort = "${BEREICHE.getValue(bereich)} ${index.toInt() + 1}"
            if (feld.isEmpty()) ort else "$ort – ${FELDER[feld] ?: feld}"
        } else {
            FELDER[schluessel] ?: schluessel
        }
        texte.map { "$kopf: $it" }
    }
}
