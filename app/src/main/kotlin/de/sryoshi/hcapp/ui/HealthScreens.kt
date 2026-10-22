package de.sryoshi.hcapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sryoshi.hcapp.AppViewModel
import de.sryoshi.hcapp.Screen
import de.sryoshi.hcapp.core.CalorieDay
import de.sryoshi.hcapp.core.Datum
import de.sryoshi.hcapp.core.HealthProfile
import de.sryoshi.hcapp.core.HealthProfilePatch
import de.sryoshi.hcapp.core.MealTemplate
import de.sryoshi.hcapp.core.MealTemplatePatch
import de.sryoshi.hcapp.core.Portionsrechner
import de.sryoshi.hcapp.ui.theme.Hc
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// ── Gemeinsames ─────────────────────────────────────────────────────────

/** `2420` → `"2.420 kcal"`. */
internal fun kcalText(kcal: Int?): String = if (kcal == null) "–" else String.format(Locale.GERMANY, "%,d", kcal) + " kcal"

/** `"82.4"` → `"82,4"`, `"7.40"` → `"7,4"`. */
internal fun zahlDe(text: String?): String = zahlKurz(text).replace('.', ',')

/** Ampel des Tages: `under` (zu wenig), `ok`, `over` (zu viel) – wie im Web. */
@Composable
internal fun statusFarbe(status: String?): Color = when (status) {
    "ok" -> Hc.farben.ok
    "over" -> MaterialTheme.colorScheme.error
    "under" -> Hc.farben.warn
    else -> Hc.farben.muted
}

internal fun statusText(status: String?): String = when (status) {
    "ok" -> "im Rahmen"
    "over" -> "über dem Bedarf"
    "under" -> "unter dem Bedarf"
    else -> ""
}

/** Zahlenfeld mit Komma oder Punkt (Nachkommastellen, Gramm, Gewicht …). */
@Composable
internal fun Dezimalfeld(wert: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, fehler: Boolean = false) {
    OutlinedTextField(
        wert, { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) }, label = { Text(label) }, singleLine = true, isError = fehler,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier,
    )
}

/** Fortschrittsbalken in der Farbe der App. */
@Composable
private fun Balken(anteil: Float, farbe: Color) {
    LinearProgressIndicator(
        progress = { anteil.coerceIn(0f, 1f) }, color = farbe, trackColor = Hc.farben.card2,
        modifier = Modifier.fillMaxWidth().height(8.dp),
    )
}

@Composable
private fun Laden(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ErrorText(vm)
        if (vm.busy) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

// ── Einstieg: Einrichten oder Übersicht ─────────────────────────────────

@Composable
fun HealthScreen(vm: AppViewModel, s: Screen.Health) {
    LaunchedEffect(s) { vm.loadHealth(s) }
    val p = vm.healthProfil
    when {
        p == null -> Laden(vm)
        !p.enabled -> HealthEinrichten(vm, s, p)
        else -> HealthUebersicht(vm, s, p)
    }
}

@Composable
private fun HealthEinrichten(vm: AppViewModel, s: Screen.Health, p: HealthProfile) {
    var geburt by remember(p) { mutableStateOf(p.birthDate) }
    var groesse by remember(p) { mutableStateOf(p.heightCm?.toString().orEmpty()) }
    var geschlecht by remember(p) { mutableStateOf(p.sex) }
    var aktivitaet by remember(p) { mutableStateOf(p.activity.orEmpty()) }
    var gewicht by remember(p) { mutableStateOf(zahlDe(p.metrics.weightKg)) }
    var pruefen by remember { mutableStateOf(false) }

    val groesseZahl = groesse.trim().toIntOrNull()?.takeIf { it in 50..260 }
    val gewichtZahl = Portionsrechner.zahl(gewicht)?.takeIf { it in 20.0..400.0 }
    val vollstaendig = geburt != null && groesseZahl != null && geschlecht.isNotBlank() && aktivitaet.isNotBlank() && gewichtZahl != null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Health einrichten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Für deinen Kalorienbedarf braucht Health ein paar Angaben. Alles bleibt streng privat: nur du siehst diese Daten, sie lassen sich nicht teilen.",
                color = Hc.farben.muted, style = MaterialTheme.typography.bodyMedium,
            )
        }
        item { DatumFeld("Geburtsdatum", geburt, { geburt = it }, Modifier.fillMaxWidth()) }
        item {
            OutlinedTextField(
                groesse, { groesse = it.filter(Char::isDigit).take(3) }, label = { Text("Körpergröße in cm (50–260)") }, singleLine = true,
                isError = pruefen && groesseZahl == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Biologisches Geschlecht (für den Grundumsatz)", style = MaterialTheme.typography.labelLarge, color = Hc.farben.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.options.sex.forEach { o -> FilterChip(selected = geschlecht == o.value, onClick = { geschlecht = o.value }, label = { Text(o.label) }) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Aktivitätsgrad im Alltag (ohne Sport)", style = MaterialTheme.typography.labelLarge, color = Hc.farben.muted)
                p.options.activity.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { aktivitaet = o.value }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = aktivitaet == o.value, onClick = { aktivitaet = o.value })
                        Text(o.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Dezimalfeld(gewicht, { gewicht = it }, "Aktuelles Gewicht in kg", Modifier.fillMaxWidth(), fehler = pruefen && gewichtZahl == null)
        }
        item { ErrorText(vm) }
        item {
            if (pruefen && !vollstaendig) {
                Text("Bitte alle Angaben ausfüllen (Größe 50–260 cm, Gewicht 20–400 kg).", color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    if (!vollstaendig) pruefen = true
                    else vm.setupHealth(
                        s,
                        HealthProfilePatch().birthDate(geburt).heightCm(groesseZahl).sex(geschlecht).activity(aktivitaet),
                        String.format(Locale.US, "%.1f", gewichtZahl ?: 0.0),
                    )
                },
                enabled = !vm.busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Health aktivieren") }
        }
    }
}

// ── Übersicht ───────────────────────────────────────────────────────────

@Composable
private fun HealthUebersicht(vm: AppViewModel, s: Screen.Health, p: HealthProfile) {
    val heute = vm.healthHeute
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ErrorText(vm) }
        if (heute != null) item { HeuteKarte(heute) { vm.show(Screen.HealthDay(s.instance, s.me, heute.date)) } }
        item { KennzahlenKarte(p) }
        p.goal?.let { ziel ->
            item {
                HcKarte(Modifier.fillMaxWidth(), onClick = { vm.show(Screen.HealthWeight(s.instance, s.me)) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Ziel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${zahlDe(ziel.weightKg)} kg" + (ziel.date?.let { " bis ${Datum.anzeige(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Balken((ziel.progressPercent ?: 0) / 100f, MaterialTheme.colorScheme.primary)
                        Text(
                            when {
                                ziel.reached -> "Ziel erreicht"
                                ziel.overdue -> "Das Zieldatum ist vorbei, noch ${zahlDe(ziel.distanceKg)} kg"
                                else -> "noch ${zahlDe(ziel.distanceKg)} kg" + (ziel.daysLeft?.let { " · $it Tage" } ?: "")
                            },
                            style = MaterialTheme.typography.bodySmall, color = if (ziel.overdue) MaterialTheme.colorScheme.error else Hc.farben.muted,
                        )
                    }
                }
            }
        }
        item { NavigationsZeile("Kalorientracker – Monatsübersicht") { vm.show(Screen.HealthMonth(s.instance, s.me, LocalDate.now().toString().take(7))) } }
        item { NavigationsZeile("Gewicht") { vm.show(Screen.HealthWeight(s.instance, s.me)) } }
        item { NavigationsZeile("Mahlzeitvorlagen") { vm.show(Screen.HealthTemplates(s.instance, s.me)) } }
        item {
            Text(
                "Gesundheitschecks, Impfbuch, Laborwerte und Training folgen in einer späteren Version der App. Bis dahin findest du sie im Web.",
                style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun NavigationsZeile(text: String, onClick: () -> Unit) {
    HcKarte(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Hc.farben.muted)
        }
    }
}

@Composable
private fun HeuteKarte(tag: CalorieDay, onClick: () -> Unit) {
    val farbe = statusFarbe(tag.status)
    HcKarte(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Heute", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(kcalText(tag.totalKcal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (tag.dailyNeed != null) {
                Balken(tag.percent / 100f, if (tag.status == "over") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(
                    "von ${kcalText(tag.dailyNeed)} · " + when {
                        tag.excessKcal != null -> "${kcalText(tag.excessKcal)} zu viel"
                        tag.remainingKcal != null -> "noch ${kcalText(tag.remainingKcal)}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium, color = farbe,
                )
            } else {
                Text("Für einen Bedarf fehlen Angaben im Profil.", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
            }
            if (tag.entries.isEmpty()) Text("Noch nichts erfasst. Tippen zum Erfassen.", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
        }
    }
}

@Composable
private fun KennzahlenKarte(p: HealthProfile) {
    val m = p.metrics
    HcKarte(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Kennzahlen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Kennzahl("Gewicht", m.weightKg?.let { "${zahlDe(it)} kg" } ?: "–")
            Kennzahl("BMI", m.bmi?.let { zahlDe(it) + (m.bmiCategory?.let { k -> " ($k)" } ?: "") } ?: "–")
            m.normalWeightKg?.takeIf { it.size == 2 && it[0] != null && it[1] != null }?.let { Kennzahl("Normalgewicht", "${zahlDe(it[0])} – ${zahlDe(it[1])} kg") }
            Kennzahl("Grundumsatz", kcalText(m.basalMetabolicRate))
            Kennzahl("Tagesbedarf", kcalText(m.dailyNeed))
            m.age?.let { Kennzahl("Alter", "$it Jahre") }
        }
    }
}

@Composable
private fun Kennzahl(name: String, wert: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), color = Hc.farben.muted)
        Text(wert, fontWeight = FontWeight.SemiBold)
    }
}

// ── Monatsübersicht ─────────────────────────────────────────────────────

@Composable
fun HealthMonthScreen(vm: AppViewModel, s: Screen.HealthMonth) {
    LaunchedEffect(s) { vm.loadMonth(s) }
    val m = vm.kalorienMonat?.takeIf { it.month == s.month }
    val monat = runCatching { YearMonth.parse(s.month) }.getOrNull() ?: YearMonth.now()
    val heute = LocalDate.now().toString()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.show(Screen.HealthMonth(s.instance, s.me, monat.minusMonths(1).toString())) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vormonat") }
            Text(
                monat.month.getDisplayName(TextStyle.FULL, Locale.GERMAN) + " ${monat.year}", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
            IconButton(onClick = { vm.show(Screen.HealthMonth(s.instance, s.me, monat.plusMonths(1).toString())) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Folgemonat") }
        }
        ErrorText(vm)
        if (m == null) {
            if (vm.busy) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        HcKarte(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Kennzahl("Durchschnitt je erfasstem Tag", kcalText(m.averageKcal))
                Kennzahl("Durchschnittlicher Bedarf", kcalText(m.averageNeed ?: m.dailyNeed))
                Kennzahl("Erfasste Tage", m.trackedDays.toString())
                if (m.trainingKcal > 0) Kennzahl("Training im Monat", kcalText(m.trainingKcal))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = Hc.farben.muted)
            }
        }
        // Der Monatserste steht in der Spalte seines Wochentags (Montag = erste Spalte).
        val leer = monat.atDay(1).dayOfWeek.value - 1
        LazyVerticalGrid(
            columns = GridCells.Fixed(7), modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(leer) { Spacer(Modifier.aspectRatio(0.8f)) }
            items(m.days.size) { i ->
                val t = m.days[i]
                val tag = t.date.takeLast(2).trimStart('0')
                val rahmen = if (t.date == heute) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Hc.farben.line)
                Column(
                    Modifier.aspectRatio(0.8f).background(Hc.farben.card, RoundedCornerShape(8.dp)).border(rahmen, RoundedCornerShape(8.dp))
                        .clickable { vm.show(Screen.HealthDay(s.instance, s.me, t.date)) }.padding(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(tag, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    if (t.totalKcal > 0) {
                        Text(t.totalKcal.toString(), fontSize = 10.sp, color = Hc.farben.muted, maxLines = 1)
                        Box(Modifier.size(8.dp).background(statusFarbe(t.status), CircleShape))
                    }
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Legende("im Rahmen", Hc.farben.ok)
            Legende("darunter", Hc.farben.warn)
            Legende("darüber", MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Legende(text: String, farbe: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(farbe, CircleShape))
        Text("  $text", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
    }
}

// ── Gewicht ─────────────────────────────────────────────────────────────

@Composable
fun HealthWeightScreen(vm: AppViewModel, s: Screen.HealthWeight) {
    LaunchedEffect(s) { vm.loadWeights(s) }
    val p = vm.healthProfil
    var datum by remember { mutableStateOf<String?>(LocalDate.now().toString()) }
    var kg by remember { mutableStateOf("") }
    var pruefen by remember { mutableStateOf(false) }
    var zielDialog by remember { mutableStateOf(false) }
    val kgZahl = Portionsrechner.zahl(kg)?.takeIf { it in 20.0..400.0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ErrorText(vm) }
        item {
            HcKarte(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Aktuell", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                    Text(p?.metrics?.weightKg?.let { "${zahlDe(it)} kg" } ?: "–", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    p?.goal?.let { z ->
                        Balken((z.progressPercent ?: 0) / 100f, MaterialTheme.colorScheme.primary)
                        Text(
                            "Ziel ${zahlDe(z.weightKg)} kg" + (z.date?.let { " bis ${Datum.anzeige(it)}" } ?: "") + " · " +
                                if (z.reached) "erreicht" else "noch ${zahlDe(z.distanceKg)} kg",
                            style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                        )
                    }
                    TextButton(onClick = { zielDialog = true }) { Text(if (p?.goal == null) "Ziel setzen" else "Ziel ändern") }
                }
            }
        }
        item {
            HcKarte(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gewicht erfassen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    DatumFeld("Datum", datum, { datum = it }, Modifier.fillMaxWidth())
                    Dezimalfeld(kg, { kg = it }, "Gewicht in kg", Modifier.fillMaxWidth(), fehler = pruefen && kgZahl == null)
                    Text("Ein zweites Wiegen am selben Tag ersetzt den Wert.", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                    Button(
                        onClick = {
                            if (kgZahl == null) pruefen = true
                            else { vm.addWeight(s, String.format(Locale.US, "%.1f", kgZahl), datum); kg = ""; pruefen = false }
                        },
                        enabled = !vm.busy, modifier = Modifier.fillMaxWidth(),
                    ) { Text("Speichern") }
                }
            }
        }
        item { Text("VERLAUF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Hc.farben.muted) }
        if (vm.gewichte.isEmpty()) item { Text("Noch kein Gewicht erfasst.", color = Hc.farben.muted) }
        items(vm.gewichte.sortedByDescending { it.date }, key = { it.id }) { w ->
            HcKarte(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Datum.anzeige(w.date), Modifier.weight(1f))
                    Text("${zahlDe(w.weightKg)} kg", fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { vm.deleteWeight(s, w.id) }) { Icon(Icons.Default.Delete, "Eintrag löschen", tint = Hc.farben.muted) }
                }
            }
        }
    }

    if (zielDialog) {
        ZielDialog(
            kgVorher = p?.targetWeightKg, datumVorher = p?.targetDate,
            onSave = { zielDialog = false; vm.updateHealthGoal(s, it) }, onCancel = { zielDialog = false },
        )
    }
}

@Composable
private fun ZielDialog(kgVorher: String?, datumVorher: String?, onSave: (HealthProfilePatch) -> Unit, onCancel: () -> Unit) {
    var kg by remember { mutableStateOf(zahlDe(kgVorher)) }
    var datum by remember { mutableStateOf(datumVorher) }
    val kgZahl = Portionsrechner.zahl(kg)?.takeIf { it in 20.0..400.0 }
    val gueltig = kg.isBlank() || kgZahl != null
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Zielgewicht") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Dezimalfeld(kg, { kg = it }, "Zielgewicht in kg (leer = kein Ziel)", Modifier.fillMaxWidth(), fehler = !gueltig)
                DatumFeld("Ziel bis (optional)", datum, { datum = it }, Modifier.fillMaxWidth(), loeschbar = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        if (kgZahl == null) HealthProfilePatch().targetWeightKg(null).targetDate(null)
                        else HealthProfilePatch().targetWeightKg(String.format(Locale.US, "%.1f", kgZahl)).targetDate(datum),
                    )
                },
                enabled = gueltig,
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

// ── Mahlzeitvorlagen ────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthTemplatesScreen(vm: AppViewModel, s: Screen.HealthTemplates) {
    LaunchedEffect(s) { vm.loadTemplates(s.instance) }
    var neu by remember { mutableStateOf(false) }
    var bearbeite by remember { mutableStateOf<MealTemplate?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ErrorText(vm) }
            if (vm.vorlagen.isEmpty() && !vm.busy) {
                item { Text("Noch keine Vorlagen. Mit „+“ legst du eine Mahlzeit an, die du oft isst – im Kalorientracker übernimmst du sie mit einem Tipp.", color = Hc.farben.muted) }
            }
            items(vm.vorlagen, key = { it.id }) { v ->
                HcKarte(Modifier.fillMaxWidth(), onClick = { bearbeite = v }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(v.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${kcalText(v.kcalPerServing)} × ${zahlDe(v.servings)} Portion${if (zahlDe(v.servings) == "1") "" else "en"} = ${kcalText(v.totalKcal)}",
                            style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                        )
                        naehrwerteZeile(v.proteinG, v.fatG, v.carbsG)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted) }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { neu = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Neue Vorlage") }
    }

    if (neu) {
        VorlageDialog(vorhanden = null, onSave = { neu = false; vm.createMealTemplate(s, it) }, onDelete = null, onCancel = { neu = false })
    }
    bearbeite?.let { v ->
        VorlageDialog(
            vorhanden = v, onSave = { bearbeite = null; vm.updateMealTemplate(s, v.id, it) },
            onDelete = { bearbeite = null; vm.deleteMealTemplate(s, v.id) }, onCancel = { bearbeite = null },
        )
    }
}

/** `Eiweiß 7,9 g · Fett 4 g · Kohlenhydrate 32 g` oder `null`, wenn nichts vorliegt. */
internal fun naehrwerteZeile(eiweiss: String?, fett: String?, kh: String?): String? {
    val teile = listOfNotNull(
        eiweiss?.let { "Eiweiß ${zahlDe(it)} g" }, fett?.let { "Fett ${zahlDe(it)} g" }, kh?.let { "Kohlenhydrate ${zahlDe(it)} g" },
    )
    return teile.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun VorlageDialog(vorhanden: MealTemplate?, onSave: (MealTemplatePatch) -> Unit, onDelete: (() -> Unit)?, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(vorhanden?.name.orEmpty()) }
    var kcal by remember { mutableStateOf(vorhanden?.kcalPerServing?.toString().orEmpty()) }
    var portionen by remember { mutableStateOf(vorhanden?.let { zahlDe(it.servings) } ?: "1") }
    var eiweiss by remember { mutableStateOf(zahlDe(vorhanden?.proteinG)) }
    var fett by remember { mutableStateOf(zahlDe(vorhanden?.fatG)) }
    var kh by remember { mutableStateOf(zahlDe(vorhanden?.carbsG)) }
    val eiweissAlt = remember { zahlDe(vorhanden?.proteinG) }
    val fettAlt = remember { zahlDe(vorhanden?.fatG) }
    val khAlt = remember { zahlDe(vorhanden?.carbsG) }
    var fehler by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (vorhanden == null) "Neue Vorlage" else "Vorlage bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Bezeichnung (z. B. Müsli mit Joghurt)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        kcal, { kcal = it.filter(Char::isDigit).take(5) }, label = { Text("kcal je Portion") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    Dezimalfeld(portionen, { portionen = it }, "Portionen", Modifier.weight(1f))
                }
                NaehrwerteFelder(eiweiss, { eiweiss = it }, fett, { fett = it }, kh, { kh = it })
                fehler?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("  Vorlage löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val k = kcal.trim().toIntOrNull()
                val p = Portionsrechner.zahl(portionen)
                val e = Portionsrechner.zahl(eiweiss); val f = Portionsrechner.zahl(fett); val c = Portionsrechner.zahl(kh)
                fehler = when {
                    name.isBlank() -> "Bitte eine Bezeichnung eintragen."
                    k == null || k !in 0..20000 -> "kcal je Portion: eine ganze Zahl von 0 bis 20000."
                    p == null || p <= 0 || p > 99 -> "Portionen: eine Zahl größer 0, höchstens 99."
                    (eiweiss.isBlank() != fett.isBlank()) -> "Eiweiß und Fett bitte zusammen angeben."
                    (eiweiss.isNotBlank() && e == null) || (fett.isNotBlank() && f == null) || (kh.isNotBlank() && c == null) -> "Nährwerte: Zahlen ab 0."
                    else -> null
                }
                if (fehler == null) {
                    val patch = MealTemplatePatch().name(name.trim()).kcalPerServing(k!!).servings(Portionsrechner.text(p!!))
                    // Nährwerte nur senden, wenn neu oder geändert: Handeingabe macht aus einer KI-Schätzung eine eigene Angabe.
                    if (vorhanden == null || eiweiss != eiweissAlt || fett != fettAlt || kh != khAlt) {
                        patch.proteinG(e?.let(Portionsrechner::text)).fatG(f?.let(Portionsrechner::text)).carbsG(c?.let(Portionsrechner::text))
                    }
                    onSave(patch)
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

/** Eiweiß, Fett und Kohlenhydrate je Portion in Gramm (optional; Eiweiß und Fett nur zusammen). */
@Composable
internal fun NaehrwerteFelder(
    eiweiss: String, onEiweiss: (String) -> Unit, fett: String, onFett: (String) -> Unit, kh: String, onKh: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Nährwerte je Portion in g (optional)", style = MaterialTheme.typography.labelLarge, color = Hc.farben.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dezimalfeld(eiweiss, onEiweiss, "Eiweiß", Modifier.weight(1f))
            Dezimalfeld(fett, onFett, "Fett", Modifier.weight(1f))
            Dezimalfeld(kh, onKh, "KH", Modifier.weight(1f))
        }
        Text("Eiweiß und Fett bitte zusammen angeben.", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
    }
}
