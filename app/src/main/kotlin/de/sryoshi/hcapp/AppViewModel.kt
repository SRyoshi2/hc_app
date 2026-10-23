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
import de.sryoshi.hcapp.core.AppNotification
import de.sryoshi.hcapp.core.RecipeOffer
import de.sryoshi.hcapp.core.ShareUser
import de.sryoshi.hcapp.core.DocumentCategory
import de.sryoshi.hcapp.core.HealthCheck
import de.sryoshi.hcapp.core.HealthCheckPatch
import de.sryoshi.hcapp.core.HealthDocument
import de.sryoshi.hcapp.core.LabAnalysis
import de.sryoshi.hcapp.core.LabOverview
import de.sryoshi.hcapp.core.LabPatch
import de.sryoshi.hcapp.core.LabTrend
import de.sryoshi.hcapp.core.TrafficLight
import de.sryoshi.hcapp.core.TrainingGoals
import de.sryoshi.hcapp.core.TrainingGoalsPatch
import de.sryoshi.hcapp.core.TrainingPatch
import de.sryoshi.hcapp.core.TrainingReview
import de.sryoshi.hcapp.core.TrainingTemplate
import de.sryoshi.hcapp.core.TrainingTemplatePatch
import de.sryoshi.hcapp.core.Vaccination
import de.sryoshi.hcapp.core.VaccinationPatch
import de.sryoshi.hcapp.core.IngredientInput
import de.sryoshi.hcapp.core.RecipeUnits
import de.sryoshi.hcapp.data.DateiHelfer
import de.sryoshi.hcapp.ui.SchrittEntwurf
import de.sryoshi.hcapp.ui.ZutatEntwurf
import de.sryoshi.hcapp.ui.zahlKurz
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
    data class HealthChecks(val instance: Instance, val me: Me) : Screen
    data class HealthCheckDetail(val instance: Instance, val me: Me, val id: Long, val title: String) : Screen
    data class HealthVaccinations(val instance: Instance, val me: Me) : Screen
    data class HealthLabs(val instance: Instance, val me: Me) : Screen
    data class HealthLabTrend(val instance: Instance, val me: Me, val name: String) : Screen
    /** [week] ist ein beliebiger Tag der Woche (`JJJJ-MM-TT`). */
    data class HealthTraining(val instance: Instance, val me: Me, val week: String) : Screen
    data class HealthTrainingTemplates(val instance: Instance, val me: Me) : Screen
    data class HealthTrainingGoals(val instance: Instance, val me: Me) : Screen
    data class RecipeOffers(val instance: Instance, val me: Me) : Screen
    data class Notifications(val instance: Instance, val me: Me) : Screen
    /** [source] ist `tracker` oder `plan`. */
    data class HealthTraffic(val instance: Instance, val me: Me, val week: String, val source: String) : Screen
}

/** Wem wird was geteilt: [art] ist `list`, `oweme` oder `recipe` (Anbieten); [bekannt] sind die bisherigen Empfänger. */
class TeilenZustand(
    val instance: Instance, val art: String, val key: String, val titel: String, val bekannt: List<ShareUser>,
)

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
    /** Kurze Rückmeldung nach einer Aktion (wird als Hinweis eingeblendet und danach verworfen). */
    var info by mutableStateOf<String?>(null)
        private set
    fun clearInfo() { info = null }
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

    var checks by mutableStateOf<List<HealthCheck>>(emptyList())
        private set
    var check by mutableStateOf<HealthCheck?>(null)
        private set
    var docKategorien by mutableStateOf<List<DocumentCategory>>(emptyList())
        private set
    var impfungen by mutableStateOf<List<Vaccination>>(emptyList())
        private set
    var labUebersicht by mutableStateOf<LabOverview?>(null)
        private set
    var labVerlauf by mutableStateOf<LabTrend?>(null)
        private set
    var labAnalyse by mutableStateOf<LabAnalysis?>(null)
        private set
    var trainingWoche by mutableStateOf<TrainingReview?>(null)
        private set
    var trainingVorlagen by mutableStateOf<List<TrainingTemplate>>(emptyList())
        private set
    var trainingZiele by mutableStateOf<TrainingGoals?>(null)
        private set
    var ampel by mutableStateOf<TrafficLight?>(null)
        private set

    /** Zustand des Teilen-Dialogs; `null`, wenn keiner offen ist. */
    var teilen by mutableStateOf<TeilenZustand?>(null)
        private set
    var teilBenutzer by mutableStateOf<List<ShareUser>>(emptyList())
        private set
    var angebote by mutableStateOf<List<RecipeOffer>>(emptyList())
        private set
    var meldungen by mutableStateOf<List<AppNotification>>(emptyList())
        private set
    var ungelesen by mutableStateOf(0)
        private set

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
                is Screen.HealthChecks -> Screen.Health(s.instance, s.me)
                is Screen.HealthVaccinations -> Screen.Health(s.instance, s.me)
                is Screen.HealthLabs -> Screen.Health(s.instance, s.me)
                is Screen.HealthTraining -> Screen.Health(s.instance, s.me)
                is Screen.HealthTraffic -> Screen.Health(s.instance, s.me)
                is Screen.HealthCheckDetail -> Screen.HealthChecks(s.instance, s.me)
                is Screen.HealthLabTrend -> Screen.HealthLabs(s.instance, s.me)
                is Screen.HealthTrainingTemplates -> Screen.HealthTraining(s.instance, s.me, heute())
                is Screen.HealthTrainingGoals -> Screen.HealthTraining(s.instance, s.me, heute())
                is Screen.RecipeOffers -> Screen.Recipes(s.instance, s.me)
                is Screen.Notifications -> Screen.Home(s.instance, s.me)
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
        "training_disabled" -> "Das Training ist in deinem Health-Profil ausgeschaltet."
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

    // ── Health: Arztbesuche, Impfbuch, Labor (Phase 6) ──────────────────

    fun loadChecks(s: Screen.HealthChecks) = run(s.instance) { checks = client(s.instance).healthChecks().results }

    fun saveCheck(s: Screen.HealthChecks, id: Long?, patch: HealthCheckPatch) = run(s.instance) {
        val api = client(s.instance)
        if (id == null) api.createHealthCheck(patch) else api.updateHealthCheck(id, patch)
        checks = api.healthChecks().results
    }

    fun loadCheck(s: Screen.HealthCheckDetail) {
        if (check?.id != s.id) check = null
        run(s.instance) {
            val api = client(s.instance)
            check = api.healthCheck(s.id)
            docKategorien = api.documentCategories().results
        }
    }

    fun updateCheck(s: Screen.HealthCheckDetail, patch: HealthCheckPatch) = run(s.instance) {
        check = client(s.instance).updateHealthCheck(s.id, patch)
    }

    fun deleteCheck(s: Screen.HealthCheckDetail) = run(s.instance) {
        client(s.instance).deleteHealthCheck(s.id)
        check = null
        screen = Screen.HealthChecks(s.instance, s.me)
    }

    fun addComment(s: Screen.HealthCheckDetail, text: String) = run(s.instance) {
        val api = client(s.instance)
        api.addHealthComment(s.id, text)
        check = api.healthCheck(s.id)
    }

    fun deleteComment(s: Screen.HealthCheckDetail, id: Long) = run(s.instance) {
        val api = client(s.instance)
        api.deleteHealthComment(id)
        check = api.healthCheck(s.id)
    }

    fun uploadDocument(s: Screen.HealthCheckDetail, uri: android.net.Uri, titel: String, kategorie: Long?) = run(s.instance) {
        val datei = DateiHelfer.lesen(getApplication<Application>(), uri)
        val api = client(s.instance)
        api.uploadHealthDocument(s.id, datei.bytes, datei.name, datei.mime, titel.ifBlank { datei.name }, kategorie)
        check = api.healthCheck(s.id)
    }

    /** Lädt das Dokument vom Server und schreibt es in die vom Nutzer gewählte Datei [ziel]. */
    fun saveDocument(s: Screen.HealthCheckDetail, dokument: HealthDocument, ziel: android.net.Uri) = run(s.instance) {
        val bytes = client(s.instance).downloadHealthDocument(dokument.key)
        DateiHelfer.schreiben(getApplication<Application>(), ziel, bytes)
        info = "Gespeichert: ${dokument.filename.ifBlank { dokument.title }}"
    }

    fun deleteDocument(s: Screen.HealthCheckDetail, id: Long) = run(s.instance) {
        val api = client(s.instance)
        api.deleteHealthDocument(id)
        check = api.healthCheck(s.id)
    }

    fun createDocCategory(s: Screen.HealthCheckDetail, name: String) = run(s.instance) {
        val api = client(s.instance)
        api.createDocumentCategory(name)
        docKategorien = api.documentCategories().results
    }

    fun addCheckLab(s: Screen.HealthCheckDetail, patch: LabPatch) = run(s.instance) {
        val api = client(s.instance)
        api.addLabToCheck(s.id, patch)
        check = api.healthCheck(s.id)
    }

    fun updateCheckLab(s: Screen.HealthCheckDetail, id: Long, patch: LabPatch) = run(s.instance) {
        val api = client(s.instance)
        api.updateLab(id, patch)
        check = api.healthCheck(s.id)
    }

    fun deleteCheckLab(s: Screen.HealthCheckDetail, id: Long) = run(s.instance) {
        val api = client(s.instance)
        api.deleteLab(id)
        check = api.healthCheck(s.id)
    }

    fun loadVaccinations(s: Screen.HealthVaccinations) = run(s.instance) { impfungen = client(s.instance).vaccinations().results }

    fun saveVaccination(s: Screen.HealthVaccinations, id: Long?, patch: VaccinationPatch) = run(s.instance) {
        val api = client(s.instance)
        if (id == null) api.createVaccination(patch) else api.updateVaccination(id, patch)
        impfungen = api.vaccinations().results
    }

    fun deleteVaccination(s: Screen.HealthVaccinations, id: Long) = run(s.instance) {
        val api = client(s.instance)
        api.deleteVaccination(id)
        impfungen = api.vaccinations().results
    }

    fun loadLabs(s: Screen.HealthLabs) = run(s.instance) {
        val api = client(s.instance)
        labUebersicht = api.labs()
        labAnalyse = api.labAnalysis()
    }

    fun addLab(s: Screen.HealthLabs, patch: LabPatch) = run(s.instance) {
        val api = client(s.instance)
        api.addLab(patch)
        labUebersicht = api.labs()
    }

    fun loadLabTrend(s: Screen.HealthLabTrend) {
        if (labVerlauf?.name != s.name) labVerlauf = null
        run(s.instance) { labVerlauf = client(s.instance).labTrend(s.name) }
    }

    fun updateLab(s: Screen.HealthLabTrend, id: Long, patch: LabPatch) = run(s.instance) {
        val api = client(s.instance)
        api.updateLab(id, patch)
        labVerlauf = api.labTrend(s.name)
    }

    /** Löscht eine Messung; war es die letzte dieser Bezeichnung, geht es zurück zur Übersicht. */
    fun deleteLab(s: Screen.HealthLabTrend, id: Long) = run(s.instance) {
        val api = client(s.instance)
        api.deleteLab(id)
        try {
            labVerlauf = api.labTrend(s.name)
        } catch (e: ApiException) {
            if (e.httpStatus != 404) throw e
            labVerlauf = null
            screen = Screen.HealthLabs(s.instance, s.me)
        }
    }

    // ── Health: Training und Ernährungsampel (Phase 6) ──────────────────

    /** Training im Profil an- oder ausschalten. */
    fun setTrainingEnabled(s: Screen.Health, an: Boolean) = run(s.instance) {
        healthProfil = client(s.instance).updateHealthProfile(HealthProfilePatch().trainingEnabled(an))
    }

    fun loadTrainingWeek(s: Screen.HealthTraining) {
        if (trainingWoche?.let { s.week in it.weekStart..it.weekEnd } != true) trainingWoche = null
        run(s.instance) {
            val api = client(s.instance)
            trainingWoche = api.trainingReview(week = s.week, month = s.week.take(7))
            trainingVorlagen = api.trainingTemplates().results
        }
    }

    private suspend fun trainingNeu(s: Screen.HealthTraining) {
        trainingWoche = client(s.instance).trainingReview(week = s.week, month = s.week.take(7))
    }

    fun addTraining(s: Screen.HealthTraining, patch: TrainingPatch) = run(s.instance) {
        client(s.instance).addTraining(patch)
        trainingNeu(s)
    }

    fun updateTraining(s: Screen.HealthTraining, id: Long, patch: TrainingPatch) = run(s.instance) {
        client(s.instance).updateTraining(id, patch)
        trainingNeu(s)
    }

    fun deleteTraining(s: Screen.HealthTraining, id: Long) = run(s.instance) {
        client(s.instance).deleteTraining(id)
        trainingNeu(s)
    }

    fun applyTrainingTemplate(s: Screen.HealthTraining, id: Long, date: String) = run(s.instance) {
        client(s.instance).applyTrainingTemplate(id, date)
        trainingNeu(s)
    }

    fun loadTrainingTemplates(s: Screen.HealthTrainingTemplates) = run(s.instance) {
        trainingVorlagen = client(s.instance).trainingTemplates().results
    }

    fun saveTrainingTemplate(s: Screen.HealthTrainingTemplates, id: Long?, patch: TrainingTemplatePatch) = run(s.instance) {
        val api = client(s.instance)
        if (id == null) api.createTrainingTemplate(patch) else api.updateTrainingTemplate(id, patch)
        trainingVorlagen = api.trainingTemplates().results
    }

    fun deleteTrainingTemplate(s: Screen.HealthTrainingTemplates, id: Long) = run(s.instance) {
        val api = client(s.instance)
        api.deleteTrainingTemplate(id)
        trainingVorlagen = api.trainingTemplates().results
    }

    fun loadTrainingGoals(s: Screen.HealthTrainingGoals) = run(s.instance) { trainingZiele = client(s.instance).trainingGoals() }

    fun saveTrainingGoals(s: Screen.HealthTrainingGoals, patch: TrainingGoalsPatch) = run(s.instance) {
        trainingZiele = client(s.instance).setTrainingGoals(patch)
        info = "Ziele gespeichert."
    }

    fun loadTraffic(s: Screen.HealthTraffic) {
        if (ampel?.let { it.source != s.source || s.week !in it.weekStart..it.weekEnd } != false) ampel = null
        run(s.instance) { ampel = client(s.instance).trafficLight(s.week, s.source) }
    }

    // ── Teilen, Export, Meldungen (Phase 7) ─────────────────────────────

    /** Öffnet den Teilen-Dialog; bei Liste und Konto mit der bisherigen Freigabe, bei Rezepten (Anbieten) ohne. */
    fun teilenOeffnen(instance: Instance, art: String, key: String, titel: String) = run(instance) {
        val api = client(instance)
        val bekannt = when (art) {
            "list" -> api.listSharing(key).users
            "oweme" -> api.oweMeSharing(key).users
            else -> emptyList()
        }
        teilBenutzer = api.shareUsers().results
        teilen = TeilenZustand(instance, art, key, titel, bekannt)
    }

    fun benutzerSuchen(q: String) {
        val z = teilen ?: return
        run(z.instance) { teilBenutzer = client(z.instance).shareUsers(q).results }
    }

    fun teilenSchliessen() { teilen = null; teilBenutzer = emptyList() }

    fun teilenSpeichern(ids: List<Long>) {
        val z = teilen ?: return
        teilenSchliessen()
        run(z.instance) {
            val api = client(z.instance)
            when (z.art) {
                "list" -> { api.setListSharing(z.key, ids); lists = api.shoppingLists().results; info = "Freigabe gespeichert." }
                "oweme" -> { api.setOweMeSharing(z.key, ids); info = "Freigabe gespeichert." }
                else -> {
                    val r = api.shareRecipe(z.key, ids)
                    info = when {
                        r.offered == 0 && r.alreadyOffered > 0 -> "Schon angeboten."
                        r.alreadyOffered > 0 -> "${r.offered} angeboten, ${r.alreadyOffered} hatten es schon."
                        else -> "${r.offered} angeboten."
                    }
                }
            }
        }
    }

    /** Kontoauszug als CSV vom Server holen und in die gewählte Datei schreiben. */
    fun exportOweMe(s: Screen.OweMeKonto, ziel: android.net.Uri) = run(s.instance) {
        val bytes = client(s.instance).exportOweMe(s.key)
        DateiHelfer.schreiben(getApplication<Application>(), ziel, bytes)
        info = "Kontoauszug gespeichert."
    }

    fun loadOffers(s: Screen.RecipeOffers) = run(s.instance) { angebote = client(s.instance).recipeOffers().results }

    fun acceptOffer(s: Screen.RecipeOffers, angebot: RecipeOffer) = run(s.instance) {
        val kopie = client(s.instance).acceptRecipeOffer(angebot.id)
        angebote = angebote.filter { it.id != angebot.id }
        recipe = kopie
        info = "„${kopie.title}“ ist jetzt in deinen Rezepten."
        screen = Screen.Recipe(s.instance, s.me, kopie.key, kopie.title)
    }

    fun declineOffer(s: Screen.RecipeOffers, angebot: RecipeOffer) = run(s.instance) {
        client(s.instance).declineRecipeOffer(angebot.id)
        angebote = angebote.filter { it.id != angebot.id }
    }

    /** Zähler für die Glocke; Störungen bleiben still (kein Grund für eine Fehlermeldung auf der Startseite). */
    fun loadUnread(instance: Instance) {
        viewModelScope.launch {
            ungelesen = try { client(instance).unreadCount() } catch (e: ApiException) { 0 }
        }
    }

    fun loadNotifications(s: Screen.Notifications) = run(s.instance) {
        val seite = client(s.instance).notifications()
        meldungen = seite.results
        ungelesen = seite.unreadCount
    }

    /** Markiert als gelesen und öffnet das Ziel, soweit die App es kennt. */
    fun openNotification(s: Screen.Notifications, m: AppNotification) = run(s.instance) {
        if (!m.read) {
            client(s.instance).markNotificationRead(m.id)
            meldungen = meldungen.map { if (it.id == m.id) it.copy(read = true) else it }
            ungelesen = (ungelesen - 1).coerceAtLeast(0)
        }
        val t = m.target ?: return@run
        screen = when (t.kind) {
            "shopping_list" -> Screen.Items(s.instance, s.me, t.key, "Einkaufsliste")
            "oweme_account" -> Screen.OweMeKonto(s.instance, s.me, t.key, "Konto")
            "recipe" -> Screen.Recipe(s.instance, s.me, t.key, "Rezept")
            else -> return@run
        }
    }

    fun markAllRead(s: Screen.Notifications) = run(s.instance) {
        client(s.instance).markAllNotificationsRead()
        meldungen = meldungen.map { it.copy(read = true) }
        ungelesen = 0
    }

    fun deleteNotification(s: Screen.Notifications, m: AppNotification) = run(s.instance) {
        client(s.instance).deleteNotification(m.id)
        meldungen = meldungen.filter { it.id != m.id }
        if (!m.read) ungelesen = (ungelesen - 1).coerceAtLeast(0)
    }

    // ── KI-Funktionen (Phase 7c) ────────────────────────────────────────
    // Jeder Aufruf kostet beim Anbieter: Die Bildschirme zeigen vorher den Text, der hinausgeht, und fragen nach.

    fun requestLabAnalysis(s: Screen.HealthLabs, checksum: String) = run(s.instance) {
        val api = client(s.instance)
        labAnalyse = api.requestLabAnalysis(checksum)
        labUebersicht = api.labs()
        info = "KI-Einordnung abgelegt."
    }

    fun requestTrainingReview(s: Screen.HealthTraining, woche: String) = run(s.instance) {
        val api = client(s.instance)
        api.requestTrainingReview(woche)
        trainingNeu(s)
    }

    fun requestTrafficAdvice(s: Screen.HealthTraffic) = run(s.instance) {
        val api = client(s.instance)
        api.requestTrafficAdvice(s.week, s.source)
        ampel = api.trafficLight(s.week, s.source)
    }

    private fun schaetzInfo(gefuellt: Int, uebersprungen: Int) {
        info = when {
            gefuellt == 0 -> "Die KI konnte das nicht zuordnen – die Nährwerte bleiben leer."
            uebersprungen > 0 -> "$gefuellt geschätzt, $uebersprungen nicht zuzuordnen."
            else -> "Nährwerte geschätzt."
        }
    }

    fun estimateEntry(s: Screen.HealthDay, id: Long) = run(s.instance) {
        val api = client(s.instance)
        val r = api.estimateEntryNutrients(id)
        kalorienTag = api.calorieDay(s.date)
        schaetzInfo(r.filled, r.skipped)
    }

    fun estimateDay(s: Screen.HealthDay) = run(s.instance) {
        val api = client(s.instance)
        val r = api.estimateDayNutrients(s.date)
        kalorienTag = api.calorieDay(s.date)
        schaetzInfo(r.filled, r.skipped)
    }

    fun discardEstimate(s: Screen.HealthDay, id: Long) = run(s.instance) {
        val api = client(s.instance)
        api.discardEntryEstimate(id)
        kalorienTag = api.calorieDay(s.date)
    }

    /** Füllt den Rezept-Entwurf mit einem KI-Vorschlag (ersetzt Titel, Beschreibung, Zutaten und Schritte). Gespeichert wird erst per „Speichern“. */
    fun generateRecipe(s: Screen.RecipeEdit, zutaten: String, portionen: Int, wunsch: String) = run(s.instance) {
        val r = client(s.instance).generateRecipe(zutaten.trim(), portionen, wunsch.trim())
        val e = entwurf ?: return@run
        if (r.title.isNotBlank()) e.titel = r.title
        e.beschreibung = r.description
        r.servings?.let { zahlKurz(it).takeIf { p -> p.isNotBlank() } }?.let { e.portionen = it }
        val einheiten = RecipeUnits.alle.map { it.first }.toSet()
        e.zutaten.clear()
        r.ingredients.filter { it.name.isNotBlank() }.forEach {
            e.zutaten.add(ZutatEntwurf(null, it.name.trim(), zahlKurz(it.amount), it.unit.trim().lowercase().takeIf { u -> u in einheiten }.orEmpty()))
        }
        if (e.zutaten.isEmpty()) e.zutaten.add(ZutatEntwurf(null))
        e.schritte.clear()
        r.steps.filter { it.text.isNotBlank() }.forEach { e.schritte.add(SchrittEntwurf(it.heading.trim(), it.text.trim())) }
        if (e.schritte.isEmpty()) e.schritte.add(SchrittEntwurf())
        info = "Vorschlag übernommen – bitte prüfen und anpassen."
    }

    /** Die Zutaten, die eine Nährwertschätzung des Entwurfs an die KI schickt. */
    fun schaetzZutaten(): List<IngredientInput> =
        entwurf?.zutaten.orEmpty().filter { it.name.isNotBlank() }.map {
            IngredientInput(name = it.name.trim(), amount = it.menge.trim().replace(',', '.').ifBlank { null }, unit = it.einheit)
        }

    fun estimateRecipeNutrition(s: Screen.RecipeEdit) = run(s.instance) {
        val e = entwurf ?: return@run
        val portionen = (e.portionen.trim().toIntOrNull() ?: 1).coerceIn(1, 99)
        val n = client(s.instance).estimateRecipeNutrition(e.titel.trim(), schaetzZutaten(), portionen)
        n.kcal?.let { e.kcal = zahlKurz(it) }
        n.proteinG?.let { e.eiweiss = zahlKurz(it) }
        n.fatG?.let { e.fett = zahlKurz(it) }
        n.carbsG?.let { e.kohlenhydrate = zahlKurz(it) }
        info = "Nährwerte geschätzt – bitte prüfen."
    }

    fun clearFoods() {
        foodJob?.cancel()
        foods = emptyList()
    }
}
