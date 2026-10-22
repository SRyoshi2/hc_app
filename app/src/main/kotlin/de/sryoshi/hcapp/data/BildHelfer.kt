package de.sryoshi.hcapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

class BildDaten(val bytes: ByteArray, val name: String, val mime: String)

/**
 * Bereitet ein vom Nutzer gewähltes Bild für den Upload vor. Der Server lehnt Dateien über 10 MB ab und liest nur
 * gängige Formate; Handyfotos sind oft größer oder im HEIC-Format.
 *
 * - Kleine JPEG/PNG/WebP-Dateien gehen unverändert hoch (inklusive Ausrichtung).
 * - Alles andere wird auf höchstens [MAX_KANTE] Pixel verkleinert und als JPEG gesendet. Die Drehung aus den
 *   EXIF-Daten wird dabei in die Pixel übernommen, weil sie sonst verloren ginge.
 */
object BildHelfer {
    private const val MAX_BYTES = 9 * 1024 * 1024
    private const val MAX_KANTE = 2048
    private val DIREKT = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp")

    suspend fun vorbereiten(context: Context, uri: Uri): BildDaten = withContext(Dispatchers.IO) {
        val aufloeser = context.contentResolver
        val mime = aufloeser.getType(uri).orEmpty()
        val original = aufloeser.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Das Bild lässt sich nicht lesen.")

        val endung = DIREKT[mime]
        if (endung != null && original.size <= MAX_BYTES) return@withContext BildDaten(original, "rezept.$endung", mime)

        val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, grenzen)
        if (grenzen.outWidth <= 0 || grenzen.outHeight <= 0) throw IOException("Das ist keine unterstützte Bilddatei.")

        // Grob verkleinern beim Lesen (spart Speicher), danach genau auf die Zielgröße.
        var teiler = 1
        while (max(grenzen.outWidth, grenzen.outHeight) / (teiler * 2) >= MAX_KANTE) teiler *= 2
        val optionen = BitmapFactory.Options().apply { inSampleSize = teiler }
        val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size, optionen)
            ?: throw IOException("Das ist keine unterstützte Bilddatei.")

        val gedreht = drehen(bitmap, grad(original))
        val faktor = MAX_KANTE.toFloat() / max(gedreht.width, gedreht.height)
        val fertig = if (faktor < 1f) {
            Bitmap.createScaledBitmap(gedreht, (gedreht.width * faktor).toInt().coerceAtLeast(1), (gedreht.height * faktor).toInt().coerceAtLeast(1), true)
        } else gedreht

        val aus = ByteArrayOutputStream()
        fertig.compress(Bitmap.CompressFormat.JPEG, 85, aus)
        BildDaten(aus.toByteArray(), "rezept.jpg", "image/jpeg")
    }

    private fun grad(daten: ByteArray): Int = try {
        when (ExifInterface(ByteArrayInputStream(daten)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: IOException) {
        0
    }

    private fun drehen(bitmap: Bitmap, grad: Int): Bitmap {
        if (grad == 0) return bitmap
        val matrix = Matrix().apply { postRotate(grad.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
