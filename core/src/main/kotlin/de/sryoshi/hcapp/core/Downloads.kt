package de.sryoshi.hcapp.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Download Center (API 0.93.1083): nur lesen und laden; gepflegt wird im Web ───

@Serializable
data class DownloadRef(val id: Long, val name: String)

/** Ein Download. Die Liste liefert die Felder bis `updatedAt`, das Detail zusätzlich Beschreibung und Prüfsumme. */
@Serializable
data class DownloadItem(
    val key: String,
    val title: String,
    val type: DownloadRef? = null,
    val category: DownloadRef? = null,
    val version: String = "",
    val filename: String = "",
    val size: Long? = null,
    @SerialName("size_text") val sizeText: String = "",
    val downloads: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    /** HTML aus dem Editor des Webs. */
    val description: String = "",
    @SerialName("description_text") val descriptionText: String = "",
    /** SHA-256 der Datei (Kleinbuchstaben); nur im Detail. */
    val sha256: String = "",
)

/** Typ oder Kategorie zur Auswahl, mit Anzahl der sichtbaren Downloads. */
@Serializable
data class DownloadChoice(val id: Long, val name: String, val count: Int = 0)

@Serializable
data class DownloadPage(
    val count: Int = 0,
    val results: List<DownloadItem> = emptyList(),
    val types: List<DownloadChoice> = emptyList(),
    val categories: List<DownloadChoice> = emptyList(),
)

/** Ergebnis eines Ladevorgangs: empfangene Bytes und deren SHA-256 (Kleinbuchstaben). */
data class DownloadResult(val bytes: Long, val sha256: String)
