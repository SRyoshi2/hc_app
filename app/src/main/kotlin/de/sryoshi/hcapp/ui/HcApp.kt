package de.sryoshi.hcapp.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sryoshi.hcapp.R
import de.sryoshi.hcapp.AppViewModel
import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.core.AppKey
import de.sryoshi.hcapp.core.ShoppingItem
import de.sryoshi.hcapp.core.ShoppingList
import de.sryoshi.hcapp.data.Instance
import de.sryoshi.hcapp.ui.theme.Hc
import de.sryoshi.hcapp.ui.theme.Welt
import de.sryoshi.hcapp.ui.theme.kategorieFarbe
import de.sryoshi.hcapp.ui.theme.kopf

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
    val welt = when (s) {
        is Screen.Lists, is Screen.Items -> Welt.KUECHE
        else -> Welt.HOMECENTER
    }
    val (kopfFarbe, kopfText) = Hc.farben.kopf(welt)
    val zurueck = s !is Screen.Instances && !(s is Screen.AddInstance && vm.instances.isEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titel, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (zurueck) {
                        IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = kopfFarbe, titleContentColor = kopfText,
                    navigationIconContentColor = kopfText, actionIconContentColor = kopfText,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
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

/** Karte wie im Web: Kartenfläche, 1 px Linie, abgerundet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HcKarte(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val farben = CardDefaults.cardColors(containerColor = Hc.farben.card)
    val rand = BorderStroke(1.dp, Hc.farben.line)
    val form = RoundedCornerShape(14.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = form, colors = farben, border = rand, content = content)
    } else {
        Card(modifier = modifier, shape = form, colors = farben, border = rand, content = content)
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
    HcKarte(Modifier.fillMaxWidth(), onClick = { vm.open(i) }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(i.name, style = MaterialTheme.typography.titleMedium)
                Text(i.url, style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                Text("Angemeldet als ${i.displayName}", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
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
        Text("${s.meta.productName} · ${s.url}", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
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

/** Eine App im App-Center: Name je Betriebsmodus, Symbol und ob die App schon umgesetzt ist. */
private class AppEintrag(
    val schluessel: String,
    val titel: String,
    val titelOffice: String,
    @DrawableRes val symbol: Int,
    val umgesetzt: Boolean,
    val oeffnen: ((Screen.Home) -> Screen)? = null,
)

private val APPS = listOf(
    AppEintrag(AppKey.SHOPPING, "Einkaufsliste", "Einkaufsliste", R.drawable.app_einkaufsliste, true) { Screen.Lists(it.instance, it.me) },
    AppEintrag(AppKey.RECIPES, "Rezeptverwaltung", "Rezeptverwaltung", R.drawable.app_rezepte, false),
    AppEintrag(AppKey.OWEME, "OweMe", "OweMe", R.drawable.app_oweme, false),
    AppEintrag(AppKey.HEALTH, "Health", "Health", R.drawable.app_health, false),
)

/**
 * Startseite wie das „App-Center“ im Web: Kopf mit Untertitel und Suche, Trennlinie „Standard-Apps“ mit
 * Zähler, darunter Kacheln (Symbol in einer Karte, Name darunter), alphabetisch wie dort.
 * Gezeigt werden nur Apps, die die Instanz für dieses Konto freigibt.
 */
@Composable
private fun HomeScreen(vm: AppViewModel, s: Screen.Home) {
    var suche by rememberSaveable { mutableStateOf("") }
    val office = s.instance.mode == "office"
    val sichtbar = APPS
        .filter { s.me.appAktiv(it.schluessel) }
        .map { it to (if (office) it.titelOffice else it.titel) }
        .sortedBy { it.second.lowercase() }
    val treffer = sichtbar.filter { suche.isBlank() || it.second.lowercase().contains(suche.trim().lowercase()) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 88.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text("App-Center", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Alle Anwendungen deiner ${s.instance.name}-Installation",
                        style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                    )
                }
                OutlinedTextField(
                    value = suche, onValueChange = { suche = it },
                    placeholder = { Text("App suchen…") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Hc.farben.muted) },
                    singleLine = true, shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorText(vm)
            }
        }
        if (treffer.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { AbschnittKopf("Standard-Apps", treffer.size) }
            items(treffer, key = { it.first.schluessel }) { (app, name) ->
                AppKachel(name, app.symbol, app.umgesetzt) { app.oeffnen?.let { vm.show(it(s)) } }
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (sichtbar.isEmpty()) "Für dieses Konto sind keine Apps freigeschaltet." else "Keine App gefunden.",
                    color = Hc.farben.muted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
        }
    }
}

/** Trennlinie mit Bezeichnung und Zähler-Chip (`.app-section`). */
@Composable
private fun AbschnittKopf(titel: String, anzahl: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            titel.uppercase(), color = Hc.farben.muted, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp,
        )
        Text(
            anzahl.toString(), color = Hc.farben.muted, fontSize = 11.sp,
            modifier = Modifier.padding(start = 10.dp)
                .background(Hc.farben.card2, RoundedCornerShape(10.dp))
                .border(1.dp, Hc.farben.line, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp),
        )
        Box(Modifier.padding(start = 10.dp).weight(1f).height(1.dp).background(Hc.farben.line))
    }
}

/** Kachel wie `.app-tile`: 74 dp große Karte mit 40 dp Symbol, Name darunter. Nicht umgesetzte Apps sind abgeblendet. */
@Composable
private fun AppKachel(name: String, @DrawableRes symbol: Int, umgesetzt: Boolean, onClick: () -> Unit) {
    val form = RoundedCornerShape(20.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().clickable(enabled = umgesetzt, onClick = onClick),
    ) {
        Box(
            Modifier.size(74.dp).shadow(4.dp, form).background(Hc.farben.card, form).border(1.dp, Hc.farben.line, form),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(symbol), contentDescription = name,
                modifier = Modifier.size(40.dp).alpha(if (umgesetzt) 1f else 0.45f),
            )
        }
        Text(
            name, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2,
            color = if (umgesetzt) Hc.farben.ink else Hc.farben.muted,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!umgesetzt) Text("bald", fontSize = 11.sp, color = Hc.farben.muted)
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
                    HcKarte(Modifier.fillMaxWidth(), onClick = { vm.show(Screen.Items(s.instance, s.me, l.key, l.title)) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${l.openCount} offen" + if (!l.isOwner) " · von ${l.owner}" else "",
                                    style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.items.sortedBy { it.done }, key = { it.id }) { a -> ArtikelZeile(vm, s, a) }
        }
    }
}

/** Artikelzeile mit Kante in der Kategoriefarbe (wie im Web: neun Töne, stabil je Kategoriename). */
@Composable
private fun ArtikelZeile(vm: AppViewModel, s: Screen.Items, a: ShoppingItem) {
    val kante = Hc.farben.kategorieFarbe(a.category)
    HcKarte(Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(if (a.done) Hc.farben.line else kante))
            Checkbox(checked = a.done, onCheckedChange = { vm.toggle(s, a) })
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    a.name,
                    textDecoration = if (a.done) TextDecoration.LineThrough else null,
                    color = if (a.done) Hc.farben.muted else Hc.farben.ink,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val zusatz = listOf(a.amount, a.category, a.store).filter { it.isNotBlank() }.joinToString(" · ")
                if (zusatz.isNotEmpty()) {
                    Text(zusatz, style = MaterialTheme.typography.bodySmall, color = if (a.done) Hc.farben.muted else kante)
                }
            }
            IconButton(onClick = { vm.deleteItem(s, a) }) { Icon(Icons.Default.Delete, "Artikel löschen", tint = Hc.farben.muted) }
        }
    }
}
