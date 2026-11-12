# LocalTune Privacy Notice

Last updated: 2026-11-11

## Scope

This notice covers the Android LocalTune app in this repository. LocalTune is a
local-first music player and an unofficial fork of ArchiveTune. Playback,
library management and lyric generation work offline. The app contacts only two
optional, keyless services: LRCLIB for lyrics when you explicitly request an
online lookup, and Deezer for artist photos and release metadata when online
artist info is enabled.

## Privacy Summary

- All app data is stored locally on your device.
- LocalTune does not collect, sell or share personal data.
- There is no account system, no analytics, no advertising SDK and no automatic
  crash reporting.
- Lyrics are read from the audio file, from a `.lrc` file next to the song, or
  generated on-device with whisper.cpp. No audio is uploaded.
- The optional online features send only track or artist metadata to LRCLIB or
  Deezer, and can be disabled in Settings → Player.
- A manual backup export is available, and it is created only when you ask for
  it.

## Data the App Stores on Your Device

| Category | Examples | Why it is stored |
| --- | --- | --- |
| Library data | Song, artist, album, playlist, like state, total play time, audio format metadata | Library management, playlists and statistics |
| Search history | Local search queries | Search suggestions and history |
| Listening history | Playback events with song ID, timestamp and play time | History and listening statistics |
| Lyrics | Lyrics text and its source | Offline lyrics display |
| App settings | Language, theme, playback, gesture, equalizer, widget and backup preferences | Personalization |
| Cached files | Album artwork and image thumbnails | Faster artwork loading |
| Whisper model | A copy of the bundled speech model | Offline lyrics transcription |

The bundled whisper model is copied to `noBackupFilesDir/whisper` on first use
and is excluded from Android backup and device transfer.

## Network Access

LocalTune declares the `INTERNET` and `ACCESS_NETWORK_STATE` permissions for two
optional features:

- **Online lyrics (LRCLIB)** – when you choose the online-lyrics action, the app
  sends the track title, artist and album (or a combined search query) to
  `lrclib.net` and stores the returned lyrics locally.
- **Online artist info (Deezer)** – when an artist screen is opened and the
  feature is enabled, the app sends the artist name to `api.deezer.com` and
  receives a photo, profile link and release metadata.

Both features are enabled by default and can be turned off in Settings → Player.
No audio is transmitted. No other remote services are used, and there are no
accounts, telemetry or update servers.

## Android Permissions

| Permission | Why the app requests it |
| --- | --- |
| `READ_MEDIA_AUDIO` (Android 13+) | Read audio files from the local library |
| `READ_EXTERNAL_STORAGE` (Android 12 and below) | Read audio files on older versions |
| `MANAGE_EXTERNAL_STORAGE` (Android 11+) | Write `.lrc` sidecars next to audio files |
| `WRITE_EXTERNAL_STORAGE` (Android 9 and below) | Write `.lrc` files next to audio files |
| `POST_NOTIFICATIONS` | Show the playback notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playback running in the background |
| `WAKE_LOCK` | Keep playback alive while the device is idle |
| `BLUETOOTH_CONNECT` | Show metadata and controls on Bluetooth devices |
| `BLUETOOTH` (Android 11 and below) | Bluetooth audio device integration |
| `MODIFY_AUDIO_SETTINGS` | Equalizer, audio effects and volume handling |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Optional LRCLIB lyrics lookup and Deezer artist info |
| `VIBRATE` | Haptic feedback for controls |

The app does not request location, contacts, camera, calendar, SMS, call log or
microphone permissions.

If you deny a permission, the related feature may not work.

## Backups, Device Transfer and Retention

LocalTune enables Android backup support. Local database content and app
preferences may be included in Android cloud backup or device transfer,
depending on your Android settings. The whisper model is explicitly excluded.

The app also provides a manual backup feature that creates a ZIP archive with
app settings and database files. This export is triggered only by you, and the
destination is chosen by you.

Local data remains on your device until you clear app data, uninstall the app,
or remove it through an app action.

## User Controls

- You can use the app without granting any optional permission beyond media
  access.
- You can pause or clear search history and listening history from Settings.
- You can create a local backup export at any time.
- You can clear app data or uninstall the app to remove local storage.

## Security Notes

- The app database, preferences and cache files are not encrypted at rest.
  Android's file-based encryption and device lock provide the platform-level
  protection.
- The app enables `allowAudioPlaybackCapture`. Under Android platform rules,
  compatible system features or authorized apps may be able to capture app
  audio playback.
- No secrets, tokens, API keys or credentials are stored because the app has no
  online accounts and the optional services are keyless.

## Changes to This Notice

This file must be reviewed whenever LocalTune changes its permissions, storage
model, backup behavior or network architecture.

## Project Contact

For questions or corrections, use the repository's issue tracker.

## Technical Appendix

| Topic | Implementation |
| --- | --- |
| Permissions and backup behavior | `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml` |
| Local database contents | Room schema under `app/schemas/` |
| Settings stored locally | `app/src/main/kotlin/moe/rukamori/archivetune/constants/PreferenceKeys.kt` |
| Manual backup export | `app/src/main/kotlin/moe/rukamori/archivetune/viewmodels/BackupRestoreViewModel.kt` and `backup/BackupArchiveRepository.kt` |
| Local lyrics and `.lrc` sidecars | `app/src/main/kotlin/moe/rukamori/archivetune/lyrics/LocalLyricsRepository.kt` |
| On-device transcription | `app/src/main/kotlin/moe/rukamori/archivetune/lyrics/WhisperEngine.kt`, `AudioPcmDecoder.kt`, `LocalLyricsTranscriber.kt` |
| Native whisper.cpp build | `app/src/main/cpp/CMakeLists.txt`, `app/src/main/cpp/whisper_jni.cpp`, `app/src/main/cpp/whisper.cpp/` |
| Dependency posture | No Firebase, Crashlytics, Sentry, ad SDK or analytics SDK is declared in `app/build.gradle.kts` or `gradle/libs.versions.toml`. |
