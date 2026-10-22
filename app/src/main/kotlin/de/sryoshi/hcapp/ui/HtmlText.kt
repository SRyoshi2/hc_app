package de.sryoshi.hcapp.ui

import android.graphics.Typeface
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat
import de.sryoshi.hcapp.core.RichText

/**
 * Wandelt das bereinigte HTML des Rich-Text-Editors in formatierten Text: fett, kursiv, unterstrichen,
 * durchgestrichen, Überschriften (fett), Listen (mit Marke), Zeilenumbrüche und Links (hervorgehoben).
 * Die Textfarbe aus dem Editor wird bewusst nicht übernommen: sie wäre im Dunkelmodus oft unlesbar.
 * Text ohne HTML bleibt unverändert, damit seine Zeilenumbrüche erhalten bleiben.
 */
fun htmlZuText(html: String, linkFarbe: Color): AnnotatedString {
    if (!RichText.istHtml(html)) return AnnotatedString(html.trim())

    val spanned = HtmlCompat.fromHtml(RichText.listenMarken(html), HtmlCompat.FROM_HTML_MODE_COMPACT)
    val text = spanned.toString().trim()
    // Nach dem Kürzen von Anfang und Ende verschieben sich die Positionen der Auszeichnungen.
    val versatz = spanned.toString().indexOf(text).coerceAtLeast(0)

    return buildAnnotatedString {
        append(text)
        for (span in spanned.getSpans(0, spanned.length, Any::class.java)) {
            val start = (spanned.getSpanStart(span) - versatz).coerceIn(0, text.length)
            val ende = (spanned.getSpanEnd(span) - versatz).coerceIn(0, text.length)
            if (start >= ende) continue
            val stil: SpanStyle = when (span) {
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    else -> continue
                }
                is UnderlineSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
                is StrikethroughSpan -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                is URLSpan -> SpanStyle(color = linkFarbe, textDecoration = TextDecoration.Underline)
                else -> continue
            }
            addStyle(stil, start, ende)
        }
    }
}

/** Der Text ohne Formatierung (Listenpunkte als `•` bzw. `1.`), zum Bearbeiten in einem Eingabefeld. */
fun htmlZuKlartext(html: String): String = htmlZuText(html, Color.Unspecified).text

/** Text, der HTML enthalten darf (Beschreibung, Zubereitungsschritte). */
@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    val link = MaterialTheme.colorScheme.primary
    val text = remember(html, link) { htmlZuText(html, link) }
    Text(text, modifier = modifier, style = style)
}
