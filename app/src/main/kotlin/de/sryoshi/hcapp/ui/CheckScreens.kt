package de.sryoshi.hcapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.AppViewModel
import de.sryoshi.hcapp.core.Datum
import de.sryoshi.hcapp.core.DueUnit
import de.sryoshi.hcapp.core.HealthCheck
import de.sryoshi.hcapp.core.HealthCheckPatch
import de.sryoshi.hcapp.core.HealthDocument
import de.sryoshi.hcapp.core.LabMeasurement
import de.sryoshi.hcapp.core.LabPatch
import de.sryoshi.hcapp.core.Vaccination
import de.sryoshi.hcapp.core.VaccinationPatch
import de.sryoshi.hcapp.ui.theme.Hc
import java.time.LocalDate

// ── Gemeinsame Bausteine ────────────────────────────────────────────────

/** „Fällig am 01.03.2027 (in 120 Tagen)“; `null`, wenn kein Termin berechnet ist. */
internal fun faelligText(dueDate: String?, daysUntil: Int?, daysOverdue: Int?, geschaetzt: Boolean): String? {
    if (dueDate == null) return null
    val rest = when {
        daysOverdue != null && daysOverdue > 0 -> "seit ${tage(daysOverdue)} überfällig"
        daysUntil == 0 -> "heute"
        daysUntil != null -> "in ${tage(daysUntil)}"
        else -> null
    }
    return "Fällig ${if (geschaetzt) "etwa " else ""}am ${Datum.anzeige(dueDate)}" + (rest?.let { " ($it)" } ?: "")
}

private fun tage(n: Int) = if (n == 1) "1 Tag" else "$n Tagen"

/** Auswahl aus wenigen Werten als Chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Wahl(label: String, optionen: List<Pair<String, String>>, wert: String?, onChange: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Hc.farben.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            optionen.forEach { (v, text) ->
                FilterChip(selected = wert == v, onClick = { onChange(if (wert == v) null else v) }, label = { Text(text) })
            }
        }
    }
}

/** Uhrzeit `HH:MM` (leer = keine Angabe). */
@Composable
internal fun Zeitfeld(wert: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        wert, { onChange(it.filter { c -> c.isDigit() || c == ':' }.take(5)) }, label = { Text(label) }, singleLine = true,
        placeholder = { Text("HH:MM") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier,
    )
}

@Composable
internal fun Bestaetigen(text: String, bestaetigung: String = "Löschen", onJa: () -> Unit, onNein: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNein,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onJa) { Text(bestaetigung, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onNein) { Text("Abbrechen") } },
    )
}

/** Nächster Termin: entweder ein festes Datum oder „in N Tagen/Wochen/Monaten/Jahren“ nach dem letzten. */
@Composable
internal fun FaelligkeitFelder(
    datum: String?, onDatum: (String?) -> Unit, menge: String, onMenge: (String) -> Unit, einheit: String?, onEinheit: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DatumFeld("Nächster Termin am", datum, onDatum, Modifier.fillMaxWidth(), loeschbar = true)
        Text("oder nach einem Zeitraum:", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
        OutlinedTextField(
            menge, { onMenge(it.filter(Char::isDigit).take(3)) }, label = { Text("Anzahl") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        Wahl("Einheit", DueUnit.alle, einheit, onEinheit)
    }
}

private fun String.punkt() = trim().replace(',', '.')

// ── Arztbesuche ─────────────────────────────────────────────────────────

@Composable
fun HealthChecksScreen(vm: AppViewModel, s: Screen.HealthChecks) {
    LaunchedEffect(s) { vm.loadChecks(s) }
    var neu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ErrorText(vm) }
            if (vm.checks.isEmpty() && !vm.busy) item { Text("Noch keine Arztbesuche. Mit „+“ trägst du einen ein.", color = Hc.farben.muted) }
            items(vm.checks, key = { it.id }) { c ->
                HcKarte(Modifier.fillMaxWidth(), onClick = { vm.show(Screen.HealthCheckDetail(s.instance, s.me, c.id, c.title)) }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(c.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                c.visitDate?.let { Datum.anzeige(it) + (c.visitTime?.let { t -> ", $t" } ?: "") },
                                c.doctor.takeIf { it.isNotBlank() }, if (c.isPlanned) "geplant" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                        )
                        faelligText(c.dueDate, c.daysUntilDue, c.daysOverdue, c.dueIsEstimate)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = if (c.isDue) MaterialTheme.colorScheme.error else Hc.farben.muted)
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { neu = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Neuer Arztbesuch") }
    }
    if (neu) CheckDialog(null, onSave = { neu = false; vm.saveCheck(s, null, it) }, onCancel = { neu = false })
}

/** Arztbesuch anlegen oder bearbeiten. Es wird alles gesendet; der Server führt das mit dem Bestand zusammen. */
@Composable
private fun CheckDialog(vorhanden: HealthCheck?, onSave: (HealthCheckPatch) -> Unit, onCancel: () -> Unit) {
    var titel by remember { mutableStateOf(vorhanden?.title.orEmpty()) }
    var arzt by remember { mutableStateOf(vorhanden?.doctor.orEmpty()) }
    var datum by remember { mutableStateOf(vorhanden?.visitDate ?: if (vorhanden == null) LocalDate.now().toString() else null) }
    var beginn by remember { mutableStateOf(vorhanden?.visitTime.orEmpty()) }
    var ende by remember { mutableStateOf(vorhanden?.visitEndTime.orEmpty()) }
    var notizen by remember { mutableStateOf(vorhanden?.notes.orEmpty()) }
    var faellig by remember { mutableStateOf(vorhanden?.nextDueDate) }
    var menge by remember { mutableStateOf(vorhanden?.nextDueAmount?.toString().orEmpty()) }
    var einheit by remember { mutableStateOf(vorhanden?.nextDueUnit?.takeIf { it.isNotBlank() }) }
    var fehler by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (vorhanden == null) "Neuer Arztbesuch" else "Arztbesuch bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(titel, { titel = it }, label = { Text("Titel (z. B. Zahnarzt)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(arzt, { arzt = it }, label = { Text("Arzt / Praxis") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DatumFeld("Datum", datum, { datum = it }, Modifier.fillMaxWidth(), loeschbar = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Zeitfeld(beginn, { beginn = it }, "Beginn", Modifier.weight(1f))
                    Zeitfeld(ende, { ende = it }, "Ende", Modifier.weight(1f))
                }
                OutlinedTextField(notizen, { notizen = it }, label = { Text("Notizen") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                FaelligkeitFelder(faellig, { faellig = it }, menge, { menge = it }, einheit, { einheit = it })
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                fehler = when {
                    titel.isBlank() -> "Bitte einen Titel eintragen."
                    (menge.isBlank()) != (einheit == null) -> "Zeitraum: Anzahl und Einheit gehören zusammen."
                    else -> null
                }
                if (fehler == null) {
                    onSave(
                        HealthCheckPatch().title(titel.trim()).doctor(arzt.trim()).visitDate(datum)
                            .visitTime(beginn.ifBlank { null }).visitEndTime(ende.ifBlank { null }).notes(notizen)
                            .nextDueDate(faellig).nextDueAmount(menge.toIntOrNull()).nextDueUnit(einheit),
                    )
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

@Composable
fun HealthCheckScreen(vm: AppViewModel, s: Screen.HealthCheckDetail) {
    LaunchedEffect(s) { vm.loadCheck(s) }
    val c = vm.check?.takeIf { it.id == s.id }
    var bearbeiten by remember { mutableStateOf(false) }
    var loeschen by remember { mutableStateOf(false) }
    var neueMessung by remember { mutableStateOf(false) }
    var messung by remember { mutableStateOf<LabMeasurement?>(null) }
    var gewaehlt by remember { mutableStateOf<android.net.Uri?>(null) }
    var dokLoeschen by remember { mutableStateOf<HealthDocument?>(null) }
    var kommentar by remember { mutableStateOf("") }
    var speichern by remember { mutableStateOf<HealthDocument?>(null) }

    val waehlen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) gewaehlt = uri }
    val ablegen = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val d = speichern
        speichern = null
        if (uri != null && d != null) vm.saveDocument(s, d, uri)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ErrorText(vm) }
        if (c == null) return@LazyColumn
        item {
            HcKarte(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (c.doctor.isNotBlank()) Text(c.doctor)
                    Text(
                        listOfNotNull(
                            c.visitDate?.let { Datum.anzeige(it) },
                            c.visitTime?.let { t -> if (c.visitEndTime != null) "$t–${c.visitEndTime}" else t },
                            if (c.isPlanned) "geplant" else null,
                        ).joinToString(" · "),
                        color = Hc.farben.muted,
                    )
                    faelligText(c.dueDate, c.daysUntilDue, c.daysOverdue, c.dueIsEstimate)?.let {
                        Text(it, color = if (c.isDue) MaterialTheme.colorScheme.error else Hc.farben.muted)
                    }
                    if (c.notes.isNotBlank()) Text(c.notes, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { bearbeiten = true }) { Text("Bearbeiten") }
                        TextButton(onClick = { loeschen = true }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        item { Abschnitt("Laborwerte", "Messung hinzufügen") { neueMessung = true } }
        if (c.labs.isEmpty()) item { Text("Keine Laborwerte zu diesem Besuch.", color = Hc.farben.muted) }
        items(c.labs, key = { "lab${it.id}" }) { m -> MessungKarte(m) { messung = m } }

        item { Abschnitt("Dokumente", "Datei hinzufügen") { waehlen.launch(arrayOf("*/*")) } }
        if (c.documents.isEmpty()) item { Text("Keine Dokumente.", color = Hc.farben.muted) }
        items(c.documents, key = { "dok${it.id}" }) { d ->
            HcKarte(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.title.ifBlank { d.filename }, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(d.category?.name, d.size?.let { groesse(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                        )
                    }
                    IconButton(onClick = { speichern = d; ablegen.launch(d.filename.ifBlank { d.title.ifBlank { "dokument" } }) }) { Icon(Icons.Default.Download, "Herunterladen") }
                    IconButton(onClick = { dokLoeschen = d }) { Icon(Icons.Default.Delete, "Löschen") }
                }
            }
        }

        item { Text("Kommentare", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(c.comments, key = { "kom${it.id}" }) { k ->
            HcKarte(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(k.text)
                        Text(k.createdAt.take(10).let { Datum.anzeige(it) }, style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                    }
                    IconButton(onClick = { vm.deleteComment(s, k.id) }) { Icon(Icons.Default.Delete, "Kommentar löschen") }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(kommentar, { kommentar = it }, label = { Text("Kommentar") }, modifier = Modifier.weight(1f))
                Button(onClick = { vm.addComment(s, kommentar.trim()); kommentar = "" }, enabled = kommentar.isNotBlank()) { Text("Senden") }
            }
        }
    }

    if (bearbeiten && c != null) CheckDialog(c, onSave = { bearbeiten = false; vm.updateCheck(s, it) }, onCancel = { bearbeiten = false })
    if (loeschen) Bestaetigen("Diesen Arztbesuch mit allen Dokumenten und Kommentaren löschen?", onJa = { loeschen = false; vm.deleteCheck(s) }, onNein = { loeschen = false })
    if (neueMessung) LabDialog(null, onSave = { neueMessung = false; vm.addCheckLab(s, it) }, onDelete = null, onCancel = { neueMessung = false })
    messung?.let { m ->
        LabDialog(m, onSave = { messung = null; vm.updateCheckLab(s, m.id, it) }, onDelete = { messung = null; vm.deleteCheckLab(s, m.id) }, onCancel = { messung = null })
    }
    dokLoeschen?.let { d -> Bestaetigen("„${d.title.ifBlank { d.filename }}“ löschen?", onJa = { dokLoeschen = null; vm.deleteDocument(s, d.id) }, onNein = { dokLoeschen = null }) }
    gewaehlt?.let { uri ->
        DokumentDialog(vm, s, onOk = { titel, kat -> gewaehlt = null; vm.uploadDocument(s, uri, titel, kat) }, onCancel = { gewaehlt = null })
    }
}

private fun groesse(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.GERMANY, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
private fun Abschnitt(titel: String, aktion: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(titel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onClick) { Icon(Icons.Default.Add, null); Text(" $aktion") }
    }
}

@Composable
private fun DokumentDialog(vm: AppViewModel, s: Screen.HealthCheckDetail, onOk: (String, Long?) -> Unit, onCancel: () -> Unit) {
    var titel by remember { mutableStateOf("") }
    var kategorie by remember { mutableStateOf<Long?>(null) }
    var neueKategorie by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Dokument hochladen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(titel, { titel = it }, label = { Text("Titel (leer = Dateiname)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (vm.docKategorien.isNotEmpty()) {
                    Wahl("Kategorie", vm.docKategorien.map { it.id.toString() to it.name }, kategorie?.toString()) { kategorie = it?.toLongOrNull() }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(neueKategorie, { neueKategorie = it }, label = { Text("Neue Kategorie") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.createDocCategory(s, neueKategorie.trim()); neueKategorie = "" }, enabled = neueKategorie.isNotBlank()) { Text("Anlegen") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(titel.trim(), kategorie) }) { Text("Hochladen") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

// ── Laborwerte ──────────────────────────────────────────────────────────

@Composable
private fun messungFarbe(m: LabMeasurement): Color = if (m.flagged) MaterialTheme.colorScheme.error else Hc.farben.muted

@Composable
private fun MessungKarte(m: LabMeasurement, onClick: () -> Unit) {
    HcKarte(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(m.name, style = MaterialTheme.typography.titleSmall)
            Text("${zahlDe(m.value)} ${m.unit}".trim(), style = MaterialTheme.typography.titleMedium, color = if (m.flagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(
                listOfNotNull(
                    Datum.anzeige(m.date).takeIf { it.isNotBlank() }, bereichText(m), m.classificationLabel.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = messungFarbe(m),
            )
        }
    }
}

private fun bereichText(m: LabMeasurement): String? = when {
    m.refFrom != null && m.refTo != null -> "Referenz ${zahlDe(m.refFrom)}–${zahlDe(m.refTo)}"
    m.refFrom != null -> "Referenz ab ${zahlDe(m.refFrom)}"
    m.refTo != null -> "Referenz bis ${zahlDe(m.refTo)}"
    else -> null
}

@Composable
private fun LabDialog(vorhanden: LabMeasurement?, onSave: (LabPatch) -> Unit, onDelete: (() -> Unit)?, onCancel: () -> Unit, namen: List<String> = emptyList()) {
    var name by remember { mutableStateOf(vorhanden?.name.orEmpty()) }
    var datum by remember { mutableStateOf(vorhanden?.date ?: LocalDate.now().toString()) }
    var wert by remember { mutableStateOf(zahlDe(vorhanden?.value)) }
    var einheit by remember { mutableStateOf(vorhanden?.unit.orEmpty()) }
    var von by remember { mutableStateOf(zahlDe(vorhanden?.refFrom)) }
    var bis by remember { mutableStateOf(zahlDe(vorhanden?.refTo)) }
    var notiz by remember { mutableStateOf(vorhanden?.note.orEmpty()) }
    var fehler by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (vorhanden == null) "Neuer Laborwert" else "Laborwert bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Bezeichnung (z. B. Cholesterin)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (vorhanden == null) {
                    val passend = namen.filter { name.isNotBlank() && it != name && it.contains(name, ignoreCase = true) }.take(4)
                    if (passend.isNotEmpty()) Wahl("Bekannte Bezeichnungen", passend.map { it to it }, null) { it?.let { n -> name = n } }
                }
                DatumFeld("Datum", datum, { it?.let { d -> datum = d } }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dezimalfeld(wert, { wert = it }, "Wert", Modifier.weight(1f))
                    OutlinedTextField(einheit, { einheit = it }, label = { Text("Einheit") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dezimalfeld(von, { von = it }, "Referenz von", Modifier.weight(1f))
                    Dezimalfeld(bis, { bis = it }, "Referenz bis", Modifier.weight(1f))
                }
                OutlinedTextField(notiz, { notiz = it }, label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth())
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("  Messung löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                fehler = when {
                    name.isBlank() -> "Bitte eine Bezeichnung eintragen."
                    wert.punkt().toDoubleOrNull() == null -> "Wert: bitte eine Zahl eintragen."
                    else -> null
                }
                if (fehler == null) {
                    onSave(
                        LabPatch().name(name.trim()).date(datum).value(wert.punkt()).unit(einheit.trim())
                            .refFrom(von.punkt().ifBlank { null }).refTo(bis.punkt().ifBlank { null }).note(notiz),
                    )
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

@Composable
fun HealthLabsScreen(vm: AppViewModel, s: Screen.HealthLabs) {
    LaunchedEffect(s) { vm.loadLabs(s) }
    var neu by remember { mutableStateOf(false) }
    val u = vm.labUebersicht
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ErrorText(vm) }
            if (u != null && u.flaggedCount > 0) {
                item {
                    Text(
                        "${u.flaggedCount} Wert${if (u.flaggedCount == 1) "" else "e"} außerhalb des Referenzbereichs. Das ist eine Einordnung gegen den jeweiligen Bereich, keine Diagnose.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (u != null && u.results.isEmpty() && !vm.busy) item { Text("Noch keine Laborwerte. Mit „+“ trägst du eine Messung ein.", color = Hc.farben.muted) }
            items(u?.results.orEmpty(), key = { it.key }) { g ->
                HcKarte(Modifier.fillMaxWidth(), onClick = { vm.show(Screen.HealthLabTrend(s.instance, s.me, g.name)) }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(g.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${zahlDe(g.latest.value)} ${g.latest.unit}".trim(), style = MaterialTheme.typography.titleMedium,
                            color = if (g.flagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            listOfNotNull(
                                Datum.anzeige(g.latest.date).takeIf { it.isNotBlank() }, bereichText(g.latest), g.latest.classificationLabel.takeIf { it.isNotBlank() },
                                g.difference?.let { d -> "Veränderung ${if (d.startsWith("-")) "" else "+"}${zahlDe(d)}" }, "${g.count} Messungen",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = messungFarbe(g.latest),
                        )
                    }
                }
            }
            vm.labAnalyse?.takeIf { it.aiAvailable }?.analysis?.let { a ->
                item {
                    HcKarte(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("KI-Einordnung", style = MaterialTheme.typography.titleSmall)
                            Text(a.text)
                            Text(
                                (if (a.outdated) "Seit dieser Einordnung sind Messungen dazugekommen – sie ist nicht mehr aktuell. " else "") +
                                    "Erzeugen oder erneuern kannst du sie im Web. Keine ärztliche Beratung.",
                                style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                            )
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { neu = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Neue Messung") }
    }
    if (neu) LabDialog(null, onSave = { neu = false; vm.addLab(s, it) }, onDelete = null, onCancel = { neu = false }, namen = u?.names.orEmpty())
}

@Composable
fun HealthLabTrendScreen(vm: AppViewModel, s: Screen.HealthLabTrend) {
    LaunchedEffect(s) { vm.loadLabTrend(s) }
    val v = vm.labVerlauf?.takeIf { it.name == s.name }
    var messung by remember { mutableStateOf<LabMeasurement?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ErrorText(vm) }
        if (v == null) return@LazyColumn
        if (v.points.size >= 2) item { Verlauf(v.points) }
        items(v.points.reversed(), key = { it.id }) { m -> MessungKarte(m) { messung = m } }
    }
    messung?.let { m ->
        LabDialog(m, onSave = { messung = null; vm.updateLab(s, m.id, it) }, onDelete = { messung = null; vm.deleteLab(s, m.id) }, onCancel = { messung = null })
    }
}

/** Linie der Messwerte (älteste links) mit dem Referenzbereich der jüngsten Messung als Band. */
@Composable
private fun Verlauf(punkte: List<LabMeasurement>) {
    val werte = punkte.map { it.value?.toDoubleOrNull() }
    val gueltig = werte.filterNotNull()
    if (gueltig.size < 2) return
    val letzte = punkte.last()
    val von = letzte.refFrom?.toDoubleOrNull()
    val bis = letzte.refTo?.toDoubleOrNull()
    val unten = (gueltig + listOfNotNull(von)).min()
    val oben = (gueltig + listOfNotNull(bis)).max()
    val spanne = (oben - unten).takeIf { it > 0 } ?: 1.0
    val linie = MaterialTheme.colorScheme.primary
    val band = Hc.farben.ok.copy(alpha = 0.18f)
    HcKarte(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(140.dp).padding(16.dp)) {
            fun y(w: Double) = (size.height * (1 - (w - unten) / spanne)).toFloat()
            fun x(i: Int) = size.width * i / (punkte.size - 1)
            if (von != null || bis != null) {
                val oberkante = bis?.let(::y) ?: 0f
                val unterkante = von?.let(::y) ?: size.height
                drawRect(band, Offset(0f, oberkante.coerceAtMost(unterkante)), androidx.compose.ui.geometry.Size(size.width, kotlin.math.abs(unterkante - oberkante)))
            }
            val vorhanden = werte.mapIndexedNotNull { i, w -> w?.let { Offset(x(i), y(it)) } }
            vorhanden.zipWithNext { a, b -> drawLine(linie, a, b, strokeWidth = 4f, cap = StrokeCap.Round) }
            vorhanden.forEach { drawCircle(linie, 7f, it) }
        }
    }
}

// ── Impfbuch ────────────────────────────────────────────────────────────

@Composable
fun HealthVaccinationsScreen(vm: AppViewModel, s: Screen.HealthVaccinations) {
    LaunchedEffect(s) { vm.loadVaccinations(s) }
    var neu by remember { mutableStateOf(false) }
    var bearbeite by remember { mutableStateOf<Vaccination?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ErrorText(vm) }
            if (vm.impfungen.isEmpty() && !vm.busy) item { Text("Noch keine Impfungen. Mit „+“ trägst du eine ein.", color = Hc.farben.muted) }
            items(vm.impfungen, key = { it.id }) { v ->
                HcKarte(Modifier.fillMaxWidth(), onClick = { bearbeite = v }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(v.name, style = MaterialTheme.typography.titleMedium)
                        v.lastDate?.let { Text("Zuletzt ${Datum.anzeige(it)}", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted) }
                        faelligText(v.dueDate, v.daysUntilDue, v.daysOverdue, v.dueIsEstimate)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = if (v.isDue) MaterialTheme.colorScheme.error else Hc.farben.muted)
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { neu = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Neue Impfung") }
    }
    if (neu) ImpfDialog(null, onSave = { neu = false; vm.saveVaccination(s, null, it) }, onDelete = null, onCancel = { neu = false })
    bearbeite?.let { v ->
        ImpfDialog(v, onSave = { bearbeite = null; vm.saveVaccination(s, v.id, it) }, onDelete = { bearbeite = null; vm.deleteVaccination(s, v.id) }, onCancel = { bearbeite = null })
    }
}

@Composable
private fun ImpfDialog(vorhanden: Vaccination?, onSave: (VaccinationPatch) -> Unit, onDelete: (() -> Unit)?, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(vorhanden?.name.orEmpty()) }
    var zuletzt by remember { mutableStateOf(vorhanden?.lastDate) }
    var notizen by remember { mutableStateOf(vorhanden?.notes.orEmpty()) }
    var faellig by remember { mutableStateOf(vorhanden?.nextDueDate) }
    var menge by remember { mutableStateOf(vorhanden?.nextDueAmount?.toString().orEmpty()) }
    var einheit by remember { mutableStateOf(vorhanden?.nextDueUnit?.takeIf { it.isNotBlank() }) }
    var fehler by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (vorhanden == null) "Neue Impfung" else "Impfung bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Impfung (z. B. Tetanus)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DatumFeld("Zuletzt am", zuletzt, { zuletzt = it }, Modifier.fillMaxWidth(), loeschbar = true)
                OutlinedTextField(notizen, { notizen = it }, label = { Text("Notizen") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                FaelligkeitFelder(faellig, { faellig = it }, menge, { menge = it }, einheit, { einheit = it })
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("  Impfung löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                fehler = when {
                    name.isBlank() -> "Bitte eine Bezeichnung eintragen."
                    (menge.isBlank()) != (einheit == null) -> "Zeitraum: Anzahl und Einheit gehören zusammen."
                    else -> null
                }
                if (fehler == null) {
                    onSave(
                        VaccinationPatch().name(name.trim()).lastDate(zuletzt).notes(notizen)
                            .nextDueDate(faellig).nextDueAmount(menge.toIntOrNull()).nextDueUnit(einheit),
                    )
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}
