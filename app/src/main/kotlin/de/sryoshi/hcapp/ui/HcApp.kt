package de.sryoshi.hcapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.sryoshi.hcapp.AppViewModel
import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.core.AppKey
import de.sryoshi.hcapp.core.ShoppingItem
import de.sryoshi.hcapp.core.ShoppingList
import de.sryoshi.hcapp.data.Instance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HcApp(vm: AppViewModel) {
    val s = vm.screen
    val titel = when (s) {
        Screen.Instances -> "Instanzen"
        Screen.AddInstance -> "Instanz hinzufügen"
        is Screen.Login -> s.meta.productName
        is Screen.Home -> s.instance.name
        is Screen.Lists -> "Einkaufslisten"
        is Screen.Items -> s.title
    }
    val zurueck = s !is Screen.Instances && !(s is Screen.AddInstance && vm.instances.isEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titel) },
                navigationIcon = {
                    if (zurueck) {
                        IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (s) {
                Screen.Instances -> InstancesScreen(vm)
                Screen.AddInstance -> AddInstanceScreen(vm)
                is Screen.Login -> LoginScreen(vm, s)
                is Screen.Home -> HomeScreen(vm, s)
                is Screen.Lists -> ListsScreen(vm, s)
                is Screen.Items -> ItemsScreen(vm, s)
            }
            if (vm.busy) CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(8.dp))
        }
    }
}

@Composable
private fun ErrorText(vm: AppViewModel) {
    vm.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun InstancesScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ErrorText(vm)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(vm.instances, key = { it.id }) { i -> InstanceCard(vm, i) }
        }
        Button(onClick = { vm.show(Screen.AddInstance) }, modifier = Modifier.fillMaxWidth()) {
            Text("Instanz hinzufügen")
        }
    }
}

@Composable
private fun InstanceCard(vm: AppViewModel, i: Instance) {
    var abmelden by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { vm.open(i) }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(i.name, style = MaterialTheme.typography.titleMedium)
                Text(i.url, style = MaterialTheme.typography.bodySmall)
                Text("Angemeldet als ${i.displayName}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { abmelden = true }) { Text("Entfernen") }
        }
    }
    if (abmelden) {
        AlertDialog(
            onDismissRequest = { abmelden = false },
            title = { Text("Instanz entfernen?") },
            text = { Text("Du wirst abgemeldet, und die Anmeldung dieses Geräts wird auf der Instanz widerrufen. Die Daten auf der Instanz bleiben unberührt.") },
            confirmButton = { TextButton(onClick = { abmelden = false; vm.signOut(i) }) { Text("Entfernen") } },
            dismissButton = { TextButton(onClick = { abmelden = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun AddInstanceScreen(vm: AppViewModel) {
    var adresse by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Adresse deiner HomeCenter-Instanz, z. B. homecenter.example.de oder http://192.168.1.20:8000 im lokalen Netz.")
        OutlinedTextField(
            value = adresse, onValueChange = { adresse = it; vm.clearError() },
            label = { Text("Adresse") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        ErrorText(vm)
        Button(onClick = { vm.connect(adresse) }, enabled = !vm.busy && adresse.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Verbinden")
        }
    }
}

@Composable
private fun LoginScreen(vm: AppViewModel, s: Screen.Login) {
    var benutzer by rememberSaveable { mutableStateOf(s.username) }
    var passwort by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${s.meta.productName} · ${s.url}", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = benutzer, onValueChange = { benutzer = it; vm.clearError() },
            label = { Text("Benutzername") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = passwort, onValueChange = { passwort = it; vm.clearError() },
            label = { Text("Passwort") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        ErrorText(vm)
        Button(
            onClick = { vm.login(s, benutzer, passwort) },
            enabled = !vm.busy && benutzer.isNotBlank() && passwort.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Anmelden") }
    }
}

@Composable
private fun HomeScreen(vm: AppViewModel, s: Screen.Home) {
    val me = s.me
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hallo ${me.displayName}", style = MaterialTheme.typography.titleLarge)
        ErrorText(vm)
        Kachel("Einkaufsliste", me.appAktiv(AppKey.SHOPPING), onClick = { vm.show(Screen.Lists(s.instance, me)) })
        // Die folgenden Bereiche folgen mit den nächsten Phasen der API.
        Kachel("Rezepte", false, bald = me.appAktiv(AppKey.RECIPES))
        Kachel("OweMe", false, bald = me.appAktiv(AppKey.OWEME))
        Kachel("Health", false, bald = me.appAktiv(AppKey.HEALTH))
    }
}

@Composable
private fun Kachel(name: String, aktiv: Boolean, bald: Boolean = false, onClick: () -> Unit = {}) {
    // `bald`: auf der Instanz freigeschaltet, in der App aber noch nicht umgesetzt.
    // Nicht freigeschaltete Bereiche werden gar nicht erst angezeigt.
    if (!aktiv && !bald) return
    Card(Modifier.fillMaxWidth().then(if (aktiv) Modifier.clickable(onClick = onClick) else Modifier)) {
        Column(Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (!aktiv) Text("Kommt bald", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ListsScreen(vm: AppViewModel, s: Screen.Lists) {
    LaunchedEffect(s) { vm.loadLists(s) }
    var neu by remember { mutableStateOf(false) }
    var loeschen by remember { mutableStateOf<ShoppingList?>(null) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ErrorText(vm)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.lists, key = { it.key }) { l ->
                    Card(Modifier.fillMaxWidth().clickable { vm.show(Screen.Items(s.instance, s.me, l.key, l.title)) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${l.openCount} offen" + if (!l.isOwner) " · von ${l.owner}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (l.isOwner) {
                                IconButton(onClick = { loeschen = l }) { Icon(Icons.Default.Delete, "Liste löschen") }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { neu = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Neue Liste")
        }
    }
    if (neu) {
        TitelDialog("Neue Liste", onOk = { neu = false; vm.createList(s, it) }, onCancel = { neu = false })
    }
    loeschen?.let { l ->
        AlertDialog(
            onDismissRequest = { loeschen = null },
            title = { Text("Liste löschen?") },
            text = { Text("„${l.title}“ wird mit allen Artikeln gelöscht, auch bei Mitnutzern.") },
            confirmButton = { TextButton(onClick = { loeschen = null; vm.deleteList(s, l) }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { loeschen = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun TitelDialog(titel: String, onOk: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(titel) },
        text = { OutlinedTextField(text, { text = it }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onOk(text) }, enabled = text.isNotBlank()) { Text("Anlegen") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

@Composable
private fun ItemsScreen(vm: AppViewModel, s: Screen.Items) {
    LaunchedEffect(s) { vm.loadItems(s) }
    var neu by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                neu, { neu = it; vm.clearError() }, label = { Text("Artikel hinzufügen") },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { vm.addItem(s, neu); neu = "" },
                enabled = neu.isNotBlank() && !vm.busy,
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Hinzufügen") }
        }
        ErrorText(vm)
        Spacer(Modifier.height(8.dp))
        // Offene Artikel zuerst, erledigte darunter.
        LazyColumn {
            items(vm.items.sortedBy { it.done }, key = { it.id }) { a -> ArtikelZeile(vm, s, a) }
        }
    }
}

@Composable
private fun ArtikelZeile(vm: AppViewModel, s: Screen.Items, a: ShoppingItem) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = a.done, onCheckedChange = { vm.toggle(s, a) })
        Column(Modifier.weight(1f)) {
            Text(
                a.name,
                textDecoration = if (a.done) TextDecoration.LineThrough else null,
                style = MaterialTheme.typography.bodyLarge,
            )
            val zusatz = listOf(a.amount, a.category, a.store).filter { it.isNotBlank() }.joinToString(" · ")
            if (zusatz.isNotEmpty()) Text(zusatz, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { vm.deleteItem(s, a) }) { Icon(Icons.Default.Delete, "Artikel löschen") }
    }
}
