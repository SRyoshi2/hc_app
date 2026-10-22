package de.sryoshi.hcapp.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ── OweMe (API Phase 4) ─────────────────────────────────────────────────

/** Richtung eines Kontos: `out` = ich habe verliehen („ich bekomme“), `in` = ich habe geliehen („ich schulde“). */
object OweMeDirection {
    const val LENT = "out"
    const val BORROWED = "in"
    val alle: List<Pair<String, String>> = listOf(LENT to "Ich habe verliehen", BORROWED to "Ich habe geliehen")
}

/** Art einer Bewegung: `given` = Geld gegeben, `returned` = Rückzahlung. */
object OweMeKind {
    const val GIVEN = "given"
    const val RETURNED = "returned"
}

/** Wege zum Rückzahlungsplan (`mode`). */
object OweMePlanMode {
    const val RATE = "rate"
    const val COUNT = "anzahl"
    const val END = "ende"
    const val FREE = "frei"
}

object OweMePlanInterval {
    val alle: List<Pair<String, String>> = listOf("monthly" to "monatlich", "biweekly" to "alle zwei Wochen", "weekly" to "wöchentlich")
}

/** Ein Leihkonto: eine Person, eine Richtung. Geldbeträge sind Text mit zwei Nachkommastellen, z. B. `"300.00"`. */
@Serializable
data class OweMeAccount(
    val key: String,
    @SerialName("person_name") val personName: String,
    val direction: String,
    @SerialName("direction_label") val directionLabel: String = "",
    val note: String = "",
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("contact_id") val contactId: Long? = null,
    @SerialName("person_user_id") val personUserId: Long? = null,
    @SerialName("is_owner") val isOwner: Boolean = false,
    val shared: Boolean = false,
    @SerialName("total_given") val totalGiven: String = "0.00",
    @SerialName("total_returned") val totalReturned: String = "0.00",
    /** Offener Betrag; negativ, wenn mehr zurückkam als gegeben. */
    val balance: String = "0.00",
    @SerialName("is_settled") val isSettled: Boolean = false,
    @SerialName("is_overpaid") val isOverpaid: Boolean = false,
    @SerialName("is_due") val isDue: Boolean = false,
    @SerialName("days_overdue") val daysOverdue: Int? = null,
    @SerialName("created_at") val createdAt: String = "",
) {
    val verliehen: Boolean get() = direction == OweMeDirection.LENT
}

@Serializable
data class OweMeEntry(
    val id: Long,
    val kind: String,
    @SerialName("kind_label") val kindLabel: String = "",
    val date: String,
    val title: String = "",
    val amount: String,
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("created_at") val createdAt: String = "",
    /** Kontostand nach dieser Bewegung (nur im Kontoauszug). */
    @SerialName("balance_after") val balanceAfter: String? = null,
)

@Serializable
data class OweMePlanRate(
    @SerialName("due_date") val dueDate: String,
    val amount: String,
    /** `null` bei angehängten Terminen (Zusatzraten). */
    @SerialName("agreed_amount") val agreedAmount: String? = null,
    val covered: String = "0.00",
    /** `bezahlt`, `teilweise`, `ueberfaellig` oder `offen`. */
    val status: String = "offen",
    @SerialName("is_extra") val isExtra: Boolean = false,
)

@Serializable
data class OweMePlan(
    val interval: String,
    @SerialName("interval_label") val intervalLabel: String = "",
    @SerialName("rate_amount") val rateAmount: String = "0.00",
    val completed: Boolean = false,
    val count: Int = 0,
    @SerialName("paid_count") val paidCount: Int = 0,
    @SerialName("next_due_date") val nextDueDate: String? = null,
    @SerialName("overdue_count") val overdueCount: Int = 0,
    val rates: List<OweMePlanRate> = emptyList(),
)

/** Der Teil des Kontoauszugs, der über das Konto hinausgeht. */
@Serializable
internal data class OweMeDetailTeil(val entries: List<OweMeEntry> = emptyList(), val plan: OweMePlan? = null)

/** `GET /oweme/accounts/<key>/`: das Konto, seine Bewegungen (alt → neu) und der Plan, falls es einen gibt. */
data class OweMeAccountDetail(val account: OweMeAccount, val entries: List<OweMeEntry>, val plan: OweMePlan?)

@Serializable
data class OweMeOverview(
    val receivable: List<OweMeAccount> = emptyList(),
    val payable: List<OweMeAccount> = emptyList(),
    val settled: List<OweMeAccount> = emptyList(),
    @SerialName("total_receivable") val totalReceivable: String = "0.00",
    @SerialName("total_payable") val totalPayable: String = "0.00",
    /** Saldo: was ich bekomme minus was ich schulde. */
    val net: String = "0.00",
    /** Schlüssel der fälligen Konten. */
    val due: List<String> = emptyList(),
)

/** Antwort beim Erfassen und Ändern einer Bewegung. */
@Serializable
data class OweMeEntryResult(val entry: OweMeEntry, val balance: String)

@Serializable
data class OweMeBalance(val balance: String)

/** Änderungen an einem Konto; gesendet wird nur, was gesetzt wurde (`dueDate(null)` löscht die Frist). */
class OweMeAccountPatch {
    internal val felder = linkedMapOf<String, JsonElement>()
    private fun setze(name: String, wert: JsonElement) = apply { felder[name] = wert }

    fun direction(code: String) = setze("direction", JsonPrimitive(code))
    fun personName(name: String) = setze("person_name", JsonPrimitive(name))
    /** ISO-Datum `JJJJ-MM-TT` oder `null`. */
    fun dueDate(iso: String?) = setze("due_date", if (iso == null) JsonNull else JsonPrimitive(iso))
    fun note(text: String) = setze("note", JsonPrimitive(text))
    internal fun alsJson() = JsonObject(felder)
}

/** Bewegung erfassen oder ändern; gesendet wird nur, was gesetzt wurde. */
class OweMeEntryPatch {
    internal val felder = linkedMapOf<String, JsonElement>()
    private fun setze(name: String, wert: JsonElement) = apply { felder[name] = wert }

    fun date(iso: String) = setze("date", JsonPrimitive(iso))
    fun title(text: String) = setze("title", JsonPrimitive(text))
    /** Betrag mit Punkt, siehe [Geld.normalisieren]. */
    fun amount(betrag: String) = setze("amount", JsonPrimitive(betrag))
    internal fun alsJson() = JsonObject(felder)
}

data class OweMeRateInput(val date: String, val amount: String)

/**
 * Rückzahlungsplan. Je [mode] zählt genau eine Angabe: [rate] (Ratenhöhe), [count] (Anzahl), [end] (Enddatum) oder
 * [rates] (jede Rate von Hand). [start] ist der erste Termin (ISO-Datum).
 */
data class OweMePlanInput(
    val mode: String,
    val start: String,
    val interval: String = "monthly",
    val rate: String? = null,
    val count: Int? = null,
    val end: String? = null,
    val rates: List<OweMeRateInput> = emptyList(),
) {
    internal fun alsJson(): JsonObject {
        val felder = linkedMapOf<String, JsonElement>(
            "mode" to JsonPrimitive(mode), "start" to JsonPrimitive(start), "interval" to JsonPrimitive(interval),
        )
        when (mode) {
            OweMePlanMode.RATE -> rate?.let { felder["rate"] = JsonPrimitive(it) }
            OweMePlanMode.COUNT -> count?.let { felder["count"] = JsonPrimitive(it) }
            OweMePlanMode.END -> end?.let { felder["end"] = JsonPrimitive(it) }
            OweMePlanMode.FREE -> felder["rates"] = JsonArray(rates.map {
                JsonObject(mapOf("date" to JsonPrimitive(it.date), "amount" to JsonPrimitive(it.amount)))
            })
        }
        return JsonObject(felder)
    }
}
