/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.models.MediaMetadata
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Optional online lyrics lookup. LRCLIB is free and does not need an API key,
 * so it is used as the secondary source when no valid local lyrics exist.
 */
@Singleton
class OnlineLyricsRepository
    @Inject
    constructor(
        private val lyricsRepository: LocalLyricsRepository,
    ) {
        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()

        suspend fun fetchAndSave(mediaMetadata: MediaMetadata): String? =
            withContext(Dispatchers.IO) {
                val lyrics = fetch(mediaMetadata) ?: return@withContext null
                runCatching {
                    lyricsRepository.saveLyrics(
                        songId = mediaMetadata.id,
                        lyrics = lyrics,
                        source = LyricsEntity.Source.REMOTE,
                    )
                }.onFailure { Timber.tag(TAG).w(it, "Could not cache online lyrics") }
                lyrics
            }

        suspend fun fetch(mediaMetadata: MediaMetadata): String? =
            withContext(Dispatchers.IO) {
                if (mediaMetadata.title.isBlank()) return@withContext null
                val artist = mediaMetadata.artists.joinToString(separator = " ") { it.name }.trim()
                search(mediaMetadata, artist)
                    ?: searchByQuery(mediaMetadata, artist)
            }

        private fun search(
            mediaMetadata: MediaMetadata,
            artist: String,
        ): String? {
            val url =
                "https://lrclib.net/api/search"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("track_name", mediaMetadata.title)
                    .apply {
                        if (artist.isNotBlank()) addQueryParameter("artist_name", artist)
                        mediaMetadata.album?.title?.takeIf(String::isNotBlank)?.let {
                            addQueryParameter("album_name", it)
                        }
                    }.build()
            return requestCandidates(url.toString(), mediaMetadata)
        }

        private fun searchByQuery(
            mediaMetadata: MediaMetadata,
            artist: String,
        ): String? {
            val query = listOf(mediaMetadata.title, artist).filter(String::isNotBlank).joinToString(" ")
            if (query.isBlank()) return null
            val url =
                "https://lrclib.net/api/search"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("q", query)
                    .build()
            return requestCandidates(url.toString(), mediaMetadata)
        }

        private fun requestCandidates(
            url: String,
            mediaMetadata: MediaMetadata,
        ): String? {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", "LocalTune/${moe.rukamori.archivetune.BuildConfig.VERSION_NAME}")
                    .header("Accept", "application/json")
                    .get()
                    .build()
            return runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string()
                    if (body.isBlank()) return@use null
                    pickCandidate(JSONArray(body), mediaMetadata)
                }
            }.onFailure { Timber.tag(TAG).w(it, "LRCLIB request failed") }.getOrNull()
        }

        private fun pickCandidate(
            candidates: JSONArray,
            mediaMetadata: MediaMetadata,
        ): String? {
            data class Candidate(
                val lyrics: String,
                val synced: Boolean,
                val durationDelta: Double,
            )

            val best =
                (0 until candidates.length())
                    .mapNotNull { index ->
                        val item = candidates.optJSONObject(index) ?: return@mapNotNull null
                        if (item.optBoolean("instrumental", false)) return@mapNotNull null
                        val synced = item.optString("syncedLyrics").takeIf(::isUsableLyrics)
                        val plain = item.optString("plainLyrics").takeIf(::isUsableLyrics)
                        val lyrics = synced ?: plain ?: return@mapNotNull null
                        val duration = item.optDouble("duration", Double.NaN)
                        val delta =
                            if (duration.isNaN() || mediaMetadata.duration <= 0) {
                                Double.MAX_VALUE
                            } else {
                                abs(duration - mediaMetadata.duration)
                            }
                        Candidate(lyrics = lyrics, synced = synced != null, durationDelta = delta)
                    }.minWithOrNull(
                        compareBy<Candidate> { if (it.synced) 0 else 1 }
                            .thenBy { it.durationDelta },
                    )
            return best?.lyrics
        }

        private fun isUsableLyrics(value: String): Boolean =
            value.isNotBlank() && value != "null" && LyricsUtils.hasRenderableText(value)

        private companion object {
            const val TAG = "OnlineLyricsRepository"
        }
    }
