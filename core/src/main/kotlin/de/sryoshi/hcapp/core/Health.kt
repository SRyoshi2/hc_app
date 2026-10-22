package de.sryoshi.hcapp.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import kotlin.math.floor

// ── Health (API Phase 5): Profil, Gewicht, Kalorientracker, Mahlzeitvorlagen ─────────────────

@Serializable
data class HealthChoice(val value: String, val label: String)

@Serializable
data class HealthOptions(val sex: List<HealthChoice> = emptyList(), val activity: List<HealthChoice> = emptyList())

@Serializable
data class HealthMetrics(
    @SerialName("weight_kg") val weightKg: String? = null,
    val age: Int? = null,
    @SerialName("basal_metabolic_rate") val basalMetabolicRate: Int? = null,
    @SerialName("daily_need") val dailyNeed: Int? = null,
    val bmi: String? = null,
    @SerialName("bmi_category") val bmiCategory: String? = null,
    @SerialName("normal_weight_kg") val normalWeightKg: List<String?>? = null,
)

@Serializable
data class HealthGoal(
    @SerialName("weight_kg") val weightKg: String? = null,
    @SerialName("start_kg") val startKg: String? = null,
    /** `abnehmen`/`zunehmen` o. Ä., wie vom Server geliefert. */
    val direction: String? = null,
    @SerialName("distance_kg") val distanceKg: String? = null,
    val reached: Boolean = false,
    @SerialName("progress_percent") val progressPercent: Int? = null,
    val date: String? = null,
    @SerialName("days_left") val daysLeft: Int? = null,
    val overdue: Boolean = false,
)

/** `GET /health/profile/` – lesbar auch, solange Health für das Konto noch ausgeschaltet ist. */
@Serializable
data class HealthProfile(
    val enabled: Boolean = false,
    @SerialName("is_complete") val isComplete: Boolean = false,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("height_cm") val heightCm: Int? = null,
    /** `m` oder `w` (biologisches Geschlecht, Grundlage des Grundumsatzes). */
    val sex: String = "",
    /** Aktivitätsgrad als Text, z. B. `"1.375"`. */
    val activity: String? = null,
    @SerialName("activity_checked") val activityChecked: Boolean = true,
    @SerialName("training_enabled") val trainingEnabled: Boolean = false,
    @SerialName("default_meal_plan_id") val defaultMealPlanId: Long? = null,
    @SerialName("target_weight_kg") val targetWeightKg: String? = null,
    @SerialName("target_date") val targetDate: String? = null,
    val metrics: HealthMetrics = HealthMetrics(),
    val goal: HealthGoal? = null,
    val options: HealthOptions = HealthOptions(),
)

@Serializable
data class WeightEntry(val id: Long, val date: String, @SerialName("weight_kg") val weightKg: String)

@Serializable
data class CalorieEntry(
    val id: Long,
    val date: String,
    val title: String,
    @SerialName("kcal_per_serving") val kcalPerServing: Int,
    /** Portionen als Text, z. B. `"1.50"`. */
    val servings: String = "1",
    @SerialName("total_kcal") val totalKcal: Int = 0,
    /** `manual`, `mealplan` o. Ä. */
    val source: String = "manual",
    @SerialName("protein_g") val proteinG: String? = null,
    @SerialName("fat_g") val fatG: String? = null,
    @SerialName("carbs_g") val carbsG: String? = null,
    @SerialName("nutrients_estimated") val nutrientsEstimated: Boolean = false,
    @SerialName("has_nutrients") val hasNutrients: Boolean = false,
)

/** `GET /health/calories/<JJJJ-MM-TT>/`. Lesen übernimmt beim ersten Mal die Mahlzeiten des Speiseplans als Einträge. */
@Serializable
data class CalorieDay(
    val date: String,
    val entries: List<CalorieEntry> = emptyList(),
    @SerialName("total_kcal") val totalKcal: Int = 0,
    /** Bedarf des Tages inklusive Training; `null`, solange das Profil keinen Bedarf ergibt. */
    @SerialName("daily_need") val dailyNeed: Int? = null,
    @SerialName("base_need") val baseNeed: Int? = null,
    @SerialName("training_kcal") val trainingKcal: Int = 0,
    @SerialName("remaining_kcal") val remainingKcal: Int? = null,
    @SerialName("excess_kcal") val excessKcal: Int? = null,
    val percent: Int = 0,
    /** Ampel: `under`, `ok`, `over` oder `null` (ohne Bedarf oder Einträge). */
    val status: String? = null,
    @SerialName("has_meal_plan") val hasMealPlan: Boolean = false,
    @SerialName("seeded_count") val seededCount: Int = 0,
)

@Serializable
data class CalorieMonthDay(
    val date: String,
    @SerialName("total_kcal") val totalKcal: Int = 0,
    @SerialName("training_kcal") val trainingKcal: Int = 0,
    val status: String? = null,
)

@Serializable
data class CalorieMonth(
    /** `JJJJ-MM` */
    val month: String,
    @SerialName("daily_need") val dailyNeed: Int? = null,
    @SerialName("average_kcal") val averageKcal: Int? = null,
    @SerialName("average_need") val averageNeed: Int? = null,
    @SerialName("tracked_days") val trackedDays: Int = 0,
    @SerialName("training_kcal") val trainingKcal: Int = 0,
    @SerialName("previous_month") val previousMonth: String = "",
    @SerialName("next_month") val nextMonth: String = "",
    val days: List<CalorieMonthDay> = emptyList(),
)

@Serializable
data class MealTemplate(
    val id: Long,
    val name: String,
    @SerialName("kcal_per_serving") val kcalPerServing: Int,
    val servings: String = "1",
    @SerialName("total_kcal") val totalKcal: Int = 0,
    @SerialName("protein_g") val proteinG: String? = null,
    @SerialName("fat_g") val fatG: String? = null,
    @SerialName("carbs_g") val carbsG: String? = null,
    @SerialName("nutrients_estimated") val nutrientsEstimated: Boolean = false,
    @SerialName("has_nutrients") val hasNutrients: Boolean = false,
)

/**
 * Ein Vorschlag aus dem Bundeslebensmittelschlüssel (BLS 4.0, Max Rubner-Institut): Werte je 100 g.
 * Fehlt ein Nährwert im Schlüssel, ist er `null` (nicht 0): nur die Angabe darf der Rechner übernehmen.
 */
@Serializable
data class FoodSuggestion(
    val code: String = "",
    val name: String,
    @SerialName("kcal_100g") val kcal100g: Int,
    @SerialName("protein_100g") val protein100g: Double? = null,
    @SerialName("fat_100g") val fat100g: Double? = null,
    @SerialName("carbs_100g") val carbs100g: Double? = null,
)

@Serializable
internal data class FoodSuggestions(val suggestions: List<FoodSuggestion> = emptyList())

/** Änderungen am Health-Profil; gesendet wird nur, was gesetzt wurde (`null` löscht, wo das erlaubt ist). */
class HealthProfilePatch {
    internal val felder = linkedMapOf<String, JsonElement>()
    private fun setze(name: String, wert: JsonElement) = apply { felder[name] = wert }
    private fun text(wert: String?) = if (wert == null) JsonNull else JsonPrimitive(wert)

    fun birthDate(iso: String?) = setze("birth_date", text(iso))
    fun heightCm(cm: Int?) = setze("height_cm", if (cm == null) JsonNull else JsonPrimitive(cm))
    /** `m` oder `w`. */
    fun sex(code: String) = setze("sex", JsonPrimitive(code))
    /** Wert aus [HealthOptions.activity], z. B. `"1.375"`. */
    fun activity(wert: String) = setze("activity", JsonPrimitive(wert))
    fun trainingEnabled(an: Boolean) = setze("training_enabled", JsonPrimitive(an))
    fun targetWeightKg(kg: String?) = setze("target_weight_kg", text(kg))
    fun targetDate(iso: String?) = setze("target_date", text(iso))
    internal fun alsJson() = JsonObject(felder)
}

/** Mahlzeit erfassen oder ändern. Nährwerte nur senden, wenn sie gesetzt oder geändert wurden (Handeingabe ist keine KI-Schätzung mehr). */
class CalorieEntryPatch {
    internal val felder = linkedMapOf<String, JsonElement>()
    private fun setze(name: String, wert: JsonElement) = apply { felder[name] = wert }
    private fun text(wert: String?) = if (wert == null) JsonNull else JsonPrimitive(wert)

    fun title(text: String) = setze("title", JsonPrimitive(text))
    fun kcalPerServing(kcal: Int) = setze("kcal_per_serving", JsonPrimitive(kcal))
    /** Portionen mit Punkt, z. B. `"1.5"`; größer 0, höchstens 99. */
    fun servings(wert: String) = setze("servings", JsonPrimitive(wert))
    fun proteinG(gramm: String?) = setze("protein_g", text(gramm))
    fun fatG(gramm: String?) = setze("fat_g", text(gramm))
    fun carbsG(gramm: String?) = setze("carbs_g", text(gramm))
    internal fun alsJson() = JsonObject(felder)
}

class MealTemplatePatch {
    internal val felder = linkedMapOf<String, JsonElement>()
    private fun setze(name: String, wert: JsonElement) = apply { felder[name] = wert }
    private fun text(wert: String?) = if (wert == null) JsonNull else JsonPrimitive(wert)

    fun name(text: String) = setze("name", JsonPrimitive(text))
    fun kcalPerServing(kcal: Int) = setze("kcal_per_serving", JsonPrimitive(kcal))
    fun servings(wert: String) = setze("servings", JsonPrimitive(wert))
    fun proteinG(gramm: String?) = setze("protein_g", text(gramm))
    fun fatG(gramm: String?) = setze("fat_g", text(gramm))
    fun carbsG(gramm: String?) = setze("carbs_g", text(gramm))
    internal fun alsJson() = JsonObject(felder)
}

// ── Portionsrechner ─────────────────────────────────────────────────────

/**
 * Der Rechner aus dem Kalorientracker des Webs: Angaben je 100 g plus das tatsächlich Gegessene in Gramm ergeben die
 * Werte der Portion. Rundung wie dort: kcal auf ganze Zahlen, Nährwerte auf eine Nachkommastelle (jeweils kaufmännisch).
 */
object Portionsrechner {
    /** Das Ergebnis der Nährwerte. */
    sealed interface Naehrwerte {
        /** Keine Angabe je 100 g oder keine Gramm: nichts zu übernehmen. */
        data object Leer : Naehrwerte

        /** Eiweiß und Fett gehören zusammen (sonst zählt der Eintrag nicht als bewertet): eines fehlt. */
        data object Unvollstaendig : Naehrwerte

        /** [kohlenhydrate] ist `null`, wenn dazu keine Angabe vorliegt. */
        data class Voll(val eiweiss: Double, val fett: Double, val kohlenhydrate: Double?) : Naehrwerte
    }

    /** Wie JavaScripts `Math.round`: .5 rundet auf (bei positiven Werten). */
    private fun runden(x: Double): Double = floor(x + 0.5)

    private fun eineStelle(x: Double): Double = runden(x * 10) / 10

    /** kcal der Portion; `null`, wenn die Angabe je 100 g fehlt oder negativ ist oder die Gramm nicht größer 0 sind. */
    fun kcal(je100: Double?, gramm: Double?): Int? {
        if (je100 == null || gramm == null || !(je100 >= 0) || !(gramm > 0)) return null
        return runden(je100 * gramm / 100).toInt()
    }

    fun naehrwerte(eiweiss100: Double?, fett100: Double?, kh100: Double?, gramm: Double?): Naehrwerte {
        if (eiweiss100 == null && fett100 == null && kh100 == null) return Naehrwerte.Leer
        if (eiweiss100 == null || fett100 == null) return Naehrwerte.Unvollstaendig
        if (gramm == null || !(gramm > 0)) return Naehrwerte.Leer
        return Naehrwerte.Voll(
            eiweiss = eineStelle(eiweiss100 * gramm / 100),
            fett = eineStelle(fett100 * gramm / 100),
            kohlenhydrate = kh100?.let { eineStelle(it * gramm / 100) },
        )
    }

    /** Eingabe → Zahl ab 0; leer, ungültig oder negativ ist `null` (und `null` ist nicht 0: „keine Angabe“). Komma oder Punkt. */
    fun zahl(eingabe: String?): Double? {
        val d = eingabe?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return null
        return if (d.isFinite() && d >= 0) d else null
    }

    /** Zahl → Text für den Server ohne überflüssige Nullen, mit Punkt: `12.0` → `"12"`, `12.5` → `"12.5"`. */
    fun text(wert: Double): String = BigDecimal.valueOf(wert).stripTrailingZeros().toPlainString()
}
