package de.sryoshi.hcapp

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.sryoshi.hcapp.core.ApiClient
import de.sryoshi.hcapp.core.ApiException
import de.sryoshi.hcapp.core.Compatibility
import de.sryoshi.hcapp.core.InstanceUrl
import de.sryoshi.hcapp.core.InvalidInstanceUrl
import de.sryoshi.hcapp.core.ItemInput
import de.sryoshi.hcapp.core.Me
import de.sryoshi.hcapp.core.Meta
import de.sryoshi.hcapp.core.RecipeDetail
import de.sryoshi.hcapp.core.RecipeSummary
import de.sryoshi.hcapp.core.ShoppingItem
import de.sryoshi.hcapp.core.ShoppingList
import de.sryoshi.hcapp.core.ShoppingPreview
import de.sryoshi.hcapp.data.Instance
import de.sryoshi.hcapp.data.InstanceStore
import kotlinx.coroutines.CancellationException
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
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = InstanceStore(app)

    var instances by mutableStateOf(store.alle())
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
                Screen.Instances -> Screen.Instances
            },
        )
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
        else -> e.message
    }

    /** Token ungültig: lokal verwerfen und zur Anmeldung zurück. Darf selbst nicht werfen (läuft im catch). */
    private suspend fun abgelaufen(instance: Instance) {
        store.tokenVerwerfen(instance.id)
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
        instances = store.alle()
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
            instances = store.alle()
        }
        screen = Screen.Home(aktuell, api.me())
    }

    fun signOut(instance: Instance) = run {
        // Das Token auf dem Server zu widerrufen ist gut, aber nicht zwingend: ohne Verbindung wird es lokal gelöscht.
        runCatching { client(instance).logout() }
        store.entfernen(instance.id)
        instances = store.alle()
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
}
