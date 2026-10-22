package de.sryoshi.hcapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.sryoshi.hcapp.AppViewModel
import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.core.RecipeDetail
import de.sryoshi.hcapp.core.RecipeSummary
import de.sryoshi.hcapp.ui.theme.Hc

// ── Hilfen ──────────────────────────────────────────────────────────────

/** `"500.00"` → `"500"`, `"1.50"` → `"1.5"`; kein Dezimalpunkt bleibt unverändert. */
private fun zahlKurz(text: String?): String {
    if (text.isNullOrBlank()) return ""
    return if ('.' in text) text.trimEnd('0').trimEnd('.') else text
}

private fun metaZeile(portionen: Int, minuten: Int?, schwierigkeit: String): String =
    listOfNotNull(
        "$portionen ${if (portionen == 1) "Portion" else "Portionen"}",
        minuten?.let { "$it Min." },
        schwierigkeit.ifBlank { null },
    ).joinToString(" · ")

/** Bild oder, ohne Bild, eine ruhige Fläche mit Symbol. */
@Composable
private fun RezeptBild(url: String?, modifier: Modifier) {
    if (url == null) {
        Box(modifier.background(Hc.farben.card2), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Restaurant, null, tint = Hc.farben.muted)
        }
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

// ── Liste ───────────────────────────────────────────────────────────────

@Composable
fun RecipesScreen(vm: AppViewModel, s: Screen.Recipes) {
    LaunchedEffect(s) { vm.loadRecipes(s) }
    var suche by rememberSaveable { mutableStateOf(vm.recipeQuery) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = vm.recipeTab, containerColor = Hc.farben.card, contentColor = MaterialTheme.colorScheme.primary) {
            Tab(selected = vm.recipeTab == 0, onClick = { vm.setRecipeTab(s, 0) }, text = { Text("Meine Rezepte") })
            Tab(selected = vm.recipeTab == 1, onClick = { vm.setRecipeTab(s, 1) }, text = { Text("Entdecken") })
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = suche, onValueChange = { suche = it },
                placeholder = { Text("Rezept oder Kategorie suchen…") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Hc.farben.muted) },
                singleLine = true, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchRecipes(s, suche) }),
            )
            ErrorText(vm)
            if (vm.recipes.isEmpty() && !vm.busy) {
                Text(
                    when {
                        vm.recipeQuery.isNotBlank() -> "Keine Rezepte gefunden."
                        vm.recipeTab == 0 -> "Du hast noch keine Rezepte."
                        else -> "Es gibt noch keine öffentlichen Rezepte."
                    },
                    color = Hc.farben.muted, modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(vm.recipes, key = { it.key }) { r -> RezeptZeile(vm, s, r) }
            }
        }
    }
}

@Composable
private fun RezeptZeile(vm: AppViewModel, s: Screen.Recipes, r: RecipeSummary) {
    HcKarte(Modifier.fillMaxWidth(), onClick = { vm.show(Screen.Recipe(s.instance, s.me, r.key, r.title)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RezeptBild(r.imageUrl, Modifier.size(88.dp))
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    metaZeile(r.servings, r.gesamtMinuten, r.difficultyLabel),
                    style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                )
                val zusatz = if (vm.recipeTab == 1 && r.owner.isNotBlank()) "von ${r.owner}"
                else r.categories.joinToString(" · ") { it.name }
                if (zusatz.isNotBlank()) Text(zusatz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ── Detail ──────────────────────────────────────────────────────────────

@Composable
fun RecipeScreen(vm: AppViewModel, s: Screen.Recipe) {
    LaunchedEffect(s) { vm.loadRecipe(s) }
    val r = vm.recipe?.takeIf { it.key == s.key }
    if (r == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ErrorText(vm)
            if (vm.busy) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        r.bildUrl?.let { url ->
            item {
                AsyncImage(
                    model = url, contentDescription = r.title, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).background(Hc.farben.card2, RoundedCornerShape(14.dp)),
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    metaZeile(r.servings, r.gesamtMinuten, r.difficultyLabel),
                    style = MaterialTheme.typography.bodyMedium, color = Hc.farben.muted,
                )
                if (r.categories.isNotEmpty()) {
                    Text(r.categories.joinToString(" · ") { it.name }, color = MaterialTheme.colorScheme.primary)
                }
                if (!r.isOwner && r.owner.isNotBlank()) {
                    Text("von ${r.owner}", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                }
            }
        }
        if (r.description.isNotBlank()) item { Text(r.description) }
        item { Aktionen(vm, s, r) }
        item { ErrorText(vm) }
        if (r.ingredientSections.any { it.ingredients.isNotEmpty() }) {
            item { Abschnitt("Zutaten") { Zutaten(r) } }
        }
        if (r.steps.isNotEmpty()) {
            item { Abschnitt("Zubereitung") { Schritte(r) } }
        }
        val kcal = r.nutrition.perServing["kcal"]
        if (!kcal.isNullOrBlank()) {
            item { Abschnitt("Nährwerte pro Portion") { Naehrwerte(r) } }
        }
    }
}

@Composable
private fun Aktionen(vm: AppViewModel, s: Screen.Recipe, r: RecipeDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { vm.show(Screen.RecipeShopping(s.instance, s.me, r.key, r.title)) },
            enabled = r.ingredientSections.any { it.ingredients.isNotEmpty() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("In die Einkaufsliste") }
        if (!r.isOwner) {
            OutlinedButton(onClick = { vm.copyRecipe(s) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Als eigenes Rezept übernehmen")
            }
        }
    }
}

@Composable
private fun Abschnitt(titel: String, inhalt: @Composable () -> Unit) {
    HcKarte(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            inhalt()
        }
    }
}

@Composable
private fun Zutaten(r: RecipeDetail) {
    r.ingredientSections.forEach { abschnitt ->
        if (abschnitt.section.isNotBlank()) {
            Text(abschnitt.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
        abschnitt.ingredients.forEach { z ->
            val menge = listOf(zahlKurz(z.amount), z.unitLabel).filter { it.isNotBlank() }.joinToString(" ")
            Row {
                Text(menge, color = Hc.farben.muted, modifier = Modifier.width(84.dp))
                Text(z.name + if (z.note.isNotBlank()) " (${z.note})" else "", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Schritte(r: RecipeDetail) {
    r.steps.sortedBy { it.order }.forEachIndexed { i, schritt ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${i + 1}.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f)) {
                if (schritt.heading.isNotBlank()) Text(schritt.heading, fontWeight = FontWeight.SemiBold)
                Text(schritt.text)
            }
        }
    }
}

@Composable
private fun Naehrwerte(r: RecipeDetail) {
    val n = r.nutrition.perServing
    val zeilen = listOf("kcal" to "Kalorien", "protein_g" to "Eiweiß (g)", "fat_g" to "Fett (g)", "carbs_g" to "Kohlenhydrate (g)")
    zeilen.forEach { (feld, name) ->
        val wert = zahlKurz(n[feld])
        if (wert.isNotBlank()) {
            Row {
                Text(name, Modifier.weight(1f), color = Hc.farben.muted)
                Text(wert)
            }
        }
    }
}

// ── In die Einkaufsliste ────────────────────────────────────────────────

@Composable
fun RecipeShoppingScreen(vm: AppViewModel, s: Screen.RecipeShopping) {
    val basis = vm.recipe?.takeIf { it.key == s.key }?.servings ?: 2
    var portionen by rememberSaveable { mutableStateOf(basis) }
    var ziel by rememberSaveable { mutableStateOf<String?>(null) }      // null = neue Liste
    var zielGewaehlt by rememberSaveable { mutableStateOf(false) }
    var neuerTitel by rememberSaveable { mutableStateOf(s.title) }

    LaunchedEffect(s) { vm.loadShoppingTargets(s) }
    LaunchedEffect(s, portionen) { vm.loadPreview(s, portionen) }
    // Solange nichts gewählt wurde, ist die erste Liste vorbelegt (wie im Web die zuletzt benutzte Liste naheliegt).
    LaunchedEffect(vm.lists) {
        if (!zielGewaehlt && vm.lists.isNotEmpty()) ziel = vm.lists.first().key
    }

    val vorschau = vm.preview
    // Alle Zeilen sind zunächst angehakt; bei neuer Portionenzahl entsteht die Auswahl neu.
    val auswahl = remember(vorschau) { mutableStateListOf<Boolean>().apply { repeat(vorschau?.items?.size ?: 0) { add(true) } } }
    val anzahl = auswahl.count { it }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Portionen", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { if (portionen > 1) portionen-- }, enabled = portionen > 1) { Icon(Icons.Default.Remove, "Weniger") }
                    Text("$portionen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp), maxLines = 1)
                    IconButton(onClick = { if (portionen < 99) portionen++ }) { Icon(Icons.Default.Add, "Mehr") }
                }
                ErrorText(vm)
            }
            if (vorschau == null && vm.busy) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            vorschau?.let { v ->
                itemsIndexed(v.items) { i, z ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = auswahl.getOrElse(i) { true }, onCheckedChange = { if (i < auswahl.size) auswahl[i] = it })
                        Column(Modifier.weight(1f)) {
                            Text(z.name + if (z.note.isNotBlank()) " (${z.note})" else "")
                            if (z.amount.isNotBlank()) Text(z.amount, style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("In welche Liste?", style = MaterialTheme.typography.titleMedium)
            }
            items(vm.lists, key = { it.key }) { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = ziel == l.key, onClick = { ziel = l.key; zielGewaehlt = true })
                    Text(l.title, Modifier.weight(1f))
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = ziel == null, onClick = { ziel = null; zielGewaehlt = true })
                    Text("Neue Liste")
                }
                if (ziel == null) {
                    OutlinedTextField(
                        value = neuerTitel, onValueChange = { neuerTitel = it },
                        label = { Text("Name der neuen Liste") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Button(
            onClick = {
                vorschau?.let { v ->
                    vm.transferToList(s, v.items.filterIndexed { i, _ -> auswahl.getOrElse(i) { true } }.map { it.alsEingabe() }, ziel, neuerTitel)
                }
            },
            enabled = vorschau != null && anzahl > 0 && !vm.busy && (ziel != null || neuerTitel.isNotBlank()),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(if (anzahl == 1) "1 Artikel übernehmen" else "$anzahl Artikel übernehmen") }
    }
}
