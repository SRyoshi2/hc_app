package de.sryoshi.hcapp.core

/**
 * Aufbereitung des HTML aus dem Rich-Text-Editor von HomeCenter (Beschreibung, Zubereitungsschritte).
 *
 * Der Server lässt dort nur eine Positivliste zu (Absatz, Zeilenumbruch, Auszeichnung, Überschriften, Listen,
 * Zitat, Link; `home/sanitize.py`). Dargestellt wird in der App mit `HtmlCompat`; was Android selbst nicht
 * kann, erledigt dieser Teil.
 */
object RichText {
    private val LISTEN_TAG = Regex("""<(/?)(ul|ol|li)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[a-zA-Z/!]""")

    /** Enthält der Text überhaupt HTML? Reiner Text würde beim Umwandeln seine Zeilenumbrüche verlieren. */
    fun istHtml(text: String): Boolean = TAG.containsMatchIn(text)

    /**
     * Setzt vor jeden Listenpunkt eine sichtbare Marke: `• ` in Aufzählungen, `1. `, `2. ` … in nummerierten
     * Listen (je Liste neu gezählt, auch verschachtelt). Android zeichnet Listenpunkte sonst nur als Zeichen,
     * das beim Umwandeln in formatierten Text verloren geht.
     */
    fun listenMarken(html: String): String {
        val offen = ArrayDeque<IntArray>() // je offene Liste ein Zähler; -1 = Aufzählung
        return LISTEN_TAG.replace(html) { m ->
            val schliessen = m.groupValues[1] == "/"
            when (m.groupValues[2].lowercase()) {
                "ol" -> { if (schliessen) offen.removeLastOrNull() else offen.addLast(intArrayOf(0)); m.value }
                "ul" -> { if (schliessen) offen.removeLastOrNull() else offen.addLast(intArrayOf(-1)); m.value }
                else -> if (schliessen) m.value else {
                    val zaehler = offen.lastOrNull()
                    val marke = if (zaehler == null || zaehler[0] < 0) "• " else "${++zaehler[0]}. "
                    "<li>$marke"
                }
            }
        }
    }

    private val ABSATZ = Regex("""\n\s*\n""")

    /**
     * Einfacher Text aus einem Eingabefeld → HTML für den Server: Leerzeile = neuer Absatz, einfacher Zeilenumbruch
     * = `<br>`, Sonderzeichen werden maskiert. Leer bleibt leer. Der Server filtert das Ergebnis ohnehin noch einmal.
     */
    fun klartextZuHtml(text: String): String {
        val sauber = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (sauber.isEmpty()) return ""
        return sauber.split(ABSATZ).joinToString("") { absatz ->
            val maskiert = absatz.trim().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            "<p>" + maskiert.replace("\n", "<br>") + "</p>"
        }
    }
}
