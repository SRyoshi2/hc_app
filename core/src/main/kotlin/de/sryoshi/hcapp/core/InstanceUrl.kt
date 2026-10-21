package de.sryoshi.hcapp.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class InvalidInstanceUrl(message: String) : Exception(message)

/**
 * Prüft und vereinheitlicht die vom Nutzer eingegebene Adresse einer HomeCenter-Instanz.
 *
 * Ergebnis ist die Basisadresse ohne abschließenden Schrägstrich, z. B. `https://homecenter.example.de`.
 * Unverschlüsseltes HTTP ist nur im lokalen Netz erlaubt (siehe [istLokal]); sonst ginge das Passwort
 * im Klartext durchs Internet.
 */
object InstanceUrl {

    fun normalisieren(eingabe: String): String {
        val roh = eingabe.trim()
        if (roh.isEmpty()) throw InvalidInstanceUrl("Bitte die Adresse der Instanz eingeben.")

        val mitSchema = if ("://" in roh) roh else "https://$roh"
        val url = mitSchema.toHttpUrlOrNull()
            ?: throw InvalidInstanceUrl("Das ist keine gültige Adresse.")
        // OkHttp kennt nur http und https; "ftp://x" wird schon oben abgelehnt.

        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw InvalidInstanceUrl("Benutzername und Passwort gehören nicht in die Adresse.")
        }
        if (url.query != null || url.fragment != null) {
            throw InvalidInstanceUrl("Die Adresse darf keine Parameter enthalten.")
        }
        if (url.scheme == "http" && !istLokal(url.host)) {
            throw InvalidInstanceUrl(
                "Unverschlüsseltes HTTP ist nur im lokalen Netz erlaubt. Bitte https:// verwenden.",
            )
        }
        return basis(url)
    }

    private fun basis(url: HttpUrl): String {
        val pfad = url.encodedPath.trimEnd('/')
        val standardPort = HttpUrl.defaultPort(url.scheme)
        val port = if (url.port == standardPort) "" else ":${url.port}"
        val host = if (':' in url.host) "[${url.host}]" else url.host
        return "${url.scheme}://$host$port$pfad"
    }

    /** Loopback, private IPv4-Bereiche (RFC 1918), Link-Local, `.local`/`.lan` und Namen ohne Punkt. */
    fun istLokal(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h == "::1") return true
        if (h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa")) return true
        val teile = h.split('.')
        if (teile.size == 4 && teile.all { it.toIntOrNull() in 0..255 }) {
            val (a, b) = teile[0].toInt() to teile[1].toInt()
            return a == 10 || a == 127 || (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) || (a == 169 && b == 254)
        }
        // IPv6: Unique-Local (fc00::/7) und Link-Local (fe80::/10)
        if (':' in h) return h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80")
        // Ein einzelnes Wort ("homeserver", "nas") gibt es im Internet nicht.
        return '.' !in h
    }
}
