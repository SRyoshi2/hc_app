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
        assertEquals("<OL><LI>1. a</LI></OL>", RichText.listenMarken("<OL><LI>a</LI></OL>"))
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
}
