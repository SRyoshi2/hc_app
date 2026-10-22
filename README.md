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
| Rezepte anlegen und bearbeiten | wartet auf API-Phase 3 |
| OweMe, Health | wartet auf API-Phasen 4–6 |

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

Die Farben folgen der Web-App (`home/static/css/hc_theme.css`, OweMe-Vorlagen), Hell und Dunkel nach
Systemeinstellung. Umgesetzt in `app/.../ui/theme/HcTheme.kt`:

| Welt | Farbe | Apps |
|---|---|---|
| Küche | Grün `#2e7d4f` (dunkel `#57b57c`) | Einkaufsliste, Rezepte |
| Health | Petrol `#0f766e` / Kopf `#0f3b36` (dunkel `#2dd4bf` / `#16302c`) | Health |
| OweMe | Petrol-Kopf `#123a36`, „bekomme“ `#1f7a6e`, „schulde“ `#b0762a` | OweMe |
| HomeCenter | Rot `#dc3545` | Instanzen, Anmeldung, Startseite |

Artikel tragen eine Kante in einer der neun Kategoriefarben (`--hc-cat-1..9`). Das Web verteilt sie per
Hash auf den Namen; der Hash der App ist nicht derselbe, dieselbe Kategorie kann daher eine andere Farbe haben.
Ändern sich im Web die Farben, `HcTheme.kt` nachziehen.

## Sicherheitsregeln

- `https://` ist Standard. `http://` nimmt die App nur für lokale Adressen an
  (`localhost`, 10.x, 172.16–31.x, 192.168.x, 169.254.x, `.local`, `.lan`, Namen ohne Punkt).
  Das Android-Manifest erlaubt Klartext grundsätzlich, weil sich private Bereiche dort nicht
  freigeben lassen; die Prüfung geschieht vor dem Senden des Passworts in `InstanceUrl`.
- Tokens liegen verschlüsselt (AES-256-GCM, Schlüssel im Keystore), nie im Klartext.
- Weiterleitungen folgt der Client nicht.

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

Die Instanz braucht `mobile_api` (HomeCenter, Branch `feature/mobile-api`, Phase 0 bis 2).
Ältere Instanzen melden „unterstützt die App noch nicht“.
