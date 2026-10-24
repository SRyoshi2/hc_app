package de.sryoshi.hcapp.core

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApiClient

    @BeforeTest
    fun start() {
        server = MockWebServer().also { it.start() }
        client = ApiClient(server.url("/").toString().trimEnd('/'), { "t" })
    }

    @AfterTest
    fun stop() = server.shutdown()

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `Liste mit Filtern und Auswahl`() = runTest {
        server.enqueue(json("""{"count":1,"results":[{"key":"abc","title":"Tool","type":{"id":2,"name":"Windows"},"category":null,"version":"1.2",
            "filename":"tool.exe","size":1048576,"size_text":"1,0 MB","downloads":3,"created_at":"x","updated_at":"y"}],
            "types":[{"id":2,"name":"Windows","count":1}],"categories":[{"id":5,"name":"Tools","count":1}]}"""))
        val p = client.downloads(q = "to ol", type = 2, category = 5)
        val path = server.takeRequest().path!!
        assertTrue("/api/v1/downloads/?" in path && "type=2" in path && "category=5" in path && "q=to%20ol" in path)
        assertEquals("Windows", p.results[0].type?.name)
        assertNull(p.results[0].category)
        assertEquals(1048576L, p.results[0].size)
        assertEquals(5L, p.categories[0].id)
        assertEquals("", p.results[0].sha256)
    }

    @Test
    fun `Detail mit Beschreibung und Pruefsumme`() = runTest {
        server.enqueue(json("""{"key":"abc","title":"Tool","type":{"id":2,"name":"Windows"},"category":{"id":5,"name":"Tools"},"version":"","filename":"t.zip",
            "size":10,"size_text":"10 B","downloads":0,"created_at":"x","updated_at":"y","description":"<p>Hallo <b>Welt</b></p>","description_text":"Hallo Welt","sha256":"abcd"}"""))
        val d = client.download("abc")
        assertEquals("/api/v1/downloads/abc/", server.takeRequest().path)
        assertEquals("<p>Hallo <b>Welt</b></p>", d.description)
        assertEquals("abcd", d.sha256)
    }

    @Test
    fun `Datei wird gestreamt und geprueft`() = runTest {
        val inhalt = ByteArray(200_000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(inhalt)))
        val aus = ByteArrayOutputStream()
        var zuletzt = 0L
        val r = client.downloadFile("abc", aus) { zuletzt = it }
        val req = server.takeRequest()
        assertEquals("/api/v1/downloads/abc/file/", req.path)
        assertEquals("Bearer t", req.getHeader("Authorization"))
        assertEquals(inhalt.size.toLong(), r.bytes)
        assertEquals(inhalt.size.toLong(), zuletzt)
        assertTrue(inhalt.contentEquals(aus.toByteArray()))
        assertEquals(MessageDigest.getInstance("SHA-256").digest(inhalt).joinToString("") { "%02x".format(it) }, r.sha256)
    }

    @Test
    fun `Nicht sichtbar ist 404 und nicht freigeschaltet 403`() = runTest {
        server.enqueue(json("""{"error":{"code":"not_found","message":"Das gibt es nicht."}}""", 404))
        val out = ByteArrayOutputStream()
        assertEquals("not_found", assertFailsWith<ApiException> { client.downloadFile("zz", out) }.code)
        assertEquals(0, out.size())

        server.enqueue(json("""{"error":{"code":"app_disabled","message":"aus"}}""", 403))
        assertEquals("app_disabled", assertFailsWith<ApiException> { client.downloads() }.code)
    }
}
