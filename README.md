# SnoreNudge

An on-device Android + Wear OS app that detects likely snoring in real-time using the phone microphone and instantly vibrates the Samsung Galaxy Watch Ultra to wake/nudge the wearer.

**Package:** `com.chrisirlam.snorenudge`
**Target devices:** Samsung Galaxy S26 Ultra (phone) + Samsung Galaxy Watch Ultra (Wear OS)

---

## What it does

1. **Phone app** — runs a foreground service overnight that continuously captures audio from the phone microphone, analyses it locally (no cloud, no audio uploads), and sends a vibrate command to the paired watch when snoring is detected with sufficient confidence.

2. **Watch app** — listens for the vibrate command from the phone via the Wearable Message API and triggers a strong repeating vibration pattern, strong enough to prompt the wearer to roll over.

---

## Project structure

```
SnoreNudge/
├── app/                            Phone module
│   └── src/main/java/com/chrisirlam/snorenudge/
│       ├── MainActivity.kt
│       ├── audio/
│       │   ├── AudioCaptureManager.kt      PCM capture via AudioRecord
│       │   ├── AudioFrameProcessor.kt      Normalisation + RMS
│       │   ├── SnoreFeatureExtractor.kt    FFT + spectral features
│       │   ├── SnoreClassifier.kt          Rule-based classifier (swappable with TFLite)
│       │   └── TriggerDecisionEngine.kt    Rolling confidence + cooldown state machine
│       ├── data/
│       │   ├── SettingsDataStore.kt        All settings via Jetpack DataStore
│       │   ├── SnoreDatabase.kt            Room database
│       │   ├── SnoreEventDao.kt
│       │   └── SnoreEvent.kt              Event entity (no raw audio stored)
│       ├── navigation/
│       │   ├── Screen.kt
│       │   └── NavGraph.kt
│       ├── service/
│       │   ├── SnoreMonitoringService.kt   Foreground service (microphone type)
│       │   └── BootReceiver.kt
│       ├── ui/
│       │   ├── theme/Theme.kt             Dark Compose theme
│       │   └── screens/
│       │       ├── HomeScreen.kt
│       │       ├── LiveStatusScreen.kt
│       │       ├── HistoryScreen.kt
│       │       ├── SettingsScreen.kt
│       │       └── DebugScreen.kt
│       ├── viewmodel/
│       │   ├── MainViewModel.kt
│       │   ├── HistoryViewModel.kt
│       │   ├── SettingsViewModel.kt
│       │   └── LiveStatusViewModel.kt
│       └── watch/
│           ├── WatchCommandSender.kt       Wearable Message API
│           └── PhoneMessageListenerService.kt
│
├── wear/                           Wear OS module
│   └── src/main/java/com/chrisirlam/snorenudge/wear/
│       ├── WatchMainActivity.kt
│       ├── WatchMessageListenerService.kt  WearableListenerService
│       ├── WatchVibrationController.kt
│       ├── viewmodel/WatchMainViewModel.kt
│       └── ui/WatchMainScreen.kt
│
├── gradle/libs.versions.toml       Version catalog
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Prerequisites

- **Android Studio Hedgehog (2023.1.1)** or later
- **JDK 17**
- Android SDK 34 installed
- Wear OS SDK (installed via SDK Manager → Platforms → Android 13 / Wear OS 4)
- A Samsung Galaxy Watch Ultra paired with your phone, or a Wear OS emulator

---

## Build APKs

### 1. Clone and open

```bash
git clone <repo-url>
```

Open the project root in Android Studio. Let Gradle sync complete.

### 2. Build debug APKs (quickest)

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
```

Output locations:
- Phone APK: `app/build/outputs/apk/debug/app-debug.apk`
- Watch APK: `wear/build/outputs/apk/debug/wear-debug.apk`

### 3. Build release APKs (requires signing)

Create a keystore:
```bash
keytool -genkey -v -keystore snorenudge.jks -alias snorenudge -keyalg RSA -keysize 2048 -validity 10000
```

Add signing config to `app/build.gradle.kts` and `wear/build.gradle.kts`:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("../snorenudge.jks")
        storePassword = "your_password"
        keyAlias = "snorenudge"
        keyPassword = "your_password"
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

Then build:
```bash
./gradlew :app:assembleRelease :wear:assembleRelease
```

---

## Install APKs

### Phone

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Watch

The Samsung Galaxy Watch Ultra uses ADB over WiFi (TCP/IP).

1. On the watch: Settings → Developer Options → Enable ADB debugging, Enable ADB over WiFi
2. Note the IP address of the watch (Settings → About → IP Address)
3. Connect:
```bash
adb connect <watch-ip>:5555
```
4. List devices to confirm the watch appears:
```bash
adb devices
```
5. Install the watch APK, specifying the watch's serial:
```bash
adb -s <watch-ip>:5555 install -r wear/build/outputs/apk/debug/wear-debug.apk
```

---

## Permissions setup (Samsung / Android)

### Phone permissions

When you first launch the app:
1. Tap **Grant Permissions** to allow Microphone and Notifications.
2. Open **Settings → Apps → SnoreNudge → Battery** → select **Unrestricted** to prevent Samsung from killing the foreground service overnight.
3. Optional: Settings → Device Care → Battery → Background usage limits — exclude SnoreNudge.

### Watch permissions

The watch app requires no special permissions beyond vibration and wake lock, which are declared in the manifest.

---

## Detection algorithm

The detection pipeline (all on-device, no cloud):

```
AudioRecord (PCM 16-bit, 16 kHz, mono)
    ↓ 50ms frames (800 samples)
AudioFrameProcessor  →  normalise, compute RMS
    ↓
SnoreFeatureExtractor  →  Hann-windowed FFT, band energies (80–500 Hz),
                           spectral centroid, spectral flatness
    ↓
RuleBasedSnoreClassifier  →  weighted score [0, 1]
    ↓
TriggerDecisionEngine  →  rolling 5 s confidence window,
                           consecutive-frame gate, cooldown state machine
    ↓ (when shouldTrigger == true)
WatchCommandSender  →  Wearable MessageClient → /snore/vibrate
```

**To replace with a TFLite model later:** implement `SnoreClassifier` interface and inject a `TfLiteSnoreClassifier` in `SnoreMonitoringService`.

---

## Settings

| Setting | Default | Description |
|---------|---------|-------------|
| Sensitivity | 50% | Higher = triggers on quieter sounds |
| Trigger Duration | 5 s | Seconds of consecutive snoring before nudge |
| Cooldown Duration | 60 s | Seconds before next nudge can fire |
| Watch Vibration | On | Send vibrate command to watch |
| Phone Vibration | Off | Vibrate the phone when triggered |
| Phone Sound | Off | Play alert tone on phone |
| Strong Vibration | On | Use strong repeating pattern |
| Debug Mode | Off | Show debug tab, enable verbose logcat |

---

## Debugging

Enable **Debug Mode** in Settings to reveal the Debug tab. Use logcat to monitor the pipeline:

```bash
adb logcat -s SnoreMonitoringService:V TriggerDecisionEngine:V AudioCaptureManager:V WatchCommandSender:V WatchMessageListener:V
```

The **Debug screen** includes:
- Live confidence/state display
- Fire fake snore trigger button
- Send test vibrate button
- Start/stop monitoring

---

## Important notes

- **Samsung Health** is not used as a real-time trigger source. While Samsung Health can detect and log snoring overnight, it does not expose a real-time API and has significant processing latency that makes it unsuitable for instant anti-snore response.
- Raw audio is **never stored or transmitted**. Only lightweight metadata (timestamp, confidence score) is persisted in the local Room database.
- The app is designed for **overnight use** — the foreground service holds a partial wake lock to keep the CPU alive for audio processing even with the screen off.

---

## License

MIT