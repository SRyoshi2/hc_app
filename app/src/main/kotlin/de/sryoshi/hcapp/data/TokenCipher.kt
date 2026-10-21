package de.sryoshi.hcapp.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Verschlüsselt Tokens mit einem Schlüssel im Android Keystore (AES-256-GCM); der Schlüssel verlässt das Gerät nie. */
object TokenCipher {
    private const val ALIAS = "hcapp_tokens"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LAENGE = 12

    private fun schluessel(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun verschluesseln(klartext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, schluessel()) }
        val daten = cipher.iv + cipher.doFinal(klartext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(daten, Base64.NO_WRAP)
    }

    /** `null`, wenn sich der Wert nicht entschlüsseln lässt (z. B. Schlüssel nach Neuinstallation weg). */
    fun entschluesseln(text: String): String? = runCatching {
        val daten = Base64.decode(text, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, schluessel(), GCMParameterSpec(128, daten, 0, IV_LAENGE))
        }
        String(cipher.doFinal(daten, IV_LAENGE, daten.size - IV_LAENGE), Charsets.UTF_8)
    }.getOrNull()
}
