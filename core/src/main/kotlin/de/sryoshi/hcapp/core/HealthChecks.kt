package de.sryoshi.hcapp.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ── Health (API Phase 6, Teil 1): Arztbesuche, Dokumente, Kommentare, Impfbuch, Laborwerte ───

/** Gemeinsame Bausteine der Änderungsobjekte: gesendet wird nur, was gesetzt wurde (`null` löscht, wo erlaubt). */
abstract class FeldPatch {
    internal val felder = linkedMapOf<String, JsonElement>()
    protected fun setze(name: String, wert: JsonElement) { felder[name] = wert }
    protected fun text(name: String, wert: String?) = setze(name, if (wert == null) JsonNull else JsonPrimitive(wert))
    protected fun zahl(name: String, wert: Number?) = setze(name, if (wert == null) JsonNull else JsonPrimitive(wert))
    internal fun alsJson() = JsonObject(felder)
}

/** Einheiten für „nächster Termin in …“. */
object DueUnit {
    const val DAYS = "days"
    const val WEEKS = "weeks"
    const val MONTHS = "months"
    const val YEARS = "years"
    val alle = listOf(DAYS to "Tage", WEEKS to "Wochen", MONTHS to "Monate", YEARS to "Jahre")
    fun label(wert: String?) = alle.firstOrNull { it.first == wert }?.second.orEmpty()
}

@Serializable
data class HealthCheck(
    val id: Long,
    val title: String,
    val doctor: String = "",
    @SerialName("visit_date") val visitDate: String? = null,
    @SerialName("visit_time") val visitTime: String? = null,
    @SerialName("visit_end_time") val visitEndTime: String? = null,
    val notes: String = "",
    @SerialName("is_planned") val isPlanned: Boolean = false,
    @SerialName("followed_up_by_id") val followedUpById: Long? = null,
    @SerialName("follows_up_id") val followsUpId: Long? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("is_due") val isDue: Boolean = false,
    @SerialName("days_until_due") val daysUntilDue: Int? = null,
    @SerialName("days_overdue") val daysOverdue: Int? = null,
    @SerialName("due_is_estimate") val dueIsEstimate: Boolean = false,
    @SerialName("next_due_date") val nextDueDate: String? = null,
    @SerialName("next_due_amount") val nextDueAmount: Int? = null,
    @SerialName("next_due_unit") val nextDueUnit: String? = null,
    /** Nur im Detail. */
    val labs: List<LabMeasurement> = emptyList(),
    val documents: List<HealthDocument> = emptyList(),
    val comments: List<HealthComment> = emptyList(),
)

@Serializable
data class DocumentCategory(val id: Long, val name: String)

@Serializable
data class HealthDocument(
    val id: Long,
    /** Schlüssel für den Download. */
    val key: String,
    val title: String = "",
    val filename: String = "",
    val category: DocumentCategory? = null,
    @SerialName("uploaded_at") val uploadedAt: String = "",
    val size: Long? = null,
)

@Serializable
data class HealthComment(val id: Long, val text: String, @SerialName("created_at") val createdAt: String = "")

@Serializable
data class Vaccination(
    val id: Long,
    val name: String,
    @SerialName("last_date") val lastDate: String? = null,
    val notes: String = "",
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("is_due") val isDue: Boolean = false,
    @SerialName("days_until_due") val daysUntilDue: Int? = null,
    @SerialName("days_overdue") val daysOverdue: Int? = null,
    @SerialName("due_is_estimate") val dueIsEstimate: Boolean = false,
    @SerialName("next_due_date") val nextDueDate: String? = null,
    @SerialName("next_due_amount") val nextDueAmount: Int? = null,
    @SerialName("next_due_unit") val nextDueUnit: String? = null,
)

/** Arztbesuch anlegen oder ändern. */
class HealthCheckPatch : FeldPatch() {
    fun title(v: String) = apply { text("title", v) }
    fun doctor(v: String) = apply { text("doctor", v) }
    fun visitDate(iso: String?) = apply { text("visit_date", iso) }
    /** `HH:MM`. */
    fun visitTime(v: String?) = apply { text("visit_time", v) }
    fun visitEndTime(v: String?) = apply { text("visit_end_time", v) }
    /** Dauer in Stunden statt Ende, z. B. `"1.5"`. */
    fun durationHours(v: String?) = apply { text("duration_hours", v) }
    fun notes(v: String) = apply { text("notes", v) }
    fun nextDueDate(iso: String?) = apply { text("next_due_date", iso) }
    fun nextDueAmount(v: Int?) = apply { zahl("next_due_amount", v) }
    fun nextDueUnit(v: String?) = apply { text("next_due_unit", v) }
    /** Termin, auf den dieser Besuch folgt. */
    fun followsUpId(id: Long?) = apply { zahl("follows_up_id", id) }
}

class VaccinationPatch : FeldPatch() {
    fun name(v: String) = apply { text("name", v) }
    fun lastDate(iso: String?) = apply { text("last_date", iso) }
    fun notes(v: String) = apply { text("notes", v) }
    fun nextDueDate(iso: String?) = apply { text("next_due_date", iso) }
    fun nextDueAmount(v: Int?) = apply { zahl("next_due_amount", v) }
    fun nextDueUnit(v: String?) = apply { text("next_due_unit", v) }
}

// ── Laborwerte ──────────────────────────────────────────────────────────

/** Eine Messung; Zahlen kommen als Text ohne überflüssige Nullen. `classification` ist eine Einordnung gegen den Bereich der Messung. */
@Serializable
data class LabMeasurement(
    val id: Long,
    val name: String,
    val date: String? = null,
    val value: String? = null,
    val unit: String = "",
    @SerialName("ref_from") val refFrom: String? = null,
    @SerialName("ref_to") val refTo: String? = null,
    val note: String = "",
    val classification: String = "",
    @SerialName("classification_label") val classificationLabel: String = "",
    val flagged: Boolean = false,
    @SerialName("check_id") val checkId: Long? = null,
)

@Serializable
data class LabGroup(
    val key: String,
    val name: String,
    val count: Int = 0,
    val latest: LabMeasurement,
    val previous: LabMeasurement? = null,
    val difference: String? = null,
    val flagged: Boolean = false,
)

@Serializable
data class LabOverview(
    val count: Int = 0,
    val results: List<LabGroup> = emptyList(),
    @SerialName("flagged_count") val flaggedCount: Int = 0,
    val names: List<String> = emptyList(),
)

@Serializable
data class LabTrend(
    val key: String,
    val name: String,
    val unit: String = "",
    val count: Int = 0,
    val points: List<LabMeasurement> = emptyList(),
)

@Serializable
data class LabAnalysisText(
    val text: String,
    val model: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("measurements_at_time") val measurementsAtTime: Int = 0,
    val outdated: Boolean = false,
)

/** Was an die KI gehen würde, und die Prüfsumme genau dieses Texts (kommt bei der Anfrage mit zurück). */
@Serializable
data class LabRequestPreview(val text: String = "", val checksum: String = "")

@Serializable
data class LabAnalysis(
    @SerialName("ai_available") val aiAvailable: Boolean = false,
    val analysis: LabAnalysisText? = null,
    @SerialName("request_preview") val requestPreview: LabRequestPreview? = null,
)

class LabPatch : FeldPatch() {
    fun name(v: String) = apply { text("name", v) }
    fun date(iso: String?) = apply { text("date", iso) }
    /** Zahl mit Punkt, z. B. `"5.5"`. */
    fun value(v: String) = apply { text("value", v) }
    fun unit(v: String) = apply { text("unit", v) }
    fun refFrom(v: String?) = apply { text("ref_from", v) }
    fun refTo(v: String?) = apply { text("ref_to", v) }
    fun note(v: String) = apply { text("note", v) }
}
