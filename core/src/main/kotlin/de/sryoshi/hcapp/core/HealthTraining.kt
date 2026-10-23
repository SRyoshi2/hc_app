package de.sryoshi.hcapp.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ── Health (API Phase 6, Teil 2): Training, Vorlagen, Ziele, Rückblick, Ernährungsampel ───

@Serializable
data class TrainingSession(
    val id: Long,
    val date: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    val sport: String = "",
    @SerialName("sport_label") val sportLabel: String = "",
    val minutes: Int = 0,
    val intensity: String = "",
    @SerialName("intensity_label") val intensityLabel: String = "",
    @SerialName("distance_km") val distanceKm: String? = null,
    val pace: String? = null,
    val note: String = "",
    val kcal: Int = 0,
)

@Serializable
data class TrainingTemplate(
    val id: Long,
    val name: String,
    val sport: String = "",
    @SerialName("sport_label") val sportLabel: String = "",
    val minutes: Int = 0,
    val intensity: String = "",
    @SerialName("intensity_label") val intensityLabel: String = "",
    @SerialName("distance_km") val distanceKm: String? = null,
    val pace: String? = null,
)

/** Intensitäten des Servers; die Beschriftung liefert der Server je Einheit mit. */
object TrainingIntensity {
    val alle = listOf("locker" to "Locker", "mittel" to "Mittel", "intensiv" to "Intensiv")
}

/** Sportarten des Servers (auch in [GoalOptions.sports] zu lesen); hier als Vorgabe für die Auswahl. */
object TrainingSports {
    val alle = listOf(
        "laufen" to "Laufen", "radfahren" to "Radfahren", "schwimmen" to "Schwimmen", "kraft" to "Kraft / Fitness",
        "wandern" to "Wandern", "spaziergang" to "Spaziergang", "sonstiges" to "Sonstiges",
    )
}

class TrainingPatch : FeldPatch() {
    fun date(iso: String) = apply { text("date", iso) }
    fun startTime(v: String?) = apply { text("start_time", v) }
    fun sport(v: String) = apply { text("sport", v) }
    fun minutes(v: Int?) = apply { zahl("minutes", v) }
    fun intensity(v: String) = apply { text("intensity", v) }
    fun distanceKm(v: String?) = apply { text("distance_km", v) }
    fun note(v: String) = apply { text("note", v) }
}

class TrainingTemplatePatch : FeldPatch() {
    fun name(v: String) = apply { text("name", v) }
    fun sport(v: String) = apply { text("sport", v) }
    fun minutes(v: Int?) = apply { zahl("minutes", v) }
    fun intensity(v: String) = apply { text("intensity", v) }
    fun distanceKm(v: String?) = apply { text("distance_km", v) }
}

@Serializable
data class WeeklyGoal(
    /** Leer = alle Sportarten. */
    val sport: String = "",
    @SerialName("sport_label") val sportLabel: String = "",
    val units: Int? = null,
    val minutes: Int? = null,
)

@Serializable
data class GoalState(
    val current: String? = null,
    val target: String? = null,
    val remaining: String? = null,
    val percent: Int? = null,
    val reached: Boolean = false,
    val expired: Boolean = false,
    @SerialName("weeks_left") val weeksLeft: Int? = null,
    @SerialName("per_week") val perWeek: String? = null,
)

@Serializable
data class LongTermGoal(
    @SerialName("goal_id") val goalId: Long? = null,
    @SerialName("goal_title") val goalTitle: String? = null,
    val counts: String = "",
    val text: String = "",
    val state: GoalState? = null,
)

@Serializable
data class Sport(val value: String, val label: String)

@Serializable
data class GoalChoice(val id: Long, val title: String)

@Serializable
data class GoalOptions(
    val sports: List<Sport> = emptyList(),
    val goals: List<GoalChoice> = emptyList(),
    val counts: List<Sport> = emptyList(),
)

@Serializable
data class TrainingGoals(
    val weekly: List<WeeklyGoal> = emptyList(),
    @SerialName("long_term") val longTerm: LongTermGoal = LongTermGoal(),
    val options: GoalOptions = GoalOptions(),
)

/** `PUT health/training-goals/`: was nicht gesetzt ist, bleibt unverändert. */
class TrainingGoalsPatch {
    private var wochen: List<WeeklyGoal>? = null
    private val langzeit = linkedMapOf<String, JsonElement>()

    /** Ersetzt alle Wochenziele. */
    fun weekly(ziele: List<WeeklyGoal>) = apply { wochen = ziele }
    fun goalId(id: Long?) = apply { langzeit["goal_id"] = if (id == null) JsonNull else JsonPrimitive(id) }
    fun counts(v: String) = apply { langzeit["counts"] = JsonPrimitive(v) }
    fun text(v: String) = apply { langzeit["text"] = JsonPrimitive(v) }

    internal fun alsJson(): JsonObject {
        val m = linkedMapOf<String, JsonElement>()
        wochen?.let { liste ->
            m["weekly"] = JsonArray(liste.map {
                JsonObject(mapOf(
                    "sport" to JsonPrimitive(it.sport),
                    "units" to (it.units?.let(::JsonPrimitive) ?: JsonNull),
                    "minutes" to (it.minutes?.let(::JsonPrimitive) ?: JsonNull),
                ))
            })
        }
        if (langzeit.isNotEmpty()) m["long_term"] = JsonObject(langzeit)
        return JsonObject(m)
    }
}

@Serializable
data class TrainingDay(
    val date: String,
    val minutes: Int = 0,
    val kcal: Int = 0,
    val trainings: List<TrainingSession> = emptyList(),
)

@Serializable
data class WeekTotals(
    val count: Int = 0,
    val minutes: Int = 0,
    val kcal: Int = 0,
    val km: String? = null,
    @SerialName("active_days") val activeDays: Int = 0,
)

@Serializable
data class WeekGoalProgress(
    val sport: String = "",
    @SerialName("sport_label") val sportLabel: String = "",
    val units: Int? = null,
    val minutes: Int? = null,
    @SerialName("done_units") val doneUnits: Int = 0,
    @SerialName("done_minutes") val doneMinutes: Int = 0,
    val percent: Int = 0,
    val reached: Boolean = false,
)

@Serializable
data class TrainingLoad(
    val level: String = "",
    val sentence: String = "",
    @SerialName("this_week") val thisWeek: Int = 0,
    @SerialName("weeks_with_data") val weeksWithData: Int = 0,
)

@Serializable
data class SportSummary(
    val sport: String = "",
    @SerialName("sport_label") val sportLabel: String = "",
    val count: Int = 0,
    val minutes: Int = 0,
    val kcal: Int = 0,
    val km: String? = null,
)

@Serializable
data class MonthSummary(
    val from: String? = null,
    val to: String? = null,
    val count: Int = 0,
    val minutes: Int = 0,
    val kcal: Int = 0,
    val sports: List<SportSummary> = emptyList(),
)

@Serializable
data class AiText(val text: String, val model: String = "", @SerialName("created_at") val createdAt: String = "")

@Serializable
data class TrainingReview(
    @SerialName("week_start") val weekStart: String,
    @SerialName("week_end") val weekEnd: String,
    val days: List<TrainingDay> = emptyList(),
    val totals: WeekTotals = WeekTotals(),
    val goals: List<WeekGoalProgress> = emptyList(),
    val load: TrainingLoad = TrainingLoad(),
    val month: MonthSummary = MonthSummary(),
    @SerialName("long_term") val longTerm: GoalState? = null,
    val review: AiText? = null,
    /** Der Text, der für den KI-Rückblick hinausginge; nur mit KI-Freigabe vorhanden. */
    @SerialName("ai_text") val aiText: String? = null,
)

// ── Ernährungsampel ─────────────────────────────────────────────────────

@Serializable
data class TrafficCriterion(val name: String, val level: String = "", val value: String = "", val sentence: String = "")

@Serializable
data class TrafficDay(
    val date: String,
    /** `gruen`/`gelb`/`rot` o. Ä. – wie vom Server geliefert; die Beschriftung steht in [label]. */
    val level: String = "",
    val label: String = "",
    val kcal: Int? = null,
    val need: Int? = null,
    val entries: Int = 0,
    @SerialName("estimated_percent") val estimatedPercent: Int? = null,
    @SerialName("estimate_note") val estimateNote: String? = null,
    val criteria: List<TrafficCriterion> = emptyList(),
)

@Serializable
data class AdviceSuggestion(val title: String? = null, val reason: String? = null, @SerialName("recipe_key") val recipeKey: String? = null)

@Serializable
data class TrafficAdvice(
    val text: String,
    val model: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val suggestions: List<AdviceSuggestion> = emptyList(),
)

@Serializable
data class TrafficLight(
    @SerialName("week_start") val weekStart: String,
    @SerialName("week_end") val weekEnd: String,
    val source: String = "tracker",
    val level: String = "",
    val label: String = "",
    val days: List<TrafficDay> = emptyList(),
    val summary: List<TrafficCriterion> = emptyList(),
    @SerialName("days_with_data") val daysWithData: Int = 0,
    @SerialName("kcal_average") val kcalAverage: Int? = null,
    @SerialName("has_meal_plan") val hasMealPlan: Boolean = false,
    val advice: TrafficAdvice? = null,
    /** Der Text, der für die KI-Einschätzung hinausginge; nur mit KI-Freigabe vorhanden. */
    @SerialName("ai_text") val aiText: String? = null,
)
