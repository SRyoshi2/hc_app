package de.sryoshi.hcapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.sryoshi.hcapp.AppViewModel
import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.core.Datum
import de.sryoshi.hcapp.core.Geld
import de.sryoshi.hcapp.core.OweMeAccount
import de.sryoshi.hcapp.core.OweMeAccountPatch
import de.sryoshi.hcapp.core.OweMeDirection
import de.sryoshi.hcapp.core.OweMeEntry
import de.sryoshi.hcapp.core.OweMeEntryPatch
import de.sryoshi.hcapp.core.OweMeKind
import de.sryoshi.hcapp.core.OweMePlan
import de.sryoshi.hcapp.core.OweMePlanInput
import de.sryoshi.hcapp.core.OweMePlanInterval
import de.sryoshi.hcapp.core.OweMePlanMode
import de.sryoshi.hcapp.core.OweMeRateInput
import de.sryoshi.hcapp.ui.theme.Hc
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ── Gemeinsames ─────────────────────────────────────────────────────────

/** „Ich bekomme“ (verliehen) in der Petrol-Farbe der App, „ich schulde“ (geliehen) in Ocker, wie im Web. */
private val bekommeFarbe: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val schuldeFarbe: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary

private fun heute(): String = LocalDate.now().toString()

private fun istPositiv(betrag: String): Boolean = (betrag.toBigDecimalOrNull()?.signum() ?: 0) > 0

/**
 * Datumsfeld mit Kalenderauswahl. [iso] und das Ergebnis sind `JJJJ-MM-TT`; angezeigt wird `TT.MM.JJJJ`.
 * Mit [loeschbar] lässt sich das Datum wieder entfernen (für optionale Angaben).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatumFeld(label: String, iso: String?, onChange: (String?) -> Unit, modifier: Modifier = Modifier, loeschbar: Boolean = false) {
    var offen by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = Datum.anzeige(iso), onValueChange = {}, readOnly = true, singleLine = true, label = { Text(label) },
        trailingIcon = {
            Row {
                if (loeschbar && iso != null) IconButton(onClick = { onChange(null) }) { Icon(Icons.Default.Clear, "Datum entfernen") }
                IconButton(onClick = { offen = true }) { Icon(Icons.Default.DateRange, "Datum wählen") }
            }
        },
        modifier = modifier,
    )
    if (offen) {
        val start = iso?.let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
        val zustand = rememberDatePickerState(initialSelectedDateMillis = start)
        DatePickerDialog(
            onDismissRequest = { offen = false },
            confirmButton = {
                TextButton(onClick = {
                    zustand.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    offen = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { offen = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = zustand) }
    }
}

@Composable
private fun Betragsfeld(wert: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, fehler: Boolean = false) {
    OutlinedTextField(
        wert, { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) }, label = { Text(label) }, singleLine = true, isError = fehler,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier,
    )
}

// ── Übersicht ───────────────────────────────────────────────────────────

@Composable
fun OweMeUebersichtScreen(vm: AppViewModel, s: Screen.OweMeUebersicht) {
    LaunchedEffect(s) { vm.loadOweMe(s) }
    val u = vm.oweMe
    var neu by remember { mutableStateOf(false) }
    var erledigtOffen by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ErrorText(vm) }
            if (u == null) {
                if (vm.busy) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else {
                item {
                    HcKarte(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SummenZeile("Ich bekomme", Geld.anzeige(u.totalReceivable), bekommeFarbe)
                            SummenZeile("Ich schulde", Geld.anzeige(u.totalPayable), schuldeFarbe)
                            HorizontalDivider(color = Hc.farben.line)
                            SummenZeile("Saldo", Geld.anzeige(u.net), Hc.farben.ink, fett = true)
                        }
                    }
                }
                if (u.due.isNotEmpty()) {
                    item {
                        Text(
                            if (u.due.size == 1) "1 Konto ist fällig." else "${u.due.size} Konten sind fällig.",
                            color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (u.receivable.isEmpty() && u.payable.isEmpty() && u.settled.isEmpty()) {
                    item { Text("Noch keine Konten. Mit „+“ legst du das erste an.", color = Hc.farben.muted, modifier = Modifier.padding(vertical = 16.dp)) }
                }
                abschnitt("Ich bekomme", u.receivable, s, vm)
                abschnitt("Ich schulde", u.payable, s, vm)
                if (u.settled.isNotEmpty()) {
                    item {
                        TextButton(onClick = { erledigtOffen = !erledigtOffen }) {
                            Text((if (erledigtOffen) "Erledigt ausblenden" else "Erledigt anzeigen") + " (${u.settled.size})")
                        }
                    }
                    if (erledigtOffen) items(u.settled, key = { it.key }) { k -> KontoZeile(k) { vm.show(Screen.OweMeKonto(s.instance, s.me, k.key, k.personName)) } }
                }
            }
        }
        FloatingActionButton(onClick = { neu = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Neues Konto")
        }
    }
    if (neu) {
        KontoDialog(vorhanden = null, onSave = { neu = false; vm.createOweMeAccount(s, it) }, onCancel = { neu = false })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.abschnitt(titel: String, konten: List<OweMeAccount>, s: Screen.OweMeUebersicht, vm: AppViewModel) {
    if (konten.isEmpty()) return
    item {
        Text(titel.uppercase(), color = Hc.farben.muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp))
    }
    items(konten, key = { it.key }) { k -> KontoZeile(k) { vm.show(Screen.OweMeKonto(s.instance, s.me, k.key, k.personName)) } }
}

@Composable
private fun SummenZeile(name: String, betrag: String, farbe: Color, fett: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), fontWeight = if (fett) FontWeight.Bold else FontWeight.Normal)
        Text(betrag, color = farbe, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KontoZeile(k: OweMeAccount, onClick: () -> Unit) {
    val farbe = if (k.verliehen) bekommeFarbe else schuldeFarbe
    HcKarte(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(farbe))
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(k.personName, style = MaterialTheme.typography.titleMedium)
                val zeile = listOfNotNull(
                    k.note.takeIf { it.isNotBlank() },
                    k.dueDate?.let { "zurück bis ${Datum.anzeige(it)}" },
                ).joinToString(" · ")
                if (zeile.isNotBlank()) Text(zeile, style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted, maxLines = 2)
                if (k.isDue) {
                    val tage = k.daysOverdue?.takeIf { it > 0 }?.let { " seit $it Tag${if (it == 1) "" else "en"}" }.orEmpty()
                    Text("fällig$tage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                Geld.anzeige(k.balance), color = farbe, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

// ── Konto anlegen / bearbeiten ──────────────────────────────────────────

@Composable
private fun KontoDialog(vorhanden: OweMeAccount?, onSave: (OweMeAccountPatch) -> Unit, onCancel: () -> Unit) {
    var richtung by remember { mutableStateOf(vorhanden?.direction ?: OweMeDirection.LENT) }
    var name by remember { mutableStateOf(vorhanden?.personName.orEmpty()) }
    var frist by remember { mutableStateOf(vorhanden?.dueDate) }
    var notiz by remember { mutableStateOf(vorhanden?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (vorhanden == null) "Neues Konto" else "Konto bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OweMeDirection.alle.forEach { (code, bezeichnung) ->
                        FilterChip(selected = richtung == code, onClick = { richtung = code }, label = { Text(bezeichnung) })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DatumFeld("Zurück bis (optional)", frist, { frist = it }, Modifier.fillMaxWidth(), loeschbar = true)
                OutlinedTextField(notiz, { notiz = it }, label = { Text("Notiz (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(OweMeAccountPatch().direction(richtung).personName(name.trim()).dueDate(frist).note(notiz.trim())) },
                enabled = name.isNotBlank(),
            ) { Text(if (vorhanden == null) "Anlegen" else "Speichern") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

// ── Kontoauszug ─────────────────────────────────────────────────────────

@Composable
fun OweMeKontoScreen(vm: AppViewModel, s: Screen.OweMeKonto) {
    LaunchedEffect(s) { vm.loadOweMeAccount(s) }
    val d = vm.konto?.takeIf { it.account.key == s.key }
    var neueBewegung by remember { mutableStateOf<String?>(null) }
    var bearbeiteBewegung by remember { mutableStateOf<OweMeEntry?>(null) }
    var kontoBearbeiten by remember { mutableStateOf(false) }
    var loeschen by remember { mutableStateOf(false) }
    val exportieren = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) vm.exportOweMe(s, uri) }

    if (d == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ErrorText(vm)
            if (vm.busy) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    val k = d.account
    val farbe = if (k.verliehen) bekommeFarbe else schuldeFarbe

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            HcKarte(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(k.directionLabel.ifBlank { if (k.verliehen) "Ich habe verliehen" else "Ich habe geliehen" }, color = Hc.farben.muted, style = MaterialTheme.typography.bodySmall)
                    Text(Geld.anzeige(k.balance), color = farbe, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            k.isOverpaid -> "Mehr zurückgezahlt als gegeben"
                            k.isSettled -> "Beglichen"
                            else -> "noch offen"
                        },
                        style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                    )
                    Spacer(Modifier.height(6.dp))
                    SummenZeile("Gegeben", Geld.anzeige(k.totalGiven), Hc.farben.ink)
                    SummenZeile("Zurückgezahlt", Geld.anzeige(k.totalReturned), bekommeFarbe)
                    k.dueDate?.let {
                        Text(
                            "Zurück bis ${Datum.anzeige(it)}" + if (k.isDue) " – fällig" else "",
                            color = if (k.isDue) MaterialTheme.colorScheme.error else Hc.farben.muted, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (k.note.isNotBlank()) Text(k.note, style = MaterialTheme.typography.bodyMedium)
                    if (k.shared) Text("Mit anderen geteilt", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                }
            }
        }
        item { ErrorText(vm) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { neueBewegung = OweMeKind.GIVEN }, modifier = Modifier.weight(1f)) { Text("Geld gegeben") }
                OutlinedButton(onClick = { neueBewegung = OweMeKind.RETURNED }, modifier = Modifier.weight(1f)) { Text("Rückzahlung") }
            }
        }
        item { PlanKarte(vm, s, k, d.plan) }
        item { Text("KONTOAUSZUG", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Hc.farben.muted, modifier = Modifier.padding(top = 6.dp)) }
        if (d.entries.isEmpty()) {
            item { Text("Noch keine Bewegungen.", color = Hc.farben.muted) }
        }
        // Neueste zuerst; der Stand „danach“ kommt vom Server und stimmt in jeder Zeile.
        items(d.entries.reversed(), key = { it.id }) { e -> BewegungZeile(e) { bearbeiteBewegung = e } }
        if (k.isOwner) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { vm.teilenOeffnen(s.instance, "oweme", k.key, s.title) }, modifier = Modifier.weight(1f)) { Text("Teilen") }
                    OutlinedButton(onClick = { exportieren.launch("${s.title.ifBlank { "Konto" }}.csv") }, modifier = Modifier.weight(1f)) { Text("CSV-Export") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { kontoBearbeiten = true }, modifier = Modifier.weight(1f)) { Text("Konto bearbeiten") }
                    TextButton(onClick = { loeschen = true }, modifier = Modifier.weight(1f)) { Text("Konto löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    neueBewegung?.let { art ->
        EintragDialog(
            art = art, vorhanden = null,
            onSave = { neueBewegung = null; vm.addOweMeEntry(s, art, it) },
            onDelete = null, onCancel = { neueBewegung = null },
        )
    }
    bearbeiteBewegung?.let { e ->
        EintragDialog(
            art = e.kind, vorhanden = e,
            onSave = { bearbeiteBewegung = null; vm.updateOweMeEntry(s, e.id, it) },
            onDelete = { bearbeiteBewegung = null; vm.deleteOweMeEntry(s, e.id) },
            onCancel = { bearbeiteBewegung = null },
        )
    }
    if (kontoBearbeiten) {
        KontoDialog(vorhanden = k, onSave = { kontoBearbeiten = false; vm.updateOweMeAccount(s, it) }, onCancel = { kontoBearbeiten = false })
    }
    if (loeschen) {
        AlertDialog(
            onDismissRequest = { loeschen = false },
            title = { Text("Konto löschen?") },
            text = { Text("„${k.personName}“ wird mit allen Bewegungen und dem Plan gelöscht, auch bei allen, mit denen es geteilt ist.") },
            confirmButton = { TextButton(onClick = { loeschen = false; vm.deleteOweMeAccount(s) }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { loeschen = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun BewegungZeile(e: OweMeEntry, onClick: () -> Unit) {
    val rueckzahlung = e.kind == OweMeKind.RETURNED
    HcKarte(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.title.ifBlank { e.kindLabel.ifBlank { if (rueckzahlung) "Rückzahlung" else "Geld gegeben" } }, style = MaterialTheme.typography.bodyLarge)
                Text(
                    Datum.anzeige(e.date) + (e.createdBy.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (rueckzahlung) "−" else "+") + Geld.anzeige(e.amount),
                    color = if (rueckzahlung) bekommeFarbe else Hc.farben.ink, fontWeight = FontWeight.SemiBold,
                )
                e.balanceAfter?.let { Text("Stand ${Geld.anzeige(it)}", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted) }
            }
        }
    }
}

@Composable
private fun PlanKarte(vm: AppViewModel, s: Screen.OweMeKonto, k: OweMeAccount, plan: OweMePlan?) {
    HcKarte(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Rückzahlungsplan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (plan == null) {
                Text("Kein Plan. Mit einem Plan siehst du, welche Rate wann fällig ist.", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
            } else {
                Text(
                    if (plan.completed) "Alle ${plan.count} Raten sind abgedeckt." else
                        "${plan.paidCount} von ${plan.count} Raten bezahlt" + (plan.nextDueDate?.let { " · nächste am ${Datum.anzeige(it)}" } ?: ""),
                )
                if (plan.overdueCount > 0) {
                    Text("${plan.overdueCount} überfällig", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
            OutlinedButton(
                onClick = { vm.show(Screen.OweMeRatenplan(s.instance, s.me, s.key, s.title)) },
                enabled = plan != null || istPositiv(k.balance),
            ) { Text(if (plan == null) "Plan aufstellen" else "Plan ansehen oder ändern") }
        }
    }
}

// ── Bewegung erfassen / ändern ──────────────────────────────────────────

@Composable
private fun EintragDialog(art: String, vorhanden: OweMeEntry?, onSave: (OweMeEntryPatch) -> Unit, onDelete: (() -> Unit)?, onCancel: () -> Unit) {
    var datum by remember { mutableStateOf<String?>(vorhanden?.date ?: heute()) }
    var titel by remember { mutableStateOf(vorhanden?.title.orEmpty()) }
    var betrag by remember { mutableStateOf(vorhanden?.amount?.let { zahlKurzGeld(it) }.orEmpty()) }
    var pruefen by remember { mutableStateOf(false) }
    val normal = Geld.normalisieren(betrag)
    val ueberschrift = when {
        vorhanden != null -> "Bewegung ändern"
        art == OweMeKind.RETURNED -> "Rückzahlung"
        else -> "Geld gegeben"
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(ueberschrift) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DatumFeld("Datum", datum, { datum = it }, Modifier.fillMaxWidth())
                OutlinedTextField(titel, { titel = it }, label = { Text("Titel (z. B. Rate Oktober)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Betragsfeld(betrag, { betrag = it }, "Betrag in €", Modifier.fillMaxWidth(), fehler = pruefen && normal == null)
                if (pruefen && normal == null) Text("Bitte einen Betrag über 0 mit höchstens zwei Nachkommastellen eingeben.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("  Bewegung löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = datum
                if (normal == null || d == null) pruefen = true
                else onSave(OweMeEntryPatch().date(d).title(titel.trim()).amount(normal))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

/** `"100.00"` → `"100"`, `"12.50"` → `"12,5"` zum Bearbeiten im Eingabefeld. */
private fun zahlKurzGeld(text: String): String = zahlKurz(text).replace('.', ',')

// ── Rückzahlungsplan ────────────────────────────────────────────────────

private class FreieRate(datum: String? = null, betrag: String = "") {
    var datum by mutableStateOf(datum)
    var betrag by mutableStateOf(betrag)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OweMeRatenplanScreen(vm: AppViewModel, s: Screen.OweMeRatenplan) {
    LaunchedEffect(s) {
        if (vm.konto?.account?.key != s.key) vm.loadOweMeAccount(Screen.OweMeKonto(s.instance, s.me, s.key, s.title))
    }
    val d = vm.konto?.takeIf { it.account.key == s.key }
    if (d == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ErrorText(vm)
            if (vm.busy) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }

    var weg by rememberSaveable { mutableStateOf(OweMePlanMode.RATE) }
    var start by rememberSaveable { mutableStateOf<String?>(heute()) }
    var rhythmus by rememberSaveable { mutableStateOf("monthly") }
    var rate by rememberSaveable { mutableStateOf("") }
    var anzahl by rememberSaveable { mutableStateOf("") }
    var ende by rememberSaveable { mutableStateOf<String?>(null) }
    val freie = remember { mutableStateListOf(FreieRate()) }
    var pruefen by remember { mutableStateOf(false) }
    var beenden by remember { mutableStateOf(false) }

    val offen = d.account.balance
    val moeglich = istPositiv(offen)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        d.plan?.let { plan ->
            item {
                HcKarte(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Aktueller Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${plan.intervalLabel} · ${Geld.anzeige(plan.rateAmount)} · ${plan.paidCount} von ${plan.count} bezahlt")
                        plan.rates.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Datum.anzeige(r.dueDate) + if (r.isExtra) " (angehängt)" else "", Modifier.weight(1f))
                                Text(Geld.anzeige(r.amount) + "  ", color = Hc.farben.muted)
                                Text(
                                    when (r.status) { "bezahlt" -> "bezahlt"; "teilweise" -> "teilweise"; "ueberfaellig" -> "überfällig"; else -> "offen" },
                                    color = when (r.status) {
                                        "bezahlt" -> bekommeFarbe
                                        "ueberfaellig" -> MaterialTheme.colorScheme.error
                                        else -> Hc.farben.muted
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        TextButton(onClick = { beenden = true }) { Text("Plan beenden", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        item {
            Text(if (d.plan == null) "Plan aufstellen" else "Neuen Plan aufstellen (ersetzt den aktuellen)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Offen: ${Geld.anzeige(offen)}", color = Hc.farben.muted)
            if (!moeglich) Text("Für ein Konto ohne offenen Betrag lässt sich kein Plan aufstellen.", color = MaterialTheme.colorScheme.error)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Weg", style = MaterialTheme.typography.labelLarge, color = Hc.farben.muted)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(OweMePlanMode.RATE to "Ratenhöhe", OweMePlanMode.COUNT to "Anzahl", OweMePlanMode.END to "Enddatum", OweMePlanMode.FREE to "Einzeln")
                        .forEach { (code, name) -> FilterChip(selected = weg == code, onClick = { weg = code }, label = { Text(name) }) }
                }
            }
        }
        item { DatumFeld("Erste Rate am", start, { start = it }, Modifier.fillMaxWidth()) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Rhythmus", style = MaterialTheme.typography.labelLarge, color = Hc.farben.muted)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OweMePlanInterval.alle.forEach { (code, name) -> FilterChip(selected = rhythmus == code, onClick = { rhythmus = code }, label = { Text(name) }) }
                }
            }
        }
        when (weg) {
            OweMePlanMode.RATE -> item { Betragsfeld(rate, { rate = it }, "Betrag je Rate in €", Modifier.fillMaxWidth(), fehler = pruefen && Geld.normalisieren(rate) == null) }
            OweMePlanMode.COUNT -> item {
                OutlinedTextField(
                    anzahl, { anzahl = it.filter(Char::isDigit).take(3) }, label = { Text("Anzahl der Raten (1–240)") }, singleLine = true,
                    isError = pruefen && anzahl.toIntOrNull()?.let { it in 1..240 } != true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
            OweMePlanMode.END -> item { DatumFeld("Letzte Rate bis", ende, { ende = it }, Modifier.fillMaxWidth()) }
            else -> {
                itemsFreie(freie, pruefen)
                item { OutlinedButton(onClick = { freie.add(FreieRate()) }, modifier = Modifier.fillMaxWidth()) { Text("Rate hinzufügen") } }
            }
        }
        item { ErrorText(vm) }
        item {
            Button(
                onClick = {
                    val eingabe = planEingabe(weg, start, rhythmus, rate, anzahl, ende, freie)
                    if (eingabe == null) pruefen = true else vm.setOweMePlan(s, eingabe)
                },
                enabled = moeglich && !vm.busy, modifier = Modifier.fillMaxWidth(),
            ) { Text("Plan speichern") }
        }
    }

    if (beenden) {
        AlertDialog(
            onDismissRequest = { beenden = false },
            title = { Text("Plan beenden?") },
            text = { Text("Der Rückzahlungsplan wird gelöscht. Die Bewegungen im Kontoauszug bleiben.") },
            confirmButton = { TextButton(onClick = { beenden = false; vm.deleteOweMePlan(s) }) { Text("Beenden") } },
            dismissButton = { TextButton(onClick = { beenden = false }) { Text("Abbrechen") } },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsFreie(freie: androidx.compose.runtime.snapshots.SnapshotStateList<FreieRate>, pruefen: Boolean) {
    items(freie.size) { i ->
        val r = freie[i]
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DatumFeld("Datum", r.datum, { r.datum = it }, Modifier.weight(1.3f))
            Betragsfeld(r.betrag, { r.betrag = it }, "Betrag €", Modifier.weight(1f), fehler = pruefen && Geld.normalisieren(r.betrag) == null)
            IconButton(onClick = { if (freie.size > 1) freie.removeAt(i) }, enabled = freie.size > 1) { Icon(Icons.Default.Delete, "Rate entfernen", tint = Hc.farben.muted) }
        }
    }
}

/** Baut den Plan aus den Eingaben; `null`, wenn etwas fehlt oder ungültig ist. */
private fun planEingabe(
    weg: String, start: String?, rhythmus: String, rate: String, anzahl: String, ende: String?, freie: List<FreieRate>,
): OweMePlanInput? {
    if (start == null) return null
    return when (weg) {
        OweMePlanMode.RATE -> Geld.normalisieren(rate)?.let { OweMePlanInput(weg, start, rhythmus, rate = it) }
        OweMePlanMode.COUNT -> anzahl.toIntOrNull()?.takeIf { it in 1..240 }?.let { OweMePlanInput(weg, start, rhythmus, count = it) }
        OweMePlanMode.END -> if (ende == null || ende < start) null else OweMePlanInput(weg, start, rhythmus, end = ende)
        else -> {
            val raten = freie.map { r -> val d = r.datum; val b = Geld.normalisieren(r.betrag); if (d == null || b == null) return null else OweMeRateInput(d, b) }
            OweMePlanInput(weg, start, rhythmus, rates = raten)
        }
    }
}
