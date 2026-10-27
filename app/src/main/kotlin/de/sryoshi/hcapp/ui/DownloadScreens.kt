package de.sryoshi.hcapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.sryoshi.hcapp.AppViewModel
import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.core.Datum
import de.sryoshi.hcapp.core.DownloadItem
import de.sryoshi.hcapp.ui.theme.Hc

private fun metaZeile(d: DownloadItem) = listOfNotNull(
    d.type?.name, d.category?.name, d.version.takeIf { it.isNotBlank() }?.let { "Version $it" }, d.sizeText.takeIf { it.isNotBlank() },
).joinToString(" · ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadsScreen(vm: AppViewModel, s: Screen.Downloads) {
    LaunchedEffect(s) { vm.loadDownloads(s) }
    var suche by rememberSaveable { mutableStateOf(vm.downloadQuery) }
    val seite = vm.downloadSeite

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                value = suche, onValueChange = { suche = it },
                placeholder = { Text("Titel, Beschreibung, Version oder Dateiname…") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Hc.farben.muted) },
                singleLine = true, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.filterDownloads(s, q = suche) }),
            )
        }
        if (seite != null && seite.types.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(vm.downloadTyp == null, { vm.filterDownloads(s, typ = null) }, label = { Text("Alle Typen") })
                    seite.types.forEach { t ->
                        FilterChip(
                            vm.downloadTyp == t.id, { vm.filterDownloads(s, typ = if (vm.downloadTyp == t.id) null else t.id) },
                            label = { Text("${t.name} (${t.count})") },
                        )
                    }
                }
            }
        }
        if (seite != null && seite.categories.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    seite.categories.forEach { k ->
                        FilterChip(
                            vm.downloadKategorie == k.id, { vm.filterDownloads(s, kategorie = if (vm.downloadKategorie == k.id) null else k.id) },
                            label = { Text("${k.name} (${k.count})") },
                        )
                    }
                }
            }
        }
        item { ErrorText(vm) }
        if (seite != null && seite.results.isEmpty() && !vm.busy) {
            item {
                Text(
                    if (vm.downloadQuery.isNotBlank() || vm.downloadTyp != null || vm.downloadKategorie != null) "Keine Downloads gefunden."
                    else "Für dich gibt es noch keine Downloads.",
                    color = Hc.farben.muted, modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(seite?.results.orEmpty(), key = { it.key }) { d ->
            HcKarte(Modifier.fillMaxWidth(), onClick = { vm.show(Screen.DownloadDetail(s.instance, s.me, d.key, d.title)) }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(d.title, style = MaterialTheme.typography.titleMedium)
                    Text(metaZeile(d), style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                    if (d.filename.isNotBlank()) Text(d.filename, style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                }
            }
        }
    }
}

@Composable
fun DownloadDetailScreen(vm: AppViewModel, s: Screen.DownloadDetail) {
    LaunchedEffect(s) { vm.loadDownload(s) }
    val d = vm.downloadDetail?.takeIf { it.key == s.key }
    val ablegen = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && d != null) vm.saveDownload(s, d, uri)
    }
    val fortschritt = vm.downloadFortschritt

    // Der Inhalt scrollt (lange Beschreibung, Prüfsumme, kleine Bildschirme); der Knopf steht darunter fest und ist immer sichtbar.
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorText(vm)
            if (d == null) {
                if (!vm.busy) OutlinedButton(onClick = { vm.loadDownload(s) }, modifier = Modifier.fillMaxWidth()) { Text("Erneut laden") }
            } else {
                HcKarte(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(d.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(metaZeile(d), color = Hc.farben.muted)
                        if (d.description.isNotBlank()) HtmlText(d.description, Modifier.padding(top = 4.dp), MaterialTheme.typography.bodyMedium)
                        else if (d.descriptionText.isNotBlank()) Text(d.descriptionText)
                    }
                }
                HcKarte(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Zeile("Datei", d.filename)
                        Zeile("Größe", d.sizeText)
                        Zeile("Abrufe", d.downloads.toString())
                        Zeile("Aktualisiert", Datum.anzeige(d.updatedAt.take(10)))
                        if (d.sha256.isNotBlank()) {
                            Text("SHA-256", style = MaterialTheme.typography.labelMedium, color = Hc.farben.muted)
                            Text(d.sha256, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (fortschritt != null) {
                    val gesamt = d.size
                    if (gesamt != null && gesamt > 0) LinearProgressIndicator(progress = { (fortschritt.toFloat() / gesamt).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("${groesseText(fortschritt)}${d.size?.takeIf { it > 0 }?.let { " von ${groesseText(it)}" } ?: ""} geladen", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { if (d != null) ablegen.launch(d.filename.ifBlank { d.title }) }, enabled = d != null && fortschritt == null && !vm.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.Download, null); Text("  Herunterladen") }
            Text(
                "Die Datei wird dort gespeichert, wo du es wählst; die Prüfsumme wird nach dem Laden mit der des Servers verglichen. " +
                    "Ein abgebrochener Ladevorgang lässt sich nicht fortsetzen.",
                style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
            )
        }
    }
}

@Composable
private fun Zeile(label: String, wert: String) {
    if (wert.isBlank()) return
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(0.35f), color = Hc.farben.muted, style = MaterialTheme.typography.bodyMedium)
        Text(wert, Modifier.weight(0.65f), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun groesseText(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.GERMANY, "%.2f GB", bytes / 1073741824.0)
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.GERMANY, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
