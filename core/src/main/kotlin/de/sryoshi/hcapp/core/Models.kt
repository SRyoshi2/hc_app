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
