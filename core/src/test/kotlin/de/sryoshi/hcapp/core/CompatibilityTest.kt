package de.sryoshi.hcapp.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatibilityTest {
    private fun meta(api: Int, min: Int) = Meta("Family Manager", "family", api, min, "0.93.1058")

    @Test
    fun `passende Instanz`() {
        assertEquals(Compatibility.Ok, Compatibility.pruefen(meta(api = 1, min = 1)))
    }

    @Test
    fun `Instanz mit aelterer API`() {
        assertEquals(Compatibility.InstanceTooOld(0), Compatibility.pruefen(meta(api = 0, min = 1)))
    }

    @Test
    fun `Instanz verlangt neueren Client`() {
        assertEquals(Compatibility.ClientTooOld(2), Compatibility.pruefen(meta(api = 2, min = 2)))
    }

    @Test
    fun `neuere API bleibt nutzbar, solange der Server den Client zulaesst`() {
        assertEquals(Compatibility.Ok, Compatibility.pruefen(meta(api = 2, min = 1)))
    }
}
