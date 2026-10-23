package de.sryoshi.hcapp.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharingApiTest {
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
    fun `Benutzer suchen`() = runTest {
        server.enqueue(json("""{"count":1,"results":[{"id":4,"display_name":"Anna"}]}"""))
        val u = client.shareUsers("an")
        val path = server.takeRequest().path!!
        assertTrue("/api/v1/users/?" in path && "q=an" in path)
        assertEquals("Anna", u.results[0].displayName)
    }

    @Test
    fun `Freigabe ersetzen sendet die Nummern`() = runTest {
        server.enqueue(json("""{"users":[{"id":4,"display_name":"Anna"},{"id":5,"display_name":"Ben"}]}"""))
        val s = client.setListSharing("abc", listOf(4, 5, 4))
        val r = server.takeRequest()
        assertEquals("PUT /api/v1/shopping-lists/abc/sharing/", "${r.method} ${r.path}")
        val ids = Json.parseToJsonElement(r.body.readUtf8()).jsonObject["user_ids"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("4", "5"), ids)
        assertEquals(2, s.users.size)

        server.enqueue(json("""{"users":[]}"""))
        client.setOweMeSharing("k", emptyList())
        assertEquals("/api/v1/oweme/accounts/k/sharing/", server.takeRequest().path)
    }

    @Test
    fun `Freigabe Fehler pro Stelle`() = runTest {
        server.enqueue(json("""{"error":{"code":"validation_error","message":"x","fields":{"user_ids[0]":["Dieser Benutzer steht nicht zur Verfügung."]}}}""", 400))
        val e = assertFailsWith<ApiException> { client.setListSharing("abc", listOf(1)) }
        assertEquals(1, e.fields.size)
    }

    @Test
    fun `Export liefert die Bytes`() = runTest {
        server.enqueue(MockResponse().setBody("a;b\n1;2"))
        assertEquals("a;b\n1;2", String(client.exportOweMe("k")))
        assertEquals("Bearer t", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `Rezept anbieten und Angebote`() = runTest {
        server.enqueue(json("""{"offered":1,"already_offered":2}"""))
        val r = client.shareRecipe("rez", listOf(3, 4, 5))
        assertEquals(1, r.offered)
        assertEquals(2, r.alreadyOffered)
        assertEquals("POST", server.takeRequest().method)

        server.enqueue(json("""{"count":1,"results":[{"id":9,"sender":{"id":3,"display_name":"Cara"},"created_at":"x",
            "recipe":{"key":"abc","title":"Suppe","owner":"Cara"}}]}"""))
        val o = client.recipeOffers()
        assertEquals("Cara", o.results[0].sender.displayName)
        assertEquals("Suppe", o.results[0].recipe.title)

        server.enqueue(MockResponse().setResponseCode(204))
        client.declineRecipeOffer(9)
        server.takeRequest() // Angebote
        val d = server.takeRequest()
        assertEquals("POST /api/v1/recipe-shares/9/decline/", "${d.method} ${d.path}")
    }

    @Test
    fun `Meldungen`() = runTest {
        server.enqueue(json("""{"count":2,"unread_count":1,"results":[
            {"id":2,"type":"oweme_due","type_label":"Fällig","text":"Konto fällig","url":"/apps/oweme/abc/","target":{"kind":"oweme_account","key":"abc"},"read":false,"created_at":"x"},
            {"id":1,"type":"x","type_label":"","text":"Allgemein","url":"/","target":null,"read":true,"created_at":"y"}]}"""))
        val p = client.notifications(unreadOnly = true)
        assertTrue("unread=1" in server.takeRequest().path!!)
        assertEquals(1, p.unreadCount)
        assertEquals("abc", p.results[0].target?.key)
        assertNull(p.results[1].target)

        server.enqueue(json("""{"unread_count":3}"""))
        assertEquals(3, client.unreadCount())
        server.enqueue(json("""{"marked":3}"""))
        assertEquals(3, client.markAllNotificationsRead())
        server.enqueue(json("""{"id":2,"read":true}"""))
        assertTrue(client.markNotificationRead(2).read)
    }
}
