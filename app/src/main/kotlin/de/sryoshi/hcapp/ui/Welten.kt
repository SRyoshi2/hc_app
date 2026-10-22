package de.sryoshi.hcapp.ui

import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.ui.theme.Welt

/** Farbwelt des Bildschirms: Küche (Einkaufsliste, Rezepte) im Grün, alles andere im HomeCenter-Rot. */
fun Screen.welt(): Welt = when (this) {
    is Screen.Lists, is Screen.Items, is Screen.Recipes, is Screen.Recipe, is Screen.RecipeShopping, is Screen.RecipeEdit -> Welt.KUECHE
    else -> Welt.HOMECENTER
}
