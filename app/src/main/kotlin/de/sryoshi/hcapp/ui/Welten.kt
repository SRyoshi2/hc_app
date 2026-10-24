package de.sryoshi.hcapp.ui

import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.ui.theme.Welt

/** Farbset des Bildschirms: je App ihr eigenes, Instanzverwaltung und Startseite im HomeCenter-Rot. */
fun Screen.welt(): Welt = when (this) {
    is Screen.Lists, is Screen.Items -> Welt.EINKAUF
    is Screen.Recipes, is Screen.Recipe, is Screen.RecipeShopping, is Screen.RecipeEdit, is Screen.RecipeOffers -> Welt.REZEPTE
    is Screen.OweMeUebersicht, is Screen.OweMeKonto, is Screen.OweMeRatenplan -> Welt.OWEME
    is Screen.Health, is Screen.HealthDay, is Screen.HealthMonth, is Screen.HealthWeight, is Screen.HealthTemplates,
    is Screen.HealthChecks, is Screen.HealthCheckDetail, is Screen.HealthVaccinations, is Screen.HealthLabs, is Screen.HealthLabTrend,
    is Screen.HealthTraining, is Screen.HealthTrainingTemplates, is Screen.HealthTrainingGoals, is Screen.HealthTraffic -> Welt.HEALTH
    is Screen.Downloads, is Screen.DownloadDetail -> Welt.DOWNLOADS
    else -> Welt.HOMECENTER
}
