# ☘️ Klover — Four-Leaf Clover Finder

A Kotlin Multiplatform (Android + iOS) app that finds **four-leaf clovers with on-device AI** and highlights them in a live camera feed.

> FOUR-LEAF-style **dark, camera-first UI**. The home screen is a **real-time camera scanner**: a live preview with detection boxes, `%` confidence chips, and an fps counter. Each frame runs **on-device inference** — ONNX Runtime on Android (CameraX), CoreML/Vision on iOS (AVCaptureSession); if no model is bundled it falls back to `MockCloverDetector`. The UI is 100% shared via Compose Multiplatform.

## Status

| Feature | Android | iOS |
| --- | --- | --- |
| Shared UI (Material 3 Expressive · dark FOUR-LEAF theme) | ✓ | ✓ |
| Real-time camera scanner (home) | ✓ CameraX + per-frame inference | ✓ AVCaptureSession + per-frame inference |
| On-device inference | ✓ ONNX Runtime (~10 fps) | ✓ CoreML / Vision |
| YOLO post-processing (decode + NMS) | ✓ shared | ✓ shared |

The bundled model (`shared/src/commonMain/composeResources/files/clover.onnx`) is a YOLOv8 model trained on public four-leaf-clover datasets — see [`training/`](./training/README.md) to (re)train your own for free.

## Tech stack

- **Kotlin Multiplatform** + **Compose Multiplatform** (shared UI), **Material 3 Expressive**
- **Koin** (DI), **AndroidX Lifecycle ViewModel** (state), **Coroutines**
- **ONNX Runtime** (Android) / **CoreML + Vision** (iOS) for on-device inference
- AGP 9 / Kotlin 2.4 / Compose MP 1.11 / Gradle 9.6

## Running

### Prerequisites
- JDK 17, Android SDK (compileSdk 37), Xcode (for iOS)
- `sdk.dir` in `local.properties` (already generated locally)

### Android
```bash
./gradlew :androidApp:assembleDebug     # build APK
./gradlew :androidApp:installDebug      # install on a connected device/emulator
```
Or run the `androidApp` configuration from Android Studio. A **physical device is recommended** so the camera works.

### iOS
```bash
open iosApp/iosApp.xcodeproj            # run from Xcode
```
For device builds, copy `iosApp/Configuration/Local.xcconfig.template` to `Local.xcconfig`
and set your Apple `TEAM_ID` there (it's gitignored). Bundle id: `io.github.klover`.

Verify the shared framework compiles:
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

## Train your own model (free)

See [`training/README.md`](./training/README.md). In short: open the Colab notebook, download a public Roboflow clover dataset, train YOLOv8, export `clover.onnx`, and drop it into `shared/src/commonMain/composeResources/files/`. No server, no cost.

If a multi-class model is used (e.g. 3/4/5-leaf), set the 4-leaf class index in `YoloConfig.TARGET_CLASS_INDEX`.
