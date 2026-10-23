package de.sryoshi.hcapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.sryoshi.hcapp.AppViewModel
import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.TeilenZustand
import de.sryoshi.hcapp.core.Datum
import de.sryoshi.hcapp.ui.theme.Hc

/** Auswahl der Benutzer, mit denen geteilt wird (Liste, Konto) oder denen ein Rezept angeboten wird. */
@Composable
fun TeilenDialog(vm: AppViewModel, z: TeilenZustand) {
    val anbieten = z.art == "recipe"
    var gewaehlt by remember(z) { mutableStateOf(z.bekannt.map { it.id }.toSet()) }
    var suche by remember(z) { mutableStateOf("") }
    // Wer schon dabei ist, bleibt auch bei einer Suche sichtbar und abwählbar.
    val benutzer = (z.bekannt + vm.teilBenutzer).distinctBy { it.id }
        .filter { suche.isBlank() || it.displayName.contains(suche.trim(), ignoreCase = true) || it.id in gewaehlt }

    AlertDialog(
        onDismissRequest = vm::teilenSchliessen,
        title = { Text(if (anbieten) "„${z.titel}“ anbieten" else "„${z.titel}“ teilen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (anbieten) "Die Empfänger bekommen eine eigene Kopie, wenn sie das Angebot annehmen."
                    else "Wer hier angehakt ist, sieht und bearbeitet es mit. Abwählen beendet die Freigabe.",
                    style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                )
                OutlinedTextField(
                    suche, { suche = it; vm.benutzerSuchen(it) }, label = { Text("Benutzer suchen") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (benutzer.isEmpty()) Text("Keine Benutzer gefunden.", color = Hc.farben.muted)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(benutzer, key = { it.id }) { u ->
                        Row(
                            Modifier.fillMaxWidth().clickable { gewaehlt = if (u.id in gewaehlt) gewaehlt - u.id else gewaehlt + u.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(u.id in gewaehlt, { gewaehlt = if (it) gewaehlt + u.id else gewaehlt - u.id })
                            Text(u.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.teilenSpeichern(gewaehlt.toList()) }, enabled = !anbieten || gewaehlt.isNotEmpty()) {
                Text(if (anbieten) "Anbieten" else "Speichern")
            }
        },
        dismissButton = { TextButton(onClick = vm::teilenSchliessen) { Text("Abbrechen") } },
    )
}

@Composable
fun RecipeOffersScreen(vm: AppViewModel, s: Screen.RecipeOffers) {
    LaunchedEffect(s) { vm.loadOffers(s) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ErrorText(vm) }
        if (vm.angebote.isEmpty() && !vm.busy) item { Text("Gerade hat dir niemand ein Rezept angeboten.", color = Hc.farben.muted) }
        items(vm.angebote, key = { it.id }) { a ->
            HcKarte(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(a.recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Von ${a.sender.displayName}" + a.createdAt.take(10).takeIf { it.isNotBlank() }?.let { " · ${Datum.anzeige(it)}" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.acceptOffer(s, a) }, enabled = !vm.busy) { Text("Übernehmen") }
                        OutlinedButton(onClick = { vm.declineOffer(s, a) }, enabled = !vm.busy) { Text("Ablehnen") }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(vm: AppViewModel, s: Screen.Notifications) {
    LaunchedEffect(s) { vm.loadNotifications(s) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ErrorText(vm) }
        if (vm.ungelesen > 0) {
            item { TextButton(onClick = { vm.markAllRead(s) }) { Text("Alle als gelesen markieren") } }
        }
        if (vm.meldungen.isEmpty() && !vm.busy) item { Text("Keine Meldungen.", color = Hc.farben.muted) }
        items(vm.meldungen, key = { it.id }) { m ->
            HcKarte(Modifier.fillMaxWidth(), onClick = { vm.openNotification(s, m) }) {
                Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.text, fontWeight = if (m.read) FontWeight.Normal else FontWeight.SemiBold)
                        Text(
                            listOfNotNull(m.typeLabel.takeIf { it.isNotBlank() }, m.createdAt.take(10).takeIf { it.isNotBlank() }?.let { Datum.anzeige(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                        )
                    }
                    IconButton(onClick = { vm.deleteNotification(s, m) }) { Icon(Icons.Default.Delete, "Meldung löschen") }
                }
            }
        }
    }
}
