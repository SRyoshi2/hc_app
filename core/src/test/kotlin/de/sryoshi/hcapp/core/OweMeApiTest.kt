package de.sryoshi.hcapp.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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

class OweMeApiTest {
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

    private fun konto(key: String = "k1", richtung: String = "out", extra: String = "") = """{"key":"$key","person_name":"Werner","direction":"$richtung",
        "direction_label":"Ich habe verliehen","note":"","due_date":"2026-11-01","contact_id":null,"person_user_id":null,"is_owner":true,"shared":false,
        "total_given":"500.00","total_returned":"200.00","balance":"300.00","is_settled":false,"is_overpaid":false,"is_due":false,"days_overdue":null,
        "created_at":"2026-10-01T10:00:00+00:00"$extra}"""

    private val detail = konto(extra = """,
        "entries":[{"id":1,"kind":"given","kind_label":"Geld gegeben","date":"2026-10-01","title":"Auto","amount":"500.00","created_by":"Sven","created_at":"x","balance_after":"500.00"},
                   {"id":2,"kind":"returned","kind_label":"Rückzahlung","date":"2026-10-15","title":"","amount":"200.00","created_by":"","created_at":"x","balance_after":"300.00"}],
        "plan":{"interval":"monthly","interval_label":"monatlich","rate_amount":"100.00","completed":false,"count":3,"paid_count":1,"next_due_date":"2026-11-01","overdue_count":0,
                "rates":[{"due_date":"2026-10-01","amount":"100.00","agreed_amount":"100.00","covered":"100.00","status":"bezahlt","is_extra":false},
                         {"due_date":"2026-11-01","amount":"100.00","agreed_amount":null,"covered":"0.00","status":"offen","is_extra":true}]}""")

    @Test
    fun `Uebersicht`() = runTest {
        server.enqueue(json("""{"receivable":[${konto("a")}],"payable":[${konto("b", "in")}],"settled":[],"total_receivable":"300.00","total_payable":"50.00","net":"250.00","due":["a"]}"""))
        val u = client.oweMe()
        assertEquals("/api/v1/oweme/", server.takeRequest().path)
        assertEquals("a", u.receivable.single().key)
        assertTrue(u.receivable.single().verliehen)
        assertEquals(false, u.payable.single().verliehen)
        assertEquals("250.00", u.net)
        assertEquals(listOf("a"), u.due)
        assertNull(u.receivable.single().daysOverdue)
    }

    @Test
    fun `Konto mit Auszug und Plan aus einer Antwort`() = runTest {
        server.enqueue(json(detail))
        val d = client.oweMeAccount("k1")
        assertEquals("/api/v1/oweme/accounts/k1/", server.takeRequest().path)
        assertEquals("Werner", d.account.personName)
        assertEquals("300.00", d.account.balance)
        assertEquals("2026-11-01", d.account.dueDate)
        assertEquals(listOf("500.00", "300.00"), d.entries.map { it.balanceAfter })
        assertEquals(OweMeKind.RETURNED, d.entries[1].kind)
        val plan = d.plan!!
        assertEquals(3, plan.count)
        assertEquals("bezahlt", plan.rates[0].status)
        assertNull(plan.rates[1].agreedAmount)
        assertTrue(plan.rates[1].isExtra)
    }

    @Test
    fun `Konto ohne Plan`() = runTest {
        server.enqueue(json(konto(extra = ""","entries":[],"plan":null""")))
        val d = client.oweMeAccount("k1")
        assertNull(d.plan)
        assertTrue(d.entries.isEmpty())
    }

    @Test
    fun `unlesbare Kontoantwort wird gemeldet`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>"))
        assertEquals(ApiException.BAD_RESPONSE, assertFailsWith<ApiException> { client.oweMeAccount("k1") }.code)
    }

    @Test
    fun `Konto anlegen`() = runTest {
        server.enqueue(json(konto(extra = ""","entries":[],"plan":null"""), 201))
        client.createOweMeAccount(OweMeAccountPatch().direction(OweMeDirection.LENT).personName("Werner").dueDate("2026-11-01").note("Auto"))
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/api/v1/oweme/accounts/", r.path)
        val b = body(r)
        assertEquals(setOf("direction", "person_name", "due_date", "note"), b.keys)
        assertEquals("out", b["direction"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Frist loeschen sendet null`() = runTest {
        server.enqueue(json(detail))
        client.updateOweMeAccount("k1", OweMeAccountPatch().dueDate(null))
        val r = server.takeRequest()
        assertEquals("PATCH", r.method)
        val b = body(r)
        assertEquals(setOf("due_date"), b.keys)
        assertTrue(b["due_date"] is JsonNull)
    }

    @Test
    fun `Konto loeschen`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        client.deleteOweMeAccount("k1")
        val r = server.takeRequest()
        assertEquals("DELETE", r.method)
        assertEquals("/api/v1/oweme/accounts/k1/", r.path)
    }

    @Test
    fun `Bewegung erfassen`() = runTest {
        server.enqueue(json("""{"entry":{"id":9,"kind":"returned","kind_label":"Rückzahlung","date":"2026-10-20","title":"Rate","amount":"100.00","created_by":"Sven","created_at":"x"},"balance":"200.00"}""", 201))
        val erg = client.addOweMeEntry("k1", OweMeKind.RETURNED, OweMeEntryPatch().date("2026-10-20").title("Rate").amount("100.00"))
        assertEquals("200.00", erg.balance)
        assertEquals(9, erg.entry.id)
        assertNull(erg.entry.balanceAfter)
        val r = server.takeRequest()
        assertEquals("/api/v1/oweme/accounts/k1/entries/returned/", r.path)
        assertEquals("100.00", body(r)["amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Bewegung aendern und loeschen`() = runTest {
        server.enqueue(json("""{"entry":{"id":9,"kind":"given","date":"2026-10-20","title":"x","amount":"80.00"},"balance":"120.00"}"""))
        server.enqueue(json("""{"balance":"40.00"}"""))
        assertEquals("120.00", client.updateOweMeEntry(9, OweMeEntryPatch().amount("80.00")).balance)
        assertEquals("40.00", client.deleteOweMeEntry(9).balance)
        val a = server.takeRequest()
        assertEquals("PATCH /api/v1/oweme/entries/9/", "${a.method} ${a.path}")
        assertEquals(setOf("amount"), body(a).keys)
        val b = server.takeRequest()
        assertEquals("DELETE /api/v1/oweme/entries/9/", "${b.method} ${b.path}")
    }

    @Test
    fun `Plan mit Ratenhoehe sendet nur die passende Angabe`() = runTest {
        server.enqueue(json(detail))
        client.setOweMePlan("k1", OweMePlanInput(OweMePlanMode.RATE, "2026-11-01", "monthly", rate = "100.00", count = 5, end = "2027-01-01"))
        val r = server.takeRequest()
        assertEquals("PUT", r.method)
        assertEquals("/api/v1/oweme/accounts/k1/plan/", r.path)
        val b = body(r)
        assertEquals(setOf("mode", "start", "interval", "rate"), b.keys)
        assertEquals("100.00", b["rate"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Plan mit Anzahl und mit Enddatum`() = runTest {
        server.enqueue(json(detail))
        server.enqueue(json(detail))
        client.setOweMePlan("k1", OweMePlanInput(OweMePlanMode.COUNT, "2026-11-01", "weekly", count = 6))
        client.setOweMePlan("k1", OweMePlanInput(OweMePlanMode.END, "2026-11-01", "biweekly", end = "2027-03-01"))
        val a = body(server.takeRequest())
        assertEquals(6, a["count"]!!.jsonPrimitive.int)
        assertEquals("weekly", a["interval"]!!.jsonPrimitive.content)
        assertEquals("2027-03-01", body(server.takeRequest())["end"]!!.jsonPrimitive.content)
    }

    @Test
    fun `freier Plan sendet die Raten als Liste`() = runTest {
        server.enqueue(json(detail))
        client.setOweMePlan("k1", OweMePlanInput(OweMePlanMode.FREE, "2026-11-01", rates = listOf(OweMeRateInput("2026-11-01", "150.00"), OweMeRateInput("2026-12-01", "150.00"))))
        val b = body(server.takeRequest())
        assertEquals(setOf("mode", "start", "interval", "rates"), b.keys)
        val raten = b["rates"]!!.jsonArray
        assertEquals(2, raten.size)
        assertEquals("2026-12-01", raten[1].jsonObject["date"]!!.jsonPrimitive.content)
        assertEquals("150.00", raten[1].jsonObject["amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Plan beenden`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        client.deleteOweMePlan("k1")
        assertEquals("DELETE /api/v1/oweme/accounts/k1/plan/", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test
    fun `fremdes Konto und Feldfehler`() = runTest {
        server.enqueue(json("""{"error":{"code":"not_found","message":"Dieses Konto gibt es nicht.","fields":{}}}""", 404))
        assertEquals("not_found", assertFailsWith<ApiException> { client.oweMeAccount("x") }.code)

        server.enqueue(json("""{"error":{"code":"validation_error","message":"x","fields":{"amount":["Dieses Feld ist erforderlich."],"non_field":["Für ein Konto ohne offenen Betrag lässt sich kein Plan aufstellen."],"rates[1]":["Braucht Datum (date) und Betrag (amount)."]}}}""", 400))
        val e = assertFailsWith<ApiException> { client.setOweMePlan("k1", OweMePlanInput(OweMePlanMode.FREE, "2026-11-01")) }
        assertEquals(
            listOf("Betrag: Dieses Feld ist erforderlich.", "Für ein Konto ohne offenen Betrag lässt sich kein Plan aufstellen.", "Rate 2: Braucht Datum (date) und Betrag (amount)."),
            FeldFehler.meldungen(e.fields, FeldFehler.OWEME),
        )
        // Ohne Bereichsliste bleibt „amount“ die Menge einer Zutat.
        assertEquals("Menge: Dieses Feld ist erforderlich.", FeldFehler.meldungen(mapOf("amount" to listOf("Dieses Feld ist erforderlich."))).first())
    }
}
