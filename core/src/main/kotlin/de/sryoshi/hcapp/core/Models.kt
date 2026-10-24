package de.sryoshi.hcapp.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/v1/meta/` – ohne Anmeldung. */
@Serializable
data class Meta(
    @SerialName("product_name") val productName: String,
    /** `family` oder `office`. */
    val mode: String,
    @SerialName("api_version") val apiVersion: Int,
    @SerialName("min_client_version") val minClientVersion: Int,
    @SerialName("server_version") val serverVersion: String,
)

@Serializable
data class User(
    val id: Long,
    val username: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    @SerialName("expires_at") val expiresAt: String,
    val user: User,
)

/** Schlüssel in [Me.apps] */
object AppKey {
    const val HEALTH = "health"
    const val SHOPPING = "einkaufsliste"
    const val RECIPES = "rezepte"
    const val OWEME = "oweme"
    const val DOWNLOADS = "downloads"
}

/** `GET /api/v1/me/` */
@Serializable
data class Me(
    val id: Long,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("abo_1") val abo1: Boolean = false,
    /** Je App: auf der Instanz eingeschaltet **und** für das Konto freigegeben. */
    val apps: Map<String, Boolean> = emptyMap(),
    @SerialName("health_enabled") val healthEnabled: Boolean = false,
) {
    fun appAktiv(schluessel: String): Boolean = apps[schluessel] == true
}

@Serializable
data class Device(
    val id: Long,
    @SerialName("device_name") val deviceName: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    val current: Boolean = false,
)

@Serializable
data class Page<T>(val count: Int, val results: List<T>)

@Serializable
data class ShoppingList(
    val key: String,
    val title: String,
    @SerialName("is_owner") val isOwner: Boolean,
    val owner: String,
    val shared: Boolean,
    @SerialName("open_count") val openCount: Int,
    @SerialName("done_count") val doneCount: Int,
    @SerialName("total_count") val totalCount: Int,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ShoppingItem(
    val id: Long,
    @SerialName("list_key") val listKey: String,
    val name: String,
    val amount: String = "",
    val category: String = "",
    val store: String = "",
    /** Dezimalzahl als Text, z. B. `"2.50"`. */
    val price: String? = null,
    val done: Boolean = false,
    @SerialName("done_at") val doneAt: String? = null,
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class Suggestions(
    val categories: List<String> = emptyList(),
    val stores: List<String> = emptyList(),
)

/** `GET /shopping-lists/<key>/items/` – eine Seite Artikel mit Titel und Vorschlägen. */
@Serializable
data class ItemPage(
    val count: Int,
    val results: List<ShoppingItem>,
    val title: String = "",
    val suggestions: Suggestions = Suggestions(),
)

/** Eingabe für neue Artikel. */
data class ItemInput(
    val name: String,
    val amount: String = "",
    val category: String = "",
    val store: String = "",
    val price: String? = null,
)

// ── Rezepte (API Phase 2) ───────────────────────────────────────────────

@Serializable
data class RecipeCategory(
    val id: Long,
    val name: String,
    @SerialName("recipe_count") val recipeCount: Int = 0,
)

/** Rezept in Listen (`/recipes/`, `/recipes/public/`). */
@Serializable
data class RecipeSummary(
    val key: String,
    val title: String,
    val description: String = "",
    val servings: Int = 1,
    @SerialName("prep_minutes") val prepMinutes: Int? = null,
    @SerialName("cook_minutes") val cookMinutes: Int? = null,
    val difficulty: String = "",
    @SerialName("difficulty_label") val difficultyLabel: String = "",
    val categories: List<RecipeCategory> = emptyList(),
    /** Absolute Adresse, `null` ohne Bild. */
    @SerialName("image_url") val imageUrl: String? = null,
    val kcal: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
    val owner: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
) {
    /** Vorbereiten plus Kochen in Minuten, `null` wenn keins von beiden angegeben ist. */
    val gesamtMinuten: Int?
        get() = ((prepMinutes ?: 0) + (cookMinutes ?: 0)).takeIf { it > 0 }
}

@Serializable
data class Ingredient(
    val id: Long,
    val name: String,
    /** Dezimalzahl als Text, z. B. `"500.00"`; `null` bei „etwas Salz“. */
    val amount: String? = null,
    val unit: String = "",
    @SerialName("unit_label") val unitLabel: String = "",
    val note: String = "",
    @SerialName("shopping_category") val shoppingCategory: String = "",
    val store: String = "",
    val price: String? = null,
)

@Serializable
data class IngredientSection(
    /** Leer für Zutaten ohne Abschnitt. */
    val section: String = "",
    val ingredients: List<Ingredient> = emptyList(),
)

@Serializable
data class RecipeStep(val order: Int = 0, val heading: String = "", val text: String)

@Serializable
data class RecipeImage(val id: Long, val url: String? = null, val caption: String = "")

@Serializable
data class Nutrition(
    val source: String = "",
    /** `kcal`, `protein_g`, `fat_g`, `carbs_g` je Portion. */
    @SerialName("per_serving") val perServing: Map<String, String?> = emptyMap(),
    val total: Map<String, String?> = emptyMap(),
)

/** `GET /recipes/<key>/` */
@Serializable
data class RecipeDetail(
    val key: String,
    val title: String,
    val description: String = "",
    val servings: Int = 1,
    @SerialName("prep_minutes") val prepMinutes: Int? = null,
    @SerialName("cook_minutes") val cookMinutes: Int? = null,
    val difficulty: String = "",
    @SerialName("difficulty_label") val difficultyLabel: String = "",
    val categories: List<RecipeCategory> = emptyList(),
    @SerialName("image_url") val imageUrl: String? = null,
    val kcal: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
    val owner: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("ingredient_sections") val ingredientSections: List<IngredientSection> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val images: List<RecipeImage> = emptyList(),
    val nutrition: Nutrition = Nutrition(),
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("video_file_url") val videoFileUrl: String? = null,
    @SerialName("source_url") val sourceUrl: String = "",
    @SerialName("source_owner_name") val sourceOwnerName: String = "",
    @SerialName("is_owner") val isOwner: Boolean = false,
    @SerialName("can_edit") val canEdit: Boolean = false,
) {
    val gesamtMinuten: Int?
        get() = ((prepMinutes ?: 0) + (cookMinutes ?: 0)).takeIf { it > 0 }

    /** Titelbild, sonst das erste Galeriebild. */
    val bildUrl: String?
        get() = imageUrl ?: images.firstNotNullOfOrNull { it.url }
}

/** Eine auf die Portionenzahl hochgerechnete Zutat (`/recipes/<key>/shopping-preview/`). */
@Serializable
data class PreviewItem(
    val name: String,
    val note: String = "",
    val section: String = "",
    /** Fertig für die Einkaufsliste, z. B. `"500 g"` oder `"2"`. */
    val amount: String = "",
    @SerialName("amount_value") val amountValue: String? = null,
    val unit: String = "",
    @SerialName("unit_label") val unitLabel: String = "",
    val category: String = "",
    val store: String = "",
    val price: String? = null,
) {
    fun alsEingabe() = ItemInput(name = name, amount = amount, category = category, store = store, price = price)
}

@Serializable
data class ShoppingPreview(
    /** Gewünschte Portionen als Text, z. B. `"4"` oder `"1.5"`. */
    val servings: String,
    @SerialName("base_servings") val baseServings: Int,
    val items: List<PreviewItem> = emptyList(),
)

@Serializable
data class ToShoppingResult(
    @SerialName("list_key") val listKey: String,
    @SerialName("created_count") val createdCount: Int,
)
