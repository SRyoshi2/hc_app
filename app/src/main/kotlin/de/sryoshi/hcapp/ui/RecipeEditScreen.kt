package de.sryoshi.hcapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.sryoshi.hcapp.AppViewModel
import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.core.RecipeDifficulty
import de.sryoshi.hcapp.core.RecipeUnits
import de.sryoshi.hcapp.ui.theme.Hc

/** Vertauscht den Eintrag an [i] mit seinem Nachbarn ([schritt] = -1 nach oben, +1 nach unten). */
private fun <T> SnapshotStateList<T>.verschieben(i: Int, schritt: Int) {
    val j = i + schritt
    if (j !in indices) return
    val t = this[i]
    this[i] = this[j]
    this[j] = t
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(vm: AppViewModel, s: Screen.RecipeEdit) {
    LaunchedEffect(s) { vm.startEditor(s) }
    val e = vm.entwurf
    if (e == null || e.key != s.key) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ErrorText(vm)
            if (vm.busy) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }

    val bildWahl = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            e.titelbild = uri
            e.titelbildEntfernen = false
        }
    }
    var neueKategorie by remember { mutableStateOf(false) }
    var vorschlag by remember { mutableStateOf(false) }
    var schaetzFragen by remember { mutableStateOf(false) }
    if (vorschlag) {
        RezeptVorschlagDialog(
            onOk = { zutaten, portionen, wunsch -> vorschlag = false; vm.generateRecipe(s, zutaten, portionen, wunsch) },
            onCancel = { vorschlag = false },
        )
    }
    if (schaetzFragen) {
        KiDialog(
            "Nährwerte schätzen",
            "${e.titel.ifBlank { "Unbenanntes Rezept" }} (${e.portionen} Portionen)\n" +
                vm.schaetzZutaten().joinToString("\n") { "• ${it.amount.orEmpty()} ${it.unit} ${it.name}".replace("  ", " ").trim() },
            "Die Werte je Portion landen in den Feldern unten; du prüfst und speicherst sie selbst.",
            onJa = { schaetzFragen = false; vm.estimateRecipeNutrition(s) }, onNein = { schaetzFragen = false },
        )
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedButton(onClick = { vorschlag = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Rezept aus Zutaten vorschlagen lassen (KI)")
                }
            }
            // Titelbild
            item {
                val zeigen: Any? = e.titelbild ?: if (e.titelbildEntfernen) null else e.original?.imageUrl
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (zeigen != null) {
                        AsyncImage(
                            model = zeigen, contentDescription = "Titelbild", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(180.dp).background(Hc.farben.card2, RoundedCornerShape(14.dp)),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            bildWahl.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text(if (zeigen == null) "Titelbild wählen" else "Anderes Bild") }
                        if (zeigen != null) {
                            OutlinedButton(onClick = {
                                // Ein neu gewähltes Bild wird nur verworfen; ein gespeichertes wird beim Speichern gelöscht.
                                if (e.titelbild != null) e.titelbild = null else e.titelbildEntfernen = true
                            }) { Text("Entfernen") }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(e.titel, { e.titel = it }, label = { Text("Titel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        e.portionen, { e.portionen = it.filter(Char::isDigit) }, label = { Text("Portionen") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        e.vorbereitung, { e.vorbereitung = it.filter(Char::isDigit) }, label = { Text("Vorbereiten (Min.)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        e.kochen, { e.kochen = it.filter(Char::isDigit) }, label = { Text("Kochen (Min.)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Schwierigkeit", style = MaterialTheme.typography.labelLarge, color = Hc.farben.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecipeDifficulty.alle.forEach { (code, name) ->
                            FilterChip(selected = e.schwierigkeit == code, onClick = { e.schwierigkeit = code }, label = { Text(name) })
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        e.beschreibung, { e.beschreibung = it }, label = { Text("Beschreibung") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Texte werden hier als einfacher Text bearbeitet. Bleibt ein Text unverändert, behält er seine Formatierung aus dem Web.",
                        style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                    )
                }
            }
            // Kategorien
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Kategorien", style = MaterialTheme.typography.labelLarge, color = Hc.farben.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.kategorien.forEach { k ->
                            val gewaehlt = k.id in e.kategorien
                            FilterChip(
                                selected = gewaehlt,
                                onClick = { if (gewaehlt) e.kategorien.remove(k.id) else e.kategorien.add(k.id) },
                                label = { Text(k.name) },
                            )
                        }
                        AssistChip(onClick = { neueKategorie = true }, label = { Text("+ Neue Kategorie") })
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Öffentlich", style = MaterialTheme.typography.bodyLarge)
                        Text("Andere sehen das Rezept unter „Entdecken“ und können es übernehmen.", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                    }
                    Switch(checked = e.oeffentlich, onCheckedChange = { e.oeffentlich = it })
                }
            }

            // Zutaten
            item { Text("Zutaten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
            itemsIndexed(e.zutaten) { i, z -> ZutatZeile(e.zutaten, i, z) }
            item {
                OutlinedButton(
                    onClick = {
                        // Neue Zutaten übernehmen den Abschnitt der letzten: Abschnitte sind zusammenhängende Läufe.
                        e.zutaten.add(ZutatEntwurf(null, abschnitt = e.zutaten.lastOrNull()?.abschnitt.orEmpty()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Zutat hinzufügen") }
            }

            // Schritte
            item { Text("Zubereitung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
            itemsIndexed(e.schritte) { i, schritt -> SchrittZeile(e.schritte, i, schritt) }
            item {
                OutlinedButton(onClick = { e.schritte.add(SchrittEntwurf()) }, modifier = Modifier.fillMaxWidth()) { Text("Schritt hinzufügen") }
            }

            // Nährwerte
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nährwerte pro Portion (optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(
                        onClick = { schaetzFragen = true }, enabled = !vm.busy && e.zutaten.any { it.name.isNotBlank() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Von der KI schätzen lassen") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Zahlenfeld(e.kcal, { e.kcal = it }, "Kalorien", Modifier.weight(1f))
                        Zahlenfeld(e.eiweiss, { e.eiweiss = it }, "Eiweiß (g)", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Zahlenfeld(e.fett, { e.fett = it }, "Fett (g)", Modifier.weight(1f))
                        Zahlenfeld(e.kohlenhydrate, { e.kohlenhydrate = it }, "Kohlenhydrate (g)", Modifier.weight(1f))
                    }
                }
            }
        }

        // Fehler und Speichern bleiben unten sichtbar, auch wenn das Formular lang ist.
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ErrorText(vm)
            Button(onClick = { vm.saveRecipe(s) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (s.key == null) "Rezept anlegen" else "Speichern")
            }
        }
    }

    if (neueKategorie) {
        TitelDialog("Neue Kategorie", onOk = { neueKategorie = false; vm.addRecipeCategory(s, it) }, onCancel = { neueKategorie = false })
    }
}

@Composable
private fun Zahlenfeld(wert: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        wert, { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier,
    )
}

@Composable
private fun ZutatZeile(liste: SnapshotStateList<ZutatEntwurf>, i: Int, z: ZutatEntwurf) {
    HcKarte(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Zahlenfeld(z.menge, { z.menge = it }, "Menge", Modifier.width(92.dp))
                EinheitAuswahl(z)
                OutlinedTextField(z.name, { z.name = it }, label = { Text("Zutat") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    z.hinweis, { z.hinweis = it }, label = { Text("Hinweis, z. B. fein gehackt") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { liste.verschieben(i, -1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Nach oben") }
                IconButton(onClick = { liste.verschieben(i, 1) }, enabled = i < liste.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Nach unten") }
                IconButton(onClick = { liste.removeAt(i) }) { Icon(Icons.Default.Delete, "Zutat löschen", tint = Hc.farben.muted) }
            }
            OutlinedTextField(
                z.abschnitt, { z.abschnitt = it }, label = { Text("Abschnitt (optional, z. B. Soße)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EinheitAuswahl(z: ZutatEntwurf) {
    var offen by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { offen = true }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(RecipeUnits.label(z.einheit)) }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            RecipeUnits.alle.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { z.einheit = code; offen = false })
            }
        }
    }
}

@Composable
private fun SchrittZeile(liste: SnapshotStateList<SchrittEntwurf>, i: Int, schritt: SchrittEntwurf) {
    HcKarte(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Schritt ${i + 1}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { liste.verschieben(i, -1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Nach oben") }
                IconButton(onClick = { liste.verschieben(i, 1) }, enabled = i < liste.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Nach unten") }
                IconButton(onClick = { liste.removeAt(i) }) { Icon(Icons.Default.Delete, "Schritt löschen", tint = Hc.farben.muted) }
            }
            OutlinedTextField(
                schritt.ueberschrift, { schritt.ueberschrift = it }, label = { Text("Überschrift (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(schritt.text, { schritt.text = it }, label = { Text("Anleitung") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        }
    }
}
