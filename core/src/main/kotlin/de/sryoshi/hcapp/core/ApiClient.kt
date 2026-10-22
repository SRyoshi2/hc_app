package de.sryoshi.hcapp.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    // ── Rezepte ─────────────────────────────────────────────────────────

    /** Eigene Rezepte. [q] sucht in Titel und Kategorie, [categories] sind Kategorienamen (alle zutreffenden). */
    suspend fun recipes(q: String = "", categories: List<String> = emptyList(), limit: Int = 50, offset: Int = 0): Page<RecipeSummary> =
        get("recipes/", *rezeptAbfrage(q, categories, limit, offset))

    /** „Entdecken“: öffentliche Rezepte anderer Nutzer. */
    suspend fun publicRecipes(q: String = "", categories: List<String> = emptyList(), limit: Int = 50, offset: Int = 0): Page<RecipeSummary> =
        get("recipes/public/", *rezeptAbfrage(q, categories, limit, offset))

    suspend fun recipe(key: String): RecipeDetail = get("recipes/$key/")

    suspend fun recipeCategories(): Page<RecipeCategory> = get("recipe-categories/", "limit" to "200")

    /** Öffentliches Rezept als eigene Kopie übernehmen; liefert die Kopie. */
    suspend fun copyRecipe(key: String): RecipeDetail = post("recipes/$key/copy/", null)

    /** Zutaten auf [servings] Portionen hochgerechnet, nichts wird gespeichert. `null` = Portionen des Rezepts. */
    suspend fun shoppingPreview(key: String, servings: String? = null): ShoppingPreview =
        post("recipes/$key/shopping-preview/", obj("servings" to servings))

    /**
     * Zeilen in eine Einkaufsliste übernehmen: entweder [targetListKey] (bestehende Liste) **oder** [newListTitle]
     * (neue Liste) – nie beides, der Server lehnt das ab.
     */
    suspend fun toShoppingList(key: String, targetListKey: String?, newListTitle: String?, items: List<ItemInput>): ToShoppingResult {
        val zeilen = JsonArray(items.map {
            obj("name" to it.name, "amount" to it.amount, "category" to it.category, "store" to it.store, "price" to it.price)
        })
        val felder = mutableMapOf<String, JsonElement>("items" to zeilen)
        targetListKey?.let { felder["target_list_key"] = JsonPrimitive(it) }
        newListTitle?.let { felder["new_list_title"] = JsonPrimitive(it) }
        return post("recipes/$key/to-shopping-list/", JsonObject(felder))
    }

    /** Neues Rezept. Nur gesetzte Felder werden gesendet; `title` ist Pflicht. Liefert das gespeicherte Rezept. */
    suspend fun createRecipe(patch: RecipePatch): RecipeDetail = post("recipes/", patch.alsJson())

    /** Ändert nur die in [patch] gesetzten Felder (nur der Besitzer; sonst 403 `forbidden`). */
    suspend fun updateRecipe(key: String, patch: RecipePatch): RecipeDetail = send("PATCH", "recipes/$key/", patch.alsJson())

    suspend fun deleteRecipe(key: String) = ohneAntwort("DELETE", "recipes/$key/")

    /**
     * Bild hochladen: [asTitle] setzt das Titelbild, sonst kommt es in die Galerie. Der Server lehnt Dateien über
     * 10 MB ab. Liefert das Rezept mit dem neuen Bild.
     */
    suspend fun uploadRecipeImage(
        key: String, bild: ByteArray, dateiname: String, mimeType: String, asTitle: Boolean, caption: String = "",
    ): RecipeDetail {
        val teile = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", dateiname, bild.toRequestBody(mimeType.toMediaType()))
            .addFormDataPart("title", if (asTitle) "true" else "false")
            .apply { if (caption.isNotBlank()) addFormDataPart("caption", caption) }
            .build()
        val url = basis.newBuilder().addPathSegments("api/v1/recipes/$key/images/").build()
        val request = Request.Builder().url(url).post(teile).header("Accept", "application/json")
            .apply { token()?.let { header("Authorization", "Bearer $it") } }
            .build()
        return antwort(request)
    }

    suspend fun deleteRecipeImage(key: String, bildId: Long) = ohneAntwort("DELETE", "recipes/$key/images/$bildId/")

    suspend fun deleteRecipeTitleImage(key: String) = ohneAntwort("DELETE", "recipes/$key/title-image/")

    /** Legt eine Kategorie an; gibt es sie schon, kommt die vorhandene zurück. */
    suspend fun createRecipeCategory(name: String): RecipeCategory = post("recipe-categories/", obj("name" to name))

    private fun rezeptAbfrage(q: String, categories: List<String>, limit: Int, offset: Int): Array<Pair<String, String>> =
        buildList {
            add("limit" to limit.toString())
            add("offset" to offset.toString())
            if (q.isNotBlank()) add("q" to q.trim())
            categories.forEach { add("category" to it) }
        }.toTypedArray()

    // ── Technik ─────────────────────────────────────────────────────────

    private suspend inline fun <reified T> get(pfad: String, vararg query: Pair<String, String>): T =
        antwort(anfrage("GET", pfad, null, query.toList()))

    private suspend inline fun <reified T> post(pfad: String, body: JsonObject?, mitToken: Boolean = true): T =
        antwort(anfrage("POST", pfad, body, mitToken = mitToken))

    private suspend inline fun <reified T> send(methode: String, pfad: String, body: JsonObject?): T =
        antwort(anfrage(methode, pfad, body))

    private suspend fun ohneAntwort(methode: String, pfad: String) {
        ausfuehren(anfrage(methode, pfad, null))
    }

    private suspend inline fun <reified T> antwort(request: Request): T {
        val (status, text) = ausfuehren(request)
        return try {
            JSON.decodeFromString<T>(text)
        } catch (e: Exception) {
            throw ApiException(ApiException.BAD_RESPONSE, "Unerwartete Antwort der Instanz.", status, cause = e)
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

    /**
     * Führt die Anfrage aus und liest den Antworttext **vollständig im IO-Kontext**: Wer den Text erst
     * später liest, tut es auf dem Aufrufer-Thread, und Android wirft dort bei Netzwerkzugriff eine
     * `NetworkOnMainThreadException`. Fehlerantworten werden zu [ApiException]. Ergebnis: Status und Text.
     */
    private suspend fun ausfuehren(request: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw fehler(r.code, text)
                r.code to text
            }
        } catch (e: IOException) {
            throw ApiException(ApiException.NETWORK, "Die Instanz ist nicht erreichbar.", cause = e)
        }
    }

    private fun fehler(status: Int, text: String): ApiException {
        val fehler = runCatching { JSON.parseToJsonElement(text).jsonObject["error"]?.jsonObject }.getOrNull()
        if (fehler != null) {
            val felder = fehler["fields"]?.let { f ->
                runCatching {
                    f.jsonObject.mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content }.orEmpty() }
                }.getOrNull()
            }.orEmpty()
            return ApiException(
                code = fehler["code"]?.jsonPrimitive?.contentOrNull ?: "error",
                message = fehler["message"]?.jsonPrimitive?.contentOrNull ?: "Fehler $status",
                httpStatus = status,
                fields = felder,
            )
        }
        // Keine JSON-Fehlerantwort: bei 404 gibt es die API auf dieser Instanz nicht (zu alter Stand oder falsche Adresse).
        return if (status == 404) {
            ApiException(ApiException.NO_API, "Diese Instanz unterstützt die App noch nicht.", 404)
        } else {
            ApiException("http_$status", "Unerwartete Antwort der Instanz ($status).", status)
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
