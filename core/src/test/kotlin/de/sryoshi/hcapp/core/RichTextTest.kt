package de.sryoshi.hcapp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RichTextTest {
    @Test
    fun `Aufzaehlung bekommt Punkte`() {
        assertEquals("<ul><li>• Eier</li><li>• Mehl</li></ul>", RichText.listenMarken("<ul><li>Eier</li><li>Mehl</li></ul>"))
    }

    @Test
    fun `nummerierte Liste zaehlt`() {
        assertEquals(
            "<ol><li>1. Rühren</li><li>2. Backen</li><li>3. Kühlen</li></ol>",
            RichText.listenMarken("<ol><li>Rühren</li><li>Backen</li><li>Kühlen</li></ol>"),
        )
    }

    @Test
    fun `jede Liste zaehlt neu, auch nach einer Aufzaehlung dazwischen`() {
        val html = "<ol><li>a</li></ol><ul><li>b</li></ul><ol><li>c</li><li>d</li></ol>"
        assertEquals("<ol><li>1. a</li></ol><ul><li>• b</li></ul><ol><li>1. c</li><li>2. d</li></ol>", RichText.listenMarken(html))
    }

    @Test
    fun `verschachtelte Listen`() {
        val html = "<ol><li>außen<ul><li>innen</li></ul></li><li>zweiter</li></ol>"
        assertEquals("<ol><li>1. außen<ul><li>• innen</li></ul></li><li>2. zweiter</li></ol>", RichText.listenMarken(html))
    }

    @Test
    fun `Listenpunkt ohne Liste bekommt einen Punkt und Attribute bleiben`() {
        assertEquals("<li>• lose</li>", RichText.listenMarken("<li>lose</li>"))
        assertEquals("""<ol start="3" style="color:red"><li>1. a</li></ol>""", RichText.listenMarken("""<ol start="3" style="color:red"><li style="x">a</li></ol>"""))
    }

    @Test
    fun `Grossschreibung der Tags`() {
        // Gezählt wird auch bei großgeschriebenen Tags; das öffnende <li> wird dabei neu aufgebaut (klein), HTML ist da tolerant.
        assertEquals("<OL><li>1. a</LI></OL>", RichText.listenMarken("<OL><LI>a</LI></OL>"))
    }

    @Test
    fun `anderes HTML bleibt unangetastet`() {
        val html = "<p>Hallo <strong>Welt</strong><br>Zeile</p>"
        assertEquals(html, RichText.listenMarken(html))
    }

    @Test
    fun `Erkennung von HTML`() {
        assertTrue(RichText.istHtml("<p>x</p>"))
        assertTrue(RichText.istHtml("Text<br>mehr"))
        assertFalse(RichText.istHtml("Erst mischen.\nDann backen."))
        assertFalse(RichText.istHtml("5 < 6 und 7 > 3"))
        assertFalse(RichText.istHtml(""))
    }

    @Test
    fun `Klartext wird zu Absaetzen mit Zeilenumbruechen`() {
        assertEquals("<p>Erst mischen.<br>Dann backen.</p><p>Fertig.</p>", RichText.klartextZuHtml("Erst mischen.\nDann backen.\n\nFertig."))
    }

    @Test
    fun `Klartext maskiert Sonderzeichen und normalisiert Zeilenenden`() {
        assertEquals("<p>5 &lt; 6 &amp; 7 &gt; 3</p>", RichText.klartextZuHtml("5 < 6 & 7 > 3"))
        assertEquals("<p>a<br>b</p><p>c</p>", RichText.klartextZuHtml("a\r\nb\r\n\r\nc"))
    }

    @Test
    fun `leerer Klartext bleibt leer`() {
        assertEquals("", RichText.klartextZuHtml(""))
        assertEquals("", RichText.klartextZuHtml("  \n \n "))
        assertEquals("<p>x</p>", RichText.klartextZuHtml("\n\n x \n\n"))
    }
}
