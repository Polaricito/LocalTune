<div align="center">

  <h1>ArchiveTune</h1>

  <p align="center">
    <a href="README.md">English</a> •
    <a href="README_JA.md">日本語</a>
  </p>

  <p align="center">
    <strong>A fully offline music player for Android.</strong>
    <br />
    <em>Your files, your library, your lyrics. Nothing leaves the device.</em>
  </p>

</div>

---

ArchiveTune is a local music player built with Kotlin and Jetpack Compose. It
scans the audio files already stored on your phone and gives them a modern
Material 3 interface: library browsing, playlists, a full player, widgets,
sleep timer, equalizer support and offline lyrics.

The app has **no internet permission**. It does not connect to YouTube Music,
Spotify, Last.fm, ListenBrainz, Discord or any other online service. There are
no accounts, no downloads and no remote catalogs.

## Features

- **Local library** – songs, albums and artists read from MediaStore, with
  incremental rescans and support for selected folders.
- **Local playlists** – create, rename, reorder, hide and delete playlists.
  Playlist covers are stored locally.
- **Playback** – background playback with a media session, queue management,
  shuffle, repeat, crossfade, loudness normalization, tempo/pitch control,
  sleep timer and system equalizer integration.
- **Lyrics, entirely offline**:
  - lyrics embedded in the audio tags are imported automatically;
  - a `.lrc` file next to the song is used when present;
  - any song can be transcribed on-device with the bundled whisper.cpp engine.
- **Batch lyrics** – Settings → Local lyrics can transcribe the entire library
  or a single playlist, optionally overwriting existing lyrics. Songs are
  processed sequentially with progress and cancellation.
- **`.lrc` sidecars** – generated lyrics are saved as `Song.lrc` next to
  `Song.flac`, `Song.mp3`, `Song.m4a`, etc. (Android 10+ uses MediaStore; older
  versions write the file directly).
- **Backup and restore** – export and import the local database and settings.
- **Appearance** – dynamic colors, multiple player styles and backgrounds,
  gesture and animation settings.
- **Home screen widgets and AOD player** – driven by the local playback state.

## Lyrics and whisper.cpp

whisper.cpp is compiled from source with the Android NDK and CMake for four
ABIs: `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`. The native library is
built as `libarchivetune_whisper.so`.

A quantized multilingual `tiny` model (`ggml-tiny-q5_1.bin`, about 32 MB) is
bundled in `app/src/main/assets/whisper/`. On first use it is copied to the
app's `noBackupFilesDir`, then loaded lazily. Transcription runs on CPU with a
maximum of four threads and is serialized.

The model and the whisper.cpp sources are MIT licensed. The full license text
is available in `app/src/main/assets/whisper/LICENSE.txt` and in
`app/src/main/cpp/whisper.cpp/LICENSE`.

## Requirements

- Android 8.0 (API 26) or newer.
- Audio files readable by MediaStore (`READ_MEDIA_AUDIO` on Android 13+,
  `READ_EXTERNAL_STORAGE` on older versions).
- On Android 9 and below, `WRITE_EXTERNAL_STORAGE` is requested only to write
  `.lrc` sidecars.

## Building

Requirements:

- JDK 21
- Android SDK with compile SDK 37
- Android NDK `30.0.16248370`
- CMake `3.22.1`

Build a debug APK:

```bash
./gradlew :app:assembleGmsMobileUniversalDebug
```

The universal variant builds all four ABIs. Smaller per-ABI variants are also
available (`arm64`, `armeabi`, `x86`, `x86_64`).

Build a signed release APK:

```bash
./gradlew :app:assembleGmsMobileUniversalRelease
```

Release and nightly builds use the committed self-signed key at
`app/keystore/release.keystore` (alias, key password and store password:
`archivetune`). Keeping the same key in the repository guarantees that every
build has the same signature, so existing installations can be updated in
place. The key is not a secret and must not be treated as one.

## Project layout

- `app/src/main/kotlin/moe/rukamori/archivetune/` – app code.
- `app/src/main/cpp/` – CMake wrapper, JNI bridge and vendored whisper.cpp.
- `app/src/main/assets/whisper/` – bundled model and its license.
- `app/src/main/res/` – resources and translations.
- `fastlane/` – store metadata.

## Privacy

- No `INTERNET` permission is declared.
- No analytics, crash reporting, advertising or tracking SDKs are included.
- No account system and no remote synchronization.
- Lyrics, playlists, playback history and settings stay in the app's local
  database and files.

## License

ArchiveTune is licensed under the **GNU General Public License v3.0**. See
[`LICENSE`](LICENSE) for the complete terms.

Bundled components:

- whisper.cpp and ggml – MIT License, copyright the ggml authors.
- Whisper `tiny` model – MIT License, OpenAI.

The ArchiveTune name, logo and application icon are not licensed under the
GPLv3. Unofficial forks must not present themselves as official ArchiveTune
releases or imply endorsement by the maintainers.

This project has no official Google Play listing. Builds obtained from
unofficial sources are not supported.
