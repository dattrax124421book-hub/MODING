# APK Mod Injector 🚀

A high-performance, native Android on-device APK patcher, mod injector, zipalign utility, and APK signer built with **Jetpack Compose**, **Material 3**, **Kotlin Coroutines**, and Google's official **Android APK Signature Scheme (apksig)** library.

---

## 📌 Project Overview

**APK Mod Injector** enables on-device modification of Android application packages (`.apk`) using modular mod patch archives (`.zip`). It streams APK and patch zip contents with low memory overhead, replaces or adds assets, native binaries (`.so`), and Dalvik bytecode (`classes.dex`), strips obsolete signatures, performs 4-byte boundary zipalign, re-signs the output APK using V1, V2, and V3 signature schemes, saves the modded file to the public Download directory, and automatically triggers Android's native package installer.

---

## ✨ Key Features

- **Jetpack Compose & Material 3 UI**: Clean, responsive, single-screen workflow with dynamic status badges, cards, and animations.
- **On-Device APK Metadata Extraction**: Parses application name, package ID, version, and icon dynamically without loading large files into RAM.
- **Automated Compatibility Engine**: Matches the selected APK's package name against `patch_manifest.json`'s `targetPackage`. Prevents accidental injection when packages do not match (`Selected patch is for [Game A], but selected APK is [Game B]!`).
- **Memory-Efficient Archive Streaming**: Uses buffered I/O to inject and replace files without extracting large 80+ MB APKs to storage.
- **4-Byte Boundary Zipalign**: Automatically aligns uncompressed resources (such as `.so` libraries and assets) to 4-byte boundaries for ART runtime memory-mapping.
- **Google `apksig` V1/V2/V3 Signing**: Signs the resulting APK using standard Android cryptographic keys and verifies archive integrity post-signing.
- **Live Progress Reporting**: Real-time progress bar tracking the exact build stages:
  1. *Extracting recipe... (20%)*
  2. *Injecting patch files into archive... (55%)*
  3. *Aligning 4-byte boundaries (Zipalign)... (75%)*
  4. *Signing APK with v2/v3 signatures... (90%)*
  5. *Done! (100%)*
- **Public Output & Automatic Native Installation**: Saves output to `/storage/emulated/0/Download/[original]_MODDED.apk` and invokes Android's native installer dialog via a secure `FileProvider` `content://` URI within approximately one second.
- **1-Tap Sample Mod Loader**: Built-in test generator for *Hill Climb Racing* (`com.fingersoft.hillclimb`) for instant end-to-end testing.

---

## 📋 Requirements

- **Android Studio**: Android Studio Hedgehog (2023.1.1) / Iguana / Jellyfish / Ladybug or newer
- **Gradle**: 8.5+ (configured via Gradle Wrapper 9.3.1)
- **JDK**: Java 17 or Java 21 (Temurin / OpenJDK)
- **Android SDK**:
  - `minSdk`: 24 (Android 7.0 Nougat)
  - `targetSdk`: 34 (Android 14)
  - `compileSdk`: 36

---

## 🛠️ Android Studio Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/<your-username>/android-apk-mod-injector.git
   cd android-apk-mod-injector
   ```

2. **Open in Android Studio**:
   - Open Android Studio -> Select **Open** -> Navigate to the project root directory.
   - Wait for Gradle to finish sync and index dependencies.

3. **Check SDK & JDK Settings**:
   - Go to `Settings` / `Preferences` -> `Build, Execution, Deployment` -> `Build Tools` -> `Gradle`.
   - Verify `Gradle JDK` is set to **Java 17** or **Java 21**.

---

## 🚀 Build Instructions

### Command Line (via Gradle Wrapper)

- **Assemble Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
  The generated APK will be available at:
  ```
  app/build/outputs/apk/debug/app-debug.apk
  ```

- **Run Unit & Robolectric Tests**:
  ```bash
  ./gradlew testDebugUnitTest
  ```

- **Assemble Release APK**:
  ```bash
  ./gradlew assembleRelease
  ```

---

## 📱 Run Instructions

1. Connect a physical Android device via USB (with Developer Options & USB Debugging enabled) or start an Android Virtual Device (AVD).
2. In Android Studio, click **Run 'app'** (`Shift + F10`).
3. On first launch:
   - Grant storage permissions and allow *Install Unknown Apps* from the in-app security dialog.
4. Select the original `.apk` file and the mod patch `.zip` file from device storage, or click **Load Sample Mod & APK (Hill Climb Racing)**.
5. Click **🚀 Inject Mods & Build APK**.
6. Follow Android's native package installer prompt to install the modded application.

---

## 📦 Patch ZIP Structure

A valid mod patch ZIP archive must follow this directory layout:

```
[PackageName]_mod_patch.zip
│
├── patch_manifest.json
└── inject/
    ├── assets/
    │   ├── mod_engine_config.json
    │   └── game_settings.json
    ├── lib/
    │   └── arm64-v8a/
    │       └── libil2cpp.so
    └── classes.dex
```

- Any file placed inside `inject/` is mapped into the APK with the `inject/` prefix removed (e.g. `inject/classes.dex` -> `classes.dex`).
- If `replaceFiles` is `true` in `patch_manifest.json`, existing files in the APK are replaced; otherwise, new entries are appended.

---

## 📄 `patch_manifest.json` Format

The manifest file located at the root of the patch ZIP specifies target metadata and injection rules:

```json
{
  "modEngineVersion": "2.4",
  "targetPackage": "com.fingersoft.hillclimb",
  "appName": "Hill Climb Racing",
  "appliedPatches": [
    "Unlimited Coins",
    "No Ads",
    "God Mode"
  ],
  "rules": {
    "replaceFiles": true,
    "signV2": true,
    "signV3": true
  }
}
```

### JSON Fields:

| Field | Type | Description |
| :--- | :--- | :--- |
| `modEngineVersion` | String | Version of the mod injection engine (e.g., `"2.4"`) |
| `targetPackage` | String | Target Android package ID (e.g., `"com.fingersoft.hillclimb"`) |
| `appName` | String | Target application display name |
| `appliedPatches` | Array of String | List of patch modifications included in the archive |
| `rules.replaceFiles` | Boolean | Whether to replace existing files found in the APK |
| `rules.signV2` | Boolean | Enable APK Signature Scheme v2 |
| `rules.signV3` | Boolean | Enable APK Signature Scheme v3 |

---

## 🐙 GitHub Actions CI

The repository includes an automated continuous integration workflow located at:
`.github/workflows/android-build.yml`

### Automated Workflow Pipeline:
```
git push (main / develop)
       │
       ▼
GitHub Actions (ubuntu-latest)
       │
       ▼
Checkout Code & Setup JDK 17
       │
       ▼
Android SDK & Gradle Setup
       │
       ▼
Run Unit & Robolectric Tests
       │
       ▼
Assemble Debug APK (./gradlew assembleDebug)
       │
       ▼
Upload Artifact (android-apk-mod-injector-debug)
```

The compiled `app-debug.apk` is automatically published as a downloadable workflow artifact on every push and pull request.

---

## 📂 Output APK Location & Naming

Generated APKs are saved to the public Downloads directory:
```
/storage/emulated/0/Download/
```

### Naming Convention:
- If the original APK was named `hill_climb_v1.60.apk`, the output file is:
  ```
  hill_climb_v1.60_MODDED.apk
  ```
- If the original filename cannot be resolved, the output uses the package name:
  ```
  [packageName]_MODDED.apk
  ```

---

## 🔒 Security & Privacy

- **No Hardcoded Secrets**: Does not track private keystores or credentials in Git.
- **Secure FileProvider**: Uses `content://` URIs with transient read grants (`FLAG_GRANT_READ_URI_PERMISSION`). Never exposes insecure raw `file://` URIs.
- **Scoped Temporary Files**: Working files in `context.cacheDir` are cleaned up immediately following completion or error.

---

## ❓ Troubleshooting

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| *Selected patch is for [Game A], but selected APK is [Game B]!* | Package mismatch between APK and patch manifest | Verify you selected the correct patch ZIP for the target game. |
| *Please allow this app to install unknown applications* | Android install permission not granted | Tap the security icon in the top app bar and allow unknown app sources for APK Mod Injector. |
| *patch_manifest.json not found* | Invalid patch archive structure | Ensure `patch_manifest.json` is located at the root of the ZIP file. |
| *No injectable patch files found* | Missing `inject/` directory | Verify that patch files are placed inside the `inject/` directory inside the ZIP. |
| *Duplicate file found in APK during build* | Packaging resources conflict | Handled via `packaging.resources.excludes` in `app/build.gradle.kts`. |

---

## 📜 License

Licensed under the [Apache License, Version 2.0](LICENSE).
