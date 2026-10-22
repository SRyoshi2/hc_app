package de.sryoshi.hcapp.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Einheiten, die der Server für Zutaten kennt (`kitchen.models.UNIT_CHOICES`): Code und Anzeigename. */
object RecipeUnits {
    val alle: List<Pair<String, String>> = listOf(
        "" to "–", "g" to "g", "kg" to "kg", "ml" to "ml", "l" to "l", "stk" to "Stück", "el" to "EL",
        "tl" to "TL", "prise" to "Prise", "pck" to "Packung", "dose" to "Dose", "bund" to "Bund",
    )

    fun label(code: String): String = alle.firstOrNull { it.first == code }?.second ?: code
}

/** Schwierigkeitsgrade (`kitchen.models.DIFFICULTY_CHOICES`). */
object RecipeDifficulty {
    val alle: List<Pair<String, String>> = listOf("" to "–", "easy" to "einfach", "medium" to "mittel", "hard" to "aufwendig")
}

/** Eine Zutat zum Speichern. Mit [id] wird die vorhandene Zutat geändert, ohne wird eine neue angelegt. */
data class IngredientInput(
    val id: Long? = null,
    val name: String,
    /** Dezimalzahl mit Punkt, z. B. `"1.5"`; `null` oder leer bei „etwas Salz“. */
    val amount: String? = null,
    val unit: String = "",
    val note: String = "",
    val section: String = "",
    val store: String = "",
    val shoppingCategory: String = "",
    val price: String? = null,
) {
    companion object {
        /** Übernimmt eine vorhandene Zutat unverändert, damit beim Speichern nichts verloren geht. */
        fun von(z: Ingredient, abschnitt: String) = IngredientInput(
            id = z.id, name = z.name, amount = z.amount, unit = z.unit, note = z.note, section = abschnitt,
            store = z.store, shoppingCategory = z.shoppingCategory, price = z.price,
        )
    }
}

/** Ein Zubereitungsschritt zum Speichern. [text] ist HTML (siehe [RichText.klartextZuHtml]). */
data class StepInput(val heading: String = "", val text: String)

/**
 * Änderungen an einem Rezept. Gesendet wird **nur, was gesetzt wurde**: ein Feld, das nie angefasst wird,
 * bleibt auf dem Server unverändert. `null` bei den Werten, die leer sein dürfen, löscht sie.
 *
 * Zutaten und Schritte gelten immer als ganze Liste: was fehlt, wird gelöscht.
 */
class RecipePatch {
    internal val felder = linkedMapOf<String, JsonElement>()

    private fun setze(name: String, wert: JsonElement) = apply { felder[name] = wert }
    private fun text(wert: String?) = if (wert == null) JsonNull else JsonPrimitive(wert)
    private fun zahl(wert: Int?) = if (wert == null) JsonNull else JsonPrimitive(wert)

    fun title(wert: String) = setze("title", JsonPrimitive(wert))
    /** HTML aus [RichText.klartextZuHtml] oder unverändert das ursprüngliche HTML. */
    fun description(html: String) = setze("description", JsonPrimitive(html))
    fun servings(wert: Int) = setze("servings", JsonPrimitive(wert))
    fun prepMinutes(wert: Int?) = setze("prep_minutes", zahl(wert))
    fun cookMinutes(wert: Int?) = setze("cook_minutes", zahl(wert))
    /** `""`, `easy`, `medium` oder `hard` (siehe [RecipeDifficulty]). */
    fun difficulty(code: String) = setze("difficulty", JsonPrimitive(code))
    fun kcal(wert: String?) = setze("kcal", text(wert))
    fun proteinG(wert: String?) = setze("protein_g", text(wert))
    fun fatG(wert: String?) = setze("fat_g", text(wert))
    fun carbsG(wert: String?) = setze("carbs_g", text(wert))
    fun isPublic(wert: Boolean) = setze("is_public", JsonPrimitive(wert))
    fun categoryIds(ids: List<Long>) = setze("category_ids", JsonArray(ids.map { JsonPrimitive(it) }))

    fun ingredients(liste: List<IngredientInput>) = setze("ingredients", JsonArray(liste.map { z ->
        JsonObject(buildMap<String, JsonElement> {
            z.id?.let { put("id", JsonPrimitive(it)) }
            put("name", JsonPrimitive(z.name))
            put("amount", text(z.amount?.takeIf { it.isNotBlank() }))
            put("unit", JsonPrimitive(z.unit))
            put("note", JsonPrimitive(z.note))
            put("section", JsonPrimitive(z.section))
            put("store", JsonPrimitive(z.store))
            put("shopping_category", JsonPrimitive(z.shoppingCategory))
            put("price", text(z.price?.takeIf { it.isNotBlank() }))
        })
    }))

    fun steps(liste: List<StepInput>) = setze("steps", JsonArray(liste.map { s ->
        JsonObject(mapOf("heading" to JsonPrimitive(s.heading), "text" to JsonPrimitive(s.text)))
    }))

    internal fun alsJson() = JsonObject(felder)
}
