/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.artist

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class OnlineArtistRelease(
    val title: String,
    val releaseDate: String?,
    val year: Int?,
    val coverUrl: String?,
    val url: String?,
)

@Immutable
data class OnlineArtistInfo(
    val name: String,
    val pictureUrl: String?,
    val profileUrl: String?,
    val latestRelease: OnlineArtistRelease?,
)

/**
 * Optional artist metadata from Deezer. Deezer exposes a public, keyless API
 * that provides artist pictures and discographies, which fills the gap left by
 * the local-only library.
 */
@Singleton
class OnlineArtistRepository
    @Inject
    constructor() {
        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()

        suspend fun fetch(artistName: String): OnlineArtistInfo? =
            withContext(Dispatchers.IO) {
                val name = artistName.trim()
                if (name.isEmpty()) return@withContext null
                val artist = searchArtist(name) ?: return@withContext null
                val latestRelease = fetchLatestRelease(artist.optLong("id"))
                OnlineArtistInfo(
                    name = artist.optString("name").ifBlank { name },
                    pictureUrl = artist.pickImage(),
                    profileUrl = artist.optString("link").takeIf(String::isNotBlank),
                    latestRelease = latestRelease,
                )
            }

        private fun searchArtist(name: String): JSONObject? {
            val url =
                "https://api.deezer.com/search/artist"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("q", name)
                    .addQueryParameter("limit", "8")
                    .build()
            val payload = requestJson(url.toString()) ?: return null
            val data = payload.optJSONArray("data") ?: return null
            val target = normalize(name)

            val candidates = data.objects()
            return candidates.firstOrNull { normalize(it.optString("name")) == target }
                ?: candidates.firstOrNull {
                    val candidate = normalize(it.optString("name"))
                    candidate.isNotEmpty() && (candidate.contains(target) || target.contains(candidate))
                }
        }

        private fun fetchLatestRelease(artistId: Long): OnlineArtistRelease? {
            if (artistId <= 0L) return null
            val url =
                "https://api.deezer.com/artist/$artistId/albums"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("limit", "50")
                    .build()
            val payload = requestJson(url.toString()) ?: return null
            val albums = payload.optJSONArray("data")?.objects().orEmpty()
            if (albums.isEmpty()) return null

            val latest =
                albums
                    .filter { it.optString("title").isNotBlank() }
                    .maxWithOrNull(
                        compareBy<JSONObject> { it.optString("release_date").orEmpty() }
                            .thenBy { it.optLong("id") },
                    ) ?: return null

            val releaseDate = latest.optString("release_date").takeIf(String::isNotBlank)
            return OnlineArtistRelease(
                title = latest.optString("title"),
                releaseDate = releaseDate,
                year = releaseDate?.take(4)?.toIntOrNull(),
                coverUrl = latest.pickImage(),
                url = latest.optString("link").takeIf(String::isNotBlank),
            )
        }

        private fun requestJson(url: String): JSONObject? {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", "LocalTune/${BuildConfig.VERSION_NAME}")
                    .header("Accept", "application/json")
                    .get()
                    .build()
            return runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string()
                    if (body.isBlank()) return@use null
                    val json = JSONObject(body)
                    if (json.has("error")) return@use null
                    json
                }
            }.onFailure { Timber.tag(TAG).w(it, "Deezer request failed") }.getOrNull()
        }

        private fun JSONObject.pickImage(): String? =
            sequenceOf("picture_xl", "picture_big", "picture_medium", "picture", "cover_xl", "cover_big", "cover_medium", "cover")
                .map { optString(it) }
                .firstOrNull { it.isNotBlank() }

        private fun JSONArray.objects(): List<JSONObject> =
            (0 until length()).mapNotNull { optJSONObject(it) }

        private fun normalize(value: String): String =
            Normalizer
                .normalize(value, Normalizer.Form.NFD)
                .replace(CombiningMarks, "")
                .lowercase(Locale.ROOT)
                .replace(NonWord, "")

        private companion object {
            const val TAG = "OnlineArtistRepository"
            val CombiningMarks = Regex("\\p{Mn}+")
            val NonWord = Regex("[^\\p{L}\\p{N}]+")
        }
    }
