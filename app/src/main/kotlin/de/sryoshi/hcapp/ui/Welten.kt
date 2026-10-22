package de.sryoshi.hcapp.ui

import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.ui.theme.Welt

/** Farbset des Bildschirms: je App ihr eigenes, Instanzverwaltung und Startseite im HomeCenter-Rot. */
fun Screen.welt(): Welt = when (this) {
    is Screen.Lists, is Screen.Items -> Welt.EINKAUF
    is Screen.Recipes, is Screen.Recipe, is Screen.RecipeShopping, is Screen.RecipeEdit -> Welt.REZEPTE
    is Screen.OweMeUebersicht, is Screen.OweMeKonto, is Screen.OweMeRatenplan -> Welt.OWEME
    else -> Welt.HOMECENTER
}
