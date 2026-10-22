package de.sryoshi.hcapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.sryoshi.hcapp.AppViewModel
import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.core.CalorieDay
import de.sryoshi.hcapp.core.CalorieEntry
import de.sryoshi.hcapp.core.CalorieEntryPatch
import de.sryoshi.hcapp.core.FoodSuggestion
import de.sryoshi.hcapp.core.Portionsrechner
import de.sryoshi.hcapp.ui.theme.Hc
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

/** Zahl mit Komma statt Punkt, ohne überflüssige Nullen (`12.0` → `"12"`, `7.9` → `"7,9"`). */
private fun zahlText(d: Double): String = Portionsrechner.text(d).replace('.', ',')

// ── Tagesansicht ────────────────────────────────────────────────────────

@Composable
fun HealthDayScreen(vm: AppViewModel, s: Screen.HealthDay) {
    LaunchedEffect(s) { vm.loadDay(s) }
    val t = vm.kalorienTag?.takeIf { it.date == s.date }
    var neu by remember { mutableStateOf(false) }
    var bearbeite by remember { mutableStateOf<CalorieEntry?>(null) }
    val tag = runCatching { LocalDate.parse(s.date) }.getOrNull() ?: LocalDate.now()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.show(Screen.HealthDay(s.instance, s.me, tag.minusDays(1).toString())) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vortag")
                    }
                    DatumFeld("Tag", s.date, { d -> if (d != null) vm.show(Screen.HealthDay(s.instance, s.me, d)) }, Modifier.weight(1f))
                    IconButton(onClick = { vm.show(Screen.HealthDay(s.instance, s.me, tag.plusDays(1).toString())) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Folgetag")
                    }
                }
            }
            item { ErrorText(vm) }
            if (t == null) {
                if (vm.busy) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else {
                item { TagesKarte(t) }
                if (t.seededCount > 0) {
                    item {
                        Text(
                            "${t.seededCount} Mahlzeit${if (t.seededCount == 1) "" else "en"} aus deinem Speiseplan übernommen. Du kannst sie ändern oder löschen.",
                            style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                        )
                    }
                }
                if (t.entries.isEmpty()) item { Text("Noch nichts erfasst. Mit „+“ trägst du eine Mahlzeit ein.", color = Hc.farben.muted) }
                items(t.entries, key = { it.id }) { e -> EintragZeile(e) { bearbeite = e } }
            }
            item {
                TextButton(onClick = { vm.show(Screen.HealthMonth(s.instance, s.me, s.date.take(7))) }) { Text("Monatsübersicht") }
            }
        }
        FloatingActionButton(onClick = { neu = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Mahlzeit erfassen") }
    }

    if (neu) {
        MahlzeitDialog(
            vm = vm, s = s, vorhanden = null,
            onSave = { neu = false; vm.addCalorieEntry(s, it) },
            onVorlage = { id -> neu = false; vm.applyMealTemplate(s, id) },
            onDelete = null, onCancel = { neu = false },
        )
    }
    bearbeite?.let { e ->
        MahlzeitDialog(
            vm = vm, s = s, vorhanden = e,
            onSave = { bearbeite = null; vm.updateCalorieEntry(s, e.id, it) },
            onVorlage = {}, onDelete = { bearbeite = null; vm.deleteCalorieEntry(s, e.id) }, onCancel = { bearbeite = null },
        )
    }
}

@Composable
private fun TagesKarte(t: CalorieDay) {
    HcKarte(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(kcalText(t.totalKcal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (t.dailyNeed != null) {
                LinearProgressIndicator(
                    progress = { (t.percent / 100f).coerceIn(0f, 1f) },
                    color = if (t.status == "over") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = Hc.farben.card2, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                )
                Text(
                    "von ${kcalText(t.dailyNeed)} (${t.percent} %)" + when {
                        t.excessKcal != null -> " · ${kcalText(t.excessKcal)} zu viel"
                        t.remainingKcal != null -> " · noch ${kcalText(t.remainingKcal)}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium, color = statusFarbe(t.status),
                )
                statusText(t.status).takeIf { it.isNotBlank() && t.totalKcal > 0 }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = statusFarbe(t.status)) }
                if (t.trainingKcal > 0) {
                    Text("Bedarf inklusive ${kcalText(t.trainingKcal)} Training", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                }
            } else {
                Text("Für einen Bedarf fehlen Angaben im Health-Profil.", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
            }
        }
    }
}

@Composable
private fun EintragZeile(e: CalorieEntry, onClick: () -> Unit) {
    HcKarte(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${e.kcalPerServing} kcal × ${zahlDe(e.servings)}" + if (e.source == "mealplan") " · Speiseplan" else "",
                    style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                )
                naehrwerteZeile(e.proteinG, e.fatG, e.carbsG)?.let {
                    Text(it + if (e.nutrientsEstimated) " (geschätzt)" else "", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                }
            }
            Text(kcalText(e.totalKcal), fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Mahlzeit erfassen / ändern ──────────────────────────────────────────

/**
 * Erfassen und Ändern einer Mahlzeit. Beim Erfassen kommen beim Tippen Vorschläge aus dem Bundeslebensmittelschlüssel;
 * ein Treffer öffnet den Portionsrechner mit den Werten je 100 g, und das Ergebnis füllt kcal und Nährwerte.
 * Nährwerte werden beim Ändern nur gesendet, wenn sie geändert wurden (sonst bliebe nichts von einer KI-Schätzung).
 */
@Composable
private fun MahlzeitDialog(
    vm: AppViewModel, s: Screen.HealthDay, vorhanden: CalorieEntry?,
    onSave: (CalorieEntryPatch) -> Unit, onVorlage: (Long) -> Unit, onDelete: (() -> Unit)?, onCancel: () -> Unit,
) {
    val neu = vorhanden == null
    var titel by remember { mutableStateOf(vorhanden?.title.orEmpty()) }
    var kcal by remember { mutableStateOf(vorhanden?.kcalPerServing?.toString().orEmpty()) }
    var portionen by remember { mutableStateOf(vorhanden?.let { zahlDe(it.servings) } ?: "1") }
    val eiweissAlt = remember { zahlDe(vorhanden?.proteinG) }
    val fettAlt = remember { zahlDe(vorhanden?.fatG) }
    val khAlt = remember { zahlDe(vorhanden?.carbsG) }
    var eiweiss by remember { mutableStateOf(eiweissAlt) }
    var fett by remember { mutableStateOf(fettAlt) }
    var kh by remember { mutableStateOf(khAlt) }
    var naehrOffen by remember { mutableStateOf(eiweissAlt.isNotEmpty() || fettAlt.isNotEmpty() || khAlt.isNotEmpty()) }
    var rechner by remember { mutableStateOf(false) }
    var vorbelegung by remember { mutableStateOf<FoodSuggestion?>(null) }
    var vorlagenWahl by remember { mutableStateOf(false) }
    var unterdruecken by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }

    // Vorschläge: erst nach einer kurzen Pause im Tippen fragen, nicht bei jedem Buchstaben. Wird der Titel durch
    // einen Treffer gesetzt, soll das keine neue Suche auslösen.
    LaunchedEffect(titel) {
        if (!neu) return@LaunchedEffect
        if (unterdruecken) {
            unterdruecken = false
            return@LaunchedEffect
        }
        delay(300)
        vm.searchFoods(s, titel)
    }
    DisposableEffect(Unit) { onDispose { vm.clearFoods() } }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (neu) "Mahlzeit erfassen" else "Mahlzeit ändern") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(titel, { titel = it }, label = { Text("Bezeichnung (z. B. Haferflocken)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (neu && vm.foods.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().background(Hc.farben.card2, RoundedCornerShape(10.dp))) {
                        vm.foods.forEach { f ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    titel = f.name
                                    unterdruecken = true
                                    vm.clearFoods()
                                    vorbelegung = f
                                    rechner = true
                                }.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(f.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                Text("${f.kcal100g} kcal/100 g", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                            }
                        }
                        Text(
                            "Bundeslebensmittelschlüssel 4.0 · Max Rubner-Institut",
                            style = MaterialTheme.typography.labelSmall, color = Hc.farben.muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        kcal, { kcal = it.filter(Char::isDigit).take(5) }, label = { Text("kcal je Portion") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    Dezimalfeld(portionen, { portionen = it }, "Portionen", Modifier.weight(0.7f))
                }
                OutlinedButton(onClick = { vorbelegung = null; rechner = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Portion ausrechnen (Angabe je 100 g)")
                }
                TextButton(onClick = { naehrOffen = !naehrOffen }) { Text(if (naehrOffen) "Nährwerte ausblenden" else "Nährwerte eintragen") }
                if (naehrOffen) NaehrwerteFelder(eiweiss, { eiweiss = it }, fett, { fett = it }, kh, { kh = it })
                if (neu) TextButton(onClick = { vorlagenWahl = true }) { Text("Aus Mahlzeitvorlage übernehmen") }
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("  Eintrag löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val k = kcal.trim().toIntOrNull()
                val p = Portionsrechner.zahl(portionen)
                val e = Portionsrechner.zahl(eiweiss); val f = Portionsrechner.zahl(fett); val c = Portionsrechner.zahl(kh)
                fehler = when {
                    titel.isBlank() -> "Bitte eine Bezeichnung eintragen."
                    k == null || k !in 0..20000 -> "kcal je Portion: eine ganze Zahl von 0 bis 20000."
                    p == null || p <= 0 || p > 99 -> "Portionen: eine Zahl größer 0, höchstens 99."
                    eiweiss.isBlank() != fett.isBlank() -> "Eiweiß und Fett bitte zusammen angeben."
                    (eiweiss.isNotBlank() && e == null) || (fett.isNotBlank() && f == null) || (kh.isNotBlank() && c == null) -> "Nährwerte: Zahlen ab 0."
                    else -> null
                }
                if (fehler == null) {
                    val patch = CalorieEntryPatch().title(titel.trim()).kcalPerServing(k!!).servings(Portionsrechner.text(p!!))
                    if (neu) {
                        e?.let { patch.proteinG(Portionsrechner.text(it)) }
                        f?.let { patch.fatG(Portionsrechner.text(it)) }
                        c?.let { patch.carbsG(Portionsrechner.text(it)) }
                    } else {
                        if (eiweiss != eiweissAlt) patch.proteinG(e?.let(Portionsrechner::text))
                        if (fett != fettAlt) patch.fatG(f?.let(Portionsrechner::text))
                        if (kh != khAlt) patch.carbsG(c?.let(Portionsrechner::text))
                    }
                    onSave(patch)
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )

    if (rechner) {
        PortionsrechnerDialog(
            vorbelegung = vorbelegung,
            onUebernehmen = { ergebnisKcal, naehrwerte ->
                kcal = ergebnisKcal.toString()
                if (naehrwerte is Portionsrechner.Naehrwerte.Voll) {
                    eiweiss = zahlText(naehrwerte.eiweiss)
                    fett = zahlText(naehrwerte.fett)
                    kh = naehrwerte.kohlenhydrate?.let(::zahlText).orEmpty()
                    naehrOffen = true
                }
                rechner = false
                vorbelegung = null
            },
            onCancel = { rechner = false; vorbelegung = null },
        )
    }
    if (vorlagenWahl) {
        VorlagenWahlDialog(vm, s, onWahl = { id -> vorlagenWahl = false; onVorlage(id) }, onCancel = { vorlagenWahl = false })
    }
}

// ── Portionsrechner ─────────────────────────────────────────────────────

/**
 * Der Rechner aus dem Kalorientracker des Webs: Angaben je 100 g und das tatsächlich Gegessene in Gramm ergeben kcal und
 * Nährwerte der Portion. Kommt der Aufruf von einem Vorschlag, sind die Werte je 100 g schon eingetragen und der
 * Cursor steht bei den Gramm.
 */
@Composable
private fun PortionsrechnerDialog(vorbelegung: FoodSuggestion?, onUebernehmen: (Int, Portionsrechner.Naehrwerte) -> Unit, onCancel: () -> Unit) {
    fun je100(d: Double?) = d?.let(::zahlText).orEmpty()
    var kcal100 by remember { mutableStateOf(vorbelegung?.kcal100g?.toString().orEmpty()) }
    var gramm by remember { mutableStateOf("") }
    var eiweiss100 by remember { mutableStateOf(je100(vorbelegung?.protein100g)) }
    var fett100 by remember { mutableStateOf(je100(vorbelegung?.fat100g)) }
    var kh100 by remember { mutableStateOf(je100(vorbelegung?.carbs100g)) }

    val gewicht = Portionsrechner.zahl(gramm)
    val kcal = Portionsrechner.kcal(Portionsrechner.zahl(kcal100), gewicht)
    val naehrwerte = Portionsrechner.naehrwerte(Portionsrechner.zahl(eiweiss100), Portionsrechner.zahl(fett100), Portionsrechner.zahl(kh100), gewicht)

    val fokus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fokus.requestFocus() } }
    fun eins(d: Double) = String.format(Locale.GERMANY, "%.1f", d)

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Portion ausrechnen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vorbelegung?.let { Text(it.name, fontWeight = FontWeight.SemiBold) }
                Text(
                    "Auf der Packung stehen die Werte je 100 g. Trage ein, wie viel du tatsächlich gegessen hast.",
                    style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dezimalfeld(kcal100, { kcal100 = it }, "kcal je 100 g", Modifier.weight(1f).then(if (vorbelegung == null) Modifier.focusRequester(fokus) else Modifier))
                    Dezimalfeld(gramm, { gramm = it }, "Gegessen in g", Modifier.weight(1f).then(if (vorbelegung != null) Modifier.focusRequester(fokus) else Modifier))
                }
                Text(
                    kcal?.let { "= " + String.format(Locale.GERMANY, "%,d", it) + " kcal" } ?: "–",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                )
                Text("Nährwerte je 100 g (freiwillig)", style = MaterialTheme.typography.labelLarge, color = Hc.farben.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dezimalfeld(eiweiss100, { eiweiss100 = it }, "Eiweiß", Modifier.weight(1f))
                    Dezimalfeld(fett100, { fett100 = it }, "Fett", Modifier.weight(1f))
                    Dezimalfeld(kh100, { kh100 = it }, "KH", Modifier.weight(1f))
                }
                when (naehrwerte) {
                    is Portionsrechner.Naehrwerte.Voll -> Text(
                        "Eiweiß ${eins(naehrwerte.eiweiss)} g · Fett ${eins(naehrwerte.fett)} g" +
                            (naehrwerte.kohlenhydrate?.let { " · Kohlenhydrate ${eins(it)} g" } ?: "") + " je Portion",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Portionsrechner.Naehrwerte.Unvollstaendig -> Text(
                        "Eiweiß und Fett gehören zusammen – sonst werden die Nährwerte nicht übernommen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                    Portionsrechner.Naehrwerte.Leer -> {}
                }
            }
        },
        confirmButton = { TextButton(onClick = { kcal?.let { onUebernehmen(it, naehrwerte) } }, enabled = kcal != null) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

// ── Aus Vorlage ─────────────────────────────────────────────────────────

@Composable
private fun VorlagenWahlDialog(vm: AppViewModel, s: Screen.HealthDay, onWahl: (Long) -> Unit, onCancel: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadTemplates(s.instance) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Mahlzeitvorlage") },
        text = {
            LazyColumn {
                if (vm.vorlagen.isEmpty()) item { Text(if (vm.busy) "Lädt …" else "Noch keine Vorlagen. Du legst sie unter Health → Mahlzeitvorlagen an.", color = Hc.farben.muted) }
                items(vm.vorlagen, key = { it.id }) { v ->
                    Column(Modifier.fillMaxWidth().clickable { onWahl(v.id) }.padding(vertical = 10.dp)) {
                        Text(v.name, style = MaterialTheme.typography.bodyLarge)
                        Text(kcalText(v.totalKcal), style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}
