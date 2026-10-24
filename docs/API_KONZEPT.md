# API-Konzept: Mobile Schnittstelle für HomeCenter

Stand: 2026-10-23 · Status: **umgesetzt** auf Seite von HomeCenter (siehe „Umsetzungsstand“ unten)

Grundlage ist eine Codeanalyse von `SRyoshi2/HomeCenter` (Django 5.0.7, DRF installiert).
Alles hier Beschriebene betrifft **HomeCenter** (neue App `mobile_api`), nicht diesen
Android-Client. Der Client in `hc_app` baut darauf auf.

## Umsetzungsstand

Die Schnittstelle ist in HomeCenter gebaut (Zweig `feature/mobile-api`, Stand `a4b6b75`; danach in `release/0.93` und `master`).
**Maßgeblich für den Client ist ab jetzt [`API_REFERENZ_HomeCenter.md`](API_REFERENZ_HomeCenter.md)** – sie wird im HomeCenter-Repo aus
dem Code erzeugt (`manage.py api_referenz`, ein Test hält sie aktuell) und hier als Kopie abgelegt. Die Sicherheitsdurchsicht steht in
`docs/API_SICHERHEIT.md` im HomeCenter-Repo.

Alle Phasen 0–6 sind umgesetzt, dazu aus Stufe 2: **Freigaben** (Einkaufslisten, OweMe, Rezepte anbieten), **OweMe-Export**,
**KI-Funktionen** (mit Bestätigung) und **Benachrichtigungen als Abruf**. **Nicht gebaut:** Push per FCM (braucht pro Instanz einen
zeitgesteuerten Dienst), Tandoor-Import, Veröffentlichen im Social-Feed, Speiseplan und Einkaufsplan.

**Abweichungen vom Konzept unten – im Zweifel gilt die Referenz:**

- Kein `PUT` bei Rezepten, nur `PATCH` (Zutaten und Schritte als Gesamtliste; Einträge mit `id` bleiben erhalten). Rezepte filtern mit
  `?category=<Name>` (mehrfach), nicht mit der Nummer.
- `ShoppingItem` hat kein `updated_at`; Delta-Sync bleibt offen.
- Gewicht und Profil gehen auch **vor** der Aktivierung von Health (die Aktivierung verlangt ein Gewicht); eingeschaltet wird mit
  `POST /health/profile/enable/` und `{"enabled": true|false}`. Kalorien, Checks, Training und Co. brauchen ein aktives Profil.
- Bilder: höchstens 10 MB, Dokumente höchstens 25 MB (das Web kennt keine Grenze).
- Lebensmittel-Vorschläge aus dem Bundeslebensmittelschlüssel (lokale CSV) für die Erfassung: `GET /health/foods/suggest/?q=`.
- `Laborwerte`: `/health/labs/…` mit Verlauf (`/trend/?name=`), Erfassen am Arztbesuch (`/health/checks/<built-in function id>/labs/`) und der KI-Einordnung
  mit Prüfsumme des Texts (`/labs/analysis/`).
- Wochenziele sind **je Sportart** (`/health/training-goals/`, `weekly`), dazu das Langzeitziel (`long_term`).
- `GET /me/` und `GET /meta/` wie unten beschrieben; `server_version` kommt aus `SERVER_VERSION` (`0.93.1059`).

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
7. **Social:** vorerst nicht Teil der App (22.10.2026). Befund für später, Stand `feature/mobile-api` `ce13fd6`:
   - Die vorhandene JSON-API unter `/api/social/` deckt Feed (cursorbasiert, Suche), Beitrag lesen/erstellen/ändern/löschen,
     Liken und Kommentare ab; Chat-Nachrichten lesen/senden und „ungelesen“ liegen als JSON unter `social/api/chat/…`
     (Zeit nur als `HH:MM`). Freundesliste, Anfragen, Profile, die Liste aller Chats und „Chat starten“ gibt es nur als HTML.
   - Die Social-API kennt nur Session und HTTP Basic, nicht das App-Token. Ohne Server-Änderung blieben nur Basic
     (Passwort auf dem Gerät, langsames Hashing bei jedem Aufruf, kein Widerruf) oder ein Session-Login mit CSRF.
   - Empfehlung für später: `BearerTokenAuthentication` in die DRF-Standard-Authentifizierung aufnehmen, dann nimmt die
     vorhandene Social-API das App-Token an; für Freunde, Profile und Chat-Liste neue JSON-Endpunkte als eigene Phase.
   - Unabhängig davon: Wegen Basic Auth ist `/api/social/` auf öffentlichen Instanzen ohne Sperre nach Fehlversuchen
     erreichbar; in `settings/base.py` lässt sich Basic aus den DRF-Einstellungen nehmen oder drosseln.

## 11. Ergänzung für Phase 5: Lebensmittelsuche im Kalorientracker (offen)

Der Kalorientracker des Webs schlägt beim Tippen Lebensmittel aus dem Bundeslebensmittelschlüssel (BLS 4.0, rund 7.100
Einträge in `tools/data/bls_4_0.csv`, Tabelle `tools.BlsFood`) vor und rechnet mit dem Portionsrechner aus „Werte je 100 g“
und „gegessene Gramm“ die Werte der Portion aus. Der **Rechner** läuft komplett in der App (Rundung wie im Web: kcal auf ganze
Zahlen, Nährwerte auf eine Nachkommastelle; Eiweiß und Fett nur zusammen). Die **Vorschläge** brauchen einen Endpunkt, den
die mobile API noch nicht hat: der Web-Endpunkt `tools/api/lebensmittel/bls/suggest/` kennt nur Session und CSRF, nicht das
App-Token.

Gewünscht (Phase 5b):

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/health/foods/?q=<Text>` | Vorschläge, höchstens acht, ab zwei Zeichen |

- Antwort wie im Web: `{"suggestions": [{"code", "name", "kcal_100g", "protein_100g", "fat_100g", "carbs_100g"}]}`;
  `protein_100g`, `fat_100g`, `carbs_100g` sind Zahlen oder `null` (fehlt im Schlüssel; `null` ist nicht 0).
- Umsetzung: `tools.services.bls.suche(q)` aufrufen (Normalisierung und Rangfolge bleiben auf dem Server, die App dupliziert
  nichts). Berechtigung `AppEnabled('health')`. Wie im Web immer 200, bei kurzer Eingabe oder ohne Treffer eine leere Liste.
- Die App zeigt die Quelle „Bundeslebensmittelschlüssel 4.0 · Max Rubner-Institut“ unter den Vorschlägen. Ohne den Endpunkt
  (404) zeigt sie einfach keine Vorschläge; der Rechner funktioniert trotzdem.
- Alternative ohne Server-Änderung: die CSV (rund 450 KB, laut BLS-Dokumentation frei auch für App-Entwicklung) in die App
  einbauen und dort durchsuchen. Das verdoppelt die Suchlogik, hält die Daten außerhalb der Instanz und funktioniert offline;
  empfohlen wird es nicht.

### Stand: umgesetzt

Der Endpunkt ist da, mit anderer Form als oben gewünscht: `GET /health/foods/suggest/?q=<Text>&limit=<n>` liefert
`{"count", "results": [{code, name, kcal_100g, protein_100g, fat_100g, carbs_100g}]}`; Gramm sind Text (`"13.2"`) oder `null`.
Die App liest diese Form (`GrammSerializer` akzeptiert Text und Zahl) und zeigt die Quelle unter den Vorschlägen.

## 12. Health, Phase 6 (umgesetzt)

Server: `mobile_api/health_checks.py`, `health_labor.py`, `health_training.py` (Branch `feature/mobile-api`).

| Bereich | Endpunkte | In der App |
|---|---|---|
| Arztbesuche | `health/checks/`, `…/<id>/` (Liste, Anlegen, Detail mit Laborwerten/Dokumenten/Kommentaren, Ändern, Löschen) | Liste, Detail, Dialog; Fälligkeit („nächster Termin“ als Datum oder Zeitraum) |
| Dokumente | `health/checks/<id>/documents/` (multipart `file`, `title`, `category_id`), `health/documents/<key>/` (Download nur mit Token), `DELETE …/<id>/`, `health/document-categories/` | Auswahl per Dateiwähler (höchstens 25 MB), Speichern über „Datei erstellen“, Kategorien anlegen |
| Kommentare | `health/checks/<id>/comments/`, `health/comments/<id>/` | Liste, Senden, Löschen |
| Impfbuch | `health/vaccinations/` | Liste, Dialog, Fälligkeit |
| Laborwerte | `health/labs/` (je Bezeichnung neueste Messung), `…/trend/?name=`, `…/<id>/`, `health/checks/<id>/labs/`, `health/labs/analysis/` | Übersicht mit Einordnung gegen den Bereich der Messung, Verlauf mit Linie und Referenzband, Erfassen/Ändern/Löschen; KI-Einordnung nur lesen |
| Training | `health/trainings/`, `training-templates/` (+ `apply/<tag>/`), `training-goals/` (PUT), `training/review/` | Wochenansicht mit Summen, Wochenzielen, Belastung, Monat; Einheiten, Vorlagen, Wochen- und Langzeitziele |
| Ernährungsampel | `health/traffic-light/?week=&source=tracker\|plan` | Wochen- und Tagesampel mit Kriterien |

Hinweise:

- Das Training lässt sich im Profil abschalten; dann antwortet der Server mit 403 `training_disabled`. Die App zeigt den
  Eintrag „Training“ nur bei eingeschaltetem Training und hat dafür einen Schalter in der Health-Übersicht.
- KI-Texte (Laboreinordnung, Wochenrückblick, Ernährungseinschätzung) liest die App nur, wenn sie abgelegt sind; sie zu
  erzeugen bleibt im Web (Bestätigung des angezeigten Textes). Die App kennzeichnet sie als Schätzung ohne ärztliche Beratung.
- Sportarten und Intensitäten (`locker`/`mittel`/`intensiv`) sind im Client als Auswahl hinterlegt; Sportarten stehen zusätzlich
  in `training-goals/` unter `options`.

## 13. Phase 7 (umgesetzt)

Server: `mobile_api/freigaben.py`, `benachrichtigungen.py`.

- **Benutzerliste** `users/?q=`: aktive Benutzer ohne einen selbst. **Freigabe** `PUT shopping-lists/<key>/sharing/` und
  `oweme/accounts/<key>/sharing/` mit `{"user_ids": [...]}` ersetzt die Freigabe (nur Besitzer); Fehler je Stelle (`user_ids[0]`).
  Die App zeigt die Benutzer mit Häkchen; Abwählen beendet die Freigabe.
- **Rezepte** `POST recipes/<key>/share/` bietet an (`offered`, `already_offered`); `recipe-shares/` listet Angebote an dich,
  `…/<id>/accept/` übernimmt als eigene Kopie, `…/decline/` lehnt ab.
- **Export** `GET oweme/accounts/<key>/export/` liefert CSV (Semikolon, BOM); die App speichert über „Datei erstellen“.
- **Meldungen** `notifications/` (+ `unread-count/`, `<id>/read/`, `read-all/`, `DELETE <id>/`): Abruf statt Push (FCM bräuchte
  einen zeitgesteuerten Dienst). Der erste Abruf am Tag lässt den Server fällige OweMe-Erinnerungen erzeugen. Die App holt den
  Zähler beim Öffnen der Startseite; ein Tipp markiert als gelesen und öffnet Liste, Konto oder Rezept (`target`).

## 14. Phase 7c und 8 (umgesetzt)

**KI-Funktionen** (`mobile_api/ki.py`): Voraussetzung ist `abo_ai` (sonst 403 `ai_required`); jede Anfrage braucht
`{"confirmed": true}`, die App zeigt vorher den Text, der hinausgeht (`ai_text`, bei Laborwerten `request_preview`), und fragt
nach. Anbieterfehler kommen als 502 `ai_failed` mit Grund.

| Funktion | Endpunkt | In der App |
|---|---|---|
| Laborwerte einordnen | `POST health/labs/analysis/` mit `checksum` aus `request_preview` (409 `checksum_mismatch`, wenn sich Werte geändert haben) | Knopf auf der Laborseite, Dialog mit Text |
| Wochenrückblick Training | `POST health/training/review/` (`week`) | Knopf in der Wochenansicht |
| Ernährungseinschätzung | `POST health/traffic-light/` (`week`, `source`) | Knopf bei der Ampel |
| Nährwerte schätzen | `POST health/calorie-entries/<id>/estimate-nutrients/`, `…/calories/<tag>/estimate-nutrients/`, `DELETE` nimmt zurück | Knopf am Tag (offene Mahlzeiten: `open_estimates`), Zurücknehmen je Eintrag |
| Rezept vorschlagen | `POST recipes/ai/generate/` (speichert nichts) | Editor: füllt den Entwurf, gespeichert wird erst per „Speichern“ |
| Rezeptnährwerte schätzen | `POST recipes/ai/estimate-nutrition/` | Editor: füllt die Nährwertfelder |

**Phase 8:** Referenz aus dem Code (`manage.py api_referenz`), Sicherheitsdurchsicht; `DELETE …/read/` löschte eine Meldung (behoben).

## 15. Download Center (API 0.93.1083, umgesetzt)

Nur lesen und laden; gepflegt wird im Web. App-Schlüssel `downloads` (in `GET /me/` unter `apps`), gekoppelt an `abo_1`.

- `GET downloads/?type=&category=&q=&limit=&offset=`: sichtbare Downloads nach Typ, Kategorie und Titel; dazu `types` und
  `categories` zur Auswahl (mit Anzahl, nur solche mit sichtbaren Downloads).
- `GET downloads/<key>/`: Detail mit `description` (HTML aus dem Editor, die App zeigt es wie die Rezepttexte), `description_text`,
  Größe und `sha256`.
- `GET downloads/<key>/file/`: Datei als Anhang, jeder Abruf wird gezählt, **ohne Range**: ein abgebrochener Ladevorgang
  beginnt von vorn. Dateien sind bis 2 GB möglich, deshalb schreibt die App streamend in eine vom Nutzer gewählte Datei
  (Storage Access Framework) und prüft danach den SHA-256 gegen den Wert des Details.
- Wer einen Download nicht sehen darf (Gruppenregel), bekommt 404, auch für die Datei.

