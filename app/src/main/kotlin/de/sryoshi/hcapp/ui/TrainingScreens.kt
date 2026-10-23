package de.sryoshi.hcapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.sryoshi.hcapp.AppViewModel
import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.core.Datum
import de.sryoshi.hcapp.core.Portionsrechner
import de.sryoshi.hcapp.core.TrainingGoalsPatch
import de.sryoshi.hcapp.core.TrainingIntensity
import de.sryoshi.hcapp.core.TrainingPatch
import de.sryoshi.hcapp.core.TrainingSession
import de.sryoshi.hcapp.core.TrainingSports
import de.sryoshi.hcapp.core.TrainingTemplate
import de.sryoshi.hcapp.core.TrainingTemplatePatch
import de.sryoshi.hcapp.core.TrafficCriterion
import de.sryoshi.hcapp.core.WeeklyGoal
import de.sryoshi.hcapp.ui.theme.Hc
import java.time.LocalDate

private fun String.punkt() = trim().replace(',', '.')

private fun kurz(iso: String) = runCatching { LocalDate.parse(iso).let { "%02d.%02d.".format(it.dayOfMonth, it.monthValue) } }.getOrDefault(iso)

private val WOCHENTAGE = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

private fun tagName(iso: String) = runCatching { WOCHENTAGE[LocalDate.parse(iso).dayOfWeek.value - 1] }.getOrDefault("")

private fun einheitText(t: TrainingSession) = listOfNotNull(
    t.sportLabel.ifBlank { t.sport }, "${t.minutes} min", t.distanceKm?.let { "${zahlDe(it)} km" }, t.pace?.let { "$it min/km" },
    t.intensityLabel.takeIf { it.isNotBlank() },
).joinToString(" · ")

// ── Woche ───────────────────────────────────────────────────────────────

@Composable
fun HealthTrainingScreen(vm: AppViewModel, s: Screen.HealthTraining) {
    LaunchedEffect(s) { vm.loadTrainingWeek(s) }
    val w = vm.trainingWoche?.takeIf { s.week in it.weekStart..it.weekEnd }
    var neu by remember { mutableStateOf(false) }
    var bearbeite by remember { mutableStateOf<TrainingSession?>(null) }
    var vorlageWaehlen by remember { mutableStateOf(false) }
    var kiFragen by remember { mutableStateOf(false) }

    fun woche(tage: Long) = vm.show(Screen.HealthTraining(s.instance, s.me, LocalDate.parse(s.week).plusDays(tage).toString()))

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { woche(-7) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vorige Woche") }
                    Text(
                        if (w != null) "${kurz(w.weekStart)} – ${kurz(w.weekEnd)}" else "Woche",
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { woche(7) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nächste Woche") }
                }
            }
            item { ErrorText(vm) }
            if (w != null) {
                item {
                    HcKarte(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Diese Woche", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    "${w.totals.count} Einheit${if (w.totals.count == 1) "" else "en"}", "${w.totals.minutes} min",
                                    w.totals.km?.let { "${zahlDe(it)} km" }, kcalText(w.totals.kcal), "${w.totals.activeDays} aktive Tage",
                                ).joinToString(" · "),
                            )
                            if (w.load.sentence.isNotBlank()) Text(w.load.sentence, style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                        }
                    }
                }
                if (w.goals.isNotEmpty()) {
                    item {
                        HcKarte(Modifier.fillMaxWidth(), onClick = { vm.show(Screen.HealthTrainingGoals(s.instance, s.me)) }) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Wochenziele", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                w.goals.forEach { g ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            g.sportLabel.ifBlank { "Alle Sportarten" } + ": " + listOfNotNull(
                                                g.units?.let { "${g.doneUnits} von $it Einheiten" }, g.minutes?.let { "${g.doneMinutes} von $it min" },
                                            ).joinToString(", "),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Balken(g.percent / 100f, if (g.reached) Hc.farben.ok else MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                w.longTerm?.let { z ->
                    item {
                        HcKarte(Modifier.fillMaxWidth(), onClick = { vm.show(Screen.HealthTrainingGoals(s.instance, s.me)) }) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Langzeitziel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${zahlDe(z.current)} von ${zahlDe(z.target)}" + (z.percent?.let { " ($it %)" } ?: ""))
                                Balken((z.percent ?: 0) / 100f, MaterialTheme.colorScheme.primary)
                                Text(
                                    when {
                                        z.reached -> "Ziel erreicht"
                                        z.expired -> "Der Zeitraum ist vorbei"
                                        else -> listOfNotNull(z.weeksLeft?.let { "noch $it Wochen" }, z.perWeek?.let { "${zahlDe(it)} je Woche nötig" }).joinToString(" · ")
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                                )
                            }
                        }
                    }
                }
                items(w.days, key = { it.date }) { tag ->
                    HcKarte(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${tagName(tag.date)} ${kurz(tag.date)}" + if (tag.trainings.isEmpty()) "" else " · ${tag.minutes} min · ${kcalText(tag.kcal)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (tag.date == LocalDate.now().toString()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (tag.trainings.isEmpty()) Text("Keine Einheit", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                            tag.trainings.forEach { t ->
                                TextButton(onClick = { bearbeite = t }, contentPadding = PaddingValues(0.dp)) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(einheitText(t), color = MaterialTheme.colorScheme.onSurface)
                                        if (t.note.isNotBlank()) Text(t.note, style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    HcKarte(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Monat ${kurz(w.month.from.orEmpty())} – ${kurz(w.month.to.orEmpty())}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${w.month.count} Einheiten · ${w.month.minutes} min · ${kcalText(w.month.kcal)}")
                            w.month.sports.forEach {
                                Text(
                                    "${it.sportLabel.ifBlank { it.sport }}: ${it.count}× · ${it.minutes} min" + (it.km?.let { km -> " · ${zahlDe(km)} km" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                                )
                            }
                        }
                    }
                }
                w.aiText?.let {
                    item {
                        OutlinedButton(onClick = { kiFragen = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (w.review == null) "KI-Rückblick holen" else "KI-Rückblick erneuern")
                        }
                    }
                }
                w.review?.let { r ->
                    item {
                        HcKarte(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("KI-Rückblick", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(r.text)
                                Text("Schätzung, keine ärztliche Beratung.", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                            }
                        }
                    }
                }
            }
            item { NavigationsKarte("Trainingsvorlagen") { vm.show(Screen.HealthTrainingTemplates(s.instance, s.me)) } }
            item { NavigationsKarte("Trainingsziele") { vm.show(Screen.HealthTrainingGoals(s.instance, s.me)) } }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.trainingVorlagen.isNotEmpty()) {
                TextButton(onClick = { vorlageWaehlen = true }) { Text("Aus Vorlage") }
            }
            FloatingActionButton(onClick = { neu = true }) { Icon(Icons.Default.Add, "Neue Einheit") }
        }
    }

    val standard = LocalDate.now().toString().takeIf { w != null && it in w.weekStart..w.weekEnd } ?: w?.weekStart ?: s.week
    if (neu) EinheitDialog(null, standard, onSave = { neu = false; vm.addTraining(s, it) }, onDelete = null, onCancel = { neu = false })
    bearbeite?.let { t ->
        EinheitDialog(t, standard, onSave = { bearbeite = null; vm.updateTraining(s, t.id, it) }, onDelete = { bearbeite = null; vm.deleteTraining(s, t.id) }, onCancel = { bearbeite = null })
    }
    if (kiFragen && w?.aiText != null) {
        KiDialog("KI-Rückblick zur Woche", w.aiText, onJa = { kiFragen = false; vm.requestTrainingReview(s, w.weekStart) }, onNein = { kiFragen = false })
    }
    if (vorlageWaehlen) {
        VorlageUebernehmenDialog(vm.trainingVorlagen, standard, onOk = { id, datum -> vorlageWaehlen = false; vm.applyTrainingTemplate(s, id, datum) }, onCancel = { vorlageWaehlen = false })
    }
}

@Composable
private fun NavigationsKarte(text: String, onClick: () -> Unit) {
    HcKarte(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Hc.farben.muted)
        }
    }
}

@Composable
private fun EinheitDialog(vorhanden: TrainingSession?, standardTag: String, onSave: (TrainingPatch) -> Unit, onDelete: (() -> Unit)?, onCancel: () -> Unit) {
    var datum by remember { mutableStateOf(vorhanden?.date ?: standardTag) }
    var beginn by remember { mutableStateOf(vorhanden?.startTime.orEmpty()) }
    var sport by remember { mutableStateOf(vorhanden?.sport ?: "laufen") }
    var minuten by remember { mutableStateOf(vorhanden?.minutes?.toString().orEmpty()) }
    var intensitaet by remember { mutableStateOf(vorhanden?.intensity?.takeIf { it.isNotBlank() } ?: "mittel") }
    var km by remember { mutableStateOf(zahlDe(vorhanden?.distanceKm)) }
    var notiz by remember { mutableStateOf(vorhanden?.note.orEmpty()) }
    var fehler by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (vorhanden == null) "Neue Einheit" else "Einheit bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DatumFeld("Datum", datum, { it?.let { d -> datum = d } }, Modifier.fillMaxWidth())
                Zeitfeld(beginn, { beginn = it }, "Beginn (optional)", Modifier.fillMaxWidth())
                Wahl("Sportart", TrainingSports.alle, sport) { it?.let { v -> sport = v } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        minuten, { minuten = it.filter(Char::isDigit).take(4) }, label = { Text("Minuten") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    Dezimalfeld(km, { km = it }, "Strecke (km)", Modifier.weight(1f))
                }
                Wahl("Intensität", TrainingIntensity.alle, intensitaet) { it?.let { v -> intensitaet = v } }
                OutlinedTextField(notiz, { notiz = it }, label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth())
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("  Einheit löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = minuten.toIntOrNull()
                fehler = when {
                    m == null || m <= 0 -> "Minuten: bitte eine Zahl größer 0 eintragen."
                    km.isNotBlank() && Portionsrechner.zahl(km) == null -> "Strecke: bitte eine Zahl eintragen."
                    else -> null
                }
                if (fehler == null) {
                    onSave(
                        TrainingPatch().date(datum).startTime(beginn.ifBlank { null }).sport(sport).minutes(m).intensity(intensitaet)
                            .distanceKm(km.punkt().ifBlank { null }).note(notiz),
                    )
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

@Composable
private fun VorlageUebernehmenDialog(vorlagen: List<TrainingTemplate>, standardTag: String, onOk: (Long, String) -> Unit, onCancel: () -> Unit) {
    var datum by remember { mutableStateOf(standardTag) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Vorlage übernehmen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatumFeld("Am", datum, { it?.let { d -> datum = d } }, Modifier.fillMaxWidth())
                vorlagen.forEach { v ->
                    TextButton(onClick = { onOk(v.id, datum) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(v.name, color = MaterialTheme.colorScheme.onSurface)
                            Text("${v.sportLabel} · ${v.minutes} min", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

// ── Vorlagen ────────────────────────────────────────────────────────────

@Composable
fun HealthTrainingTemplatesScreen(vm: AppViewModel, s: Screen.HealthTrainingTemplates) {
    LaunchedEffect(s) { vm.loadTrainingTemplates(s) }
    var neu by remember { mutableStateOf(false) }
    var bearbeite by remember { mutableStateOf<TrainingTemplate?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ErrorText(vm) }
            if (vm.trainingVorlagen.isEmpty() && !vm.busy) item { Text("Noch keine Vorlagen. Lege eine Einheit an, die du oft machst.", color = Hc.farben.muted) }
            items(vm.trainingVorlagen, key = { it.id }) { v ->
                HcKarte(Modifier.fillMaxWidth(), onClick = { bearbeite = v }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(v.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(v.sportLabel, "${v.minutes} min", v.distanceKm?.let { "${zahlDe(it)} km" }, v.intensityLabel.takeIf { it.isNotBlank() }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                        )
                    }
                }
            }
        }
        FloatingActionButton(onClick = { neu = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Neue Vorlage") }
    }
    if (neu) TrainingVorlageDialog(null, onSave = { neu = false; vm.saveTrainingTemplate(s, null, it) }, onDelete = null, onCancel = { neu = false })
    bearbeite?.let { v ->
        TrainingVorlageDialog(v, onSave = { bearbeite = null; vm.saveTrainingTemplate(s, v.id, it) }, onDelete = { bearbeite = null; vm.deleteTrainingTemplate(s, v.id) }, onCancel = { bearbeite = null })
    }
}

@Composable
private fun TrainingVorlageDialog(vorhanden: TrainingTemplate?, onSave: (TrainingTemplatePatch) -> Unit, onDelete: (() -> Unit)?, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(vorhanden?.name.orEmpty()) }
    var sport by remember { mutableStateOf(vorhanden?.sport ?: "laufen") }
    var minuten by remember { mutableStateOf(vorhanden?.minutes?.toString().orEmpty()) }
    var intensitaet by remember { mutableStateOf(vorhanden?.intensity?.takeIf { it.isNotBlank() } ?: "mittel") }
    var km by remember { mutableStateOf(zahlDe(vorhanden?.distanceKm)) }
    var fehler by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (vorhanden == null) "Neue Vorlage" else "Vorlage bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Bezeichnung (z. B. Feierabendrunde)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Wahl("Sportart", TrainingSports.alle, sport) { it?.let { v -> sport = v } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        minuten, { minuten = it.filter(Char::isDigit).take(4) }, label = { Text("Minuten") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    Dezimalfeld(km, { km = it }, "Strecke (km)", Modifier.weight(1f))
                }
                Wahl("Intensität", TrainingIntensity.alle, intensitaet) { it?.let { v -> intensitaet = v } }
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("  Vorlage löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = minuten.toIntOrNull()
                fehler = when {
                    name.isBlank() -> "Bitte eine Bezeichnung eintragen."
                    m == null || m <= 0 -> "Minuten: bitte eine Zahl größer 0 eintragen."
                    km.isNotBlank() && Portionsrechner.zahl(km) == null -> "Strecke: bitte eine Zahl eintragen."
                    else -> null
                }
                if (fehler == null) {
                    onSave(TrainingTemplatePatch().name(name.trim()).sport(sport).minutes(m).intensity(intensitaet).distanceKm(km.punkt().ifBlank { null }))
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

// ── Ziele ───────────────────────────────────────────────────────────────

/** Zeile der Wochenziele im Bearbeitungsstand. */
private class ZielZeile(sport: String, einheiten: String, minuten: String) {
    var sport by mutableStateOf(sport)
    var einheiten by mutableStateOf(einheiten)
    var minuten by mutableStateOf(minuten)
}

@Composable
fun HealthTrainingGoalsScreen(vm: AppViewModel, s: Screen.HealthTrainingGoals) {
    LaunchedEffect(s) { vm.loadTrainingGoals(s) }
    val z = vm.trainingZiele
    if (z == null) {
        Column(Modifier.padding(16.dp)) { ErrorText(vm) }
        return
    }
    // Der Bearbeitungsstand hängt am geladenen Stand: nach dem Speichern kommt ein neuer und ersetzt ihn.
    val zeilen = remember(z) { z.weekly.map { ZielZeile(it.sport, it.units?.toString().orEmpty(), it.minutes?.toString().orEmpty()) }.toMutableList().let { mutableStateOf(it.toList()) } }
    var zielId by remember(z) { mutableStateOf(z.longTerm.goalId) }
    var zaehlt by remember(z) { mutableStateOf(z.longTerm.counts) }
    var text by remember(z) { mutableStateOf(z.longTerm.text) }
    var fehler by remember(z) { mutableStateOf<String?>(null) }
    val sportOptionen = listOf("" to "Alle Sportarten") + z.options.sports.ifEmpty { TrainingSports.alle.map { de.sryoshi.hcapp.core.Sport(it.first, it.second) } }.map { it.value to it.label }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ErrorText(vm) }
        item { Text("Wochenziele", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(zeilen.value.size) { i ->
            val zeile = zeilen.value[i]
            HcKarte(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Wahl("Sportart", sportOptionen, zeile.sport) { zeile.sport = it ?: "" }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            zeile.einheiten, { zeile.einheiten = it.filter(Char::isDigit).take(2) }, label = { Text("Einheiten") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            zeile.minuten, { zeile.minuten = it.filter(Char::isDigit).take(4) }, label = { Text("Minuten") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { zeilen.value = zeilen.value.filterIndexed { n, _ -> n != i } }) { Icon(Icons.Default.Delete, "Ziel entfernen") }
                    }
                }
            }
        }
        item {
            TextButton(onClick = { zeilen.value = zeilen.value + ZielZeile("", "", "") }) { Icon(Icons.Default.Add, null); Text("  Wochenziel hinzufügen") }
        }
        item { Text("Langzeitziel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            if (z.options.goals.isEmpty()) {
                Text("Ein Langzeitziel verknüpft das Training mit einem Ziel aus deinen Zielen (z. B. 500 km laufen). Lege es im Web an, dann kannst du es hier auswählen.", color = Hc.farben.muted, style = MaterialTheme.typography.bodySmall)
            } else {
                Wahl("Ziel", z.options.goals.map { it.id.toString() to it.title }, zielId?.toString()) { zielId = it?.toLongOrNull() }
            }
        }
        if (zielId != null && z.options.counts.isNotEmpty()) {
            item { Wahl("Zählt", z.options.counts.map { it.value to it.label }, zaehlt) { zaehlt = it ?: "" } }
        }
        item {
            Button(
                onClick = {
                    val liste = zeilen.value.map { WeeklyGoal(sport = it.sport, units = it.einheiten.toIntOrNull(), minutes = it.minuten.toIntOrNull()) }
                    fehler = if (liste.any { it.units == null && it.minutes == null }) "Jedes Wochenziel braucht Einheiten oder Minuten." else null
                    if (fehler == null) {
                        val patch = TrainingGoalsPatch().weekly(liste).goalId(zielId)
                        if (zielId != null && zaehlt.isNotBlank()) patch.counts(zaehlt)
                        vm.saveTrainingGoals(s, patch)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Speichern") }
            fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ── Ernährungsampel ─────────────────────────────────────────────────────

@Composable
private fun ampelFarbe(stufe: String): Color = when (stufe) {
    "gut" -> Hc.farben.ok
    "mittel" -> Hc.farben.warn
    "schlecht" -> MaterialTheme.colorScheme.error
    else -> Hc.farben.muted
}

@Composable
private fun Punkt(stufe: String) {
    Box(Modifier.size(14.dp).background(ampelFarbe(stufe), CircleShape))
}

@Composable
private fun KriterienListe(k: List<TrafficCriterion>) {
    k.forEach {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.padding(top = 4.dp)) { Punkt(it.level) }
            Column {
                Text("${it.name}${if (it.value.isNotBlank()) ": ${it.value}" else ""}", style = MaterialTheme.typography.bodyMedium)
                if (it.sentence.isNotBlank()) Text(it.sentence, style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
            }
        }
    }
}

@Composable
fun HealthTrafficScreen(vm: AppViewModel, s: Screen.HealthTraffic) {
    LaunchedEffect(s) { vm.loadTraffic(s) }
    val a = vm.ampel?.takeIf { it.source == s.source && s.week in it.weekStart..it.weekEnd }
    var kiFragen by remember { mutableStateOf(false) }
    fun neu(tage: Long = 0, quelle: String = s.source) =
        vm.show(Screen.HealthTraffic(s.instance, s.me, LocalDate.parse(s.week).plusDays(tage).toString(), quelle))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { neu(-7) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vorige Woche") }
                Text(
                    if (a != null) "${kurz(a.weekStart)} – ${kurz(a.weekEnd)}" else "Woche", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { neu(7) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nächste Woche") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.source == "tracker", { neu(quelle = "tracker") }, label = { Text("Gegessen (Tracker)") })
                FilterChip(s.source == "plan", { neu(quelle = "plan") }, label = { Text("Wochenplan") })
            }
        }
        item { ErrorText(vm) }
        if (a == null) return@LazyColumn
        item {
            HcKarte(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Punkt(a.level)
                        Text(a.label.ifBlank { "Woche" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        listOfNotNull("${a.daysWithData} Tage mit Daten", a.kcalAverage?.let { "Ø ${kcalText(it)}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                    )
                    if (s.source == "plan" && !a.hasMealPlan) {
                        Text("Es ist kein Standard-Wochenplan gewählt. Wähle ihn im Web.", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                    }
                    KriterienListe(a.summary)
                }
            }
        }
        items(a.days, key = { it.date }) { d ->
            HcKarte(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Punkt(d.level)
                        Text("${tagName(d.date)} ${kurz(d.date)}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text(d.label, style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                    }
                    if (d.kcal != null) {
                        Text(
                            kcalText(d.kcal) + (d.need?.let { " von ${kcalText(it)}" } ?: "") + " · ${d.entries} Eintr${if (d.entries == 1) "ag" else "äge"}",
                            style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                        )
                    }
                    d.estimateNote?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted) }
                    KriterienListe(d.criteria)
                }
            }
        }
        a.aiText?.let {
            item {
                OutlinedButton(onClick = { kiFragen = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (a.advice == null) "KI-Einschätzung holen" else "KI-Einschätzung erneuern")
                }
            }
        }
        a.advice?.let { r ->
            item {
                HcKarte(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("KI-Einschätzung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(r.text)
                        r.suggestions.forEach { v ->
                            Text("• ${v.title.orEmpty()}${v.reason?.let { " – $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Keine ärztliche oder ernährungsmedizinische Beratung.", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                    }
                }
            }
        }
    }
    if (kiFragen && a?.aiText != null) {
        KiDialog("KI-Einschätzung der Woche", a.aiText, onJa = { kiFragen = false; vm.requestTrafficAdvice(s) }, onNein = { kiFragen = false })
    }
}
