# ArchiveTune Privacy Notice

Last updated: 2026-11-02

## Scope

This notice covers the Android ArchiveTune app in this repository. ArchiveTune
is a fully offline local music player. It does not declare the `INTERNET`
permission and does not contact YouTube, YouTube Music, Spotify, Last.fm,
ListenBrainz, Discord, lyrics providers, update servers or any other remote
service.

## Privacy Summary

- All app data is stored locally on your device.
- ArchiveTune does not collect, sell or share personal data.
- There is no account system, no analytics, no advertising SDK and no automatic
  crash reporting.
- Lyrics are read from the audio file, from a `.lrc` file next to the song, or
  generated on-device with whisper.cpp. No audio or text is uploaded.
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

ArchiveTune does not request the `INTERNET` permission. The app cannot create
network connections, so no data is transmitted to the developer or to third
parties.

If a future build adds network functionality, this notice and the manifest must
be updated together.

## Android Permissions

| Permission | Why the app requests it |
| --- | --- |
| `READ_MEDIA_AUDIO` (Android 13+) | Read audio files from the local library |
| `READ_EXTERNAL_STORAGE` (Android 12 and below) | Read audio files on older versions |
| `WRITE_EXTERNAL_STORAGE` (Android 9 and below) | Write `.lrc` files next to audio files |
| `POST_NOTIFICATIONS` | Show the playback notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playback running in the background |
| `WAKE_LOCK` | Keep playback alive while the device is idle |
| `BLUETOOTH_CONNECT` | Show metadata and controls on Bluetooth devices |
| `BLUETOOTH` (Android 11 and below) | Bluetooth audio device integration |
| `MODIFY_AUDIO_SETTINGS` | Equalizer, audio effects and volume handling |
| `VIBRATE` | Haptic feedback for controls |

The app does not request location, contacts, camera, calendar, SMS, call log or
microphone permissions.

If you deny a permission, the related feature may not work.

## Backups, Device Transfer and Retention

ArchiveTune enables Android backup support. Local database content and app
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
- No secrets, tokens or credentials are stored because the app has no online
  accounts.

## Changes to This Notice

This file must be reviewed whenever ArchiveTune changes its permissions,
storage model, backup behavior or network architecture.

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
