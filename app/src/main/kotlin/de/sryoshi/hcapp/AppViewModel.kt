package de.sryoshi.hcapp

import android.app.Application
import android.os.Build
import android.util.Log
import java.io.IOException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.sryoshi.hcapp.core.ApiClient
import de.sryoshi.hcapp.core.ApiException
import de.sryoshi.hcapp.core.FeldFehler
import de.sryoshi.hcapp.core.Compatibility
import de.sryoshi.hcapp.core.InstanceUrl
import de.sryoshi.hcapp.core.InvalidInstanceUrl
import de.sryoshi.hcapp.core.ItemInput
import de.sryoshi.hcapp.core.WeightEntry
import de.sryoshi.hcapp.core.MealTemplatePatch
import de.sryoshi.hcapp.core.MealTemplate
import de.sryoshi.hcapp.core.HealthProfilePatch
import de.sryoshi.hcapp.core.HealthProfile
import de.sryoshi.hcapp.core.FoodSuggestion
import de.sryoshi.hcapp.core.CalorieMonth
import de.sryoshi.hcapp.core.CalorieEntryPatch
import de.sryoshi.hcapp.core.CalorieDay
import de.sryoshi.hcapp.core.Me
import de.sryoshi.hcapp.core.OweMePlanInput
import de.sryoshi.hcapp.core.OweMeOverview
import de.sryoshi.hcapp.core.OweMeEntryPatch
import de.sryoshi.hcapp.core.OweMeAccountPatch
import de.sryoshi.hcapp.core.OweMeAccountDetail
import de.sryoshi.hcapp.core.Meta
import de.sryoshi.hcapp.core.RecipeCategory
import de.sryoshi.hcapp.core.RecipeDetail
import de.sryoshi.hcapp.core.RecipeSummary
import de.sryoshi.hcapp.core.ShoppingItem
import de.sryoshi.hcapp.core.ShoppingList
import de.sryoshi.hcapp.core.ShoppingPreview
import de.sryoshi.hcapp.data.BildHelfer
import de.sryoshi.hcapp.data.Instance
import de.sryoshi.hcapp.data.InstanceStore
import de.sryoshi.hcapp.ui.RezeptEntwurf
import de.sryoshi.hcapp.ui.htmlZuKlartext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Instances : Screen
    data object AddInstance : Screen

    /** [meta] ist gesetzt, wenn die Instanz neu hinzugefügt wird; sonst meldet sich ein bekanntes Konto neu an. */
    data class Login(val url: String, val meta: Meta, val username: String = "", val existing: Instance? = null) : Screen
    data class Home(val instance: Instance, val me: Me) : Screen
    data class Lists(val instance: Instance, val me: Me) : Screen
    data class Items(val instance: Instance, val me: Me, val key: String, val title: String) : Screen
    data class Recipes(val instance: Instance, val me: Me) : Screen
    data class Recipe(val instance: Instance, val me: Me, val key: String, val title: String) : Screen
    data class RecipeShopping(val instance: Instance, val me: Me, val key: String, val title: String) : Screen
    /** [key] `null` = neues Rezept. */
    data class RecipeEdit(val instance: Instance, val me: Me, val key: String?, val title: String) : Screen
    data class OweMeUebersicht(val instance: Instance, val me: Me) : Screen
    data class OweMeKonto(val instance: Instance, val me: Me, val key: String, val title: String) : Screen
    data class OweMeRatenplan(val instance: Instance, val me: Me, val key: String, val title: String) : Screen
    data class Health(val instance: Instance, val me: Me) : Screen
    /** [date] als `JJJJ-MM-TT`. */
    data class HealthDay(val instance: Instance, val me: Me, val date: String) : Screen
    /** [month] als `JJJJ-MM`. */
    data class HealthMonth(val instance: Instance, val me: Me, val month: String) : Screen
    data class HealthWeight(val instance: Instance, val me: Me) : Screen
    data class HealthTemplates(val instance: Instance, val me: Me) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = InstanceStore(app)

    var instances by mutableStateOf(store.alle())
        private set
    /** Ids der Instanzen, für die gerade ein Token gespeichert ist (die Liste zeigt „angemeldet“ bzw. „abgemeldet“). */
    var angemeldet by mutableStateOf(instances.filter { store.hatToken(it.id) }.map { it.id }.toSet())
        private set
    var screen by mutableStateOf<Screen>(if (instances.isEmpty()) Screen.AddInstance else Screen.Instances)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var lists by mutableStateOf<List<ShoppingList>>(emptyList())
        private set
    var items by mutableStateOf<List<ShoppingItem>>(emptyList())
        private set

    // Rezepte: 0 = meine, 1 = entdecken (öffentliche). Tab und Suche bleiben beim Zurückgehen erhalten.
    var recipeTab by mutableStateOf(0)
        private set
    var recipeQuery by mutableStateOf("")
        private set
    var recipes by mutableStateOf<List<RecipeSummary>>(emptyList())
        private set
    var recipe by mutableStateOf<RecipeDetail?>(null)
        private set
    var preview by mutableStateOf<ShoppingPreview?>(null)
        private set
    /** Arbeitsstand des Rezept-Editors; `null`, wenn keiner offen ist. */
    var entwurf by mutableStateOf<RezeptEntwurf?>(null)
        private set
    var kategorien by mutableStateOf<List<RecipeCategory>>(emptyList())
        private set

    var healthProfil by mutableStateOf<HealthProfile?>(null)
        private set
    /** Der heutige Tag für die Health-Übersicht. */
    var healthHeute by mutableStateOf<CalorieDay?>(null)
        private set
    var kalorienTag by mutableStateOf<CalorieDay?>(null)
        private set
    var kalorienMonat by mutableStateOf<CalorieMonth?>(null)
        private set
    var gewichte by mutableStateOf<List<WeightEntry>>(emptyList())
        private set
    var vorlagen by mutableStateOf<List<MealTemplate>>(emptyList())
        private set
    /** Vorschläge aus dem Lebensmittelschlüssel zur aktuellen Eingabe. */
    var foods by mutableStateOf<List<FoodSuggestion>>(emptyList())
        private set
    private var foodJob: Job? = null

    var oweMe by mutableStateOf<OweMeOverview?>(null)
        private set
    var konto by mutableStateOf<OweMeAccountDetail?>(null)
        private set

    fun clearError() { error = null }

    fun show(neu: Screen) { error = null; screen = neu }

    fun back() {
        show(
            when (val s = screen) {
                is Screen.AddInstance, is Screen.Login -> if (instances.isEmpty()) s else Screen.Instances
                is Screen.Home -> Screen.Instances
                is Screen.Lists -> Screen.Home(s.instance, s.me)
                is Screen.Items -> Screen.Lists(s.instance, s.me)
                is Screen.Recipes -> Screen.Home(s.instance, s.me)
                is Screen.Recipe -> Screen.Recipes(s.instance, s.me)
                is Screen.RecipeShopping -> Screen.Recipe(s.instance, s.me, s.key, s.title)
                is Screen.Health -> Screen.Home(s.instance, s.me)
                is Screen.HealthDay -> Screen.Health(s.instance, s.me)
                is Screen.HealthMonth -> Screen.Health(s.instance, s.me)
                is Screen.HealthWeight -> Screen.Health(s.instance, s.me)
                is Screen.HealthTemplates -> Screen.Health(s.instance, s.me)
                is Screen.OweMeUebersicht -> Screen.Home(s.instance, s.me)
                is Screen.OweMeKonto -> Screen.OweMeUebersicht(s.instance, s.me)
                is Screen.OweMeRatenplan -> Screen.OweMeKonto(s.instance, s.me, s.key, s.title)
                is Screen.RecipeEdit -> {
                    entwurf = null
                    if (s.key == null) Screen.Recipes(s.instance, s.me) else Screen.Recipe(s.instance, s.me, s.key, s.title)
                }
                Screen.Instances -> Screen.Instances
            },
        )
    }

    /** Liest Instanzen und Anmeldestatus neu aus dem Speicher. */
    private fun sitzungenLesen() {
        instances = store.alle()
        angemeldet = instances.filter { store.hatToken(it.id) }.map { it.id }.toSet()
    }

    private fun client(instance: Instance) = ApiClient(instance.url, { store.token(instance.id) })

    /** Führt [block] mit Fortschrittsanzeige aus und übersetzt Fehler in einen lesbaren Text. */
    private fun run(instance: Instance? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            error = null
            try {
                block()
            } catch (e: InvalidInstanceUrl) {
                error = e.message
            } catch (e: ApiException) {
                if (instance != null && e.tokenUngueltig) abgelaufen(instance) else error = text(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Auffangnetz: ein unerwarteter Fehler soll eine Meldung zeigen, nicht die App beenden.
                Log.e("HcApp", "Unerwarteter Fehler", e)
                error = "Unerwarteter Fehler: ${e.javaClass.simpleName}: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    private fun text(e: ApiException) = when (e.code) {
        ApiException.NETWORK -> "Die Instanz ist nicht erreichbar. Adresse und Verbindung prüfen."
        "app_disabled" -> "Diese App ist auf dieser Instanz abgeschaltet."
        // Mit den Feldern des Servers („Titel: Dieses Feld ist erforderlich.“) statt nur „Die Eingaben sind nicht gültig.“
        "validation_error" -> FeldFehler.meldungen(e.fields, beschriftung()).joinToString("\n").ifBlank { e.message }
        "forbidden" -> "Das darf nur der Besitzer."
        "health_not_enabled" -> "Health ist für dein Konto noch nicht eingeschaltet."
        else -> e.message
    }

    /** In OweMe heißen einige Felder anders als bei Rezepten (Betrag statt Menge, Notiz statt Hinweis). */
    private fun beschriftung(): Map<String, String> = when (screen) {
        is Screen.OweMeUebersicht, is Screen.OweMeKonto, is Screen.OweMeRatenplan -> FeldFehler.OWEME
        else -> emptyMap()
    }

    /** Token ungültig: lokal verwerfen und zur Anmeldung zurück. Darf selbst nicht werfen (läuft im catch). */
    private suspend fun abgelaufen(instance: Instance) {
        store.tokenVerwerfen(instance.id)
        sitzungenLesen()
        val meta = try {
            client(instance).meta()
        } catch (e: ApiException) {
            error = text(e)
            return
        }
        screen = Screen.Login(instance.url, meta, instance.username, instance)
        error = "Die Anmeldung ist abgelaufen. Bitte neu anmelden."
    }

    // ── Instanzen ───────────────────────────────────────────────────────

    fun connect(eingabe: String) = run {
        val url = InstanceUrl.normalisieren(eingabe)
        val meta = ApiClient(url).meta()
        when (val c = Compatibility.pruefen(meta)) {
            Compatibility.Ok -> screen = Screen.Login(url, meta)
            is Compatibility.InstanceTooOld ->
                error = "Diese Instanz ist zu alt für die App (API-Version ${c.serverApi}). Bitte HomeCenter aktualisieren."
            is Compatibility.ClientTooOld ->
                error = "Diese Instanz verlangt eine neuere Version der App."
        }
    }

    fun login(login: Screen.Login, benutzer: String, passwort: String) = run {
        val antwort = ApiClient(login.url).login(benutzer.trim(), passwort, Build.MODEL ?: "Android")
        val instanz = (login.existing ?: Instance(url = login.url, name = login.meta.productName, mode = login.meta.mode,
            username = antwort.user.username, displayName = antwort.user.displayName))
            .copy(name = login.meta.productName, mode = login.meta.mode,
                username = antwort.user.username, displayName = antwort.user.displayName)
        store.speichern(instanz, antwort.token)
        sitzungenLesen()
        screen = Screen.Home(instanz, client(instanz).me())
    }

    fun open(instance: Instance) = run(instance) {
        if (store.token(instance.id) == null) {
            abgelaufen(instance)
            return@run
        }
        val api = client(instance)
        // Name und Betriebsart gehören der Instanz: Stellt ein Admin zwischen Privat und Enterprise um, ändern sich
        // Produktname und Titel. Deshalb bei jedem Öffnen neu lesen und nicht vom Anmeldetag übernehmen.
        val aktuell = try {
            api.meta().let { instance.copy(name = it.productName, mode = it.mode) }
        } catch (e: ApiException) {
            instance // offline o. Ä.: mit den gespeicherten Angaben weitermachen, me() meldet den eigentlichen Fehler
        }
        if (aktuell != instance) {
            store.aktualisieren(aktuell)
            sitzungenLesen()
        }
        screen = Screen.Home(aktuell, api.me())
    }

    /** Abmelden: Token auf dem Server widerrufen und lokal löschen. Die Instanz bleibt für die nächste Anmeldung gespeichert. */
    fun signOut(instance: Instance) = run {
        // Das Token auf dem Server zu widerrufen ist gut, aber nicht zwingend: ohne Verbindung wird es lokal gelöscht.
        runCatching { client(instance).logout() }
        store.tokenVerwerfen(instance.id)
        sitzungenLesen()
    }

    /** Entfernen: wie Abmelden, und die Instanz verschwindet aus der Liste. */
    fun removeInstance(instance: Instance) = run {
        runCatching { client(instance).logout() }
        store.entfernen(instance.id)
        sitzungenLesen()
        screen = if (instances.isEmpty()) Screen.AddInstance else Screen.Instances
    }

    // ── Einkaufsliste ───────────────────────────────────────────────────

    fun loadLists(s: Screen.Lists) = run(s.instance) { lists = client(s.instance).shoppingLists().results }

    fun createList(s: Screen.Lists, titel: String) = run(s.instance) {
        client(s.instance).createList(titel.trim())
        lists = client(s.instance).shoppingLists().results
    }

    fun deleteList(s: Screen.Lists, liste: ShoppingList) = run(s.instance) {
        client(s.instance).deleteList(liste.key)
        lists = client(s.instance).shoppingLists().results
    }

    fun loadItems(s: Screen.Items) = run(s.instance) { items = client(s.instance).items(s.key).results }

    fun addItem(s: Screen.Items, name: String) = run(s.instance) {
        client(s.instance).addItem(s.key, ItemInput(name = name.trim()))
        items = client(s.instance).items(s.key).results
    }

    fun toggle(s: Screen.Items, artikel: ShoppingItem) = run(s.instance) {
        val neu = client(s.instance).toggleItem(artikel.id)
        items = items.map { if (it.id == neu.id) neu else it }
    }

    fun deleteItem(s: Screen.Items, artikel: ShoppingItem) = run(s.instance) {
        client(s.instance).deleteItem(artikel.id)
        items = items.filterNot { it.id == artikel.id }
    }
    // ── Rezepte ─────────────────────────────────────────────────────────

    fun loadRecipes(s: Screen.Recipes) = run(s.instance) {
        val api = client(s.instance)
        recipes = (if (recipeTab == 0) api.recipes(q = recipeQuery) else api.publicRecipes(q = recipeQuery)).results
    }

    fun setRecipeTab(s: Screen.Recipes, tab: Int) {
        recipeTab = tab
        loadRecipes(s)
    }

    fun searchRecipes(s: Screen.Recipes, q: String) {
        recipeQuery = q
        loadRecipes(s)
    }

    fun loadRecipe(s: Screen.Recipe) {
        // Das vorige Rezept nicht kurz weiter zeigen, während das neue lädt.
        if (recipe?.key != s.key) recipe = null
        run(s.instance) { recipe = client(s.instance).recipe(s.key) }
    }

    /** Öffentliches Rezept als eigene Kopie übernehmen und zur Kopie wechseln. */
    fun copyRecipe(s: Screen.Recipe) = run(s.instance) {
        val kopie = client(s.instance).copyRecipe(s.key)
        recipe = kopie
        screen = Screen.Recipe(s.instance, s.me, kopie.key, kopie.title)
    }

    /** Ziellisten für „In die Einkaufsliste“ laden (dieselben wie auf dem Listenbildschirm). */
    fun loadShoppingTargets(s: Screen.RecipeShopping) = run(s.instance) {
        lists = client(s.instance).shoppingLists().results
    }

    fun loadPreview(s: Screen.RecipeShopping, portionen: Int) = run(s.instance) {
        preview = client(s.instance).shoppingPreview(s.key, portionen.toString())
    }

    /**
     * Gewählte Zeilen in eine bestehende ([listKey]) oder neue ([newTitle]) Liste übernehmen und zur Liste wechseln.
     * Liste und Titel gehen nie zusammen an den Server.
     */
    fun transferToList(s: Screen.RecipeShopping, items: List<ItemInput>, listKey: String?, newTitle: String?) = run(s.instance) {
        val titel = newTitle?.trim().orEmpty()
        val ergebnis = client(s.instance).toShoppingList(s.key, listKey, if (listKey == null) titel else null, items)
        val name = lists.firstOrNull { it.key == ergebnis.listKey }?.title ?: titel.ifBlank { "Einkaufsliste" }
        screen = Screen.Items(s.instance, s.me, ergebnis.listKey, name)
    }
    // ── Rezepte bearbeiten ──────────────────────────────────────────────

    /** Öffnet den Editor: lädt das Rezept (falls nötig) und die Kategorien. Ein offener Entwurf bleibt erhalten (Drehen des Geräts). */
    fun startEditor(s: Screen.RecipeEdit) {
        val vorhanden = entwurf
        if (vorhanden != null && vorhanden.key == s.key) return
        run(s.instance) {
            val api = client(s.instance)
            val detail = if (s.key == null) null else (recipe?.takeIf { it.key == s.key } ?: api.recipe(s.key))
            kategorien = api.recipeCategories().results
            entwurf = RezeptEntwurf(s.key, detail) { htmlZuKlartext(it) }
        }
    }

    fun addRecipeCategory(s: Screen.RecipeEdit, name: String) = run(s.instance) {
        val neu = client(s.instance).createRecipeCategory(name.trim())
        kategorien = (kategorien + neu).distinctBy { it.id }
        entwurf?.kategorien?.let { if (neu.id !in it) it.add(neu.id) }
    }

    /**
     * Speichert den Entwurf (neu oder Änderung) und zeigt danach das Rezept. Das Titelbild geht in einem zweiten Schritt
     * hoch: scheitert nur das Bild, ist das Rezept trotzdem gespeichert, und die App sagt das.
     */
    fun saveRecipe(s: Screen.RecipeEdit) = run(s.instance) {
        val e = entwurf ?: return@run
        val patch = e.zuPatch()
        val api = client(s.instance)
        var rezept = if (s.key == null) api.createRecipe(patch) else api.updateRecipe(s.key, patch)

        var warnung: String? = null
        try {
            val bild = e.titelbild
            if (bild != null) {
                val daten = BildHelfer.vorbereiten(getApplication<Application>(), bild)
                rezept = api.uploadRecipeImage(rezept.key, daten.bytes, daten.name, daten.mime, asTitle = true)
            } else if (e.titelbildEntfernen && rezept.imageUrl != null) {
                api.deleteRecipeTitleImage(rezept.key)
                rezept = api.recipe(rezept.key)
            }
        } catch (x: ApiException) {
            warnung = "Das Rezept ist gespeichert, aber das Titelbild nicht: ${text(x)}"
        } catch (x: IOException) {
            warnung = "Das Rezept ist gespeichert, aber das Titelbild nicht: ${x.message}"
        }

        recipe = rezept
        entwurf = null
        screen = Screen.Recipe(s.instance, s.me, rezept.key, rezept.title)
        if (warnung != null) error = warnung
    }

    fun deleteRecipe(s: Screen.Recipe) = run(s.instance) {
        client(s.instance).deleteRecipe(s.key)
        recipe = null
        screen = Screen.Recipes(s.instance, s.me)
    }
    // ── OweMe ───────────────────────────────────────────────────────────

    fun loadOweMe(s: Screen.OweMeUebersicht) = run(s.instance) { oweMe = client(s.instance).oweMe() }

    /** Legt das Konto an und zeigt es gleich. */
    fun createOweMeAccount(s: Screen.OweMeUebersicht, patch: OweMeAccountPatch) = run(s.instance) {
        val neu = client(s.instance).createOweMeAccount(patch)
        konto = neu
        screen = Screen.OweMeKonto(s.instance, s.me, neu.account.key, neu.account.personName)
    }

    fun loadOweMeAccount(s: Screen.OweMeKonto) {
        if (konto?.account?.key != s.key) konto = null
        run(s.instance) { konto = client(s.instance).oweMeAccount(s.key) }
    }

    fun updateOweMeAccount(s: Screen.OweMeKonto, patch: OweMeAccountPatch) = run(s.instance) {
        konto = client(s.instance).updateOweMeAccount(s.key, patch)
    }

    fun deleteOweMeAccount(s: Screen.OweMeKonto) = run(s.instance) {
        client(s.instance).deleteOweMeAccount(s.key)
        konto = null
        screen = Screen.OweMeUebersicht(s.instance, s.me)
    }

    /** Neue Bewegung; danach wird der Kontoauszug neu gelesen, weil sich die Stände aller späteren Zeilen ändern. */
    fun addOweMeEntry(s: Screen.OweMeKonto, kind: String, patch: OweMeEntryPatch) = run(s.instance) {
        val api = client(s.instance)
        api.addOweMeEntry(s.key, kind, patch)
        konto = api.oweMeAccount(s.key)
    }

    fun updateOweMeEntry(s: Screen.OweMeKonto, id: Long, patch: OweMeEntryPatch) = run(s.instance) {
        val api = client(s.instance)
        api.updateOweMeEntry(id, patch)
        konto = api.oweMeAccount(s.key)
    }

    fun deleteOweMeEntry(s: Screen.OweMeKonto, id: Long) = run(s.instance) {
        val api = client(s.instance)
        api.deleteOweMeEntry(id)
        konto = api.oweMeAccount(s.key)
    }

    fun setOweMePlan(s: Screen.OweMeRatenplan, plan: OweMePlanInput) = run(s.instance) {
        konto = client(s.instance).setOweMePlan(s.key, plan)
        screen = Screen.OweMeKonto(s.instance, s.me, s.key, s.title)
    }

    fun deleteOweMePlan(s: Screen.OweMeRatenplan) = run(s.instance) {
        val api = client(s.instance)
        api.deleteOweMePlan(s.key)
        konto = api.oweMeAccount(s.key)
        screen = Screen.OweMeKonto(s.instance, s.me, s.key, s.title)
    }
    // ── Health ──────────────────────────────────────────────────────────

    private fun heute(): String = java.time.LocalDate.now().toString()

    /** Profil laden; ist Health eingeschaltet, dazu der heutige Tag für die Übersicht. */
    fun loadHealth(s: Screen.Health) = run(s.instance) {
        val api = client(s.instance)
        val profil = api.healthProfile()
        healthProfil = profil
        healthHeute = if (profil.enabled) api.calorieDay(heute()) else null
    }

    /** Stammdaten speichern, erstes Gewicht erfassen und Health einschalten. */
    fun setupHealth(s: Screen.Health, patch: HealthProfilePatch, gewichtKg: String) = run(s.instance) {
        val api = client(s.instance)
        api.updateHealthProfile(patch)
        api.addWeight(gewichtKg)
        val profil = api.setHealthEnabled(true)
        healthProfil = profil
        healthHeute = if (profil.enabled) api.calorieDay(heute()) else null
    }

    fun loadDay(s: Screen.HealthDay) {
        if (kalorienTag?.date != s.date) kalorienTag = null
        run(s.instance) { kalorienTag = client(s.instance).calorieDay(s.date) }
    }

    fun loadMonth(s: Screen.HealthMonth) = run(s.instance) { kalorienMonat = client(s.instance).calorieMonth(s.month) }

    fun addCalorieEntry(s: Screen.HealthDay, patch: CalorieEntryPatch) = run(s.instance) {
        val api = client(s.instance)
        api.addCalorieEntry(s.date, patch)
        kalorienTag = api.calorieDay(s.date)
    }

    fun updateCalorieEntry(s: Screen.HealthDay, id: Long, patch: CalorieEntryPatch) = run(s.instance) {
        val api = client(s.instance)
        api.updateCalorieEntry(id, patch)
        kalorienTag = api.calorieDay(s.date)
    }

    fun deleteCalorieEntry(s: Screen.HealthDay, id: Long) = run(s.instance) {
        val api = client(s.instance)
        api.deleteCalorieEntry(id)
        kalorienTag = api.calorieDay(s.date)
    }

    fun applyMealTemplate(s: Screen.HealthDay, id: Long) = run(s.instance) {
        val api = client(s.instance)
        api.applyMealTemplate(id, s.date)
        kalorienTag = api.calorieDay(s.date)
    }

    fun loadTemplates(instance: Instance) = run(instance) { vorlagen = client(instance).mealTemplates().results }

    fun createMealTemplate(s: Screen.HealthTemplates, patch: MealTemplatePatch) = run(s.instance) {
        val api = client(s.instance)
        api.createMealTemplate(patch)
        vorlagen = api.mealTemplates().results
    }

    fun updateMealTemplate(s: Screen.HealthTemplates, id: Long, patch: MealTemplatePatch) = run(s.instance) {
        val api = client(s.instance)
        api.updateMealTemplate(id, patch)
        vorlagen = api.mealTemplates().results
    }

    fun deleteMealTemplate(s: Screen.HealthTemplates, id: Long) = run(s.instance) {
        val api = client(s.instance)
        api.deleteMealTemplate(id)
        vorlagen = api.mealTemplates().results
    }

    fun loadWeights(s: Screen.HealthWeight) = run(s.instance) {
        val api = client(s.instance)
        gewichte = api.weights().results
        healthProfil = api.healthProfile()
    }

    fun addWeight(s: Screen.HealthWeight, kg: String, date: String?) = run(s.instance) {
        val api = client(s.instance)
        api.addWeight(kg, date)
        gewichte = api.weights().results
        healthProfil = api.healthProfile()
    }

    fun deleteWeight(s: Screen.HealthWeight, id: Long) = run(s.instance) {
        val api = client(s.instance)
        api.deleteWeight(id)
        gewichte = api.weights().results
        healthProfil = api.healthProfile()
    }

    /** Zielgewicht und -datum ändern (`null` entfernt das Ziel). */
    fun updateHealthGoal(s: Screen.HealthWeight, patch: HealthProfilePatch) = run(s.instance) {
        val api = client(s.instance)
        healthProfil = api.updateHealthProfile(patch)
    }

    /**
     * Vorschläge zur Eingabe im Titelfeld. Kein Fortschrittsbalken, keine Fehlermeldung: wie im Web heißt jede Störung
     * (auch ein Server ohne diese Suche) einfach „keine Vorschläge“. Die jüngste Anfrage gewinnt.
     */
    fun searchFoods(s: Screen.HealthDay, text: String) {
        foodJob?.cancel()
        if (text.trim().length < 2) {
            foods = emptyList()
            return
        }
        foodJob = viewModelScope.launch {
            foods = try {
                client(s.instance).searchFoods(text)
            } catch (e: ApiException) {
                emptyList()
            }
        }
    }

    fun clearFoods() {
        foodJob?.cancel()
        foods = emptyList()
    }
}
