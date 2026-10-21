# API-Konzept: Mobile Schnittstelle für HomeCenter

Stand: 2026-10-21 · Status: abgestimmt (siehe Abschnitt 10)

Grundlage ist eine Codeanalyse von `SRyoshi2/HomeCenter` (Django 5.0.7, DRF installiert).
Alles hier Beschriebene betrifft **HomeCenter** (neue App `mobile_api`), nicht diesen
Android-Client. Der Client in `hc_app` baut darauf auf.

## 1. Ziel und Umfang

Eine Android-App, die sich mit **einer beliebigen bestehenden HomeCenter-Instanz** verbindet
(Internet oder lokales Netz, mehrere Instanzen wählbar) und folgende Bereiche bietet:

1. Health (komplett)
2. Einkaufsliste
3. Rezeptverwaltung, mit Übernahme von Zutaten in die Einkaufsliste
4. OweMe

Anmeldung mit Benutzername und Passwort. Die Instanz läuft als „Family Manager" oder
„Office Manager" (`SysAdmin.use == 'enterprise'`, siehe `home/produkt.py`); die App passt
Name und Funktionen daran an.

**Nicht im Umfang (erste Ausbaustufe):** Push-Benachrichtigungen (HomeCenter hat keinen
Scheduler), KI-Funktionen (`abo_ai`), Rezept-Import (Tandoor), Teilen von Rezepten mit
Nutzern, Speiseplan und Einkaufsplan, Veröffentlichen im Social-Feed.

## 2. Ausgangslage im Code

| Bereich | Stand heute | Konsequenz |
|---|---|---|
| Anmeldung | Django-Session, `AnmeldeForm`, CSRF | Token-Login nötig |
| DRF | in `INSTALLED_APPS`, nur von `social/api_views.py` genutzt, kein Token-Mechanismus | eigene Token-Verwaltung |
| Einkaufsliste | JSON unter `api/einkaufsliste/...` (Artikel), Listen nur per HTML | Listenverwaltung ergänzen |
| Rezepte | nur HTML | komplett neu |
| Rezept → Liste | `rezept_to_shopping` nimmt `items_json`, Mengenskalierung im Browser (`rezept_detail.html`) | Skalierung in Server-Service |
| OweMe | nur HTML, Saldo und Fälligkeit in den Models | Models wiederverwenden |
| Health | nur HTML, Logik in `health/services/*`, Validierung in `health/forms.py` | Services und Forms wiederverwenden |
| App-Sperre | `DisabledAppMiddleware`, Regex `^/(?:apps\|api)(?:/widget)?/([a-z_]+)/` | greift **nicht** bei `/api/v1/...` (Ziffer im Segment) |

## 3. Architektur

- Neue Django-App `mobile_api`, eingehängt unter `/api/v1/`.
- Die Views rufen vorhandene Models und Services auf. Fachlogik wird **nicht** kopiert; wo sie
  heute in Views oder Templates steckt (Rezeptskalierung, Tagesauswertung), wird sie in einen
  Service verschoben, den Web-Views und API gemeinsam nutzen.
- Serializer: DRF. Eingabevalidierung nach Möglichkeit über die bestehenden `ModelForm`s,
  damit Web und API dieselben Regeln haben.
- Datenisolation: jede Abfrage ist auf `request.user` bzw. Besitzer/Freigabe gefiltert,
  genau wie in den Web-Views (`user_can_access`, `user_can_view`, `user_can_edit`).
- Fehlerformat einheitlich: `{"error": {"code": "...", "message": "...", "fields": {...}}}`.
- Zahlen: Dezimalwerte als Strings (z. B. `"2.50"`), Datum ISO 8601, Zeitstempel UTC.

### 3.1 App-Sperre und Rechte (Pflicht)

Die Middleware erkennt `/api/v1/...` nicht. Zwei Wege, entweder
(a) Regex auf `[a-z0-9_]+` ändern und Schlüssel `v1` überspringen, oder
(b) eine eigene Berechtigungsklasse `AppEnabled('health')` in `mobile_api`, die
`home.middleware.app_is_hidden(...)` abfragt. `app_is_hidden` erwartet einen `url_name`; die Klasse löst den Schlüssel deshalb über `APP_PATH_KEYS` auf (`health`→`health_dashboard`, `rezepte`→`rezept_overview`, `einkaufsliste`→`einkaufsliste_overview`, `oweme`→`oweme_overview`). **Empfehlung: (b)**, ohne Eingriff in die Middleware
und je Endpunkt explizit.

Zusätzlich je Endpunkt:
- `abo_1` verlangt (wie `oweme_overview`, `_guard` in Health).
- Health zusätzlich `HealthProfile.enabled`; sonst `403` mit Code `health_not_enabled`.
- Es gelten dieselben Schlüssel wie in `APP_PATH_KEYS`: `health`, `einkaufsliste`, `rezepte`, `oweme`.

## 4. Anmeldung und Token

Neues Model `ApiToken` in `mobile_api` (kein DRF-`authtoken`, weil Gerätename, Ablauf und
Widerruf gebraucht werden):

| Feld | Zweck |
|---|---|
| `user` | FK auf `CustomUser` |
| `key_hash` | SHA-256 des Tokens; Klartext wird nur einmal ausgeliefert |
| `device_name` | z. B. „Pixel 8" |
| `created_at`, `last_used_at`, `expires_at` | Ablauf (Vorschlag: 90 Tage Gleitfrist) |
| `revoked_at` | Widerruf |

Endpunkte:

| Methode | Pfad | Zweck |
|---|---|---|
| POST | `/api/v1/auth/login/` | `{username, password, device_name}` → `{token, expires_at, user}` |
| POST | `/api/v1/auth/logout/` | widerruft das aktuelle Token |
| GET | `/api/v1/auth/devices/` | eigene Geräte/Tokens |
| DELETE | `/api/v1/auth/devices/<id>/` | Gerät abmelden |

Regeln:
- Prüfung über `django.contrib.auth.authenticate`, danach `is_active`. Wartende Konten
  (`freigabe_ausstehend`) bekommen Code `pending_approval` wie im Web-Login.
- Drosselung pro IP und Benutzername (z. B. 5 Fehlversuche je 10 Minuten, Cache-basiert wie
  die Registrierung).
- Header: `Authorization: Bearer <token>`. Die API-Views setzen **keine** Session- und
  keine CSRF-Authentifizierung voraus. Die Bearer-Klasse hängt nur an den `mobile_api`-Views, `social/api_views.py` bleibt bei der Session.
- Passwortänderung widerruft alle Tokens des Kontos (Signal auf dem Benutzermodell, greift also auch bei Passwort-Reset und Änderung im Admin).
- Passwort-Reset und Registrierung bleiben im Web (Link in der App).

## 5. Meta und Versionierung

`GET /api/v1/meta/` (ohne Anmeldung, ohne personenbezogene Daten):

```json
{
  "product_name": "Family Manager",
  "mode": "family",
  "api_version": 1,
  "min_client_version": 1,
  "server_version": "0.93.1058"
}
```

`server_version` hat im HomeCenter heute keine Quelle im Python-Code (die Nummer steht nur in `base.html`). Phase 0 führt dafür `SERVER_VERSION` in `home/produkt.py` ein; ein Test hält sie mit `base.html` gleich. `mode` ist `family` oder `office`.

`GET /api/v1/me/` (angemeldet): Benutzer, Anzeigename und Freischaltungen.

```json
{
  "id": 7, "username": "sven", "display_name": "Sven",
  "abo_1": true,
  "apps": {"health": true, "einkaufsliste": true, "rezepte": true, "oweme": false},
  "health_enabled": true
}
```

`apps[...]` verbindet Admin-Schalter (`ApplicationsHomeCenterTools.display`) mit `abo_1`.
Die App blendet Bereiche aus, die `false` melden.

Versionspolitik: Alle drei Instanzen laufen eventuell mit unterschiedlichem Stand.
- `api_version` steigt nur bei inkompatiblen Änderungen. Neue Felder sind additiv.
- Die App prüft beim Verbinden: fehlt `/api/v1/meta/` → „Diese Instanz unterstützt die App
  noch nicht"; `api_version` zu hoch/niedrig → verständliche Meldung.
- Der Client ignoriert unbekannte Felder.

## 6. Endpunkte nach Bereich

Konvention: Listen mit `?limit=&offset=`, Antwort `{count, results}`. Änderbare Ressourcen
liefern `updated_at`; Konflikte werden ohne Sperren per Letzter-gewinnt gelöst (Einzelnutzer-Annahme).

### 6.1 Einkaufsliste (`tools.ShoppingList`, `ShoppingItem`)

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/shopping-lists/` | Listen (eigene + freigegebene) mit Zähler offen/erledigt |
| POST | `/shopping-lists/` | neu: `{title}` |
| PATCH | `/shopping-lists/<key>/` | umbenennen (nur Besitzer) |
| DELETE | `/shopping-lists/<key>/` | löschen (nur Besitzer) |
| GET | `/shopping-lists/<key>/items/` | Artikel, Vorschläge für Kategorie/Laden |
| POST | `/shopping-lists/<key>/items/` | `{name, amount, category, store, price}` |
| PATCH | `/shopping-items/<id>/` | ändern |
| POST | `/shopping-items/<id>/toggle/` | abhaken/zurücknehmen |
| DELETE | `/shopping-items/<id>/` | löschen |

Hinweis: Mitnutzer-Benachrichtigung (`notify(... SHOPPING_LIST_ITEM_ADDED ...)`) aus den
Web-Views soll in einen gemeinsamen Service, damit die API dasselbe auslöst. Freigaben
verwalten (`shared_with`) kommt in Stufe 2.

### 6.2 Rezepte (`kitchen.Recipe`, `RecipeIngredient`, `RecipeStep`, `RecipeImage`)

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/recipes/` | eigene Rezepte; Filter `category`, Suche `q` |
| GET | `/recipes/<key>/` | Detail: Zutaten (nach `section`), Schritte, Bilder, Nährwerte |
| POST | `/recipes/` | anlegen (verschachtelt: Zutaten, Schritte) |
| PUT/PATCH | `/recipes/<key>/` | ändern (`user_can_edit`) |
| DELETE | `/recipes/<key>/` | löschen |
| POST | `/recipes/<key>/images/` | Bild hochladen (multipart) |
| GET/POST | `/recipe-categories/` | Kategorien |
| GET | `/recipes/public/` | entdecken (`is_public`) |
| POST | `/recipes/<key>/copy/` | öffentliches Rezept übernehmen (`copy_for`) |

Bilder: `title_image` und `RecipeImage` liegen unter `/media/` ohne Anmeldung. Der Client
lädt sie über die absolute URL aus der API-Antwort.

### 6.3 Rezept → Einkaufsliste (Kernfunktion)

Neu und serverseitig (statt Browser-Skalierung):

`POST /recipes/<key>/shopping-preview/`
```json
{ "servings": 4 }
```
Antwort: Zutatenzeilen, auf die Portionenzahl skaliert (Menge, Einheit, Name, Notiz,
vorbelegte Kategorie/Laden/Preis aus `RecipeIngredient.shopping_category/store/price`).

`POST /recipes/<key>/to-shopping-list/`
```json
{
  "target_list_key": "abc123",
  "new_list_title": null,
  "items": [ {"name": "Mehl", "amount": "500 g", "category": "Backen", "store": "", "price": null} ]
}
```
- Entweder `target_list_key` **oder** `new_list_title`; Zugriff wie in `rezept_to_shopping`
  (`user_can_view` am Rezept, `user_can_access` an der Liste).
- Antwort: `{list_key, created_count}`. Mitnutzer werden benachrichtigt wie im Web.
- Technisch: Logik aus `kitchen/views.py:rezept_to_shopping` und der Skalierung aus dem
  Template in `kitchen/services/shopping.py` auslagern; Web-View und API rufen sie auf.
- Die Skalierung ist die einzige Stelle, an der heute **kein** Servercode existiert; hier
  entsteht neue Logik (Einheiten, Brüche, Zutaten ohne Menge).

### 6.4 OweMe (`OweMeAccount`, `OweMeEntry`, `OweMePlan`, `OweMePlanRate`)

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/oweme/` | Übersicht: `bekomme`, `schulde`, `erledigt`, Summen, Saldo, fällige |
| POST | `/oweme/accounts/` | Konto anlegen (`direction`, `person_name`, `due_date`, `note`) |
| GET | `/oweme/accounts/<key>/` | Kontoauszug mit Bewegungen, Saldo, Plan und Raten |
| PATCH/DELETE | `/oweme/accounts/<key>/` | ändern/löschen (Besitzer) |
| POST | `/oweme/accounts/<key>/entries/<kind>/` | `kind` = `given` \| `returned`; `{date, title, amount}` |
| PATCH/DELETE | `/oweme/entries/<id>/` | Bewegung ändern/löschen |
| PUT/DELETE | `/oweme/accounts/<key>/plan/` | Rückzahlungsplan setzen/beenden |

Saldo, `is_due`, `days_overdue` und Planraten kommen aus den vorhandenen Properties und aus
`oweme/plan.py`; die API gibt sie als berechnete, nur lesbare Felder zurück. Export und
Freigabe (`shared_with`) in Stufe 2.

### 6.5 Health (`health.*`)

Alle Endpunkte hinter `abo_1` **und** `HealthProfile.enabled` (außer Profil/Aktivierung).

**Profil und Gewicht** (`HealthProfile`, `WeightEntry`)

| Methode | Pfad | Zweck |
|---|---|---|
| GET/PATCH | `/health/profile/` | Stammdaten, Zielgewicht (Ziel-Logik in `health/services/ziel.py`), Trainingsschalter |
| POST | `/health/profile/enable/` | Health ein-/ausschalten (`health_toggle`) |
| GET/POST | `/health/weights/` | Gewichtsverlauf, Eintrag (Grenzen 20–400 kg serverseitig in `WeightEntryForm`, daher Form wiederverwenden) |
| DELETE | `/health/weights/<id>/` | Eintrag löschen |

**Kalorientracker** (`CalorieDay`, `CalorieEntry`, `MahlzeitVorlage`)

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/health/calories/?month=YYYY-MM` | Monatsübersicht |
| GET | `/health/calories/<YYYY-MM-DD>/` | Tag inkl. Bilanz, Zielwerte; übernimmt beim ersten Aufruf die Speiseplanvorschläge (`seed_day_from_mealplan`) |
| POST | `/health/calories/<YYYY-MM-DD>/entries/` | Eintrag |
| PATCH/DELETE | `/health/calorie-entries/<id>/` | Portionen ändern/löschen |
| GET/POST | `/health/meal-templates/` | Vorlagen |
| PATCH/DELETE | `/health/meal-templates/<id>/` | ändern/löschen |
| POST | `/health/meal-templates/<id>/apply/<YYYY-MM-DD>/` | Vorlage erfassen |

Hinweis: Das Lesen eines Tages schreibt (Speiseplanvorschläge werden übernommen). Das ist
Web-Verhalten und wird für die API beibehalten, aber im Client sichtbar gemacht
(Quelle `source`).

**Gesundheitschecks, Dokumente, Kommentare, Impfbuch** (`HealthCheck`, `HealthDocument`, `HealthComment`, `DocumentCategory`, `Vaccination`)

| Methode | Pfad | Zweck |
|---|---|---|
| GET/POST | `/health/checks/` | Liste (mit `due_date`, `is_due`), anlegen |
| GET/PATCH/DELETE | `/health/checks/<id>/` | Detail/ändern/löschen |
| POST | `/health/checks/<id>/documents/` | Upload (multipart) |
| GET | `/health/documents/<key>/` | Datei **mit Bearer-Token** (statt Cookie-Session); ersetzt `health_document_download` |
| DELETE | `/health/documents/<id>/` | löschen |
| POST/DELETE | `/health/checks/<id>/comments/`, `/health/comments/<id>/` | Kommentare |
| GET/POST/DELETE | `/health/document-categories/` | Kategorien |
| GET/POST | `/health/vaccinations/` | Impfbuch |
| PATCH/DELETE | `/health/vaccinations/<id>/` | ändern/löschen |

Dokumente liegen bewusst in privatem Speicher ohne öffentliche URL (`PrivateMediaStorage`).
Die API behält das bei: Auslieferung nur durch eine authentifizierte View, kein Link in der
Antwort, den man ohne Token öffnen könnte. Der Client lädt über Header und speichert lokal.

**Laborwerte** (`Laborwert`, `Laboranalyse`; Stand 0.93.1056–1058). Im Web gehören sie zu den Gesundheitschecks. Jede Messung hat einen eigenen Referenzbereich und eine berechnete Einstufung (zu niedrig/normal/zu hoch u. a.); sie kann an einem Arztbesuch (`HealthCheck`) hängen (`health_check`, Herkunft „Besuch“) und erscheint dann in derselben Übersicht. Gemeinsame Regeln liegen in `health/services/labor.py`, Validierung in `LaborwertForm`.

| Methode | Pfad | Zweck |
|---|---|---|
| GET/POST | `/health/labs/` | Liste (gruppiert nach Laborwert, `?name=`), Messung anlegen |
| GET/PATCH/DELETE | `/health/labs/<id>/` | Messung |
| GET | `/health/labs/trend/?name=` | Verlauf mit Referenzgrenzen |
| POST | `/health/checks/<id>/labs/` | Messung am Arztbesuch erfassen |
| GET | `/health/labs/analysis/` | letzte KI-Einordnung (nur lesen) |

Die KI-Einordnung ist in Stufe 1 ausgenommen. Im Web ist sie bestätigungspflichtig: Der angezeigte Text trägt eine Prüfsumme (`pruefsumme` in `health/services/laboranalyse.py`) und wird nur erzeugt, wenn der Nutzer genau diesen Stand bestätigt. Wird sie später aufgenommen, muss die API dieselbe Prüfsumme verlangen, sonst fehlt die Bestätigung.

**Training und Ampel** (`Training`, `TrainingVorlage`, `TrainingWochenziel`): `/health/trainings/`,
`/health/trainings/<id>/`, `/health/training-templates/`, `/health/training-goals/` (Wochenziele **je Sportart**, seit 0.93.1055; ein Ziel ohne Sportart gilt für alle; Liste, anlegen, ändern, löschen, Validierung über `WochenzielZeileForm`),
`/health/training/review/` (Rückblick) und `/health/traffic-light/` (Ampel, nur lesen). Beratungstexte
(`Ernaehrungsrat`, `Trainingsrat`) sind KI-gestützt und entfallen in Stufe 1.

Für `traffic-light`, Rückblick und Kalorienbilanz wird die Berechnung aus
`health/services/*` aufgerufen; die Views rendern heute nur.

## 7. Querschnittsthemen

| Thema | Entscheidung |
|---|---|
| Transport | Server-URL frei wählbar; HTTPS Standard; HTTP nur für private Adressen (RFC 1918), mit Warnung |
| Zertifikate | System-Truststore; selbstsignierte Zertifikate per Fingerabdruck bestätigen, keine globale Abschaltung |
| Mehrere Instanzen | Token je Instanz, im Android Keystore |
| Offline | Stufe 1: nur Lesen aus Cache (Einkaufsliste); Schreiben nur online |
| Sync | Einkaufsliste per Abruf; `updated_at`-Feld erlaubt später Delta-Sync |
| Größen | Upload-Limits wie im Web (Bilder, Dokumente) |
| Tests | je Endpunkt: Anmeldung, fremde Daten (404), abgeschaltete App (404/403), Validierung |
| Dokumentation | OpenAPI-Schema aus DRF (`drf-spectacular`) als Vertrag für den Client |

## 8. Umsetzungsreihenfolge

| Phase | Inhalt | Ergebnis für die App |
|---|---|---|
| 0 | App `mobile_api`, `/meta/`, Token-Modell, Login/Logout, `AppEnabled`-Berechtigung | Verbinden und Anmelden |
| 1 | Einkaufsliste | erste nutzbare Funktion |
| 2 | Rezepte lesen, Rezept → Einkaufsliste (inkl. Skalierung als Service) | Kernanforderung |
| 3 | Rezepte schreiben (Anlegen, Bilder) | Verwaltung |
| 4 | OweMe | |
| 5 | Health: Profil, Gewicht, Kalorien | |
| 6 | Health: Checks, Dokumente, Impfbuch, Labor, Training, Ampel | |
| 7 | Stufe 2: Freigaben, Export, Push, KI | optional |

Jede Phase ist für sich auslieferbar. Die Instanzen brauchen dafür jeweils ein Update;
`/meta/` und `min_client_version` sorgen dafür, dass die App ältere Stände erkennt.

## 9. Risiken

1. **Versionsstreuung der Instanzen:** `api_version` und Meldung beim Verbinden (Abschnitt 5).
2. **Doppelte Logik:** Skalierung und Tagesbilanz werden in Services verschoben, nicht neu
   geschrieben; Abweichungen zwischen Web und App wären sonst unvermeidlich.
3. **Health ist der größte Block** (rund 3.600 Zeilen Modelle/Views/Formulare) und wird zuletzt geliefert.
4. **Sensible Daten:** Gesundheitsdokumente, Laborwerte. Token im Keystore, kein Logging von
   Inhalten, Dokumente nur über authentifizierte Abrufe.
5. **Lesen mit Seiteneffekt** (Kalorientag): bewusst übernommen, im Client sichtbar.
6. **Fälligkeiten ohne Scheduler:** Hinweise (OweMe, Impfungen, Checks) erscheinen in der App nur
   beim Öffnen. Push ist Stufe 2 und verlangt pro Instanz Scheduler und FCM.

## 10. Entscheidungen

Am 2026-10-21 abgestimmt:

1. **Ablage:** `mobile_api` liegt direkt im HomeCenter-Repo, damit Models und Services ohne Umweg nutzbar sind.
2. **Token-Laufzeit:** 90 Tage Gleitfrist.
3. **Rezepte:** Die App darf Rezepte anlegen und bearbeiten (Phase 3 bleibt im Umfang).
4. **Health nicht aktiviert:** Die App zeigt einen Aktivierungshinweis und bietet `/health/profile/enable/` an.
5. **Client:** Kotlin mit Jetpack Compose (nur Android).

6. **Branch:** `feature/mobile-api`, abgezweigt von `release/0.93` (21.10.2026).

Noch offen: keine fachlichen Fragen. Phase 0 ist begonnen.
