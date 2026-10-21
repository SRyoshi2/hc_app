package de.sryoshi.hcapp.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client für `/api/v1/` einer HomeCenter-Instanz.
 *
 * @param baseUrl normalisierte Basisadresse, siehe [InstanceUrl.normalisieren]
 * @param token liefert das Token der Instanz; `null` heißt nicht angemeldet
 */
class ApiClient(
    baseUrl: String,
    private val token: () -> String? = { null },
    http: OkHttpClient = standardHttp(),
) {
    private val basis: HttpUrl = baseUrl.toHttpUrl()
    private val http = http

    // ── Meta und Anmeldung ──────────────────────────────────────────────

    suspend fun meta(): Meta = get("meta/")

    suspend fun login(username: String, password: String, deviceName: String): LoginResponse =
        post("auth/login/", obj("username" to username, "password" to password, "device_name" to deviceName), mitToken = false)

    suspend fun logout() = ohneAntwort("POST", "auth/logout/")

    suspend fun me(): Me = get("me/")

    suspend fun devices(): Page<Device> = get("auth/devices/")

    suspend fun removeDevice(id: Long) = ohneAntwort("DELETE", "auth/devices/$id/")

    // ── Einkaufsliste ───────────────────────────────────────────────────

    suspend fun shoppingLists(limit: Int = 200, offset: Int = 0): Page<ShoppingList> =
        get("shopping-lists/", "limit" to limit.toString(), "offset" to offset.toString())

    suspend fun createList(title: String): ShoppingList = post("shopping-lists/", obj("title" to title))

    suspend fun renameList(key: String, title: String): ShoppingList =
        send("PATCH", "shopping-lists/$key/", obj("title" to title))

    suspend fun deleteList(key: String) = ohneAntwort("DELETE", "shopping-lists/$key/")

    suspend fun items(key: String, limit: Int = 200, offset: Int = 0): ItemPage =
        get("shopping-lists/$key/items/", "limit" to limit.toString(), "offset" to offset.toString())

    suspend fun addItem(key: String, eingabe: ItemInput): ShoppingItem =
        post(
            "shopping-lists/$key/items/",
            obj(
                "name" to eingabe.name, "amount" to eingabe.amount, "category" to eingabe.category,
                "store" to eingabe.store, "price" to eingabe.price,
            ),
        )

    /** Ändert nur die übergebenen Felder (`name`, `amount`, `category`, `store`, `price`). */
    suspend fun updateItem(id: Long, felder: Map<String, String?>): ShoppingItem =
        send("PATCH", "shopping-items/$id/", obj(*felder.toList().toTypedArray()))

    suspend fun toggleItem(id: Long): ShoppingItem = post("shopping-items/$id/toggle/", null)

    suspend fun deleteItem(id: Long) = ohneAntwort("DELETE", "shopping-items/$id/")

    // ── Technik ─────────────────────────────────────────────────────────

    private suspend inline fun <reified T> get(pfad: String, vararg query: Pair<String, String>): T =
        antwort(anfrage("GET", pfad, null, query.toList()))

    private suspend inline fun <reified T> post(pfad: String, body: JsonObject?, mitToken: Boolean = true): T =
        antwort(anfrage("POST", pfad, body, mitToken = mitToken))

    private suspend inline fun <reified T> send(methode: String, pfad: String, body: JsonObject?): T =
        antwort(anfrage(methode, pfad, body))

    private suspend fun ohneAntwort(methode: String, pfad: String) {
        ausfuehren(anfrage(methode, pfad, null)).use { }
    }

    private suspend inline fun <reified T> antwort(request: Request): T =
        ausfuehren(request).use { r ->
            val text = r.body?.string().orEmpty()
            try {
                JSON.decodeFromString<T>(text)
            } catch (e: Exception) {
                throw ApiException(ApiException.BAD_RESPONSE, "Unerwartete Antwort der Instanz.", r.code, cause = e)
            }
        }

    private fun anfrage(
        methode: String,
        pfad: String,
        body: JsonObject?,
        query: List<Pair<String, String>> = emptyList(),
        mitToken: Boolean = true,
    ): Request {
        val url = basis.newBuilder().addPathSegments("api/v1/$pfad").apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val rumpf = when {
            body != null -> JSON.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_TYP)
            methode == "GET" || methode == "DELETE" -> null
            else -> "".toRequestBody(null) // POST ohne Inhalt
        }
        return Request.Builder().url(url).method(methode, rumpf)
            .header("Accept", "application/json")
            .apply { if (mitToken) token()?.let { header("Authorization", "Bearer $it") } }
            .build()
    }

    /** Führt aus und wandelt Fehlerantworten in [ApiException]; die Antwort muss der Aufrufer schließen. */
    private suspend fun ausfuehren(request: Request): Response = withContext(Dispatchers.IO) {
        val r = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException(ApiException.NETWORK, "Die Instanz ist nicht erreichbar.", cause = e)
        }
        if (r.isSuccessful) return@withContext r
        r.use { throw fehler(it) }
    }

    private fun fehler(r: Response): ApiException {
        val text = r.body?.string().orEmpty()
        val fehler = runCatching { JSON.parseToJsonElement(text).jsonObject["error"]?.jsonObject }.getOrNull()
        if (fehler != null) {
            val felder = fehler["fields"]?.let { f ->
                runCatching {
                    f.jsonObject.mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content }.orEmpty() }
                }.getOrNull()
            }.orEmpty()
            return ApiException(
                code = fehler["code"]?.jsonPrimitive?.contentOrNull ?: "error",
                message = fehler["message"]?.jsonPrimitive?.contentOrNull ?: "Fehler ${r.code}",
                httpStatus = r.code,
                fields = felder,
            )
        }
        // Keine JSON-Fehlerantwort: bei 404 gibt es die API auf dieser Instanz nicht (zu alter Stand oder falsche Adresse).
        return if (r.code == 404) {
            ApiException(ApiException.NO_API, "Diese Instanz unterstützt die App noch nicht.", 404)
        } else {
            ApiException("http_${r.code}", "Unerwartete Antwort der Instanz (${r.code}).", r.code)
        }
    }

    private fun obj(vararg paare: Pair<String, String?>): JsonObject =
        JsonObject(paare.associate { (k, v) -> k to (v?.let { JsonPrimitive(it) } ?: JsonNull) as JsonElement })

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private val JSON_TYP = "application/json; charset=utf-8".toMediaType()

        fun standardHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Weiterleitungen würden bei POST den Inhalt verlieren; die API leitet nicht um.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
