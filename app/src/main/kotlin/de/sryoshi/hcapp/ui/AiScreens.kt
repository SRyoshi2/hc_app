package de.sryoshi.hcapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.sryoshi.hcapp.ui.theme.Hc

/**
 * Bestätigung vor einer KI-Anfrage: zeigt den Text, der an den KI-Anbieter der Instanz geht. Die Anfrage kostet dort Geld
 * oder Rechenzeit und braucht die Freigabe `abo_ai`; der Server verlangt dieselbe Bestätigung.
 */
@Composable
internal fun KiDialog(titel: String, text: String?, hinweis: String? = null, onJa: () -> Unit, onNein: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNein,
        title = { Text(titel) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Das geht an den KI-Anbieter deiner Instanz:", style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted)
                if (!text.isNullOrBlank()) {
                    Text(text, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    hinweis ?: "Die Antwort ist eine Schätzung und keine ärztliche oder ernährungsmedizinische Beratung.",
                    style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                )
            }
        },
        confirmButton = { TextButton(onClick = onJa) { Text("An KI senden") } },
        dismissButton = { TextButton(onClick = onNein) { Text("Abbrechen") } },
    )
}

/** Eingabe für „Rezept aus Zutaten vorschlagen“. Der Vorschlag ersetzt Titel, Beschreibung, Zutaten und Schritte des Entwurfs. */
@Composable
internal fun RezeptVorschlagDialog(onOk: (zutaten: String, portionen: Int, wunsch: String) -> Unit, onCancel: () -> Unit) {
    var zutaten by remember { mutableStateOf("") }
    var portionen by remember { mutableStateOf("2") }
    var wunsch by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Rezept vorschlagen lassen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Was hast du da? Die Zutatenliste und dein Wunsch gehen an den KI-Anbieter deiner Instanz. Der Vorschlag ersetzt " +
                        "Titel, Beschreibung, Zutaten und Schritte im Editor und wird erst mit „Speichern“ übernommen.",
                    style = MaterialTheme.typography.bodySmall, color = Hc.farben.muted,
                )
                OutlinedTextField(zutaten, { zutaten = it }, label = { Text("Zutaten (z. B. Paprika, Reis, Feta)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        portionen, { portionen = it.filter(Char::isDigit).take(2) }, label = { Text("Portionen") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(wunsch, { wunsch = it.take(300) }, label = { Text("Wunsch (optional, z. B. vegetarisch)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onOk(zutaten, (portionen.toIntOrNull() ?: 2).coerceIn(1, 99), wunsch) },
                enabled = zutaten.isNotBlank(),
            ) { Text("Vorschlag holen") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}
