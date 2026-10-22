package de.sryoshi.hcapp.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
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

class HealthApiTest {
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

    private fun body(r: okhttp3.mockwebserver.RecordedRequest): JsonObject = Json.parseToJsonElement(r.body.readUtf8()).jsonObject

    private val profil = """{"enabled":true,"is_complete":true,"birth_date":"1985-04-02","height_cm":178,"sex":"m","activity":"1.375",
        "activity_checked":true,"training_enabled":false,"default_meal_plan_id":null,"target_weight_kg":"75.0","target_date":"2027-01-01",
        "metrics":{"weight_kg":"82.4","age":41,"basal_metabolic_rate":1760,"daily_need":2420,"bmi":"26.0","bmi_category":"Übergewicht","normal_weight_kg":["58.6","78.9"]},
        "goal":{"weight_kg":"75.0","start_kg":"84.0","direction":"abnehmen","distance_kg":"7.4","reached":false,"progress_percent":19,"date":"2027-01-01","days_left":72,"overdue":false},
        "options":{"sex":[{"value":"m","label":"männlich"},{"value":"w","label":"weiblich"}],
                   "activity":[{"value":"1.200","label":"Sitzend"},{"value":"1.375","label":"Leicht aktiv"}]}}"""

    @Test
    fun `Profil mit Kennzahlen, Ziel und Auswahlwerten`() = runTest {
        server.enqueue(json(profil))
        val p = client.healthProfile()
        assertEquals("/api/v1/health/profile/", server.takeRequest().path)
        assertTrue(p.enabled)
        assertEquals(178, p.heightCm)
        assertEquals(2420, p.metrics.dailyNeed)
        assertEquals(listOf("58.6", "78.9"), p.metrics.normalWeightKg)
        assertEquals(19, p.goal!!.progressPercent)
        assertEquals("1.375", p.options.activity[1].value)
        assertNull(p.defaultMealPlanId)
    }

    @Test
    fun `Profil ohne Ziel und ohne Bedarf`() = runTest {
        server.enqueue(json("""{"enabled":false,"is_complete":false,"birth_date":null,"height_cm":null,"sex":"","activity":null,
            "metrics":{"weight_kg":null,"age":null,"basal_metabolic_rate":null,"daily_need":null,"bmi":null,"bmi_category":null,"normal_weight_kg":null},"goal":null,"options":{"sex":[],"activity":[]}}"""))
        val p = client.healthProfile()
        assertEquals(false, p.enabled)
        assertNull(p.goal)
        assertNull(p.metrics.dailyNeed)
    }

    @Test
    fun `Profil aendern sendet nur gesetzte Felder, null loescht`() = runTest {
        server.enqueue(json(profil))
        client.updateHealthProfile(HealthProfilePatch().heightCm(180).sex("w").activity("1.375").targetWeightKg(null))
        val r = server.takeRequest()
        assertEquals("PATCH", r.method)
        val b = body(r)
        assertEquals(setOf("height_cm", "sex", "activity", "target_weight_kg"), b.keys)
        assertEquals(180, b["height_cm"]!!.jsonPrimitive.int)
        assertTrue(b["target_weight_kg"] is JsonNull)
    }

    @Test
    fun `Health einschalten`() = runTest {
        server.enqueue(json(profil))
        client.setHealthEnabled(true)
        val r = server.takeRequest()
        assertEquals("/api/v1/health/profile/enable/", r.path)
        assertEquals(true, body(r)["enabled"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `unvollstaendiges Profil kann nicht eingeschaltet werden`() = runTest {
        server.enqueue(json("""{"error":{"code":"profile_incomplete","message":"Für Health fehlen noch Angaben.","fields":{}}}""", 400))
        assertEquals("profile_incomplete", assertFailsWith<ApiException> { client.setHealthEnabled(true) }.code)
    }

    @Test
    fun `Gewicht erfassen mit und ohne Datum, loeschen`() = runTest {
        server.enqueue(json("""{"id":3,"date":"2026-10-22","weight_kg":"82.4"}""", 201))
        server.enqueue(json("""{"id":3,"date":"2026-10-20","weight_kg":"82.0"}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals("82.4", client.addWeight("82.4").weightKg)
        client.addWeight("82.0", "2026-10-20")
        client.deleteWeight(3)
        assertEquals(setOf("weight_kg"), body(server.takeRequest()).keys)
        assertEquals(setOf("weight_kg", "date"), body(server.takeRequest()).keys)
        assertEquals("DELETE /api/v1/health/weights/3/", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test
    fun `Gewichtsliste`() = runTest {
        server.enqueue(json("""{"count":2,"results":[{"id":2,"date":"2026-10-22","weight_kg":"82.4"},{"id":1,"date":"2026-10-01","weight_kg":"83.0"}]}"""))
        val l = client.weights()
        assertEquals(2, l.count)
        assertEquals("/api/v1/health/weights/?limit=200&offset=0", server.takeRequest().path)
    }

    @Test
    fun `Tagesbilanz mit Eintraegen`() = runTest {
        server.enqueue(json("""{"date":"2026-10-22","entries":[
            {"id":5,"date":"2026-10-22","title":"Haferflocken","kcal_per_serving":209,"servings":"1.50","total_kcal":314,"source":"manual",
             "protein_g":"7.9","fat_g":"4.0","carbs_g":null,"nutrients_estimated":false,"has_nutrients":true}],
            "total_kcal":314,"daily_need":2420,"base_need":2420,"training_kcal":0,"remaining_kcal":2106,"excess_kcal":null,"percent":13,"status":"under",
            "has_meal_plan":false,"seeded_count":0}"""))
        val t = client.calorieDay("2026-10-22")
        assertEquals("/api/v1/health/calories/2026-10-22/", server.takeRequest().path)
        assertEquals(314, t.totalKcal)
        assertEquals(2106, t.remainingKcal)
        assertNull(t.excessKcal)
        assertEquals("under", t.status)
        val e = t.entries.single()
        assertEquals(314, e.totalKcal)
        assertEquals("1.50", e.servings)
        assertEquals("7.9", e.proteinG)
        assertNull(e.carbsG)
        assertTrue(e.hasNutrients)
    }

    @Test
    fun `Tag ohne Bedarf`() = runTest {
        server.enqueue(json("""{"date":"2026-10-22","entries":[],"total_kcal":0,"daily_need":null,"base_need":null,"training_kcal":0,"remaining_kcal":null,"excess_kcal":null,"percent":0,"status":null,"has_meal_plan":true,"seeded_count":2}"""))
        val t = client.calorieDay("2026-10-22")
        assertNull(t.dailyNeed)
        assertNull(t.status)
        assertEquals(2, t.seededCount)
    }

    @Test
    fun `Monatsuebersicht`() = runTest {
        server.enqueue(json("""{"month":"2026-10","daily_need":2420,"average_kcal":2210,"average_need":2480,"tracked_days":3,"training_kcal":300,
            "previous_month":"2026-09","next_month":"2026-11","days":[{"date":"2026-10-01","total_kcal":2300,"training_kcal":0,"status":"ok"},{"date":"2026-10-02","total_kcal":0,"training_kcal":0,"status":null}]}"""))
        val m = client.calorieMonth("2026-10")
        assertEquals("/api/v1/health/calories/?month=2026-10", server.takeRequest().path)
        assertEquals("2026-11", m.nextMonth)
        assertEquals(2, m.days.size)
        assertEquals("ok", m.days[0].status)
        assertNull(m.days[1].status)
    }

    @Test
    fun `Monat ohne Angabe fragt den laufenden`() = runTest {
        server.enqueue(json("""{"month":"2026-10","days":[]}"""))
        client.calorieMonth()
        assertEquals("/api/v1/health/calories/", server.takeRequest().path)
    }

    @Test
    fun `Eintrag erfassen mit Naehrwerten`() = runTest {
        server.enqueue(json("""{"id":9,"date":"2026-10-22","title":"Hafer","kcal_per_serving":209,"servings":"1","total_kcal":209,"source":"manual","protein_g":"7.9","fat_g":"4.0","carbs_g":"32.0","nutrients_estimated":false,"has_nutrients":true}""", 201))
        client.addCalorieEntry("2026-10-22", CalorieEntryPatch().title("Hafer").kcalPerServing(209).servings("1").proteinG("7.9").fatG("4").carbsG("32"))
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/health/calories/2026-10-22/entries/", r.path)
        val b = body(r)
        assertEquals(setOf("title", "kcal_per_serving", "servings", "protein_g", "fat_g", "carbs_g"), b.keys)
        assertEquals(209, b["kcal_per_serving"]!!.jsonPrimitive.int)
    }

    @Test
    fun `Nur die Portionen aendern laesst die Naehrwerte in Ruhe`() = runTest {
        server.enqueue(json("""{"id":9,"date":"2026-10-22","title":"Hafer","kcal_per_serving":209,"servings":"2","total_kcal":418}"""))
        client.updateCalorieEntry(9, CalorieEntryPatch().servings("2"))
        val r = server.takeRequest()
        assertEquals("PATCH /api/v1/health/calorie-entries/9/", "${r.method} ${r.path}")
        assertEquals(setOf("servings"), body(r).keys)
    }

    @Test
    fun `Eintrag loeschen`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        client.deleteCalorieEntry(9)
        assertEquals("DELETE /api/v1/health/calorie-entries/9/", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test
    fun `Health ausgeschaltet`() = runTest {
        server.enqueue(json("""{"error":{"code":"health_not_enabled","message":"Health ist für dein Konto nicht eingeschaltet.","fields":{}}}""", 403))
        assertEquals("health_not_enabled", assertFailsWith<ApiException> { client.calorieDay("2026-10-22") }.code)
    }

    @Test
    fun `Vorlagen anlegen, aendern, anwenden, loeschen`() = runTest {
        val vorlage = """{"id":4,"name":"Müsli","kcal_per_serving":320,"servings":"1","total_kcal":320,"protein_g":null,"fat_g":null,"carbs_g":null,"nutrients_estimated":false,"has_nutrients":false}"""
        server.enqueue(json("""{"count":1,"results":[$vorlage]}"""))
        server.enqueue(json(vorlage, 201))
        server.enqueue(json(vorlage))
        server.enqueue(json("""{"id":11,"date":"2026-10-22","title":"Müsli","kcal_per_serving":320,"servings":"1","total_kcal":320}""", 201))
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals("Müsli", client.mealTemplates().results.single().name)
        client.createMealTemplate(MealTemplatePatch().name("Müsli").kcalPerServing(320).servings("1"))
        client.updateMealTemplate(4, MealTemplatePatch().kcalPerServing(330))
        assertEquals(11, client.applyMealTemplate(4, "2026-10-22").id)
        client.deleteMealTemplate(4)
        assertEquals("/api/v1/health/meal-templates/?limit=200", server.takeRequest().path)
        assertEquals(setOf("name", "kcal_per_serving", "servings"), body(server.takeRequest()).keys)
        val u = server.takeRequest()
        assertEquals("PATCH /api/v1/health/meal-templates/4/", "${u.method} ${u.path}")
        val a = server.takeRequest()
        assertEquals("POST /api/v1/health/meal-templates/4/apply/2026-10-22/", "${a.method} ${a.path}")
        assertEquals(0, a.bodySize)
        assertEquals("DELETE /api/v1/health/meal-templates/4/", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test
    fun `Lebensmittelvorschlaege`() = runTest {
        server.enqueue(json("""{"suggestions":[
            {"code":"C133000","name":"Hafer Flocken","kcal_100g":348,"protein_100g":13.2,"fat_100g":6.7,"carbs_100g":53.3},
            {"code":"X1","name":"Wasser","kcal_100g":0,"protein_100g":null,"fat_100g":null,"carbs_100g":null}]}"""))
        val v = client.searchFoods("  hafer flocken ")
        assertEquals("/api/v1/health/foods/?q=hafer%20flocken", server.takeRequest().path)
        assertEquals(2, v.size)
        assertEquals(348, v[0].kcal100g)
        assertEquals(13.2, v[0].protein100g)
        assertNull(v[1].fat100g, "fehlender Wert bleibt null und wird nicht zu 0")
    }

    @Test
    fun `Suche ohne Endpunkt meldet die fehlende API`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("<html>"))
        assertEquals(ApiException.NO_API, assertFailsWith<ApiException> { client.searchFoods("hafer") }.code)
    }
}
