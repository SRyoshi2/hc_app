package de.sryoshi.hcapp.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiClientTest {
    private lateinit var server: MockWebServer
    private var token: String? = null
    private lateinit var client: ApiClient

    @BeforeTest
    fun start() {
        server = MockWebServer().also { it.start() }
        client = ApiClient(server.url("/").toString().trimEnd('/'), { token })
    }

    @AfterTest
    fun stop() = server.shutdown()

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)

    private fun bodyOf(r: okhttp3.mockwebserver.RecordedRequest) =
        Json.parseToJsonElement(r.body.readUtf8()).jsonObject

    @Test
    fun `meta wird gelesen, unbekannte Felder werden ignoriert`() = runTest {
        server.enqueue(json("""{"product_name":"Office Manager","mode":"office","api_version":1,
            "min_client_version":1,"server_version":"0.93.1058","neu":"egal"}"""))
        val meta = client.meta()
        assertEquals("Office Manager", meta.productName)
        assertEquals("office", meta.mode)
        val r = server.takeRequest()
        assertEquals("/api/v1/meta/", r.path)
        assertNull(r.getHeader("Authorization"))
    }

    @Test
    fun `login sendet Zugangsdaten ohne Token und liefert Token`() = runTest {
        token = "altes-token"
        server.enqueue(json("""{"token":"abc","expires_at":"2027-01-01T00:00:00+00:00",
            "user":{"id":3,"username":"sven","display_name":"Sven"}}"""))
        val antwort = client.login("sven", "geheim", "Pixel 8")
        assertEquals("abc", antwort.token)
        assertEquals("Sven", antwort.user.displayName)
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/auth/login/", r.path)
        assertNull(r.getHeader("Authorization"), "Beim Login darf kein altes Token mitgehen")
        val body = bodyOf(r)
        assertEquals("sven", body["username"]!!.jsonPrimitive.content)
        assertEquals("Pixel 8", body["device_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `falsches Passwort wird als ApiException mit Code gemeldet`() = runTest {
        server.enqueue(json("""{"error":{"code":"invalid_credentials","message":"Benutzername oder Passwort stimmt nicht.","fields":{}}}""", 401))
        val e = assertFailsWith<ApiException> { client.login("sven", "falsch", "Pixel") }
        assertEquals("invalid_credentials", e.code)
        assertEquals(401, e.httpStatus)
        assertEquals("Benutzername oder Passwort stimmt nicht.", e.message)
        assertFalse(e.tokenUngueltig, "Ein falsches Passwort ist kein abgelaufenes Token")
    }

    @Test
    fun `wartendes Konto und gesperrte App haben eigene Codes`() = runTest {
        server.enqueue(json("""{"error":{"code":"pending_approval","message":"wartet","fields":{}}}""", 403))
        assertEquals("pending_approval", assertFailsWith<ApiException> { client.login("a", "b", "c") }.code)
        token = "t"
        server.enqueue(json("""{"error":{"code":"app_disabled","message":"abgeschaltet","fields":{}}}""", 403))
        assertEquals("app_disabled", assertFailsWith<ApiException> { client.shoppingLists() }.code)
    }

    @Test
    fun `abgelaufenes Token wird erkannt`() = runTest {
        token = "alt"
        server.enqueue(json("""{"error":{"code":"invalid_token","message":"ungueltig","fields":{}}}""", 401))
        assertTrue(assertFailsWith<ApiException> { client.me() }.tokenUngueltig)
    }

    @Test
    fun `Bearer-Token wird mitgeschickt`() = runTest {
        token = "geheim123"
        server.enqueue(json("""{"id":1,"username":"sven","display_name":"Sven","abo_1":true,
            "apps":{"health":true,"einkaufsliste":true,"rezepte":false,"oweme":false},"health_enabled":false}"""))
        val me = client.me()
        assertEquals("Bearer geheim123", server.takeRequest().getHeader("Authorization"))
        assertTrue(me.abo1)
        assertTrue(me.appAktiv(AppKey.SHOPPING))
        assertFalse(me.appAktiv(AppKey.RECIPES))
        assertFalse(me.appAktiv("unbekannt"))
        assertFalse(me.healthEnabled)
    }

    @Test
    fun `Validierungsfehler liefern die Feldmeldungen`() = runTest {
        token = "t"
        server.enqueue(json("""{"error":{"code":"validation_error","message":"Die Eingaben sind nicht gültig.",
            "fields":{"name":["Dieses Feld ist erforderlich."],"price":["Muss eine Zahl ab 0 sein."]}}}""", 400))
        val e = assertFailsWith<ApiException> { client.addItem("abc", ItemInput(name = "")) }
        assertEquals("validation_error", e.code)
        assertEquals(listOf("Dieses Feld ist erforderlich."), e.fields["name"])
        assertEquals(listOf("Muss eine Zahl ab 0 sein."), e.fields["price"])
    }

    @Test
    fun `404 ohne JSON heisst die Instanz hat die API nicht`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "text/html").setBody("<html>nicht gefunden</html>"))
        assertEquals(ApiException.NO_API, assertFailsWith<ApiException> { client.meta() }.code)
    }

    @Test
    fun `500 ohne JSON wird als HTTP-Fehler gemeldet`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val e = assertFailsWith<ApiException> { client.meta() }
        assertEquals("http_500", e.code)
    }

    @Test
    fun `unlesbare Erfolgsantwort wird gemeldet`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>Login</html>"))
        assertEquals(ApiException.BAD_RESPONSE, assertFailsWith<ApiException> { client.meta() }.code)
    }

    @Test
    fun `Verbindungsabbruch wird als Netzwerkfehler gemeldet`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(ApiException.NETWORK, assertFailsWith<ApiException> { client.meta() }.code)
    }

    @Test
    fun `Listen werden mit Seitenparametern abgerufen`() = runTest {
        token = "t"
        server.enqueue(json("""{"count":1,"results":[{"key":"k1","title":"Wocheneinkauf","is_owner":true,
            "owner":"Sven","shared":false,"open_count":2,"done_count":1,"total_count":3,
            "created_at":"2026-10-01T10:00:00+00:00"}]}"""))
        val seite = client.shoppingLists(limit = 50, offset = 10)
        assertEquals(1, seite.count)
        assertEquals("Wocheneinkauf", seite.results.single().title)
        assertEquals(2, seite.results.single().openCount)
        assertEquals("/api/v1/shopping-lists/?limit=50&offset=10", server.takeRequest().path)
    }

    @Test
    fun `Artikel einer Liste mit Vorschlaegen`() = runTest {
        token = "t"
        server.enqueue(json("""{"count":1,"title":"Wocheneinkauf",
            "suggestions":{"categories":["Obst"],"stores":["Rewe"]},
            "results":[{"id":5,"list_key":"k1","name":"Äpfel","amount":"1 kg","category":"Obst","store":"",
            "price":"2.50","done":false,"done_at":null,"created_by":"Sven","created_at":"2026-10-01T10:00:00+00:00"}]}"""))
        val seite = client.items("k1")
        assertEquals("Wocheneinkauf", seite.title)
        assertEquals(listOf("Obst"), seite.suggestions.categories)
        val a = seite.results.single()
        assertEquals("2.50", a.price)
        assertEquals("Äpfel", a.name)
        assertFalse(a.done)
        assertEquals("/api/v1/shopping-lists/k1/items/?limit=200&offset=0", server.takeRequest().path)
    }

    private val artikelJson = """{"id":5,"list_key":"k1","name":"Milch","amount":"","category":"","store":"",
        "price":null,"done":true,"done_at":"2026-10-02T08:00:00+00:00","created_by":"","created_at":"2026-10-01T10:00:00+00:00"}"""

    @Test
    fun `Artikel anlegen sendet alle Felder, leerer Preis als null`() = runTest {
        token = "t"
        server.enqueue(json(artikelJson, 201))
        client.addItem("k1", ItemInput(name = "Milch", amount = "2x"))
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/shopping-lists/k1/items/", r.path)
        val body = bodyOf(r)
        assertEquals("Milch", body["name"]!!.jsonPrimitive.content)
        assertEquals("2x", body["amount"]!!.jsonPrimitive.content)
        assertTrue(body["price"] is kotlinx.serialization.json.JsonNull)
    }

    @Test
    fun `Artikel aendern sendet nur die genannten Felder`() = runTest {
        token = "t"
        server.enqueue(json(artikelJson))
        client.updateItem(5, mapOf("amount" to "3x"))
        val r = server.takeRequest()
        assertEquals("PATCH", r.method)
        assertEquals("/api/v1/shopping-items/5/", r.path)
        assertEquals(setOf("amount"), bodyOf(r).keys)
    }

    @Test
    fun `Abhaken ist ein POST ohne Inhalt`() = runTest {
        token = "t"
        server.enqueue(json(artikelJson))
        assertTrue(client.toggleItem(5).done)
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/shopping-items/5/toggle/", r.path)
        assertEquals(0, r.bodySize)
    }

    @Test
    fun `Loeschen und Abmelden erwarten 204`() = runTest {
        token = "t"
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        client.deleteItem(5)
        client.deleteList("k1")
        client.logout()
        assertEquals("DELETE /api/v1/shopping-items/5/", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("DELETE /api/v1/shopping-lists/k1/", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("POST /api/v1/auth/logout/", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test
    fun `Unterpfad der Instanz wird beachtet`() = runTest {
        val unter = ApiClient(server.url("/hc/").toString().trimEnd('/'), { null })
        server.enqueue(json("""{"product_name":"X","mode":"family","api_version":1,"min_client_version":1,"server_version":"1"}"""))
        unter.meta()
        assertEquals("/hc/api/v1/meta/", server.takeRequest().path)
    }

    @Test
    fun `Weiterleitungen werden nicht verfolgt`() = runTest {
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "https://anderswo.example/"))
        assertEquals("http_301", assertFailsWith<ApiException> { client.meta() }.code)
    }
}
