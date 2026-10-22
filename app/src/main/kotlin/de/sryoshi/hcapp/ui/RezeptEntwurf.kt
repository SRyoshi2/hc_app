package de.sryoshi.hcapp.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.sryoshi.hcapp.core.ApiException
import de.sryoshi.hcapp.core.IngredientInput
import de.sryoshi.hcapp.core.RecipeDetail
import de.sryoshi.hcapp.core.RecipePatch
import de.sryoshi.hcapp.core.RichText
import de.sryoshi.hcapp.core.StepInput

/** Eine Zutat im Editor. Geschäft, Einkaufskategorie und Preis sind hier nicht bearbeitbar, bleiben aber erhalten. */
class ZutatEntwurf(
    val id: Long?,
    name: String = "", menge: String = "", einheit: String = "", hinweis: String = "", abschnitt: String = "",
    val geschaeft: String = "", val kategorie: String = "", val preis: String? = null,
) {
    var name by mutableStateOf(name)
    var menge by mutableStateOf(menge)
    var einheit by mutableStateOf(einheit)
    var hinweis by mutableStateOf(hinweis)
    var abschnitt by mutableStateOf(abschnitt)
}

/**
 * Ein Schritt im Editor. Der Text wird als einfacher Text bearbeitet. Bleibt er unverändert, geht beim Speichern
 * das ursprüngliche HTML zurück zum Server, damit Fett, Listen usw. aus dem Web nicht verloren gehen.
 */
class SchrittEntwurf(
    ueberschrift: String = "", text: String = "",
    private val originalHtml: String? = null, private val originalKlartext: String? = null,
) {
    var ueberschrift by mutableStateOf(ueberschrift)
    var text by mutableStateOf(text)

    fun alsHtml(): String =
        if (originalHtml != null && text.trim() == originalKlartext?.trim()) originalHtml else RichText.klartextZuHtml(text)
}

/** `"500.00"` → `"500"`, `"1.50"` → `"1.5"`; ohne Dezimalpunkt unverändert. */
internal fun zahlKurz(text: String?): String {
    if (text.isNullOrBlank()) return ""
    return if ('.' in text) text.trimEnd('0').trimEnd('.') else text
}

/**
 * Arbeitsstand des Rezept-Editors ([key] `null` = neues Rezept). [klartext] wandelt HTML in einfachen Text um.
 */
class RezeptEntwurf(val key: String?, val original: RecipeDetail?, klartext: (String) -> String) {
    var titel by mutableStateOf(original?.title.orEmpty())
    private val beschreibungOriginal = original?.description?.let(klartext).orEmpty()
    var beschreibung by mutableStateOf(beschreibungOriginal)
    var portionen by mutableStateOf((original?.servings ?: 2).toString())
    var vorbereitung by mutableStateOf(original?.prepMinutes?.toString().orEmpty())
    var kochen by mutableStateOf(original?.cookMinutes?.toString().orEmpty())
    var schwierigkeit by mutableStateOf(original?.difficulty.orEmpty())
    var oeffentlich by mutableStateOf(original?.isPublic ?: false)
    val kategorien = mutableStateListOf<Long>().apply { original?.categories?.forEach { add(it.id) } }

    private val kcalOriginal = zahlKurz(original?.nutrition?.perServing?.get("kcal"))
    private val eiweissOriginal = zahlKurz(original?.nutrition?.perServing?.get("protein_g"))
    private val fettOriginal = zahlKurz(original?.nutrition?.perServing?.get("fat_g"))
    private val kohlenhydrateOriginal = zahlKurz(original?.nutrition?.perServing?.get("carbs_g"))
    var kcal by mutableStateOf(kcalOriginal)
    var eiweiss by mutableStateOf(eiweissOriginal)
    var fett by mutableStateOf(fettOriginal)
    var kohlenhydrate by mutableStateOf(kohlenhydrateOriginal)

    val zutaten = mutableStateListOf<ZutatEntwurf>()
    val schritte = mutableStateListOf<SchrittEntwurf>()

    /** Neu gewähltes Titelbild; `titelbildEntfernen` löscht das vorhandene. */
    var titelbild by mutableStateOf<Uri?>(null)
    var titelbildEntfernen by mutableStateOf(false)

    init {
        original?.ingredientSections?.forEach { abschnitt ->
            abschnitt.ingredients.forEach { z ->
                zutaten.add(ZutatEntwurf(z.id, z.name, zahlKurz(z.amount), z.unit, z.note, abschnitt.section, z.store, z.shoppingCategory, z.price))
            }
        }
        original?.steps?.sortedBy { it.order }?.forEach { s ->
            val k = klartext(s.text)
            schritte.add(SchrittEntwurf(s.heading, k, originalHtml = s.text, originalKlartext = k))
        }
        if (zutaten.isEmpty()) zutaten.add(ZutatEntwurf(null))
        if (schritte.isEmpty()) schritte.add(SchrittEntwurf())
    }

    /**
     * Prüft die Eingaben und baut die Änderung. Fehler kommen als [ApiException] `validation_error` mit denselben
     * Feldnamen wie vom Server, damit sie gleich angezeigt werden.
     */
    fun zuPatch(): RecipePatch {
        val fehler = linkedMapOf<String, List<String>>()
        fun melden(feld: String, text: String) { fehler[feld] = listOf(text) }

        val titelText = titel.trim()
        if (titelText.isEmpty()) melden("title", "Dieses Feld ist erforderlich.")
        val portionenZahl = portionen.trim().toIntOrNull()
        if (portionenZahl == null || portionenZahl < 1) melden("servings", "Muss eine ganze Zahl ab 1 sein.")

        fun minuten(text: String, feld: String): Int? {
            val t = text.trim()
            if (t.isEmpty()) return null
            val z = t.toIntOrNull()
            if (z == null || z < 0) { melden(feld, "Muss eine ganze Zahl ab 0 sein."); return null }
            return z
        }
        /** Dezimalzahl mit Komma oder Punkt → Text mit Punkt; leer → `null`. */
        fun dezimal(text: String, feld: String): String? {
            val t = text.trim().replace(',', '.')
            if (t.isEmpty()) return null
            val d = t.toDoubleOrNull()
            if (d == null || !d.isFinite() || d < 0) { melden(feld, "Muss eine Zahl ab 0 sein."); return null }
            return t
        }

        val vor = minuten(vorbereitung, "prep_minutes")
        val koch = minuten(kochen, "cook_minutes")

        val zutatenListe = zutaten.mapIndexedNotNull { i, z ->
            if (z.name.isBlank()) return@mapIndexedNotNull null
            IngredientInput(
                id = z.id, name = z.name.trim(), amount = dezimal(z.menge, "ingredients[$i].amount"), unit = z.einheit,
                note = z.hinweis.trim(), section = z.abschnitt.trim(), store = z.geschaeft, shoppingCategory = z.kategorie, price = z.preis,
            )
        }
        val schritteListe = schritte.filter { it.text.isNotBlank() }.map { StepInput(it.ueberschrift.trim(), it.alsHtml()) }

        val kcalWert = dezimal(kcal, "kcal")
        val eiweissWert = dezimal(eiweiss, "protein_g")
        val fettWert = dezimal(fett, "fat_g")
        val kohlenhydrateWert = dezimal(kohlenhydrate, "carbs_g")

        if (fehler.isNotEmpty()) throw ApiException("validation_error", "Die Eingaben sind nicht gültig.", 400, fehler)

        val neu = key == null
        return RecipePatch().apply {
            title(titelText)
            servings(portionenZahl!!)
            prepMinutes(vor)
            cookMinutes(koch)
            difficulty(schwierigkeit)
            isPublic(oeffentlich)
            categoryIds(kategorien.toList())
            // Texte und Werte nur senden, wenn sie sich geändert haben: so bleiben Formatierung und Nährwertquelle erhalten.
            if (neu || beschreibung.trim() != beschreibungOriginal.trim()) description(RichText.klartextZuHtml(beschreibung))
            if (neu || kcal.trim() != kcalOriginal) kcal(kcalWert)
            if (neu || eiweiss.trim() != eiweissOriginal) proteinG(eiweissWert)
            if (neu || fett.trim() != fettOriginal) fatG(fettWert)
            if (neu || kohlenhydrate.trim() != kohlenhydrateOriginal) carbsG(kohlenhydrateWert)
            ingredients(zutatenListe)
            steps(schritteListe)
        }
    }
}
