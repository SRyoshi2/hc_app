package de.sryoshi.hcapp.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
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

class RecipeWriteTest {
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

    private val detail = """{"key":"r1","title":"Suppe","servings":4,"is_owner":true,"can_edit":true}"""

    private fun body(r: okhttp3.mockwebserver.RecordedRequest): JsonObject = Json.parseToJsonElement(r.body.readUtf8()).jsonObject

    @Test
    fun `Anlegen sendet nur gesetzte Felder`() = runTest {
        server.enqueue(json(detail, 201))
        val rezept = client.createRecipe(RecipePatch().title("Suppe").servings(4).difficulty("easy").isPublic(false))
        assertEquals("r1", rezept.key)
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/recipes/", r.path)
        val b = body(r)
        assertEquals(setOf("title", "servings", "difficulty", "is_public"), b.keys)
        assertEquals(4, b["servings"]!!.jsonPrimitive.int)
        assertEquals(false, b["is_public"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `Aendern ist ein PATCH und Leeren sendet null`() = runTest {
        server.enqueue(json(detail))
        client.updateRecipe("r1", RecipePatch().prepMinutes(null).kcal(null).title("Neu"))
        val r = server.takeRequest()
        assertEquals("PATCH", r.method)
        assertEquals("/api/v1/recipes/r1/", r.path)
        val b = body(r)
        assertEquals(setOf("prep_minutes", "kcal", "title"), b.keys)
        assertTrue(b["prep_minutes"] is JsonNull)
        assertTrue(b["kcal"] is JsonNull)
    }

    @Test
    fun `Zutaten und Schritte als Listen, id nur wenn vorhanden`() = runTest {
        server.enqueue(json(detail))
        client.updateRecipe("r1", RecipePatch()
            .ingredients(listOf(
                IngredientInput(id = 7, name = "Linsen", amount = "250", unit = "g", note = "rot", section = "Suppe", store = "Rewe", shoppingCategory = "Konserven", price = "1.99"),
                IngredientInput(name = "Salz"),
            ))
            .steps(listOf(StepInput("Kochen", "<p>Köcheln.</p>")))
            .categoryIds(listOf(3, 5)))
        val b = body(server.takeRequest())
        val z = b["ingredients"]!!.jsonArray
        assertEquals(7, z[0].jsonObject["id"]!!.jsonPrimitive.int)
        assertEquals("Rewe", z[0].jsonObject["store"]!!.jsonPrimitive.content)
        assertEquals("1.99", z[0].jsonObject["price"]!!.jsonPrimitive.content)
        assertNull(z[1].jsonObject["id"], "neue Zutat ohne id")
        assertTrue(z[1].jsonObject["amount"] is JsonNull)
        assertEquals("<p>Köcheln.</p>", b["steps"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(listOf(3, 5), b["category_ids"]!!.jsonArray.map { it.jsonPrimitive.int })
    }

    @Test
    fun `leere Menge wird zu null`() = runTest {
        server.enqueue(json(detail))
        client.updateRecipe("r1", RecipePatch().ingredients(listOf(IngredientInput(name = "Pfeffer", amount = "  "))))
        assertTrue(body(server.takeRequest())["ingredients"]!!.jsonArray[0].jsonObject["amount"] is JsonNull)
    }

    @Test
    fun `Loeschen`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        client.deleteRecipe("r1")
        val r = server.takeRequest()
        assertEquals("DELETE", r.method)
        assertEquals("/api/v1/recipes/r1/", r.path)
    }

    @Test
    fun `Bild hochladen als Titelbild ist multipart`() = runTest {
        server.enqueue(json(detail, 201))
        client.uploadRecipeImage("r1", byteArrayOf(1, 2, 3), "a.jpg", "image/jpeg", asTitle = true, caption = "Fertig")
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/recipes/r1/images/", r.path)
        assertTrue(r.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertEquals("Bearer t", r.getHeader("Authorization"))
        val text = r.body.readUtf8()
        assertTrue("""name="image"; filename="a.jpg"""" in text, text)
        assertTrue("Content-Type: image/jpeg" in text)
        assertTrue("""name="title"""" in text && "true" in text)
        assertTrue("""name="caption"""" in text && "Fertig" in text)
    }

    @Test
    fun `Galeriebild ohne Unterschrift sendet kein caption`() = runTest {
        server.enqueue(json(detail, 201))
        client.uploadRecipeImage("r1", byteArrayOf(1), "b.png", "image/png", asTitle = false)
        val text = server.takeRequest().body.readUtf8()
        assertTrue("""name="title"""" in text && "false" in text)
        assertTrue("""name="caption"""" !in text)
    }

    @Test
    fun `Bilder loeschen`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        client.deleteRecipeImage("r1", 9)
        client.deleteRecipeTitleImage("r1")
        assertEquals("/api/v1/recipes/r1/images/9/", server.takeRequest().path)
        assertEquals("/api/v1/recipes/r1/title-image/", server.takeRequest().path)
    }

    @Test
    fun `Kategorie anlegen`() = runTest {
        server.enqueue(json("""{"id":4,"name":"Suppen","recipe_count":0}""", 201))
        val k = client.createRecipeCategory("Suppen")
        assertEquals(4, k.id)
        val r = server.takeRequest()
        assertEquals("/api/v1/recipe-categories/", r.path)
        assertEquals("Suppen", body(r)["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `fremdes Rezept aendern wird abgelehnt`() = runTest {
        server.enqueue(json("""{"error":{"code":"forbidden","message":"Nur der Besitzer darf das.","fields":{}}}""", 403))
        val e = assertFailsWith<ApiException> { client.updateRecipe("r1", RecipePatch().title("x")) }
        assertEquals("forbidden", e.code)
        assertEquals(403, e.httpStatus)
    }

    @Test
    fun `Validierungsfehler mit Listenstellen`() = runTest {
        server.enqueue(json("""{"error":{"code":"validation_error","message":"x","fields":{"title":["Dieses Feld ist erforderlich."],"ingredients[1].name":["Dieses Feld ist erforderlich."]}}}""", 400))
        val e = assertFailsWith<ApiException> { client.createRecipe(RecipePatch().title("")) }
        assertEquals(listOf("Titel: Dieses Feld ist erforderlich.", "Zutat 2 – Name: Dieses Feld ist erforderlich."), FeldFehler.meldungen(e.fields))
    }
}
