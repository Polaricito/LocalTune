/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsEntity
import timber.log.Timber
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

data class LyricsSaveResult(
    val savedToDatabase: Boolean,
    val savedToFile: Boolean,
)

/**
 * Reads and writes local lyrics. Lyrics are kept in the database and, whenever
 * the storage provider allows it, exported as a `.lrc` file next to the audio
 * file (`Cancion.flac` -> `Cancion.lrc`).
 */
@Singleton
class LocalLyricsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        private val resolver = context.contentResolver

        suspend fun getStoredLyrics(songId: String): LyricsEntity? =
            withContext(Dispatchers.IO) {
                database.getLyricsById(songId)
            }

        suspend fun loadLyrics(
            songId: String,
            fallbackToSidecar: Boolean = true,
        ): String? =
            withContext(Dispatchers.IO) {
                val stored = database.getLyricsById(songId)?.lyrics?.takeIf(String::isNotBlank)
                if (stored != null && stored != LyricsEntity.LYRICS_NOT_FOUND) return@withContext stored
                if (!fallbackToSidecar) return@withContext null
                readSidecar(songId)
            }

        suspend fun saveLyrics(
            songId: String,
            lyrics: String,
            source: LyricsEntity.Source = LyricsEntity.Source.USER_EDIT,
        ): LyricsSaveResult =
            withContext(Dispatchers.IO) {
                val normalized = LyricsUtils.normalizeLyricsText(lyrics)
                val savedToDatabase =
                    runCatching {
                        database.upsert(
                            LyricsEntity(
                                id = songId,
                                lyrics = normalized,
                                source = source.value,
                            ),
                        )
                    }.isSuccess
                val savedToFile = if (normalized.isBlank()) false else writeSidecar(songId, normalized)
                LyricsSaveResult(savedToDatabase, savedToFile)
            }

        suspend fun exportSidecar(songId: String): Boolean =
            withContext(Dispatchers.IO) {
                val lyrics = database.getLyricsById(songId)?.lyrics?.takeIf(String::isNotBlank) ?: return@withContext false
                writeSidecar(songId, lyrics)
            }

        suspend fun readSidecar(songId: String): String? =
            withContext(Dispatchers.IO) {
                val uri = Uri.parse(songId)
                val location = resolveLocation(uri) ?: return@withContext null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    readSidecarScoped(location)
                } else {
                    readSidecarLegacy(location)
                }
            }

        suspend fun writeSidecar(
            songId: String,
            lyrics: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val uri = Uri.parse(songId)
                val location = resolveLocation(uri) ?: return@withContext false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    writeSidecarScoped(location, lyrics)
                } else {
                    writeSidecarLegacy(location, lyrics)
                }
            }

        private fun resolveLocation(uri: Uri): AudioLocation? {
            val projection =
                buildList {
                    add(MediaStore.Audio.Media.DISPLAY_NAME)
                    add(MediaStore.MediaColumns.DATA)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        add(MediaStore.MediaColumns.RELATIVE_PATH)
                    }
                }.toTypedArray()
            return runCatching {
                resolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val displayNameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val relativeIndex =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        } else {
                            -1
                        }
                    val displayName = displayNameIndex.takeIf { it >= 0 }?.let(cursor::getString)
                    val dataPath = dataIndex.takeIf { it >= 0 }?.let(cursor::getString)
                    val relativePath = relativeIndex.takeIf { it >= 0 }?.let(cursor::getString)
                    if (displayName.isNullOrBlank() && dataPath.isNullOrBlank()) return@use null
                    AudioLocation(
                        displayName = displayName ?: File(dataPath.orEmpty()).name,
                        dataPath = dataPath,
                        relativePath = relativePath,
                    )
                }
            }.onFailure { Timber.tag(TAG).w(it, "Could not resolve audio location for %s", uri) }.getOrNull()
        }

        private fun readSidecarScoped(location: AudioLocation): String? {
            val fileUri = findScopedSidecar(location) ?: return null
            return runCatching {
                resolver.openInputStream(fileUri)?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
            }.onFailure { Timber.tag(TAG).w(it, "Could not read sidecar lyrics") }.getOrNull()
        }

        private fun writeSidecarScoped(
            location: AudioLocation,
            lyrics: String,
        ): Boolean {
            val existing = findScopedSidecar(location)
            if (existing != null) {
                val updated =
                    runCatching {
                        resolver.openOutputStream(existing, "wt")?.use { stream ->
                            stream.write(lyrics.toByteArray(StandardCharsets.UTF_8))
                        }
                    }.isSuccess
                if (updated) return true
            }

            val relativePath = location.relativePath ?: return false
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sidecarName(location))
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val inserted = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return false
            val written =
                runCatching {
                    resolver.openOutputStream(inserted, "wt")?.use { stream ->
                        stream.write(lyrics.toByteArray(StandardCharsets.UTF_8))
                    }
                }.isSuccess
            if (!written) {
                runCatching { resolver.delete(inserted, null, null) }
                return false
            }
            runCatching {
                resolver.update(
                    inserted,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            return true
        }

        private fun findScopedSidecar(location: AudioLocation): Uri? {
            val relativePath = location.relativePath ?: return null
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection =
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            val args = arrayOf(sidecarName(location), relativePath)
            return runCatching {
                resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        ContentUris.withAppendedId(collection, cursor.getLong(0))
                    } else {
                        null
                    }
                }
            }.getOrNull()
        }

        private fun readSidecarLegacy(location: AudioLocation): String? {
            val file = legacySidecarFile(location) ?: return null
            return runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
        }

        private fun writeSidecarLegacy(
            location: AudioLocation,
            lyrics: String,
        ): Boolean {
            val file = legacySidecarFile(location) ?: return false
            return runCatching {
                file.writeText(lyrics, StandardCharsets.UTF_8)
                true
            }.getOrDefault(false)
        }

        private fun legacySidecarFile(location: AudioLocation): File? {
            val audioFile = location.dataPath?.let(::File) ?: return null
            val parent = audioFile.parentFile ?: return null
            val expectedName = sidecarName(location)
            val direct = File(parent, expectedName)
            if (direct.exists()) return direct
            val caseInsensitive =
                parent.listFiles()?.firstOrNull {
                    it.isFile && it.name.equals(expectedName, ignoreCase = true)
                }
            return caseInsensitive ?: direct
        }

        private fun sidecarName(location: AudioLocation): String =
            location.displayName.substringBeforeLast('.', location.displayName) + ".lrc"

        private data class AudioLocation(
            val displayName: String,
            val dataPath: String?,
            val relativePath: String?,
        )

        private companion object {
            const val TAG = "LocalLyricsRepository"
        }
    }
