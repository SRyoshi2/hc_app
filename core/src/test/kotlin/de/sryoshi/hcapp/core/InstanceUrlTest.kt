package de.sryoshi.hcapp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstanceUrlTest {

    @Test
    fun `ohne Schema wird https ergaenzt`() {
        assertEquals("https://homecenter.sven-romann.de", InstanceUrl.normalisieren("homecenter.sven-romann.de"))
    }

    @Test
    fun `Leerraum und abschliessender Schraegstrich werden entfernt`() {
        assertEquals("https://hc.example.de", InstanceUrl.normalisieren("  https://hc.example.de/  "))
    }

    @Test
    fun `Gross- und Kleinschreibung des Hosts wird vereinheitlicht`() {
        assertEquals("https://hc.example.de", InstanceUrl.normalisieren("HTTPS://HC.Example.DE"))
    }

    @Test
    fun `Standardport entfaellt, anderer Port bleibt`() {
        assertEquals("https://hc.example.de", InstanceUrl.normalisieren("https://hc.example.de:443"))
        assertEquals("https://hc.example.de:8443", InstanceUrl.normalisieren("https://hc.example.de:8443"))
    }

    @Test
    fun `Unterpfad bleibt erhalten`() {
        assertEquals("https://example.de/hc", InstanceUrl.normalisieren("https://example.de/hc/"))
    }

    @Test
    fun `http im lokalen Netz ist erlaubt`() {
        assertEquals("http://192.168.1.20:8000", InstanceUrl.normalisieren("http://192.168.1.20:8000"))
        assertEquals("http://nas.local:8000", InstanceUrl.normalisieren("http://nas.local:8000/"))
        assertEquals("http://homeserver", InstanceUrl.normalisieren("http://homeserver"))
        assertEquals("http://10.0.2.2:8000", InstanceUrl.normalisieren("http://10.0.2.2:8000"))
    }

    @Test
    fun `http im Internet wird abgelehnt`() {
        assertFailsWith<InvalidInstanceUrl> { InstanceUrl.normalisieren("http://homecenter.sven-romann.de") }
        assertFailsWith<InvalidInstanceUrl> { InstanceUrl.normalisieren("http://8.8.8.8") }
    }

    @Test
    fun `ungueltige Eingaben werden abgelehnt`() {
        assertFailsWith<InvalidInstanceUrl> { InstanceUrl.normalisieren("") }
        assertFailsWith<InvalidInstanceUrl> { InstanceUrl.normalisieren("   ") }
        assertFailsWith<InvalidInstanceUrl> { InstanceUrl.normalisieren("ftp://hc.example.de") }
        assertFailsWith<InvalidInstanceUrl> { InstanceUrl.normalisieren("https://user:geheim@hc.example.de") }
        assertFailsWith<InvalidInstanceUrl> { InstanceUrl.normalisieren("https://hc.example.de/?a=1") }
    }

    @Test
    fun `lokale Adressen`() {
        for (h in listOf("localhost", "127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.0.5",
            "169.254.1.1", "::1", "fd12::1", "fe80::1", "meinnas", "drucker.local", "x.lan")) {
            assertTrue(InstanceUrl.istLokal(h), h)
        }
    }

    @Test
    fun `oeffentliche Adressen`() {
        for (h in listOf("example.de", "8.8.8.8", "172.32.0.1", "172.15.0.1", "192.169.0.1", "11.0.0.1",
            "300.1.1.1.com")) {
            assertFalse(InstanceUrl.istLokal(h), h)
        }
    }
}
