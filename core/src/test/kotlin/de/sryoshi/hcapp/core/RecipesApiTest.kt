package de.sryoshi.hcapp.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
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

class RecipesApiTest {
    private val lenientJson = Json { ignoreUnknownKeys = true }
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

    private val kurz = """{"key":"r1","title":"Linsensuppe","description":"","servings":4,"prep_minutes":10,"cook_minutes":25,
        "difficulty":"easy","difficulty_label":"Einfach","categories":[{"id":1,"name":"Suppen"}],"image_url":"https://x.de/media/a.jpg",
        "kcal":"320.0","is_public":false,"owner":"Sven","created_at":"2026-10-01T10:00:00+00:00","updated_at":"2026-10-02T10:00:00+00:00"}"""

    private val detail = """{"key":"r1","title":"Linsensuppe","description":"Wärmt.","servings":4,"prep_minutes":10,"cook_minutes":25,
        "difficulty":"easy","difficulty_label":"Einfach","categories":[],"image_url":null,"kcal":"320.0","is_public":true,"owner":"Anna",
        "created_at":"2026-10-01T10:00:00+00:00","updated_at":"2026-10-02T10:00:00+00:00",
        "ingredient_sections":[
          {"section":"","ingredients":[{"id":1,"name":"Linsen","amount":"250.00","unit":"g","unit_label":"g","note":"rot","shopping_category":"Konserven","store":"","price":"1.99"}]},
          {"section":"Garnitur","ingredients":[{"id":2,"name":"Salz","amount":null,"unit":"","unit_label":"","note":"","shopping_category":"","store":"","price":null}]}],
        "steps":[{"order":1,"heading":"","text":"Kochen."}],
        "images":[{"id":9,"url":"https://x.de/media/b.jpg","caption":""}],
        "nutrition":{"source":"manual","per_serving":{"kcal":"320.0","protein_g":"15.0","fat_g":null,"carbs_g":"40.0"},"total":{"kcal":"1280.0"}},
        "video_url":null,"video_file_url":null,"source_url":"","source_owner_name":"","is_owner":false,"can_edit":false}"""

    @Test
    fun `Rezeptliste mit Suche und Kategorien`() = runTest {
        server.enqueue(json("""{"count":1,"results":[$kurz]}"""))
        val seite = client.recipes(q = " Linsen ", categories = listOf("Suppen", "Vegan"), limit = 20, offset = 40)
        assertEquals(1, seite.count)
        val r = seite.results.single()
        assertEquals("Linsensuppe", r.title)
        assertEquals(35, r.gesamtMinuten)
        assertEquals("Suppen", r.categories.single().name)
        assertEquals("https://x.de/media/a.jpg", r.imageUrl)
        val pfad = server.takeRequest().path!!
        assertTrue(pfad.startsWith("/api/v1/recipes/?"), pfad)
        assertTrue("limit=20" in pfad && "offset=40" in pfad && "q=Linsen" in pfad, pfad)
        assertTrue("category=Suppen" in pfad && "category=Vegan" in pfad, pfad)
    }

    @Test
    fun `ohne Suche wird kein q gesendet`() = runTest {
        server.enqueue(json("""{"count":0,"results":[]}"""))
        client.recipes()
        assertEquals("/api/v1/recipes/?limit=50&offset=0", server.takeRequest().path)
    }

    @Test
    fun `Gesamtzeit fehlt, wenn nichts angegeben ist`() {
        val r = lenientJson.decodeFromString<RecipeSummary>("""{"key":"k","title":"T"}""")
        assertNull(r.gesamtMinuten)
        assertEquals(1, r.servings)
    }

    @Test
    fun `oeffentliche Rezepte haben einen eigenen Pfad`() = runTest {
        server.enqueue(json("""{"count":0,"results":[]}"""))
        client.publicRecipes(q = "Kuchen")
        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/recipes/public/?"))
    }

    @Test
    fun `Rezeptdetail mit Abschnitten, Schritten und Naehrwerten`() = runTest {
        server.enqueue(json(detail))
        val r = client.recipe("r1")
        assertEquals("/api/v1/recipes/r1/", server.takeRequest().path)
        assertEquals(2, r.ingredientSections.size)
        assertEquals("Garnitur", r.ingredientSections[1].section)
        val linsen = r.ingredientSections[0].ingredients.single()
        assertEquals("250.00", linsen.amount)
        assertEquals("rot", linsen.note)
        assertNull(r.ingredientSections[1].ingredients.single().amount)
        assertEquals("Kochen.", r.steps.single().text)
        assertEquals("15.0", r.nutrition.perServing["protein_g"])
        assertNull(r.nutrition.perServing["fat_g"])
        assertEquals(false, r.isOwner)
        assertEquals("https://x.de/media/b.jpg", r.bildUrl, "ohne Titelbild das erste Galeriebild")
    }

    @Test
    fun `Rezept kopieren ist ein POST ohne Inhalt`() = runTest {
        server.enqueue(json(detail, 201))
        client.copyRecipe("r1")
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/recipes/r1/copy/", r.path)
        assertEquals(0, r.bodySize)
    }

    @Test
    fun `eigenes Rezept kopieren wird vom Server abgelehnt`() = runTest {
        server.enqueue(json("""{"error":{"code":"own_recipe","message":"Das ist bereits dein Rezept.","fields":{}}}""", 400))
        assertEquals("own_recipe", assertFailsWith<ApiException> { client.copyRecipe("r1") }.code)
    }

    @Test
    fun `Einkaufsvorschau sendet die Portionen und liest die Zeilen`() = runTest {
        server.enqueue(json("""{"servings":"6","base_servings":4,"items":[
            {"name":"Linsen","note":"rot","section":"","amount":"375 g","amount_value":"375","unit":"g","unit_label":"g","category":"Konserven","store":"","price":"1.99"},
            {"name":"Salz","note":"","section":"Garnitur","amount":"","amount_value":null,"unit":"","unit_label":"","category":"","store":"","price":null}]}"""))
        val v = client.shoppingPreview("r1", "6")
        val r = server.takeRequest()
        assertEquals("/api/v1/recipes/r1/shopping-preview/", r.path)
        assertEquals("6", Json.parseToJsonElement(r.body.readUtf8()).jsonObject["servings"]!!.jsonPrimitive.content)
        assertEquals("6", v.servings)
        assertEquals(4, v.baseServings)
        assertEquals("375 g", v.items[0].amount)
        assertNull(v.items[1].amountValue)
        assertEquals(ItemInput("Linsen", "375 g", "Konserven", "", "1.99"), v.items[0].alsEingabe())
    }

    @Test
    fun `Vorschau ohne Portionen sendet null`() = runTest {
        server.enqueue(json("""{"servings":"4","base_servings":4,"items":[]}"""))
        client.shoppingPreview("r1")
        assertTrue(Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["servings"] is JsonNull)
    }

    @Test
    fun `ungueltige Portionen liefern Feldfehler`() = runTest {
        server.enqueue(json("""{"error":{"code":"validation_error","message":"x","fields":{"servings":["Muss eine Zahl größer 0 und höchstens 1000 sein."]}}}""", 400))
        val e = assertFailsWith<ApiException> { client.shoppingPreview("r1", "0") }
        assertEquals(1, e.fields["servings"]?.size)
    }

    @Test
    fun `Uebernahme in bestehende Liste sendet Liste und Zeilen, keinen Titel`() = runTest {
        server.enqueue(json("""{"list_key":"abc","created_count":2}""", 201))
        val erg = client.toShoppingList("r1", "abc", null, listOf(
            ItemInput("Linsen", "375 g", "Konserven", "", "1.99"), ItemInput("Salz"),
        ))
        assertEquals("abc", erg.listKey)
        assertEquals(2, erg.createdCount)
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/recipes/r1/to-shopping-list/", r.path)
        val body = Json.parseToJsonElement(r.body.readUtf8()).jsonObject
        assertEquals("abc", body["target_list_key"]!!.jsonPrimitive.content)
        assertNull(body["new_list_title"], "Liste und Titel dürfen nie zusammen gesendet werden")
        val zeilen: JsonArray = body["items"]!!.jsonArray
        assertEquals(2, zeilen.size)
        assertEquals("375 g", zeilen[0].jsonObject["amount"]!!.jsonPrimitive.content)
        assertEquals("1.99", zeilen[0].jsonObject["price"]!!.jsonPrimitive.content)
        assertTrue(zeilen[1].jsonObject["price"] is JsonNull)
    }

    @Test
    fun `Uebernahme in neue Liste sendet nur den Titel`() = runTest {
        server.enqueue(json("""{"list_key":"neu1","created_count":1}""", 201))
        client.toShoppingList("r1", null, "Linsensuppe", listOf(ItemInput("Linsen")))
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("Linsensuppe", body["new_list_title"]!!.jsonPrimitive.content)
        assertNull(body["target_list_key"])
    }

    @Test
    fun `Kategorien`() = runTest {
        server.enqueue(json("""{"count":1,"results":[{"id":3,"name":"Suppen","recipe_count":7}]}"""))
        val k = client.recipeCategories().results.single()
        assertEquals("Suppen", k.name)
        assertEquals(7, k.recipeCount)
    }
}
