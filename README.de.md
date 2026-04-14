![BatterySentinel Logo](app/src/main/res/logos/batterysentinel_logo_dunkel_git.png)

*[Read this in English](README.md)*

BatterySentinel ist absichtlich ein **äußerst kompaktes** und ressourcenschonendes Android-Projekt. Es hat ein einfaches und klares Ziel: Es überwacht den Akkustand im Hintergrund und gibt Warnungen bei bestimmten, benutzerdefinierten Akku-Grenzwerten aus – und das unter **minimalstem Energieverbrauch**.

## 📥 Download
[**BatterySentinel_app-release.apk herunterladen**](https://github.com/matthili/BatterySentinel/releases/latest/download/BatterySentinel_app-release.apk)

## 📱 Installation auf dem Smartphone
Da diese App nicht über den Google Play Store vertrieben wird, müssen Sie die APK-Datei manuell installieren (sogenanntes "Sideloading").
1. Laden Sie die `BatterySentinel_app-release.apk` über den obigen Link direkt auf Ihr Android-Gerät herunter (z.B. über Ihren mobilen Browser).
2. Tippen Sie auf die heruntergeladene Datei, um sie zu öffnen.
3. Ihr Smartphone wird vermutlich eine Sicherheitswarnung anzeigen in der Art: **"Aus Sicherheitsgründen kannst du auf dem Smartphone keine unbekannten Apps aus dieser Quelle installieren."**
4. Tippen Sie in diesem Dialogfeld auf **Einstellungen** und aktivieren Sie den Schalter bei **"Dieser Quelle vertrauen"** (Zulassen aus dieser Quelle).
5. Gehen Sie danach einen Schritt zurück und tippen Sie auf **Installieren**.
6. Die App ist nun installiert! Beim ersten Öffnen fordert die App zwei Dinge an: Zum einen die Berechtigung für Benachrichtigungen und zum anderen bittet sie darum, von den systemeigenen Akku-Optimierungen ausgenommen zu werden. Bitte stimmen Sie beidem zu. Die Akku-Ausnahme ist essenziell, damit Android den Überwachungsprozess nicht versehentlich in den Tiefschlaf ("Doze-Mode") versetzt.

## 🔋 Warum BatterySentinel?
Moderne Betriebssysteme beenden oft Prozesse im Hintergrund, um Akku zu sparen. BatterySentinel verzichtet komplett auf durchgehend laufende Hintergrunddienste (Foreground Services). Stattdessen reagiert die App nativ auf System-Broadcasts und nutzt ein sehr leichtgewichtiges Sicherheitsnetz über den Android WorkManager.
Das Resultat: Sie erhalten zuverlässig Warnungen, wenn Ihr Akku z. B. 40% oder 20% erreicht, ohne dass die Überwachungs-App selbst wertvollen Strom verbraucht.

## 🚀 Features
- **Null Batterie-Drain:** Verwendet nativ `ACTION_BATTERY_CHANGED` Broadcasts, anstatt permanent den Akku auszulesen.
- **Eigene Alarme:** Beliebig viele eigene Schwellenwerte (%) und Meldungen können definiert werden.
- **Neustart-resistent:** Die Überwachung aktiviert sich nach einem Geräteneustart (Reboot) ganz von selbst wieder.
- **Modernes UI:** Komplett in Kotlin und Jetpack Compose geschrieben.
- **DataStore:** Speichert alle Ihre Alarme sicher und asynchron über den modernen AndroidX DataStore.

## 🛠️ Selbst kompilieren
1. Repository klonen.
2. In Android Studio öffnen.
3. Projekt ganz normal bauen oder über `Build > Generate Signed Bundle / APK...` eine eigene APK generieren.

## 📝 Lizenz
Dieses Projekt ist Open-Source. Fühlen Sie sich frei, den Code nach Ihren Wünschen zu verwenden und anzupassen!
