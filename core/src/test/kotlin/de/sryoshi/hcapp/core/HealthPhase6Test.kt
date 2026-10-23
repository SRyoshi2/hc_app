package de.sryoshi.hcapp.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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

class HealthPhase6Test {
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

    private fun body(r: RecordedRequest): JsonObject = Json.parseToJsonElement(r.body.readUtf8()).jsonObject

    private val lab = """{"id":5,"name":"Cholesterin","date":"2026-03-01","value":"5.5","unit":"mmol/l","ref_from":null,"ref_to":"5","note":"",
        "classification":"ueber","classification_label":"darüber","flagged":true,"check_id":3,"created_at":"2026-03-01T10:00:00Z"}"""

    @Test
    fun `Arztbesuch mit Detail`() = runTest {
        server.enqueue(json("""{"id":3,"title":"Hausarzt","doctor":"Dr. X","visit_date":"2026-03-01","visit_time":"09:30","visit_end_time":null,
            "notes":"","is_planned":false,"followed_up_by_id":null,"follows_up_id":2,"due_date":"2026-09-01","is_due":false,"days_until_due":120,
            "days_overdue":null,"due_is_estimate":true,"next_due_date":null,"next_due_amount":6,"next_due_unit":"months","created_at":"2026-03-01T08:00:00Z",
            "labs":[$lab],"documents":[{"id":1,"key":"abc_1","title":"Befund","filename":"b.pdf","category":{"id":2,"name":"Labor"},"uploaded_at":"x","size":1200}],
            "comments":[{"id":9,"text":"ok","created_at":"y"}]}"""))
        val c = client.healthCheck(3)
        assertEquals("/api/v1/health/checks/3/", server.takeRequest().path)
        assertEquals(2L, c.followsUpId)
        assertEquals("months", c.nextDueUnit)
        assertEquals(1, c.labs.size)
        assertTrue(c.labs[0].flagged)
        assertNull(c.labs[0].refFrom)
        assertEquals("Labor", c.documents[0].category?.name)
        assertEquals("abc_1", c.documents[0].key)
    }

    @Test
    fun `Arztbesuch senden nur gesetzte Felder`() = runTest {
        val antwort = """{"id":3,"title":"Zahnarzt"}"""
        server.enqueue(json(antwort, 201))
        client.createHealthCheck(HealthCheckPatch().title("Zahnarzt").visitDate("2026-05-01").durationHours("1.5").followsUpId(2))
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        val b = body(r)
        assertEquals(setOf("title", "visit_date", "duration_hours", "follows_up_id"), b.keys)
        assertEquals("1.5", b["duration_hours"]!!.jsonPrimitive.content)

        server.enqueue(json(antwort))
        client.updateHealthCheck(3, HealthCheckPatch().nextDueDate(null).nextDueAmount(null))
        val p = server.takeRequest()
        assertEquals("PATCH", p.method)
        assertEquals(JsonNull, body(p)["next_due_date"])
    }

    @Test
    fun `Dokument hochladen laden und loeschen`() = runTest {
        server.enqueue(json("""{"id":4,"key":"k_4","title":"Röntgen","filename":"r.jpg","category":null,"uploaded_at":"x","size":3}""", 201))
        val d = client.uploadHealthDocument(3, byteArrayOf(1, 2, 3), "r.jpg", "image/jpeg", "Röntgen", categoryId = 2)
        val r = server.takeRequest()
        assertEquals("/api/v1/health/checks/3/documents/", r.path)
        assertTrue(r.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val text = r.body.readUtf8()
        assertTrue("name=\"category_id\"" in text && "name=\"file\"" in text)
        assertEquals("k_4", d.key)

        server.enqueue(MockResponse().setBody("PDFDATA"))
        assertEquals("PDFDATA", String(client.downloadHealthDocument("k_4")))
        val g = server.takeRequest()
        assertEquals("/api/v1/health/documents/k_4/", g.path)
        assertEquals("Bearer t", g.getHeader("Authorization"))

        server.enqueue(MockResponse().setResponseCode(404).setBody("{\"error\":{\"code\":\"not_found\",\"message\":\"x\"}}"))
        assertEquals("not_found", assertFailsWith<ApiException> { client.downloadHealthDocument("zz") }.code)

        server.enqueue(MockResponse().setResponseCode(204))
        client.deleteHealthDocument(4)
        assertEquals("DELETE /api/v1/health/documents/4/", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test
    fun `Kommentar Kategorie und Impfung`() = runTest {
        server.enqueue(json("""{"id":9,"text":"Hallo","created_at":"x"}""", 201))
        assertEquals("Hallo", client.addHealthComment(3, "Hallo").text)
        assertEquals("/api/v1/health/checks/3/comments/", server.takeRequest().path)

        server.enqueue(json("""{"id":2,"name":"Labor"}""", 201))
        assertEquals("Labor", client.createDocumentCategory("Labor").name)

        server.enqueue(json("""{"count":1,"results":[{"id":1,"name":"Tetanus","last_date":"2020-01-01","notes":"","due_date":"2030-01-01",
            "is_due":false,"days_until_due":1500,"days_overdue":null,"due_is_estimate":false,"next_due_date":null,"next_due_amount":10,"next_due_unit":"years","created_at":"x"}]}"""))
        val v = client.vaccinations()
        assertEquals(10, v.results[0].nextDueAmount)

        server.enqueue(json("""{"id":1,"name":"Tetanus"}"""))
        client.updateVaccination(1, VaccinationPatch().lastDate("2026-01-01").nextDueUnit("years"))
        server.takeRequest(); server.takeRequest() // Kategorie, Impfbuch
        val p = server.takeRequest()
        assertEquals("/api/v1/health/vaccinations/1/", p.path)
        assertEquals(setOf("last_date", "next_due_unit"), body(p).keys)
    }

    @Test
    fun `Laborwerte Uebersicht Verlauf und Erfassen`() = runTest {
        server.enqueue(json("""{"count":1,"flagged_count":1,"names":["Cholesterin"],"results":[
            {"key":"cholesterin","name":"Cholesterin","count":2,"latest":$lab,"previous":null,"difference":"0.4","flagged":true}]}"""))
        val o = client.labs()
        assertEquals(1, o.flaggedCount)
        assertEquals("0.4", o.results[0].difference)
        assertNull(o.results[0].previous)
        server.takeRequest()

        server.enqueue(json("""{"key":"cholesterin","name":"Cholesterin","unit":"mmol/l","count":1,"points":[$lab]}"""))
        assertEquals(1, client.labTrend("Cholesterin").points.size)
        assertEquals("/api/v1/health/labs/trend/?name=Cholesterin", server.takeRequest().path)

        server.enqueue(json(lab, 201))
        client.addLabToCheck(3, LabPatch().name("Cholesterin").value("5.5").unit("mmol/l").date("2026-03-01"))
        val r = server.takeRequest()
        assertEquals("/api/v1/health/checks/3/labs/", r.path)
        assertEquals("5.5", body(r)["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Laborfehler pro Feld`() = runTest {
        server.enqueue(json("""{"error":{"code":"validation_error","message":"x","fields":{"ref_from":["Muss kleiner sein."]}}}""", 400))
        val e = assertFailsWith<ApiException> { client.addLab(LabPatch().name("A").value("1")) }
        assertEquals(listOf("Muss kleiner sein."), e.fields["ref_from"])
    }

    @Test
    fun `Training und Vorlagen`() = runTest {
        server.enqueue(json("""{"count":1,"results":[{"id":1,"date":"2026-03-02","start_time":"07:00","end_time":"07:45","sport":"laufen","sport_label":"Laufen",
            "minutes":45,"intensity":"mittel","intensity_label":"Mittel","distance_km":"7.5","pace":"6:00","note":"","kcal":420,"created_at":"x"}]}"""))
        val t = client.trainings(from = "2026-03-01", to = "2026-03-07")
        val r = server.takeRequest()
        assertTrue("from=2026-03-01" in r.path!! && "to=2026-03-07" in r.path!!)
        assertEquals(420, t.results[0].kcal)
        assertEquals("7.5", t.results[0].distanceKm)

        server.enqueue(json("""{"id":2,"date":"2026-03-03"}""", 201))
        client.addTraining(TrainingPatch().date("2026-03-03").sport("radfahren").minutes(60).intensity("hoch").distanceKm(null))
        val b = body(server.takeRequest())
        assertEquals(JsonNull, b["distance_km"])
        assertEquals("60", b["minutes"]!!.jsonPrimitive.content)

        server.enqueue(json("""{"id":2,"date":"2026-03-04"}""", 201))
        client.applyTrainingTemplate(8, "2026-03-04")
        assertEquals("POST /api/v1/health/training-templates/8/apply/2026-03-04/", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test
    fun `Training ausgeschaltet`() = runTest {
        server.enqueue(json("""{"error":{"code":"training_disabled","message":"aus"}}""", 403))
        assertEquals("training_disabled", assertFailsWith<ApiException> { client.trainings() }.code)
    }

    @Test
    fun `Ziele lesen und ersetzen`() = runTest {
        val antwort = """{"weekly":[{"sport":"","sport_label":"Alle","units":3,"minutes":null}],
            "long_term":{"goal_id":4,"goal_title":"Sparen","counts":"km","text":"","state":{"current":"10","target":"100","remaining":"90","percent":10,
            "reached":false,"expired":false,"weeks_left":12,"per_week":"7.5"}},
            "options":{"sports":[{"value":"laufen","label":"Laufen"}],"goals":[{"id":4,"title":"Sparen"}],"counts":[{"value":"km","label":"Kilometer"}]}}"""
        server.enqueue(json(antwort))
        val z = client.trainingGoals()
        assertEquals(3, z.weekly[0].units)
        assertEquals(10, z.longTerm.state?.percent)
        assertEquals("laufen", z.options.sports[0].value)

        server.enqueue(json(antwort))
        client.setTrainingGoals(TrainingGoalsPatch().weekly(listOf(WeeklyGoal(sport = "laufen", units = 2))).goalId(null))
        val r = server.takeRequest()
        assertEquals("PUT", r.method)
        val b = body(r)
        assertEquals("laufen", b["weekly"]!!.jsonArray[0].jsonObject["sport"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, b["weekly"]!!.jsonArray[0].jsonObject["minutes"])
        assertEquals(JsonNull, b["long_term"]!!.jsonObject["goal_id"])
    }

    @Test
    fun `Ziele ohne Angabe senden nichts`() {
        assertEquals(JsonObject(emptyMap()), TrainingGoalsPatch().alsJson())
    }

    @Test
    fun `Rueckblick und Ampel`() = runTest {
        server.enqueue(json("""{"week_start":"2026-03-02","week_end":"2026-03-08",
            "days":[{"date":"2026-03-02","minutes":45,"kcal":420,"trainings":[]}],
            "totals":{"count":1,"minutes":45,"kcal":420,"km":"7.5","active_days":1},
            "goals":[{"sport":"","sport_label":"Alle","units":3,"minutes":null,"done_units":1,"done_minutes":45,"percent":33,"reached":false}],
            "load":{"level":"normal","sentence":"Passt.","this_week":45,"average_previous_weeks":40,"ratio":"1.1","percent_change":12,"weeks_with_data":3,"raw_minutes":45},
            "month":{"from":"2026-03-01","to":"2026-03-31","count":1,"minutes":45,"kcal":420,"sports":[{"sport":"laufen","sport_label":"Laufen","count":1,"minutes":45,"kcal":420,"km":"7.5"}]},
            "long_term":null,"review":null}"""))
        val r = client.trainingReview(week = "2026-03-04", month = "2026-03")
        val q = server.takeRequest().path!!
        assertTrue("week=2026-03-04" in q && "month=2026-03" in q)
        assertEquals(33, r.goals[0].percent)
        assertNull(r.review)
        assertEquals("laufen", r.month.sports[0].sport)

        server.enqueue(json("""{"week_start":"2026-03-02","week_end":"2026-03-08","source":"plan","level":"gelb","label":"Geht so",
            "days":[{"date":"2026-03-02","level":"gruen","label":"Gut","kcal":2100,"need":2300,"coverage":"0.91","entries":3,"estimated_percent":0,"estimate_note":null,
            "criteria":[{"name":"Eiweiß","level":"gruen","value":"90 g","sentence":"ok"}]}],
            "summary":[],"days_with_data":1,"kcal_average":2100,"has_meal_plan":true,
            "advice":{"text":"Mehr Gemüse.","model":"m","created_at":"x","suggestions":[{"title":"Suppe","reason":"leicht","recipe_key":"abc"}]}}"""))
        val a = client.trafficLight(week = "2026-03-04", source = "plan")
        assertTrue("source=plan" in server.takeRequest().path!!)
        assertEquals("gelb", a.level)
        assertEquals(2100, a.days[0].kcal)
        assertEquals("abc", a.advice?.suggestions?.get(0)?.recipeKey)
    }

    @Test
    fun `KI-Einordnung nur lesen`() = runTest {
        server.enqueue(json("""{"ai_available":true,"analysis":{"text":"Alles ruhig.","model":"m","created_at":"x","measurements_at_time":4,"outdated":true}}"""))
        val a = client.labAnalysis()
        assertTrue(a.aiAvailable)
        assertTrue(a.analysis!!.outdated)
        server.enqueue(json("""{"ai_available":false,"analysis":null}"""))
        assertNull(client.labAnalysis().analysis)
    }
}
