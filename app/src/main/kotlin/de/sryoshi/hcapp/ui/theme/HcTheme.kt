package de.sryoshi.hcapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Neutrale Flächen und Text folgen der Web-App (`home/static/css/hc_theme.css`), Hell und Dunkel; ändert sich das
 * Web-Design, hier nachziehen. Die Farben der einzelnen Apps (Abschnitt Farbsets unten) stammen aus den App-Icons.
 */
@Immutable
class HcFarben(
    val bg: Color, val card: Color, val card2: Color, val ink: Color, val muted: Color, val line: Color,
    /** HomeCenter-Rot */
    val accent: Color, val accentInk: Color,
    val ok: Color, val warn: Color,
    /** Neun unterscheidbare Töne für Kategorien und Personen (`--hc-cat-1..9`). */
    val kategorien: List<Color>,
    val dunkel: Boolean,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

val HellFarben = HcFarben(
    bg = c(0xf4f5f7), card = c(0xffffff), card2 = c(0xf6f7f9), ink = c(0x212529), muted = c(0x6c757d), line = c(0xe6e8eb),
    accent = c(0xdc3545), accentInk = c(0xc22232),
    ok = c(0x198754), warn = c(0xb8860b),
    kategorien = listOf<Long>(0xd97227, 0x3e86cc, 0xbe8b1e, 0xbc5271, 0x2f949d, 0x6079d4, 0x8261c2, 0xcc5f95, 0x7e8034).map(::c),
    dunkel = false,
)

val DunkelFarben = HcFarben(
    bg = c(0x131418), card = c(0x1d2025), card2 = c(0x24272d), ink = c(0xe9eaec), muted = c(0x9aa0a6), line = c(0x2b2e33),
    accent = c(0xdc3545), accentInk = c(0xf1737f),
    ok = c(0x2ea36b), warn = c(0xd9a520),
    kategorien = listOf<Long>(0xe89a5c, 0x6fa9e0, 0xdcac4a, 0xdb829a, 0x58b6be, 0x8b9fe4, 0xa78bda, 0xe086b4, 0xa9ab5c).map(::c),
    dunkel = true,
)

/** Jede App hat ihr eigenes Farbset; die Bildschirme der Instanzverwaltung gehören zu HomeCenter. */
enum class Welt { HOMECENTER, EINKAUF, REZEPTE, OWEME, HEALTH }

/**
 * Farbset einer App. Die Grundfarben stammen aus den App-Icons (`app_*.png`):
 * - [kopf]: genau die Farbe des Icons; [onKopf] ist weiß, wo das für Titel noch ausreicht (mindestens 3:1, „großer
 *   Text“), sonst dunkel (Rezepte: Orange mit Weiß hat nur 2,6:1).
 * - [akzent]: für Knöpfe, Links, Auswahl. Im Hellmodus so weit abgedunkelt, dass weiße Schrift darauf und der Akzent als
 *   Text auf hellem Grund mindestens 4,5:1 erreichen; im Dunkelmodus der hellere Ton aus dem Icon mit dunkler Schrift.
 * - [sekundaer]: zweite Icon-Farbe, wo es eine gibt (OweMe: Ocker; Health: Herz-Türkis).
 */
@Immutable
class AppFarben(val kopf: Color, val onKopf: Color, val akzent: Color, val onAkzent: Color, val sekundaer: Color)

private val TEXT_AUF_HELL = c(0x1c1b1f)

fun HcFarben.set(welt: Welt): AppFarben {
    val weiss = Color.White
    return when (welt) {
        // HomeCenter-Rot auf neutralem Kopf (Kartenfläche)
        Welt.HOMECENTER -> AppFarben(kopf = card, onKopf = ink, akzent = accent, onAkzent = weiss, sekundaer = accentInk)
        // Einkaufsliste: Mulberry #ca5e93
        Welt.EINKAUF -> AppFarben(
            kopf = c(0xca5e93), onKopf = weiss,
            akzent = if (dunkel) c(0xcc6396) else c(0xad507e), onAkzent = if (dunkel) TEXT_AUF_HELL else weiss,
            sekundaer = if (dunkel) c(0xcc6396) else c(0xad507e),
        )
        // Rezeptverwaltung: Orange #fd7e14, Kopf mit dunkler Schrift
        Welt.REZEPTE -> AppFarben(
            kopf = c(0xfd7e14), onKopf = TEXT_AUF_HELL,
            akzent = if (dunkel) c(0xfd7e14) else c(0xb1580e), onAkzent = if (dunkel) TEXT_AUF_HELL else weiss,
            sekundaer = if (dunkel) c(0xfd7e14) else c(0xb1580e),
        )
        // OweMe: Petrol #2c5f59, Pfeile Ocker #d8a858 und Türkis #50c0b0
        Welt.OWEME -> AppFarben(
            kopf = c(0x2c5f59), onKopf = weiss,
            akzent = if (dunkel) c(0x50c0b0) else c(0x2c5f59), onAkzent = if (dunkel) TEXT_AUF_HELL else weiss,
            sekundaer = if (dunkel) c(0xd8a858) else c(0xb0762a),
        )
        // Health: Petrol #0f766e, Herz #14b8a6
        Welt.HEALTH -> AppFarben(
            kopf = c(0x0f766e), onKopf = weiss,
            akzent = if (dunkel) c(0x14b8a6) else c(0x0f766e), onAkzent = if (dunkel) TEXT_AUF_HELL else weiss,
            sekundaer = if (dunkel) c(0x14b8a6) else c(0x0f766e),
        )
    }
}

fun HcFarben.akzent(welt: Welt): Color = set(welt).akzent

/** Kopfzeile einer Welt: Hintergrund und Schriftfarbe. */
fun HcFarben.kopf(welt: Welt): Pair<Color, Color> = set(welt).let { it.kopf to it.onKopf }

/** Farbe einer Kategorie/Person: stabil je Name, wie im Web per Hash auf die neun Töne verteilt. */
fun HcFarben.kategorieFarbe(name: String): Color {
    if (name.isBlank()) return line
    return kategorien[(name.trim().lowercase().hashCode() and 0x7fffffff) % kategorien.size]
}

val LocalHcFarben = staticCompositionLocalOf { HellFarben }

object Hc {
    val farben: HcFarben
        @Composable @ReadOnlyComposable get() = LocalHcFarben.current
}

@Composable
fun HcTheme(welt: Welt, dunkel: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val f = if (dunkel) DunkelFarben else HellFarben
    val set = f.set(welt)
    // Material verlangt deckende Container: die getönte Fläche (--hc-*-soft) wird auf den Hintergrund gerechnet.
    val soft = set.akzent.copy(alpha = if (dunkel) 0.16f else 0.10f).compositeOver(f.bg)

    val schema = (if (dunkel) darkColorScheme() else lightColorScheme()).copy(
        primary = set.akzent, onPrimary = set.onAkzent,
        primaryContainer = soft, onPrimaryContainer = f.ink,
        // Ausgewählte Chips, Schalter u. Ä. nehmen `secondaryContainer`: sonst bliebe dort das Standard-Violett.
        secondary = set.akzent, onSecondary = set.onAkzent,
        secondaryContainer = soft, onSecondaryContainer = f.ink,
        tertiary = set.sekundaer,
        background = f.bg, onBackground = f.ink,
        surface = f.card, onSurface = f.ink,
        surfaceVariant = f.card2, onSurfaceVariant = f.muted,
        surfaceContainer = f.card, surfaceContainerHigh = f.card, surfaceContainerHighest = f.card2,
        outline = f.line, outlineVariant = f.line,
        error = f.accentInk, onError = Color.White,
    )

    // Symbole der Statusleiste: dunkel auf heller Kopfzeile, hell auf dunkler.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val fenster = (view.context as? Activity)?.window
            if (fenster != null) WindowCompat.getInsetsController(fenster, view).isAppearanceLightStatusBars = set.kopf.luminance() > 0.5f
        }
    }

    CompositionLocalProvider(LocalHcFarben provides f) {
        MaterialTheme(colorScheme = schema, content = content)
    }
}
