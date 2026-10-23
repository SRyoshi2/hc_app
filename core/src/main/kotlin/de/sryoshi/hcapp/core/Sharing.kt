package de.sryoshi.hcapp.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Teilen und Benachrichtigungen (API Phase 7) ─────────────────────────

/** Ein Benutzer der Instanz, mit dem sich etwas teilen lässt. */
@Serializable
data class ShareUser(val id: Long, @SerialName("display_name") val displayName: String)

@Serializable
data class SharingState(val users: List<ShareUser> = emptyList())

@Serializable
data class RecipeShareResult(val offered: Int = 0, @SerialName("already_offered") val alreadyOffered: Int = 0)

/** Ein Rezept, das jemand angeboten hat. */
@Serializable
data class RecipeOffer(
    val id: Long,
    val sender: ShareUser,
    @SerialName("created_at") val createdAt: String = "",
    val recipe: RecipeSummary,
)

/** Wohin eine Meldung zeigt: `kind` ist `shopping_list`, `oweme_account` oder `recipe`. */
@Serializable
data class NotificationTarget(val kind: String, val key: String)

@Serializable
data class AppNotification(
    val id: Long,
    val type: String = "",
    @SerialName("type_label") val typeLabel: String = "",
    val text: String = "",
    val url: String = "",
    val target: NotificationTarget? = null,
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class NotificationPage(
    val count: Int = 0,
    val results: List<AppNotification> = emptyList(),
    @SerialName("unread_count") val unreadCount: Int = 0,
)

@Serializable
internal data class UnreadCount(@SerialName("unread_count") val unreadCount: Int = 0)

@Serializable
internal data class MarkedCount(val marked: Int = 0)
