# BatterySentinel

*[Lesen Sie dies auf Deutsch (German)](README.de.md)*

BatterySentinel is an intentionally **highly compact** and lightweight Android application designed with a single, clear objective: monitoring your device's battery in the background and alerting you when specific, customized battery thresholds are reached, all while consuming the absolute minimum amount of energy.

## 📥 Download
[**Download the latest BatterySentinel_app-release.apk**](https://github.com/matthili/BatterySentinel/releases/latest/download/BatterySentinel_app-release.apk)

## 📱 How to Install
Since this app is not distributed via the Google Play Store, you will need to "sideload" the APK file onto your Android device.
1. Download the `BatterySentinel_app-release.apk` file from the link above directly to your Android device (e.g., using your mobile browser).
2. Tap on the downloaded file to open it.
3. Your device will likely show a security warning: **"For your security, your phone is not allowed to install unknown apps from this source."**
4. Tap **Settings** on that prompt, and toggle on **"Allow from this source"**.
5. Press the back button and tap **Install**.
6. The app is now installed! Upon your first launch, the app will request two permissions: First, to send notifications, and second, to be exempt from Android's strict battery optimizations. Please grant both. The battery exemption ensures the system does not force the underlying monitor into deep sleep ("Doze Mode").

## 🔋 Why BatterySentinel?
Modern operating systems often kill background services to save battery life. BatterySentinel works natively with Android's system broadcasts and a lightweight WorkManager safety-net, entirely avoiding the need for permanent background services. 
This means you get reliable battery alerts when your phone hits 80%, 40%, 20%, etc., without the app itself draining your battery to provide those alerts.

## 🚀 Features
- **Zero-Drain Architecture:** Utilizes system's native `ACTION_BATTERY_CHANGED` broadcasting instead of constantly polling the battery sensor.
- **Custom Alarms:** Set up any number of custom battery percentage alerts.
- **Boot Persistence:** Automatically survives device reboots. 
- **Modern UI:** Built fully in Kotlin with Jetpack Compose.
- **DataStore:** Employs modern AndroidX DataStore for asynchronous, safe settings persistence.

## 🛠️ Build it yourself
1. Clone this repository.
2. Open the project in Android Studio.
3. Build the project or directly generate a Signed APK from `Build > Generate Signed Bundle / APK...`

## 📝 License
This project is open-source. Feel free to use and adapt the code as you need!
