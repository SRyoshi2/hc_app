# HomeCenter Client (Android)

Android-App für bestehende HomeCenter-Instanzen („Family Manager“ / „Office Manager“).
Die Instanz wird in der App eingetragen (Internet oder lokales Netz, mehrere möglich);
die Anmeldung erfolgt mit Benutzername und Passwort. Die App spricht die Schnittstelle
`/api/v1/` von HomeCenter (Branch `feature/mobile-api`), siehe `docs/API_KONZEPT.md`.

## Stand

| Bereich | Stand |
|---|---|
| Instanzen verwalten, Verbindungsprüfung (`/meta/`, Versionsabgleich) | umgesetzt |
| Anmeldung mit Token (im Android Keystore verschlüsselt), Abmelden | umgesetzt |
| Einkaufsliste (Listen, Artikel, Abhaken, Löschen) | umgesetzt |
| Rezepte lesen (eigene, entdecken, Suche), Rezeptdetail, Portionen wählen und Zutaten in eine bestehende oder neue Einkaufsliste übernehmen, öffentliche Rezepte kopieren | umgesetzt (API Phase 2) |
| Rezepte anlegen, bearbeiten, löschen: Titel, Portionen, Zeiten, Schwierigkeit, Beschreibung, Kategorien (auch neu anlegen), öffentlich/privat, Zutaten mit Abschnitten, Schritte, Nährwerte, Titelbild | umgesetzt (API Phase 3) |
| Rezept-Galerie (weitere Bilder), Video, Quelle, Formatierung (Fett, Listen) beim Bearbeiten | nicht in der App; bleibt auf dem Server unverändert |
| OweMe: Übersicht (bekomme/schulde/Saldo, fällige Konten), Konten anlegen/ändern/löschen, Kontoauszug mit Stand je Zeile, Geld gegeben und Rückzahlungen erfassen/ändern/löschen, Rückzahlungsplan (Ratenhöhe, Anzahl, Enddatum oder einzeln; ansehen, ersetzen, beenden) | umgesetzt (API Phase 4) |
| OweMe: Person aus Adressbuch oder HomeCenter-Benutzer wählen, Konto teilen, Export | nicht in der App (Name wird frei eingegeben) |
| Health: Einrichten (Profil, Gewicht, einschalten), Übersicht (Heute, Kennzahlen, Ziel), Kalorientracker (Tag mit Bilanz und Ampel, Monatskalender, Mahlzeiten erfassen/ändern/löschen), Portionsrechner (Werte je 100 g + gegessene Gramm → kcal und Nährwerte), Gewicht mit Zielgewicht, Mahlzeitvorlagen | umgesetzt (API Phase 5) |
| Lebensmittel-Vorschläge beim Tippen (Bundeslebensmittelschlüssel) | App fertig, wartet auf den Endpunkt `GET /health/foods/` (siehe Konzept, Abschnitt 11) |
| Health: Arztbesuche mit Dokumenten (Hochladen/Herunterladen) und Kommentaren, Impfbuch, Laborwerte mit Verlauf, Training (Einheiten, Vorlagen, Wochen-/Langzeitziele, Rückblick), Ernährungsampel; Lebensmittelvorschläge (BLS) im Kalorientracker | umgesetzt (API Phase 6) |
| Teilen von Einkaufslisten und OweMe-Konten (Benutzer wählen), Rezepte anbieten, Angebote übernehmen/ablehnen, OweMe-CSV-Export, Meldungen (Glocke auf der Startseite, Abruf beim Öffnen) | umgesetzt (API Phase 7) |

**Nicht geprüft:** Weder `core` noch `app` wurden bisher kompiliert oder getestet. Die
Entwicklungsumgebung, in der der Code entstand, erreicht Maven Central und Google Maven nicht
(429 bzw. 403). Erster Schritt auf einem normalen Rechner: `./gradlew :core:test`, dann
`./gradlew :app:assembleDebug`. Die Tests in `core/src/test` sind geschrieben, aber noch nie gelaufen.

## Aufbau

- `core/` – reines Kotlin/JVM, ohne Android: API-Client (OkHttp), Modelle, Adressprüfung
  (`InstanceUrl`), Versionsabgleich (`Compatibility`), Fehlertypen. Tests mit MockWebServer.
- `app/` – Android (Jetpack Compose, ein `AppViewModel`): Instanzliste, Einrichten, Anmeldung,
  Startseite je nach freigeschalteten Apps, Einkaufsliste.

Das Modul `app` wird nur eingebunden, wenn ein Android SDK gefunden wird (`ANDROID_HOME`,
`ANDROID_SDK_ROOT` oder `local.properties`); sonst lässt sich `core` allein bauen.

## Design

Neutrale Flächen und Text folgen der Web-App (`home/static/css/hc_theme.css`), Hell und Dunkel nach Systemeinstellung.
**Jede App hat ein eigenes Farbset, abgeleitet aus ihrem Icon** (`app/.../ui/theme/HcTheme.kt`, `Welt` und `AppFarben`):

| App | Kopfzeile (Icon-Farbe) | Akzent hell / dunkel | Zweite Farbe |
|---|---|---|---|
| Einkaufsliste | Mulberry `#ca5e93`, weiße Schrift | `#ad507e` / `#cc6396` | – |
| Rezeptverwaltung | Orange `#fd7e14`, dunkle Schrift | `#b1580e` / `#fd7e14` | – |
| OweMe | Petrol `#2c5f59`, weiße Schrift | `#2c5f59` / `#50c0b0` | Ocker `#b0762a` / `#d8a858` |
| Health | Petrol `#0f766e`, weiße Schrift | `#0f766e` / `#14b8a6` | Herz-Türkis `#14b8a6` |
| HomeCenter (Instanzen, Anmeldung, Startseite) | neutral | Rot `#dc3545` | – |

Die Kopfzeile trägt genau die Icon-Farbe. Der Akzent (Knöpfe, Links, Auswahl) ist im Hellmodus so weit abgedunkelt, dass
weiße Schrift darauf und der Akzent als Text mindestens 4,5:1 Kontrast haben. Orange ist die Ausnahme beim Titel: mit
Weiß nur 2,6:1, deshalb dunkle Schrift. Artikel tragen eine Kante in einer der neun Kategoriefarben (`--hc-cat-1..9`);
das Web verteilt sie per Hash auf den Namen, der Hash der App ist ein anderer.

## Release und Updates (fester Signaturschlüssel)

Android installiert ein Update nur, wenn es mit demselben Schlüssel signiert ist wie die installierte App.
Debug-Builds nutzen einen Schlüssel, der auf jedem Rechner anders ist; für Updates von jedem Rechner und von
GitHub aus braucht es deshalb einen festen Release-Schlüssel. **Den erzeugen Sie selbst** und legen ihn nie ins
Repository. Geht er verloren, lässt sich die App nie wieder aktualisieren, nur neu installieren: sichern Sie
Datei und Passwörter an einem zweiten Ort.

1. **Schlüssel erzeugen** (einmalig), im Terminal im Ordner, in dem die Datei liegen soll:
   ```
   keytool -genkeypair -v -keystore hc-release.jks -alias hc -keyalg RSA -keysize 4096 -validity 36500
   ```
   `keytool` liegt im JDK von Android Studio (`jbr/bin`). Oder in Android Studio:
   `Build → Generate Signed App Bundle / APK… → APK → Create new…`.
2. **Lokal bauen:** `keystore.properties.example` nach `keystore.properties` kopieren und ausfüllen (Pfad,
   Passwörter, Alias), dann `./gradlew :app:assembleRelease`. Das APK liegt in `app/build/outputs/apk/release/`.
3. **GitHub:** Unter *Settings → Secrets and variables → Actions* vier Secrets anlegen:
   | Secret | Inhalt |
   |---|---|
   | `HC_KEYSTORE_BASE64` | die Schlüsseldatei als Base64-Text |
   | `HC_KEYSTORE_PASSWORD` | Passwort der Schlüsseldatei |
   | `HC_KEY_ALIAS` | Alias, im Beispiel `hc` |
   | `HC_KEY_PASSWORD` | Passwort des Schlüssels |

   Base64 erzeugen: PowerShell `[Convert]::ToBase64String([IO.File]::ReadAllBytes("hc-release.jks")) | Set-Clipboard`,
   Linux/macOS `base64 -w0 hc-release.jks`.
4. **Veröffentlichen:** Ein Tag wie `v0.2.0` pushen. `.github/workflows/release.yml` führt die Tests aus, baut das
   signierte APK und hängt es an ein GitHub-Release. Alternativ unter *Actions → Release-APK → Run workflow* manuell
   starten; das APK liegt dann am Lauf zum Download. Die `versionCode` zählt GitHub je Lauf hoch.

Eine per Android Studio installierte Debug-App trägt eine andere Signatur: **einmalig deinstallieren**, bevor das
erste Release-APK installiert wird. Danach lassen sich Releases von jedem Rechner und von GitHub aus aktualisieren.
Der SHA-256-Fingerabdruck des Zertifikats steht im Log des Schritts „APK prüfen und benennen" und muss bei jedem
Release derselbe sein.

## Voraussetzung auf der Instanz

Die Instanz braucht `mobile_api` (HomeCenter, Branch `feature/mobile-api`, Phase 0 bis 7).
Ältere Instanzen melden „unterstützt die App noch nicht“.
