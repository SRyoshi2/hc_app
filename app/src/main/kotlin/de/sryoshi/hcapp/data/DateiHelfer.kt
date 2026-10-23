package de.sryoshi.hcapp.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class DateiDaten(val bytes: ByteArray, val name: String, val mime: String)

/** Dateien für Dokumente (Arztbesuche): lesen vor dem Hochladen, schreiben nach dem Herunterladen. */
object DateiHelfer {
    /** So groß darf ein Dokument höchstens sein; der Server lehnt mehr ab. */
    const val MAX_BYTES = 25L * 1024 * 1024

    suspend fun lesen(context: Context, uri: Uri): DateiDaten = withContext(Dispatchers.IO) {
        val aufloeser = context.contentResolver
        var name = "dokument"
        var groesse = -1L
        aufloeser.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { groesse = c.getLong(it) }
            }
        }
        if (groesse > MAX_BYTES) throw IOException("Die Datei ist zu groß (höchstens 25 MB).")
        val bytes = aufloeser.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Die Datei lässt sich nicht lesen.")
        if (bytes.size > MAX_BYTES) throw IOException("Die Datei ist zu groß (höchstens 25 MB).")
        DateiDaten(bytes, name, aufloeser.getType(uri) ?: "application/octet-stream")
    }

    suspend fun schreiben(context: Context, uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val strom = context.contentResolver.openOutputStream(uri) ?: throw IOException("Die Datei lässt sich nicht speichern.")
        strom.use { it.write(bytes) }
    }
}
