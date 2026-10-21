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
| Rezepte, Rezept → Einkaufsliste, OweMe, Health | wartet auf API-Phasen 2–6 |

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

## Sicherheitsregeln

- `https://` ist Standard. `http://` nimmt die App nur für lokale Adressen an
  (`localhost`, 10.x, 172.16–31.x, 192.168.x, 169.254.x, `.local`, `.lan`, Namen ohne Punkt).
  Das Android-Manifest erlaubt Klartext grundsätzlich, weil sich private Bereiche dort nicht
  freigeben lassen; die Prüfung geschieht vor dem Senden des Passworts in `InstanceUrl`.
- Tokens liegen verschlüsselt (AES-256-GCM, Schlüssel im Keystore), nie im Klartext.
- Weiterleitungen folgt der Client nicht.

## Voraussetzung auf der Instanz

Die Instanz braucht `mobile_api` (HomeCenter, Branch `feature/mobile-api`, Phase 0 und 1).
Ältere Instanzen melden „unterstützt die App noch nicht“.
