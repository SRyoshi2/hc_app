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

    // ── OweMe ───────────────────────────────────────────────────────────

    suspend fun oweMe(): OweMeOverview = get("oweme/")

    /** Neues Konto: `direction` und `person_name` sind Pflicht. Liefert das Konto mit leerem Auszug. */
    suspend fun createOweMeAccount(patch: OweMeAccountPatch): OweMeAccountDetail = konto(anfrage("POST", "oweme/accounts/", patch.alsJson()))

    suspend fun oweMeAccount(key: String): OweMeAccountDetail = konto(anfrage("GET", "oweme/accounts/$key/", null))

    /** Nur der Besitzer; sonst 403 `forbidden`. */
    suspend fun updateOweMeAccount(key: String, patch: OweMeAccountPatch): OweMeAccountDetail =
        konto(anfrage("PATCH", "oweme/accounts/$key/", patch.alsJson()))

    suspend fun deleteOweMeAccount(key: String) = ohneAntwort("DELETE", "oweme/accounts/$key/")

    /** [kind] ist [OweMeKind.GIVEN] oder [OweMeKind.RETURNED]; `date`, `title` und `amount` müssen gesetzt sein. */
    suspend fun addOweMeEntry(key: String, kind: String, patch: OweMeEntryPatch): OweMeEntryResult =
        post("oweme/accounts/$key/entries/$kind/", patch.alsJson())

    suspend fun updateOweMeEntry(id: Long, patch: OweMeEntryPatch): OweMeEntryResult = send("PATCH", "oweme/entries/$id/", patch.alsJson())

    /** Löscht die Bewegung und liefert den neuen Kontostand. */
    suspend fun deleteOweMeEntry(id: Long): OweMeBalance = antwort(anfrage("DELETE", "oweme/entries/$id/", null))

    /** Legt den Rückzahlungsplan an oder ersetzt ihn. */
    suspend fun setOweMePlan(key: String, plan: OweMePlanInput): OweMeAccountDetail =
        konto(anfrage("PUT", "oweme/accounts/$key/plan/", plan.alsJson()))

    suspend fun deleteOweMePlan(key: String) = ohneAntwort("DELETE", "oweme/accounts/$key/plan/")

    /** Das Konto steht auf oberster Ebene der Antwort, Auszug und Plan daneben: beides aus demselben Text lesen. */
    private suspend fun konto(request: Request): OweMeAccountDetail {
        val (status, text) = ausfuehren(request)
        return try {
            val teil = JSON.decodeFromString<OweMeDetailTeil>(text)
            OweMeAccountDetail(JSON.decodeFromString<OweMeAccount>(text), teil.entries, teil.plan)
        } catch (e: Exception) {
            throw ApiException(ApiException.BAD_RESPONSE, "Unerwartete Antwort der Instanz.", status, cause = e)
        }
    }

    // ── Health ──────────────────────────────────────────────────────────

    suspend fun healthProfile(): HealthProfile = get("health/profile/")

    suspend fun updateHealthProfile(patch: HealthProfilePatch): HealthProfile = send("PATCH", "health/profile/", patch.alsJson())

    /** Schaltet Health ein oder aus. Einschalten braucht vollständige Stammdaten und ein Gewicht (sonst `profile_incomplete`). */
    suspend fun setHealthEnabled(enabled: Boolean): HealthProfile =
        post("health/profile/enable/", JsonObject(mapOf("enabled" to JsonPrimitive(enabled))))

    suspend fun weights(limit: Int = 200, offset: Int = 0): Page<WeightEntry> =
        get("health/weights/", "limit" to limit.toString(), "offset" to offset.toString())

    /** Erneutes Wiegen am selben Tag ersetzt den Wert. [date] `null` = heute (Server). */
    suspend fun addWeight(kg: String, date: String? = null): WeightEntry =
        post("health/weights/", JsonObject(buildMap<String, JsonElement> {
            put("weight_kg", JsonPrimitive(kg))
            date?.let { put("date", JsonPrimitive(it)) }
        }))

    suspend fun deleteWeight(id: Long) = ohneAntwort("DELETE", "health/weights/$id/")

    /** [month] als `JJJJ-MM`; `null` = laufender Monat. */
    suspend fun calorieMonth(month: String? = null): CalorieMonth =
        get("health/calories/", *(if (month == null) emptyArray<Pair<String, String>>() else arrayOf("month" to month)))

    suspend fun calorieDay(date: String): CalorieDay = get("health/calories/$date/")

    suspend fun addCalorieEntry(date: String, patch: CalorieEntryPatch): CalorieEntry =
        post("health/calories/$date/entries/", patch.alsJson())

    suspend fun updateCalorieEntry(id: Long, patch: CalorieEntryPatch): CalorieEntry =
        send("PATCH", "health/calorie-entries/$id/", patch.alsJson())

    suspend fun deleteCalorieEntry(id: Long) = ohneAntwort("DELETE", "health/calorie-entries/$id/")

    suspend fun mealTemplates(): Page<MealTemplate> = get("health/meal-templates/", "limit" to "200")

    suspend fun createMealTemplate(patch: MealTemplatePatch): MealTemplate = post("health/meal-templates/", patch.alsJson())

    suspend fun updateMealTemplate(id: Long, patch: MealTemplatePatch): MealTemplate =
        send("PATCH", "health/meal-templates/$id/", patch.alsJson())

    suspend fun deleteMealTemplate(id: Long) = ohneAntwort("DELETE", "health/meal-templates/$id/")

    /** Die Vorlage als Eintrag für den Tag übernehmen (eine Kopie). */
    suspend fun applyMealTemplate(id: Long, date: String): CalorieEntry =
        post("health/meal-templates/$id/apply/$date/", null)

    /**
     * Vorschläge aus dem Bundeslebensmittelschlüssel während der Eingabe (höchstens acht, ab zwei Zeichen).
     * Setzt den Endpunkt `GET /api/v1/health/foods/?q=` voraus (siehe docs/API_KONZEPT.md); gibt es ihn auf der
     * Instanz nicht, wirft der Aufruf eine [ApiException] – der Aufrufer zeigt dann einfach keine Vorschläge.
     */
    suspend fun searchFoods(q: String): List<FoodSuggestion> =
        get<FoodSuggestions>("health/foods/suggest/", "q" to q.trim()).results

    // ── Health: Arztbesuche, Dokumente, Impfbuch, Labor (Phase 6) ───────

    suspend fun healthChecks(limit: Int = 200, offset: Int = 0): Page<HealthCheck> =
        get("health/checks/", "limit" to limit.toString(), "offset" to offset.toString())

    suspend fun healthCheck(id: Long): HealthCheck = get("health/checks/$id/")

    suspend fun createHealthCheck(patch: HealthCheckPatch): HealthCheck = post("health/checks/", patch.alsJson())

    suspend fun updateHealthCheck(id: Long, patch: HealthCheckPatch): HealthCheck = send("PATCH", "health/checks/$id/", patch.alsJson())

    suspend fun deleteHealthCheck(id: Long) = ohneAntwort("DELETE", "health/checks/$id/")

    /** Datei an einen Arztbesuch hängen (höchstens 25 MB, der Server prüft). [categoryId] ist optional. */
    suspend fun uploadHealthDocument(
        checkId: Long, datei: ByteArray, dateiname: String, mimeType: String, title: String, categoryId: Long? = null,
    ): HealthDocument {
        val teile = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", dateiname, datei.toRequestBody(mimeType.toMediaType()))
            .addFormDataPart("title", title)
            .apply { if (categoryId != null) addFormDataPart("category_id", categoryId.toString()) }
            .build()
        val url = basis.newBuilder().addPathSegments("api/v1/health/checks/$checkId/documents/").build()
        val request = Request.Builder().url(url).post(teile).header("Accept", "application/json")
            .apply { token()?.let { header("Authorization", "Bearer $it") } }
            .build()
        return antwort(request)
    }

    /** Lädt die Datei eines Dokuments (nur mit Token; es gibt keine öffentliche Adresse). */
    suspend fun downloadHealthDocument(key: String): ByteArray = withContext(Dispatchers.IO) {
        val url = basis.newBuilder().addPathSegments("api/v1/health/documents/$key/").build()
        val request = Request.Builder().url(url).get()
            .apply { token()?.let { header("Authorization", "Bearer $it") } }
            .build()
        try {
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw fehler(r.code, r.body?.string().orEmpty())
                r.body?.bytes() ?: ByteArray(0)
            }
        } catch (e: IOException) {
            throw ApiException(ApiException.NETWORK, "Die Instanz ist nicht erreichbar.", cause = e)
        }
    }

    suspend fun deleteHealthDocument(id: Long) = ohneAntwort("DELETE", "health/documents/$id/")

    suspend fun addHealthComment(checkId: Long, text: String): HealthComment =
        post("health/checks/$checkId/comments/", obj("text" to text))

    suspend fun deleteHealthComment(id: Long) = ohneAntwort("DELETE", "health/comments/$id/")

    suspend fun documentCategories(): Page<DocumentCategory> = get("health/document-categories/", "limit" to "200")

    suspend fun createDocumentCategory(name: String): DocumentCategory = post("health/document-categories/", obj("name" to name))

    suspend fun deleteDocumentCategory(id: Long) = ohneAntwort("DELETE", "health/document-categories/$id/")

    suspend fun vaccinations(limit: Int = 200, offset: Int = 0): Page<Vaccination> =
        get("health/vaccinations/", "limit" to limit.toString(), "offset" to offset.toString())

    suspend fun createVaccination(patch: VaccinationPatch): Vaccination = post("health/vaccinations/", patch.alsJson())

    suspend fun updateVaccination(id: Long, patch: VaccinationPatch): Vaccination =
        send("PATCH", "health/vaccinations/$id/", patch.alsJson())

    suspend fun deleteVaccination(id: Long) = ohneAntwort("DELETE", "health/vaccinations/$id/")

    suspend fun labs(limit: Int = 200, offset: Int = 0): LabOverview =
        get("health/labs/", "limit" to limit.toString(), "offset" to offset.toString())

    suspend fun labTrend(name: String): LabTrend = get("health/labs/trend/", "name" to name)

    suspend fun addLab(patch: LabPatch): LabMeasurement = post("health/labs/", patch.alsJson())

    /** Messung am Arztbesuch erfassen (Herkunft = der Besuch). */
    suspend fun addLabToCheck(checkId: Long, patch: LabPatch): LabMeasurement = post("health/checks/$checkId/labs/", patch.alsJson())

    suspend fun updateLab(id: Long, patch: LabPatch): LabMeasurement = send("PATCH", "health/labs/$id/", patch.alsJson())

    suspend fun deleteLab(id: Long) = ohneAntwort("DELETE", "health/labs/$id/")

    /** Die abgelegte KI-Einordnung (nur lesen). */
    suspend fun labAnalysis(): LabAnalysis = get("health/labs/analysis/")

    // ── Health: Training und Ernährungsampel (Phase 6) ──────────────────

    /** Einheiten, neueste zuerst; [from]/[to] als `JJJJ-MM-TT`. Ist das Training im Profil aus, antwortet der Server mit 403 `training_disabled`. */
    suspend fun trainings(from: String? = null, to: String? = null, limit: Int = 200, offset: Int = 0): Page<TrainingSession> =
        get("health/trainings/", *buildList {
            add("limit" to limit.toString()); add("offset" to offset.toString())
            from?.let { add("from" to it) }; to?.let { add("to" to it) }
        }.toTypedArray())

    suspend fun addTraining(patch: TrainingPatch): TrainingSession = post("health/trainings/", patch.alsJson())

    suspend fun updateTraining(id: Long, patch: TrainingPatch): TrainingSession = send("PATCH", "health/trainings/$id/", patch.alsJson())

    suspend fun deleteTraining(id: Long) = ohneAntwort("DELETE", "health/trainings/$id/")

    suspend fun trainingTemplates(): Page<TrainingTemplate> = get("health/training-templates/", "limit" to "200")

    suspend fun createTrainingTemplate(patch: TrainingTemplatePatch): TrainingTemplate = post("health/training-templates/", patch.alsJson())

    suspend fun updateTrainingTemplate(id: Long, patch: TrainingTemplatePatch): TrainingTemplate =
        send("PATCH", "health/training-templates/$id/", patch.alsJson())

    suspend fun deleteTrainingTemplate(id: Long) = ohneAntwort("DELETE", "health/training-templates/$id/")

    suspend fun applyTrainingTemplate(id: Long, date: String): TrainingSession = post("health/training-templates/$id/apply/$date/", null)

    suspend fun trainingGoals(): TrainingGoals = get("health/training-goals/")

    suspend fun setTrainingGoals(patch: TrainingGoalsPatch): TrainingGoals = send("PUT", "health/training-goals/", patch.alsJson())

    /** Wochenrückblick; [week] ist ein beliebiger Tag der Woche, [month] `JJJJ-MM`. */
    suspend fun trainingReview(week: String? = null, month: String? = null): TrainingReview =
        get("health/training/review/", *buildList {
            week?.let { add("week" to it) }; month?.let { add("month" to it) }
        }.toTypedArray())

    /** Ernährungsampel einer Woche; [source] ist `tracker` oder `plan`. */
    suspend fun trafficLight(week: String? = null, source: String = "tracker"): TrafficLight =
        get("health/traffic-light/", *buildList {
            add("source" to source); week?.let { add("week" to it) }
        }.toTypedArray())

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
