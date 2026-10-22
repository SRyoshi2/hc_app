package de.sryoshi.hcapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Die Farben der Web-App (`home/static/css/hc_theme.css`, OweMe: `oweme/templates/oweme/*.html`),
 * Hell und Dunkel. Die Farben der einzelnen Apps sind final; Flächen und Text folgen dem Web-Design
 * und können sich dort noch ändern – dann hier nachziehen.
 */
@Immutable
class HcFarben(
    val bg: Color, val card: Color, val card2: Color, val ink: Color, val muted: Color, val line: Color,
    /** HomeCenter-Rot */
    val accent: Color, val accentInk: Color,
    /** Küche: Rezepte, Speiseplan, Einkaufsplan, Einkaufsliste */
    val kueche: Color, val kuecheInk: Color,
    /** Health: Petrol */
    val health: Color, val healthInk: Color, val healthDeep: Color, val healthBright: Color,
    /** OweMe: Petrol-Kopf, „bekomme“ (Petrol-Grün) und „schulde“ (Ocker) */
    val omPetrol: Color, val omBekomme: Color, val omSchulde: Color,
    val ok: Color, val warn: Color,
    /** Neun unterscheidbare Töne für Kategorien und Personen (`--hc-cat-1..9`). */
    val kategorien: List<Color>,
    val dunkel: Boolean,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

val HellFarben = HcFarben(
    bg = c(0xf4f5f7), card = c(0xffffff), card2 = c(0xf6f7f9), ink = c(0x212529), muted = c(0x6c757d), line = c(0xe6e8eb),
    accent = c(0xdc3545), accentInk = c(0xc22232),
    kueche = c(0x2e7d4f), kuecheInk = c(0x276b44),
    health = c(0x0f766e), healthInk = c(0x0f766e), healthDeep = c(0x0f3b36), healthBright = c(0x5eead4),
    omPetrol = c(0x123a36), omBekomme = c(0x1f7a6e), omSchulde = c(0xb0762a),
    ok = c(0x198754), warn = c(0xb8860b),
    kategorien = listOf<Long>(0xd97227, 0x3e86cc, 0xbe8b1e, 0xbc5271, 0x2f949d, 0x6079d4, 0x8261c2, 0xcc5f95, 0x7e8034).map(::c),
    dunkel = false,
)

val DunkelFarben = HcFarben(
    bg = c(0x131418), card = c(0x1d2025), card2 = c(0x24272d), ink = c(0xe9eaec), muted = c(0x9aa0a6), line = c(0x2b2e33),
    accent = c(0xdc3545), accentInk = c(0xf1737f),
    kueche = c(0x57b57c), kuecheInk = c(0x8ed3aa),
    health = c(0x2dd4bf), healthInk = c(0x5eead4), healthDeep = c(0x16302c), healthBright = c(0x5eead4),
    omPetrol = c(0x1c2b28), omBekomme = c(0x55c0b2), omSchulde = c(0xdfa95e),
    ok = c(0x2ea36b), warn = c(0xd9a520),
    kategorien = listOf<Long>(0xe89a5c, 0x6fa9e0, 0xdcac4a, 0xdb829a, 0x58b6be, 0x8b9fe4, 0xa78bda, 0xe086b4, 0xa9ab5c).map(::c),
    dunkel = true,
)

/** Die Welt einer App bestimmt Kopfzeile und Akzent, wie im Web die Farbwelt je App. */
enum class Welt { HOMECENTER, KUECHE, HEALTH, OWEME }

fun HcFarben.akzent(welt: Welt): Color = when (welt) {
    Welt.HOMECENTER -> accent
    Welt.KUECHE -> kueche
    Welt.HEALTH -> health
    Welt.OWEME -> omBekomme
}

/** Text auf einer vollflächigen Akzentfarbe: hell auf dem dunklen Ton, dunkel auf dem hellen Ton des Dunkelmodus. */
private fun HcFarben.aufAkzent(welt: Welt): Color =
    if (dunkel && welt != Welt.HOMECENTER) c(0x0b1f1a) else Color.White

/** Kopfzeile einer Welt: Hintergrund und Schriftfarbe. */
fun HcFarben.kopf(welt: Welt): Pair<Color, Color> = when (welt) {
    Welt.HOMECENTER -> card to ink
    Welt.KUECHE -> kueche to aufAkzent(welt)
    Welt.HEALTH -> healthDeep to Color.White
    Welt.OWEME -> omPetrol to Color.White
}

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
    val akzent = f.akzent(welt)
    val auf = f.aufAkzent(welt)
    // Material verlangt deckende Container: die getönte Fläche (--hc-*-soft) wird auf den Hintergrund gerechnet.
    val soft = akzent.copy(alpha = if (dunkel) 0.16f else 0.10f).compositeOver(f.bg)

    val schema = (if (dunkel) darkColorScheme() else lightColorScheme()).copy(
        primary = akzent, onPrimary = auf,
        primaryContainer = soft, onPrimaryContainer = if (welt == Welt.HOMECENTER) f.accentInk else f.ink,
        secondary = akzent, onSecondary = auf,
        background = f.bg, onBackground = f.ink,
        surface = f.card, onSurface = f.ink,
        surfaceVariant = f.card2, onSurfaceVariant = f.muted,
        surfaceContainer = f.card, surfaceContainerHigh = f.card, surfaceContainerHighest = f.card2,
        outline = f.line, outlineVariant = f.line,
        error = f.accentInk, onError = Color.White,
    )
    CompositionLocalProvider(LocalHcFarben provides f) {
        MaterialTheme(colorScheme = schema, content = content)
    }
}
