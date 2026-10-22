package de.sryoshi.hcapp.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/** Eine gespeicherte HomeCenter-Instanz. Das Token liegt getrennt und verschlüsselt. */
@Serializable
data class Instance(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    /** Produktname laut Instanz („Family Manager“ / „Office Manager“). */
    val name: String,
    /** `family` oder `office` */
    val mode: String,
    val username: String,
    val displayName: String,
)

class InstanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("instances", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Instance.serializer())

    fun alle(): List<Instance> =
        prefs.getString(LISTE, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    fun speichern(instanz: Instance, token: String) {
        val neu = alle().filterNot { it.id == instanz.id } + instanz
        prefs.edit()
            .putString(LISTE, json.encodeToString(serializer, neu))
            .putString(tokenSchluessel(instanz.id), TokenCipher.verschluesseln(token))
            .apply()
    }

    /** Ändert die Angaben einer bekannten Instanz (Name, Betriebsart), ohne das Token anzufassen. */
    fun aktualisieren(instanz: Instance) {
        val neu = alle().map { if (it.id == instanz.id) instanz else it }
        prefs.edit().putString(LISTE, json.encodeToString(serializer, neu)).apply()
    }

    fun token(id: String): String? = prefs.getString(tokenSchluessel(id), null)?.let(TokenCipher::entschluesseln)

    /** Ist für die Instanz ein Token gespeichert? (Ohne zu entschlüsseln.) */
    fun hatToken(id: String): Boolean = prefs.contains(tokenSchluessel(id))

    fun tokenVerwerfen(id: String) = prefs.edit().remove(tokenSchluessel(id)).apply()

    fun entfernen(id: String) {
        prefs.edit()
            .putString(LISTE, json.encodeToString(serializer, alle().filterNot { it.id == id }))
            .remove(tokenSchluessel(id))
            .apply()
    }

    private fun tokenSchluessel(id: String) = "token_$id"

    private companion object {
        const val LISTE = "liste"
    }
}
