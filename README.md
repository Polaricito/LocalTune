<div align="center">

  <h1>LocalTune</h1>

  <p align="center">
    <a href="README.md">English</a> •
    <a href="README_JA.md">日本語</a>
  </p>

  <p align="center">
    <strong>A local-first music player for Android.</strong>
    <br />
    <em>Your files, your library, your lyrics — online extras only when you want them.</em>
  </p>

</div>

---

LocalTune is a local-first music player for Android, built with Kotlin and Jetpack
Compose. It scans the audio files already stored on your phone and gives them a
modern Material 3 interface: library browsing, playlists, a full player, widgets,
sleep timer, equalizer support and on-device lyrics.

LocalTune is a fork of [ArchiveTune](https://github.com/rukamori/ArchiveTune) by
rukamori. The local playback, library and lyric-generation features work fully
offline. Two optional online features can add lyrics and artist metadata:
[LRCLIB](https://lrclib.net) for lyrics and [Deezer](https://www.deezer.com) for
artist photos and releases. Both can be turned off in Settings.

## Features

- **Local library** – songs, albums and artists read from MediaStore. The Music
  folder (`/storage/emulated/0/Music`) is included by default; additional folders
  can be included or excluded, and a minimum song duration can be set. The artist
  list has a **Subscribed Only** filter.
- **Local playlists** – create, rename, reorder, hide and delete playlists.
  Playlist covers are stored locally.
- **Playback** – background playback with a media session and media notification,
  queue management, shuffle, repeat, crossfade, loudness normalization,
  tempo/pitch control, sleep timer and system equalizer integration.
- **Persistent queue and background playback** – playback continues when the app
  is removed from the recent-tasks list, and the last queue is restored when the
  app starts again. Settings → Player has an optional **Stop music on task clear**
  switch for the opposite behavior.
- **Lyrics**:
  - lyrics embedded in the audio tags are imported automatically;
  - a `.lrc` file next to the song is used when present;
  - any song can be transcribed on-device with the bundled whisper.cpp engine;
  - plain-text lyrics can be line-synced to the audio with **Sync with Whisper**;
  - an optional online lookup (LRCLIB) is available from the lyrics menu.
- **Clean lyric rendering** – line-synced LRC is displayed as plain text with a
  live highlight; enhanced-LRC word timestamps (`<mm:ss.xx>`) and leading voice
  labels (`v1:`) are stripped from the display.
- **Batch lyrics** – Settings → Local lyrics can transcribe the entire library
  or a single playlist, optionally overwriting existing lyrics. Songs are
  processed sequentially with progress and cancellation.
- **`.lrc` sidecars** – generated lyrics are saved as `Song.lrc` next to
  `Song.flac`, `Song.mp3`, `Song.m4a`, etc. (Android 10+ uses MediaStore; older
  versions write the file directly).
- **Optional artist info** – artist photos and the latest release from Deezer,
  shown on the artist screen when online artist info is enabled.
- **Backup and restore** – export and import the local database and settings.
- **Appearance** – dynamic colors, multiple player styles and backgrounds,
  gesture and animation settings.
- **Home screen widgets and AOD player** – driven by the local playback state.

## Optional online features

LocalTune does not require an account or a connection for normal use. The app
declares the `INTERNET` permission only for these two features, both of which can
be disabled in Settings → Player:

- **Online lyrics (LRCLIB)** – a keyless lyrics database. It is queried only when
  you explicitly choose the online-lyrics action; the request sends the track
  title, artist and album (or a combined search query). Results are cached in
  the local database.
- **Online artist info (Deezer)** – a keyless artist API used when an artist
  screen is opened. It sends the artist name and receives a photo and release
  metadata.

No audio is uploaded. There are no accounts, ads, analytics or crash-reporting
SDKs. See [`PRIVACY.md`](PRIVACY.md) for details.

## Lyrics and whisper.cpp

whisper.cpp is compiled from source with the Android NDK and CMake for four
ABIs: `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`. The native library is
built as `libarchivetune_whisper.so`.

A quantized multilingual `tiny` model (`ggml-tiny-q5_1.bin`, about 32 MB) is
bundled in `app/src/main/assets/whisper/`. On first use it is copied to the
app's `noBackupFilesDir`, then loaded lazily. Transcription runs on CPU with a
maximum of four threads and is serialized.

**Transcription** turns a song into plain lyrics. **Sync with Whisper** takes
existing plain lyrics, transcribes the song for timing, and aligns the lines to
the audio. Timestamps are bounded by the detected audio duration; if the match
confidence is low, the lines are distributed evenly across the song instead of
producing out-of-range timings.

The model and the whisper.cpp sources are MIT licensed. The full license text
is available in `app/src/main/assets/whisper/LICENSE.txt` and in
`app/src/main/cpp/whisper.cpp/LICENSE`.

## Requirements

- Android 8.0 (API 26) or newer.
- Audio files readable by MediaStore (`READ_MEDIA_AUDIO` on Android 13+,
  `READ_EXTERNAL_STORAGE` on older versions).
- On Android 11 and newer, **All files access** (`MANAGE_EXTERNAL_STORAGE`) is
  requested to write `.lrc` sidecars beside the audio files. On Android 9 and
  below, `WRITE_EXTERNAL_STORAGE` is used instead.
- A network connection is only needed for the optional LRCLIB lyrics lookup and
  Deezer artist info.
- `POST_NOTIFICATIONS` is used for the playback notification; the foreground
  service permissions keep playback alive in the background.

## Building

Requirements:

- JDK 21 or newer (CI uses JDK 27)
- Android SDK with compile SDK 37
- Android NDK `30.0.16248370`
- CMake `3.22.1`

Build a debug APK:

```bash
./gradlew :app:assembleGmsMobileUniversalDebug
```

The universal variant builds all four ABIs. Smaller per-ABI variants are also
available (`arm64`, `armeabi`, `x86`, `x86_64`), as well as `foss` and TV
variants. Outputs are written under `app/build/outputs/apk/`.

Build a signed release APK:

```bash
./gradlew :app:assembleGmsMobileUniversalRelease
```

Release builds use the committed self-signed key at
`app/keystore/release.keystore` (alias, key password and store password:
`archivetune`). Keeping the same key in the repository guarantees that every
build has the same signature, so existing installations can be updated in
place. The key is not a secret and must not be treated as one.

GitHub Actions builds every APK variant on push. When `versionName` in
`app/build.gradle.kts` changes, the release workflow tags the commit and
publishes the APKs on the
[releases page](https://github.com/Polaricito/LocalTune/releases).

## Project layout

- `app/src/main/kotlin/moe/rukamori/archivetune/` – app code.
  - `localmedia/` – MediaStore scanning and folder filters.
  - `playback/` – player service, queue persistence and background playback.
  - `lyrics/` – lyric parsing, `.lrc` sidecars, whisper transcription and
    alignment, optional LRCLIB lookup.
  - `artist/` – optional Deezer artist metadata.
- `app/src/main/cpp/` – CMake wrapper, JNI bridge and vendored whisper.cpp.
- `app/src/main/assets/whisper/` – bundled model and its license.
- `app/src/main/res/` – resources and translations.
- `fastlane/` – store metadata.

## Privacy

- LocalTune is local-first: library data, playlists, playback history, lyrics
  and settings stay in the app's local database and files.
- The only network calls are the optional LRCLIB lyrics lookup and Deezer artist
  info described above. They can be disabled in Settings → Player.
- No analytics, crash reporting, advertising or tracking SDKs are included.
- No account system and no remote synchronization.
- The app database and preferences are not encrypted at rest; Android's
  file-based encryption and device lock provide platform-level protection.

## A fork of ArchiveTune

LocalTune is a fork of [ArchiveTune](https://github.com/rukamori/ArchiveTune),
created by rukamori. All credit for the original app, design and codebase goes
to the ArchiveTune project and its contributors.

The application ID (`moe.rukamori.archivetune`) and the release signing key are
kept from upstream so that existing installations can be updated in place; the
app name and user interface are LocalTune.

## License

LocalTune is licensed under the **GNU General Public License v3.0**, inherited
from ArchiveTune. See [`LICENSE`](LICENSE) for the complete terms.

Bundled components:

- whisper.cpp and ggml – MIT License, copyright the ggml authors.
- Whisper `tiny` model – MIT License, OpenAI.

The ArchiveTune name, logo and application icon are not licensed under the
GPLv3. LocalTune is an unofficial fork and is not endorsed by the ArchiveTune
maintainers.
