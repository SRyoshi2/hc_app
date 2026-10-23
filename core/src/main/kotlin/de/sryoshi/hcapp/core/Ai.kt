package de.sryoshi.hcapp.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

// ── KI-Funktionen (API Phase 7c) ────────────────────────────────────────
// Jede Anfrage kostet beim Anbieter. Der Server verlangt `confirmed: true` und die Freigabe `abo_ai` (403 `ai_required`);
// die App zeigt vorher den Text, der hinausgeht. Fehler des Anbieters kommen als 502 `ai_failed` mit Grund.

/** Zahl oder Text des Servers als Text (die KI liefert mal `200`, mal `"200"`); `null` bleibt `null`. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal object ZahlAlsText : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ZahlAlsText", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/** Ergebnis von „Nährwerte schätzen“: `skipped` sind Mahlzeiten, die die KI nicht zuordnen konnte (die Lücke bleibt). */
@Serializable
data class NutrientEstimate(
    val filled: Int = 0,
    val skipped: Int = 0,
    val entries: List<CalorieEntry> = emptyList(),
)

@Serializable
data class GeneratedIngredient(
    val name: String = "",
    @Serializable(with = ZahlAlsText::class) val amount: String? = null,
    val unit: String = "",
)

@Serializable
data class GeneratedStep(val heading: String = "", val text: String = "")

/** Rezeptvorschlag der KI; gespeichert wird erst, wenn der Nutzer ihn übernimmt. */
@Serializable
data class GeneratedRecipe(
    val title: String = "",
    val description: String = "",
    @Serializable(with = ZahlAlsText::class) val servings: String? = null,
    val ingredients: List<GeneratedIngredient> = emptyList(),
    val steps: List<GeneratedStep> = emptyList(),
)

@Serializable
internal data class GeneratedRecipeResponse(val recipe: GeneratedRecipe)

/** Geschätzte Nährwerte je Portion. */
@Serializable
data class NutritionEstimate(
    @Serializable(with = ZahlAlsText::class) val kcal: String? = null,
    @Serializable(with = ZahlAlsText::class) @SerialName("protein_g") val proteinG: String? = null,
    @Serializable(with = ZahlAlsText::class) @SerialName("fat_g") val fatG: String? = null,
    @Serializable(with = ZahlAlsText::class) @SerialName("carbs_g") val carbsG: String? = null,
)

@Serializable
internal data class NutritionEstimateResponse(val nutrition: NutritionEstimate)

@Serializable
internal data class TrainingReviewResponse(val review: AiText)

@Serializable
internal data class TrafficAdviceResponse(val advice: TrafficAdvice)
