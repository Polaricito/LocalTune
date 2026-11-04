# 🛠️ Contribute to ArchiveTune

This document explains how to set up the development environment, understand
the project structure and build **ArchiveTune** from source.

ArchiveTune is a fully local Android music player. Contributions must keep it
offline: no network calls, no online accounts, no remote catalogs and no
internet permission.

---

## Development environment

- **IDE:** Android Studio Ladybug (2024.2.1) or newer.
- **JDK:** 21.
- **Android SDK:** compile SDK 37.
- **Android NDK:** `30.0.16248370`.
- **CMake:** `3.22.1`.
- **Git:** 2.40 or newer.

The NDK and CMake are required because lyrics transcription uses a native
whisper.cpp build.

## Skills

The codebase uses:

- Kotlin with Coroutines and Flow.
- Jetpack Compose and Material 3.
- Gradle Kotlin DSL and version catalogs (`gradle/libs.versions.toml`).
- MVVM, repositories and unidirectional data flow.

## Architecture

1. **UI layer (Compose):** renders state emitted by ViewModels and forwards
   user actions.
2. **Domain layer:** use cases and playback abstractions.
3. **Data layer:** Room database, MediaStore scanning and local file access.
4. **Service layer (Media3):** `MediaSessionService` for background playback.
5. **Native layer:** CMake/JNI wrapper around whisper.cpp for offline lyrics.

There is no network layer. Do not add one.

## Getting started

1. Clone the repository and open it in Android Studio.
2. Create `local.properties` with your SDK path:

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

3. Let Gradle sync. Version catalogs pin the tested dependency versions.
4. Build a debug APK:

```bash
./gradlew :app:assembleGmsMobileUniversalDebug
```

The first native build compiles whisper.cpp for all four ABIs and can take a
few minutes.

## Build commands

| Command | Output | Context |
| --- | --- | --- |
| `./gradlew :app:assembleGmsMobileUniversalDebug` | Debug APK, all ABIs | Local testing |
| `./gradlew :app:assembleGmsMobileUniversalRelease` | Signed release APK | Release testing |
| `./gradlew :app:compileGmsMobileUniversalDebugKotlin` | Kotlin compilation only | Fast checks |
| `./gradlew :app:externalNativeBuildGmsMobileUniversalDebug` | Native libraries only | whisper.cpp changes |
| `./gradlew test` | Unit tests | Logic verification |
| `./gradlew clean` | – | Clear build outputs |

Per-ABI variants (`arm64`, `armeabi`, `x86`, `x86_64`) are also available.

## Signing

Release and nightly builds use the committed self-signed key
`app/keystore/release.keystore`:

- alias: `archivetune`
- key password: `archivetune`
- store password: `archivetune`

The key is intentionally public. Do not replace it: changing the key breaks
updates for existing installations.

## Code quality

Before opening a pull request, run:

```bash
./gradlew :app:compileGmsMobileUniversalDebugKotlin
./gradlew test
./gradlew lintDebug
```

## Native code

- `app/src/main/cpp/CMakeLists.txt` builds whisper.cpp, ggml and the JNI bridge.
- `app/src/main/cpp/whisper_jni.cpp` exposes `nativeInit`,
  `nativeRelease` and `nativeTranscribe`.
- `app/src/main/cpp/whisper.cpp/` contains the vendored MIT-licensed sources.
- If you update whisper.cpp, update the version in `CMakeLists.txt` and keep
  the license files.

## Lyrics

- Embedded tags are imported during the MediaStore scan.
- `.lrc` sidecars are read and written by `LocalLyricsRepository`.
- On-device transcription lives in `lyrics/WhisperEngine.kt`,
  `lyrics/AudioPcmDecoder.kt` and `lyrics/LocalLyricsTranscriber.kt`.
- Batch processing is exposed in Settings → Local lyrics.

## Translations

User-facing strings belong in `app/src/main/res/values/strings.xml`. Translated
resources live in the `values-*` directories. Keep the base strings in English
and preserve existing formatting placeholders.

## Troubleshooting

> [!IMPORTANT]
> **Heap memory:** if Gradle reports `GC overhead limit exceeded`, raise the
> limits in `gradle.properties`:
> `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g`

> [!WARNING]
> **Native build failures:** confirm that NDK `30.0.16248370` and CMake
> `3.22.1` are installed in the Android SDK, then delete `app/.cxx` and rebuild.

> [!WARNING]
> **Compose compiler:** a Compose version mismatch usually means the Kotlin
> plugin and Compose compiler versions in the version catalog do not match.

---

<div align="center">
<sub>ArchiveTune: your music, entirely on your device.</sub>
</div>
