# API-Referenz: mobile Schnittstelle von HomeCenter

*Diese Datei wird erzeugt (`python manage.py api_referenz`) - bitte nicht von Hand ändern. Ein Test hält sie aktuell.*

Basis: `/api/v1/`. Version der Schnittstelle: `api_version` aus `GET /api/v1/meta/`.

## Grundregeln

- **Anmeldung:** `POST /auth/login/` liefert ein Token (90 Tage Gleitfrist). Danach `Authorization: Bearer <token>`. Keine Session,
  kein CSRF. Ein neues Passwort widerruft alle Tokens des Kontos.
- **Zugriff:** Jede Zeile nennt, was zusätzlich gilt. `App „x“` heißt: die App ist im Admin-Panel eingeschaltet **und** das Konto hat
  `abo_1` (sonst 403 `app_disabled` bzw. `abo_required`). Health-Bereiche brauchen ein eingeschaltetes Profil (403
  `health_not_enabled`), das Training zusätzlich `training_enabled` (403 `training_disabled`), KI-Funktionen `abo_ai` (403 `ai_required`).
- **Fremde Daten:** gibt es nicht (404 `not_found`). Wer ein Objekt sehen, aber nicht ändern darf, bekommt 403 `forbidden`.
- **Fehlerformat:** `{"error": {"code": "...", "message": "...", "fields": {"feld": ["..."]}}}`. Bei verschachtelten Listen heißen die
  Felder `ingredients[2].amount`.
- **Zahlen:** Dezimalwerte sind Strings (`"2.50"`). Daten `JJJJ-MM-TT`, Uhrzeiten `HH:MM`, Zeitstempel ISO 8601.
- **Listen:** `?limit=` (1–200, Standard 50) und `?offset=`; Antwort `{"count": n, "results": [...]}`.
- **Änderungen:** `PATCH` ändert nur die mitgeschickten Felder; `null` leert ein optionales Feld.
- **KI:** Jede KI-Anfrage braucht `{"confirmed": true}`; die GET-Antworten liefern den Text, der hinausginge (`ai_text`).
- **Versionen:** Neue Felder sind additiv; `api_version` steigt nur bei inkompatiblen Änderungen. Unbekannte Felder ignorieren.


## Verbinden und Anmelden

| Methode | Pfad | Zugriff | Zweck |
|---|---|---|---|
| GET | `/api/v1/meta/` | offen | Ohne Anmeldung, ohne personenbezogene Daten: der Client prueft damit die Instanz. |
| GET | `/api/v1/me/` | angemeldet | Benutzer, `abo_1`, welche Apps freigeschaltet sind (`apps`) und ob Health eingeschaltet ist. |
| POST | `/api/v1/auth/login/` | offen | Anmelden mit `username`, `password` und `device_name`; liefert das Token (nur dieses eine Mal im Klartext), den Ablauf und den Benutzer. Nach fünf Fehlversuchen je Benutzername oder Adresse gilt zehn Minuten Sperre (429). |
| POST | `/api/v1/auth/logout/` | angemeldet | Das Token dieses Geräts widerrufen. |
| GET | `/api/v1/auth/devices/` | angemeldet | Die angemeldeten Geräte (Tokens) dieses Kontos; das aktuelle ist mit `current` markiert. |
| DELETE | `/api/v1/auth/devices/{id}/` | angemeldet | Ein Gerät abmelden (sein Token widerrufen). |

## Benachrichtigungen und Benutzer

| Methode | Pfad | Zugriff | Zweck |
|---|---|---|---|
| GET | `/api/v1/notifications/` | angemeldet | Die Meldungen, neueste zuerst. ``?unread=1`` zeigt nur ungelesene. |
| GET | `/api/v1/notifications/unread-count/` | angemeldet | Nur der Zähler - billig genug zum häufigen Abfragen. |
| POST | `/api/v1/notifications/read-all/` | angemeldet | Alle Meldungen als gelesen markieren; liefert die Anzahl. |
| DELETE | `/api/v1/notifications/{id}/` | angemeldet | Eine Meldung löschen. |
| POST | `/api/v1/notifications/{id}/read/` | angemeldet | Eine Meldung als gelesen markieren. |
| GET | `/api/v1/users/` | abo_1 | Wen man etwas teilen kann: aktive Benutzer ohne einen selbst. ``?q=`` sucht in Benutzername und Nickname. |

## Einkaufsliste

| Methode | Pfad | Zugriff | Zweck |
|---|---|---|---|
| GET | `/api/v1/shopping-lists/{key}/sharing/` | App „einkaufsliste“ | Mit wem eine Einkaufsliste geteilt ist (nur der Besitzer). ``PUT`` ersetzt die Freigabe. |
| PUT | `/api/v1/shopping-lists/{key}/sharing/` | App „einkaufsliste“ | Mit wem eine Einkaufsliste geteilt ist (nur der Besitzer). ``PUT`` ersetzt die Freigabe. |
| GET | `/api/v1/shopping-lists/` | App „einkaufsliste“ | Eigene und geteilte Einkaufslisten mit Zählern. |
| POST | `/api/v1/shopping-lists/` | App „einkaufsliste“ | Eine Einkaufsliste anlegen. |
| PATCH | `/api/v1/shopping-lists/{key}/` | App „einkaufsliste“ | Eine Liste umbenennen (nur der Besitzer). |
| DELETE | `/api/v1/shopping-lists/{key}/` | App „einkaufsliste“ | Eine Liste löschen (nur der Besitzer). |
| GET | `/api/v1/shopping-lists/{key}/items/` | App „einkaufsliste“ | Artikel der Liste und Vorschläge für Kategorie und Laden. |
| POST | `/api/v1/shopping-lists/{key}/items/` | App „einkaufsliste“ | Einen Artikel hinzufügen; die Mitnutzer werden benachrichtigt. |
| PATCH | `/api/v1/shopping-items/{id}/` | App „einkaufsliste“ | Name, Menge, Kategorie, Laden oder Preis eines Artikels ändern. |
| DELETE | `/api/v1/shopping-items/{id}/` | App „einkaufsliste“ | Einen Artikel löschen. |
| POST | `/api/v1/shopping-items/{id}/toggle/` | App „einkaufsliste“ | Einen Artikel abhaken oder wieder öffnen. |

## Rezepte

| Methode | Pfad | Zugriff | Zweck |
|---|---|---|---|
| POST | `/api/v1/recipes/ai/generate/` | App „rezepte“, abo_ai | Ein Rezeptvorschlag aus einer Zutatenliste. Speichert nichts - übernommen wird per ``POST /recipes/``. |
| POST | `/api/v1/recipes/ai/estimate-nutrition/` | App „rezepte“, abo_ai | Nährwerte je Portion aus Titel und Zutaten (noch nicht gespeichert). Speichert nichts. |
| POST | `/api/v1/recipes/{key}/share/` | App „rezepte“ | Das eigene Rezept anderen Benutzern anbieten. Wer es schon angeboten bekommen hat, wird übersprungen. |
| GET | `/api/v1/recipe-shares/` | App „rezepte“ | Rezepte, die andere einem angeboten haben. |
| POST | `/api/v1/recipe-shares/{id}/accept/` | App „rezepte“ | Als eigenständige Kopie übernehmen (201, das neue Rezept). |
| POST | `/api/v1/recipe-shares/{id}/decline/` | App „rezepte“ | Ein angebotenes Rezept ablehnen. |
| GET | `/api/v1/recipes/` | App „rezepte“ | Eigene Rezepte. ``?q=`` sucht in Titel und Kategorie, ``?category=<Name>`` (mehrfach) filtert. |
| POST | `/api/v1/recipes/` | App „rezepte“ | Eigene Rezepte. ``?q=`` sucht in Titel und Kategorie, ``?category=<Name>`` (mehrfach) filtert. |
| GET | `/api/v1/recipes/public/` | App „rezepte“ | „Rezepte entdecken“: oeffentliche Rezepte anderer Nutzer. |
| GET | `/api/v1/recipes/{key}/` | App „rezepte“ | Ein Rezept mit Zutaten (nach Abschnitt), Schritten, Bildern und Nährwerten. |
| PATCH | `/api/v1/recipes/{key}/` | App „rezepte“ | Ein eigenes Rezept ändern; Zutaten und Schritte als Gesamtliste (Einträge mit `id` bleiben erhalten). |
| DELETE | `/api/v1/recipes/{key}/` | App „rezepte“ | Ein eigenes Rezept löschen. |
| POST | `/api/v1/recipes/{key}/images/` | App „rezepte“ | Bild hochladen (multipart, Feld ``image``): ``title=true`` setzt das Titelbild, sonst kommt es in die Galerie. |
| DELETE | `/api/v1/recipes/{key}/images/{id}/` | App „rezepte“ | Ein Galeriebild löschen. |
| DELETE | `/api/v1/recipes/{key}/title-image/` | App „rezepte“ | Das Titelbild entfernen. |
| POST | `/api/v1/recipes/{key}/copy/` | App „rezepte“ | Oeffentliches Rezept als eigene Kopie uebernehmen (``Recipe.copy_for``). |
| POST | `/api/v1/recipes/{key}/shopping-preview/` | App „rezepte“ | Zutaten auf eine Portionenzahl hochgerechnet - ohne etwas zu speichern. |
| POST | `/api/v1/recipes/{key}/to-shopping-list/` | App „rezepte“ | Vom Nutzer bestaetigte Zeilen (meist aus der Vorschau) in eine Liste uebernehmen. |
| GET | `/api/v1/recipe-categories/` | App „rezepte“ | Eigene Rezeptkategorien mit Anzahl. |
| POST | `/api/v1/recipe-categories/` | App „rezepte“ | Eine Kategorie anlegen (201) oder die vorhandene liefern (200). |

## OweMe

| Methode | Pfad | Zugriff | Zweck |
|---|---|---|---|
| GET | `/api/v1/oweme/accounts/{key}/sharing/` | App „oweme“ | Mit wem ein OweMe-Konto geteilt ist (nur der Besitzer). ``PUT`` ersetzt die Freigabe. |
| PUT | `/api/v1/oweme/accounts/{key}/sharing/` | App „oweme“ | Mit wem ein OweMe-Konto geteilt ist (nur der Besitzer). ``PUT`` ersetzt die Freigabe. |
| GET | `/api/v1/oweme/accounts/{key}/export/` | App „oweme“ | Der Kontoauszug als CSV (Semikolon, mit BOM) - wie der Export im Web. |
| GET | `/api/v1/oweme/` | App „oweme“ | Übersicht: was ich bekomme, was ich schulde, Erledigtes, Summen, Saldo, fällige Konten. |
| POST | `/api/v1/oweme/accounts/` | App „oweme“ | Ein Konto anlegen (Person frei oder aus Adressbuch/Benutzern). |
| GET | `/api/v1/oweme/accounts/{key}/` | App „oweme“ | Kontoauszug mit laufendem Stand, Plan und Raten. |
| PATCH | `/api/v1/oweme/accounts/{key}/` | App „oweme“ | Ein Konto ändern (nur der Besitzer). |
| DELETE | `/api/v1/oweme/accounts/{key}/` | App „oweme“ | Ein Konto samt Auszug löschen (nur der Besitzer). |
| POST | `/api/v1/oweme/accounts/{key}/entries/{kind}/` | App „oweme“ | Eine Bewegung erfassen; `kind` ist `given` oder `returned`. |
| PUT | `/api/v1/oweme/accounts/{key}/plan/` | App „oweme“ | Rückzahlungsplan setzen (`mode`: rate, anzahl, ende, frei); ersetzt einen bestehenden. |
| DELETE | `/api/v1/oweme/accounts/{key}/plan/` | App „oweme“ | Den Plan beenden; die Bewegungen bleiben. |
| PATCH | `/api/v1/oweme/entries/{id}/` | App „oweme“ | Datum, Bezeichnung oder Betrag einer Bewegung ändern (die Art nicht). |
| DELETE | `/api/v1/oweme/entries/{id}/` | App „oweme“ | Eine Bewegung löschen; liefert den neuen Saldo. |

## Health

| Methode | Pfad | Zugriff | Zweck |
|---|---|---|---|
| POST | `/api/v1/health/calorie-entries/{id}/estimate-nutrients/` | App „health“, Health eingeschaltet, abo_ai | Schätzt die Nährwerte einer Mahlzeit. ``DELETE`` nimmt eine Schätzung wieder zurück. |
| DELETE | `/api/v1/health/calorie-entries/{id}/estimate-nutrients/` | App „health“, Health eingeschaltet, abo_ai | Schätzt die Nährwerte einer Mahlzeit. ``DELETE`` nimmt eine Schätzung wieder zurück. |
| POST | `/api/v1/health/calories/{tag}/estimate-nutrients/` | App „health“, Health eingeschaltet, abo_ai | Schätzt alles an diesem Tag, was noch keine Nährwerte hat. |
| GET | `/api/v1/health/profile/` | App „health“ | Stammdaten, Kennzahlen (Grundumsatz, Tagesbedarf, BMI), Zielgewicht und die Auswahllisten. |
| PATCH | `/api/v1/health/profile/` | App „health“ | Stammdaten, Zielgewicht und Trainingsschalter ändern (beim ersten Mal alle Pflichtfelder). |
| GET | `/api/v1/health/trainings/` | App „health“, Health eingeschaltet, Training eingeschaltet | ``GET ?from=&to=`` (Datum, optional), neueste zuerst; ``POST`` erfasst eine Einheit. |
| POST | `/api/v1/health/trainings/` | App „health“, Health eingeschaltet, Training eingeschaltet | ``GET ?from=&to=`` (Datum, optional), neueste zuerst; ``POST`` erfasst eine Einheit. |
| GET | `/api/v1/health/trainings/{id}/` | App „health“, Health eingeschaltet, Training eingeschaltet | Eine Trainingseinheit. |
| PATCH | `/api/v1/health/trainings/{id}/` | App „health“, Health eingeschaltet, Training eingeschaltet | Eine Einheit ändern; der Verbrauch wird neu gerechnet, das Langzeitziel nachgezogen. |
| DELETE | `/api/v1/health/trainings/{id}/` | App „health“, Health eingeschaltet, Training eingeschaltet | Eine Einheit löschen; ihr Anteil am Langzeitziel entfällt. |
| GET | `/api/v1/health/training-templates/` | App „health“, Health eingeschaltet, Training eingeschaltet | Trainingsvorlagen. |
| POST | `/api/v1/health/training-templates/` | App „health“, Health eingeschaltet, Training eingeschaltet | Eine Trainingsvorlage anlegen. |
| PATCH | `/api/v1/health/training-templates/{id}/` | App „health“, Health eingeschaltet, Training eingeschaltet | Eine Trainingsvorlage ändern. |
| DELETE | `/api/v1/health/training-templates/{id}/` | App „health“, Health eingeschaltet, Training eingeschaltet | Eine Trainingsvorlage löschen; erfasste Einheiten bleiben. |
| POST | `/api/v1/health/training-templates/{id}/apply/{tag}/` | App „health“, Health eingeschaltet, Training eingeschaltet | Die Vorlage als Einheit für den Tag übernehmen. |
| GET | `/api/v1/health/training-goals/` | App „health“, Health eingeschaltet, Training eingeschaltet | Wochenziele (je Sportart oder für alle), Langzeitziel mit Stand und die Auswahllisten. |
| PUT | `/api/v1/health/training-goals/` | App „health“, Health eingeschaltet, Training eingeschaltet | ``weekly`` (Liste) ersetzt alle Wochenziele, ``long_term`` (Objekt) setzt Ziel, Einzahlung und Satz. Was fehlt, bleibt unverändert. |
| GET | `/api/v1/health/training/review/` | App „health“, Health eingeschaltet, Training eingeschaltet | ``GET`` wie bisher, dazu der Text für die Anfrage. ``POST`` holt den formulierten Wochenrückblick. |
| POST | `/api/v1/health/training/review/` | App „health“, Health eingeschaltet, Training eingeschaltet | ``GET`` wie bisher, dazu der Text für die Anfrage. ``POST`` holt den formulierten Wochenrückblick. |
| GET | `/api/v1/health/traffic-light/` | App „health“, Health eingeschaltet | ``GET`` wie bisher, dazu der Text für die Anfrage. ``POST`` holt die Einschätzung der Woche. |
| POST | `/api/v1/health/traffic-light/` | App „health“, Health eingeschaltet | ``GET`` wie bisher, dazu der Text für die Anfrage. ``POST`` holt die Einschätzung der Woche. |
| GET | `/api/v1/health/labs/` | App „health“, Health eingeschaltet | ``GET``: je Bezeichnung die neueste Messung, die davor und der Unterschied. ``POST``: Messung eintragen. |
| POST | `/api/v1/health/labs/` | App „health“, Health eingeschaltet | ``GET``: je Bezeichnung die neueste Messung, die davor und der Unterschied. ``POST``: Messung eintragen. |
| GET | `/api/v1/health/labs/trend/` | App „health“, Health eingeschaltet | Alle Messungen einer Bezeichnung (``?name=``), älteste zuerst, mit dem Bereich jeder Messung. |
| GET | `/api/v1/health/labs/analysis/` | App „health“, Health eingeschaltet | ``GET`` wie bisher, dazu der Text und die Prüfsumme für die nächste Anfrage. ``POST`` holt die Einordnung. |
| POST | `/api/v1/health/labs/analysis/` | App „health“, Health eingeschaltet | ``GET`` wie bisher, dazu der Text und die Prüfsumme für die nächste Anfrage. ``POST`` holt die Einordnung. |
| GET | `/api/v1/health/labs/{id}/` | App „health“, Health eingeschaltet | Eine Messung mit Einstufung. |
| PATCH | `/api/v1/health/labs/{id}/` | App „health“, Health eingeschaltet | Eine Messung ändern; der Arztbesuch bleibt erhalten. |
| DELETE | `/api/v1/health/labs/{id}/` | App „health“, Health eingeschaltet | Eine Messung löschen. |
| POST | `/api/v1/health/checks/{id}/labs/` | App „health“, Health eingeschaltet | Eine Messung am Arztbesuch erfassen: dieselbe Tabelle wie die Übersicht, mit dem Besuch als Herkunft. |
| GET | `/api/v1/health/checks/` | App „health“, Health eingeschaltet | Arztbesuche, neueste zuerst, mit Fälligkeit. |
| POST | `/api/v1/health/checks/` | App „health“, Health eingeschaltet | Einen Arztbesuch anlegen; optional als Folgetermin eines früheren (`follows_up_id`). |
| GET | `/api/v1/health/checks/{id}/` | App „health“, Health eingeschaltet | Ein Arztbesuch mit Dokumenten, Kommentaren und Laborwerten. |
| PATCH | `/api/v1/health/checks/{id}/` | App „health“, Health eingeschaltet | Einen Besuch ändern (`duration_hours` ersetzt das Ende). |
| DELETE | `/api/v1/health/checks/{id}/` | App „health“, Health eingeschaltet | Einen Besuch samt Dokumentdateien löschen; Laborwerte bleiben. |
| POST | `/api/v1/health/checks/{id}/documents/` | App „health“, Health eingeschaltet | Datei hochladen (multipart): ``file``, ``title``, optional ``category_id``. |
| POST | `/api/v1/health/checks/{id}/comments/` | App „health“, Health eingeschaltet | Einen Kommentar zum Besuch anlegen. |
| GET | `/api/v1/health/documents/{ref}/` | App „health“, Health eingeschaltet | ``GET`` liefert die Datei (per Schlüssel), ``DELETE`` löscht sie (per Nummer). |
| DELETE | `/api/v1/health/documents/{ref}/` | App „health“, Health eingeschaltet | ``GET`` liefert die Datei (per Schlüssel), ``DELETE`` löscht sie (per Nummer). |
| DELETE | `/api/v1/health/comments/{id}/` | App „health“, Health eingeschaltet | Einen Kommentar löschen. |
| GET | `/api/v1/health/document-categories/` | App „health“, Health eingeschaltet | Eigene Dokumentkategorien. |
| POST | `/api/v1/health/document-categories/` | App „health“, Health eingeschaltet | Eine Dokumentkategorie anlegen (je Benutzer eindeutig). |
| DELETE | `/api/v1/health/document-categories/{id}/` | App „health“, Health eingeschaltet | Eine Kategorie löschen; die Dokumente bleiben. |
| GET | `/api/v1/health/vaccinations/` | App „health“, Health eingeschaltet | Das Impfbuch mit Fälligkeiten. |
| POST | `/api/v1/health/vaccinations/` | App „health“, Health eingeschaltet | Eine Impfung eintragen. |
| PATCH | `/api/v1/health/vaccinations/{id}/` | App „health“, Health eingeschaltet | Eine Impfung ändern. |
| DELETE | `/api/v1/health/vaccinations/{id}/` | App „health“, Health eingeschaltet | Eine Impfung löschen. |
| POST | `/api/v1/health/profile/enable/` | App „health“ | Health ein- oder ausschalten: ``{"enabled": true\|false}``. Einschalten geht nur mit vollständigen Stammdaten. |
| GET | `/api/v1/health/weights/` | App „health“ | Der Gewichtsverlauf, neueste zuerst. |
| POST | `/api/v1/health/weights/` | App „health“ | Gewicht eintragen (20–400 kg, nicht in der Zukunft); am selben Tag wird der Wert ersetzt (200 statt 201). |
| DELETE | `/api/v1/health/weights/{id}/` | App „health“ | Einen Wiegeeintrag löschen. |
| GET | `/api/v1/health/foods/suggest/` | App „health“, Health eingeschaltet | Vorschläge aus dem Bundeslebensmittelschlüssel (lokale CSV, kein Netz): ``?q=`` ab zwei Zeichen, ``?limit=``. |
| GET | `/api/v1/health/calories/` | App „health“, Health eingeschaltet | Monatsübersicht des Kalorientrackers (`?month=JJJJ-MM`). |
| GET | `/api/v1/health/calories/{tag}/` | App „health“, Health eingeschaltet | Ein Tag mit Einträgen und Bilanz. Übernimmt beim ersten Aufruf die Speiseplan-Vorschläge (`seeded_count`). |
| POST | `/api/v1/health/calories/{tag}/entries/` | App „health“, Health eingeschaltet | Eine Mahlzeit erfassen, optional mit Nährwerten je Portion. |
| PATCH | `/api/v1/health/calorie-entries/{id}/` | App „health“, Health eingeschaltet | Portionen, Titel, kcal oder Nährwerte eines Eintrags ändern. |
| DELETE | `/api/v1/health/calorie-entries/{id}/` | App „health“, Health eingeschaltet | Einen Eintrag löschen; ein gelöschter Speiseplan-Vorschlag bleibt gelöscht. |
| GET | `/api/v1/health/meal-templates/` | App „health“, Health eingeschaltet | Mahlzeitvorlagen. |
| POST | `/api/v1/health/meal-templates/` | App „health“, Health eingeschaltet | Eine Mahlzeitvorlage anlegen. |
| PATCH | `/api/v1/health/meal-templates/{id}/` | App „health“, Health eingeschaltet | Eine Vorlage ändern. |
| DELETE | `/api/v1/health/meal-templates/{id}/` | App „health“, Health eingeschaltet | Eine Vorlage löschen; erfasste Einträge bleiben. |
| POST | `/api/v1/health/meal-templates/{id}/apply/{tag}/` | App „health“, Health eingeschaltet | Die Vorlage als Eintrag für den Tag übernehmen (eine Kopie). |
