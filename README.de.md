![BatterySentinel Logo](app/logos/batterysentinel_logo_dunkel_git.png)

[![Version](https://img.shields.io/badge/version-2.6-blue.svg)](https://github.com/matthili/BatterySentinel/releases)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-29+-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=29)
[![Kotlin](https://img.shields.io/badge/kotlin-100%25-orange.svg)](https://kotlinlang.org)
[![Firebase](https://img.shields.io/badge/Firebase-Multi--Device-yellow.svg)](https://firebase.google.com)

*[Read this in English](README.md)*

BatterySentinel ist absichtlich ein **äußerst kompaktes** und ressourcenschonendes Android-Projekt. Es hat ein einfaches und klares Ziel: Es überwacht den Akkustand im Hintergrund und gibt Warnungen bei bestimmten, benutzerdefinierten Akku-Grenzwerten aus – und das unter **minimalstem Energieverbrauch**.

BatterySentinel unterstützt **Multi-Device Synchronisation**, sodass sich Ihre Geräte gegenseitig warnen können, wenn der Akku zur Neige geht – und hält sich über einen **integrierten GitHub-Updater** selbst aktuell, ganz ohne App-Store!

## 📥 Download
[**BatterySentinel_app-release.apk herunterladen**](https://github.com/matthili/BatterySentinel/releases/latest/download/BatterySentinel_app-release.apk)

## 📱 Installation auf dem Smartphone
Da diese App nicht über den Google Play Store vertrieben wird, müssen Sie die APK-Datei manuell installieren (sogenanntes "Sideloading").
1. Laden Sie die `BatterySentinel_app-release.apk` über den obigen Link direkt auf Ihr Android-Gerät herunter (z.B. über Ihren mobilen Browser).
2. Tippen Sie auf die heruntergeladene Datei, um sie zu öffnen.
3. Ihr Smartphone wird vermutlich eine Sicherheitswarnung anzeigen in der Art: **"Aus Sicherheitsgründen kannst du auf dem Smartphone keine unbekannten Apps aus dieser Quelle installieren."**
4. Tippen Sie in diesem Dialogfeld auf **Einstellungen** und aktivieren Sie den Schalter bei **"Dieser Quelle vertrauen"** (Zulassen aus dieser Quelle).
5. Gehen Sie danach einen Schritt zurück und tippen Sie auf **Installieren**.
6. Die App ist nun installiert! Beim ersten Öffnen fordert die App Berechtigungen für Benachrichtigungen und eine Ausnahme von den Akku-Optimierungen an. **Bitte stimmen Sie beidem zu.**

## 🔋 Warum BatterySentinel?
Moderne Betriebssysteme beenden oft Prozesse im Hintergrund, um Akku zu sparen. BatterySentinel verzichtet komplett auf durchgehend laufende Hintergrunddienste. Stattdessen reagiert die App nativ auf System-Broadcasts und nutzt ein sehr leichtgewichtiges Sicherheitsnetz über den Android WorkManager.
Das Resultat: Sie erhalten zuverlässig Warnungen, ohne dass die Überwachungs-App selbst wertvollen Strom verbraucht.

## 🚀 Features
- **Null Batterie-Drain:** Verwendet nativ `ACTION_BATTERY_CHANGED` Broadcasts, anstatt permanent den Akku auszulesen.
- **Adaptive Doze-Erkennung:** Aktiviert vollautomatisch einen speziellen Backup-Timer, wenn der Tiefschlaf-Modus von Android (Doze) die normalen Hintergrundaufgaben blockiert.
- **Multi-Device Sync:** Geräte, die mit demselben Google-Konto verbunden sind, können sich gegenseitig Akku-Warnungen schicken.
- **Integrierter Updater:** Prüft die GitHub-Releases und installiert neue Versionen direkt in der App – kein Play Store nötig.
- **Diagnose-Log:** Verfügt über einen integrierten, robusten Log-Viewer auf Textbasis, um Hintergrundaktivitäten und Schwellenwert-Prüfungen sicher direkt in der App zu überprüfen.
- **EU-Infrastruktur:** Die von uns vorkompilierte Release-APK nutzt für die Cloud-Funktionen **ausschließlich Server innerhalb der Europäischen Union (EU)** für maximalen Datenschutz.
- **Eigene Alarme:** Beliebig viele eigene Schwellenwerte (%) und Meldungen können definiert werden.
- **Neustart-resistent:** Die Überwachung aktiviert sich nach einem Geräteneustart (Reboot) ganz von selbst wieder.
- **Modernes UI:** Komplett in Kotlin und Jetpack Compose geschrieben.
- **DataStore:** Speichert alle Ihre Alarme sicher und asynchron über den modernen AndroidX DataStore.

## 🧠 Architektur im Detail
Möchten Sie wissen, wie BatterySentinel eine zuverlässige Hintergrundausführung ohne Akkuverbrauch erreicht, oder wie die neue Multi-Device Synchronisation unter der Haube funktioniert?

👉 **[Lesen Sie die Architektur im Detail (Deep Dive)](ARCHITECTURE.de.md)**

## 🛠️ Selbst kompilieren
1. Repository klonen.
2. In Android Studio öffnen.
3. Projekt ganz normal bauen oder über `Build > Generate Signed Bundle / APK...` eine eigene APK generieren.

## 📝 Lizenz
Dieses Projekt ist unter der [AGPL-3.0 Lizenz](LICENSE) lizenziert.
