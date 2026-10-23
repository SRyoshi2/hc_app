package de.sryoshi.hcapp.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiApiTest {
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

    private fun body(r: RecordedRequest) = Json.parseToJsonElement(r.body.readUtf8()).jsonObject

    @Test
    fun `Laborwerte Vorschau und Anfrage mit Pruefsumme`() = runTest {
        server.enqueue(json("""{"ai_available":true,"analysis":null,"request_preview":{"text":"Cholesterin 5,5","checksum":"abc123"}}"""))
        val a = client.labAnalysis()
        assertEquals("abc123", a.requestPreview?.checksum)
        server.takeRequest()

        server.enqueue(json("""{"ai_available":true,"analysis":{"text":"ok","model":"m","created_at":"x","measurements_at_time":1,"outdated":false}}""", 201))
        val b = client.requestLabAnalysis("abc123")
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        val j = body(r)
        assertTrue(j["confirmed"]!!.jsonPrimitive.boolean)
        assertEquals("abc123", j["checksum"]!!.jsonPrimitive.content)
        assertEquals("ok", b.analysis?.text)
    }

    @Test
    fun `Pruefsumme veraltet`() = runTest {
        server.enqueue(json("""{"error":{"code":"checksum_mismatch","message":"geändert"}}""", 409))
        assertEquals("checksum_mismatch", assertFailsWith<ApiException> { client.requestLabAnalysis("x") }.code)
    }

    @Test
    fun `Rueckblick und Ampel bekommen den KI-Text`() = runTest {
        server.enqueue(json("""{"week_start":"2026-03-02","week_end":"2026-03-08","ai_text":"Woche mit 3 Einheiten","review":null}"""))
        assertEquals("Woche mit 3 Einheiten", client.trainingReview("2026-03-02").aiText)
        server.takeRequest()

        server.enqueue(json("""{"review":{"text":"Gut gemacht.","model":"m","created_at":"x"}}""", 201))
        assertEquals("Gut gemacht.", client.requestTrainingReview("2026-03-02").text)
        assertEquals("2026-03-02", body(server.takeRequest())["week"]!!.jsonPrimitive.content)

        server.enqueue(json("""{"advice":{"text":"Mehr Gemüse.","model":"m","created_at":"x","suggestions":[]}}""", 201))
        assertEquals("Mehr Gemüse.", client.requestTrafficAdvice("2026-03-02", "plan").text)
        val j = body(server.takeRequest())
        assertEquals("plan", j["source"]!!.jsonPrimitive.content)
        assertTrue(j["confirmed"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `Naehrwerte schaetzen und zuruecknehmen`() = runTest {
        val eintrag = """{"id":5,"date":"2026-03-02","title":"Müsli","kcal_per_serving":300,"servings":"1","total_kcal":300,"source":"manual",
            "protein_g":"8.0","fat_g":"5.0","carbs_g":"50.0","nutrients_estimated":true,"has_nutrients":true,"needs_estimate":false}"""
        server.enqueue(json("""{"filled":1,"skipped":0,"entries":[$eintrag]}"""))
        val r = client.estimateEntryNutrients(5)
        val q = server.takeRequest()
        assertEquals("POST /api/v1/health/calorie-entries/5/estimate-nutrients/", "${q.method} ${q.path}")
        assertTrue(body(q)["confirmed"]!!.jsonPrimitive.boolean)
        assertEquals(1, r.filled)
        assertTrue(r.entries[0].nutrientsEstimated)

        server.enqueue(json("""{"filled":0,"skipped":2,"entries":[]}"""))
        assertEquals(2, client.estimateDayNutrients("2026-03-02").skipped)
        assertEquals("/api/v1/health/calories/2026-03-02/estimate-nutrients/", server.takeRequest().path)

        server.enqueue(json(eintrag.replace("true", "false")))
        client.discardEntryEstimate(5)
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `Tag und Eintrag kennen offene Schaetzungen`() = runTest {
        server.enqueue(json("""{"date":"2026-03-02","open_estimates":2,"entries":[{"id":1,"date":"2026-03-02","title":"Pizza","kcal_per_serving":800,"needs_estimate":true}]}"""))
        val d = client.calorieDay("2026-03-02")
        assertEquals(2, d.openEstimates)
        assertTrue(d.entries[0].needsEstimate)
    }

    @Test
    fun `KI nicht freigegeben und Anbieterfehler`() = runTest {
        server.enqueue(json("""{"error":{"code":"ai_required","message":"fehlt"}}""", 403))
        assertEquals("ai_required", assertFailsWith<ApiException> { client.estimateDayNutrients("2026-03-02") }.code)
        server.enqueue(json("""{"error":{"code":"ai_failed","message":"Die Schätzung kam nicht zustande: Zeitüberschreitung"}}""", 502))
        val e = assertFailsWith<ApiException> { client.requestTrainingReview("2026-03-02") }
        assertEquals("ai_failed", e.code)
        assertTrue("Zeitüberschreitung" in e.message)
    }

    @Test
    fun `Rezeptvorschlag liest Zahlen und Texte`() = runTest {
        server.enqueue(json("""{"recipe":{"title":"Gemüsepfanne","description":"Schnell","servings":2,
            "ingredients":[{"name":"Paprika","amount":2,"unit":"stk"},{"name":"Salz","amount":null,"unit":""},{"name":"Öl","amount":"1.5","unit":"el"}],
            "steps":[{"heading":"Kochen","text":"Anbraten."}]}}""", 201))
        val r = client.generateRecipe("Paprika, Zwiebel", 2, "vegetarisch")
        val j = body(server.takeRequest())
        assertEquals("Paprika, Zwiebel", j["ingredients_text"]!!.jsonPrimitive.content)
        assertEquals("2", j["servings"]!!.jsonPrimitive.content)
        assertEquals("2", r.servings)
        assertEquals("2", r.ingredients[0].amount)
        assertNull(r.ingredients[1].amount)
        assertEquals("1.5", r.ingredients[2].amount)
        assertEquals("Anbraten.", r.steps[0].text)
    }

    @Test
    fun `Rezeptnaehrwerte schaetzen`() = runTest {
        server.enqueue(json("""{"nutrition":{"kcal":420,"protein_g":18.5,"fat_g":"12","carbs_g":55}}""", 201))
        val n = client.estimateRecipeNutrition("Suppe", listOf(IngredientInput(name = "Kartoffel", amount = "500", unit = "g")), 4)
        val j = body(server.takeRequest())
        assertEquals("4", j["servings"]!!.jsonPrimitive.content)
        assertEquals("Kartoffel", (j["ingredients"] as JsonArray)[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("420", n.kcal)
        assertEquals("18.5", n.proteinG)
        assertEquals("12", n.fatG)
    }
}
