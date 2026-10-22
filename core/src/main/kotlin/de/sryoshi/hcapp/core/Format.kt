package de.sryoshi.hcapp.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Geldbeträge: Anzeige im deutschen Format, Eingabe in die Form des Servers bringen. */
object Geld {
    private val EINGABE = Regex("""^\d{1,8}([.]\d{1,2})?$""")

    /** `"1234.5"` → `"1.234,50 €"` (geschütztes Leerzeichen vor dem Euro); `null` und Ungültiges → `"–"`. */
    fun anzeige(text: String?): String {
        val zahl = text?.trim()?.toBigDecimalOrNull() ?: return "–"
        return String.format(Locale.GERMANY, "%,.2f", zahl.setScale(2, RoundingMode.HALF_UP)) + " €"
    }

    /**
     * Eingabe → Betrag für den Server (`"12,5"` → `"12.50"`). `null`, wenn es kein gültiger Betrag über null mit
     * höchstens zwei Nachkommastellen ist. Tausendertrennzeichen werden nicht angenommen.
     */
    fun normalisieren(eingabe: String): String? {
        val t = eingabe.trim().replace(',', '.')
        if (!EINGABE.matches(t)) return null
        val zahl = BigDecimal(t)
        return if (zahl.signum() > 0) zahl.setScale(2).toPlainString() else null
    }

    /** Ist der Betrag (Text vom Server) kleiner als null? */
    fun negativ(text: String?): Boolean = (text?.trim()?.toBigDecimalOrNull()?.signum() ?: 0) < 0
}

object Datum {
    private val ANZEIGE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** `"2026-10-01"` → `"01.10.2026"`; `null` → `""`, Ungültiges bleibt unverändert. */
    fun anzeige(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching { LocalDate.parse(iso).format(ANZEIGE) }.getOrDefault(iso)
    }
}
