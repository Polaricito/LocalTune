/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.net.Uri
import moe.rukamori.archivetune.db.entities.LyricsEntity
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LyricsTranscriptionResult {
    data class Success(
        val lyrics: String,
        val savedToFile: Boolean,
    ) : LyricsTranscriptionResult

    data object Skipped : LyricsTranscriptionResult

    data class Failure(
        val reason: LyricsTranscriptionFailure,
    ) : LyricsTranscriptionResult
}

enum class LyricsTranscriptionFailure {
    AUDIO_UNAVAILABLE,
    MODEL_UNAVAILABLE,
    EMPTY_RESULT,
    SAVE_FAILED,
}

@Singleton
class LocalLyricsTranscriber
    @Inject
    constructor(
        private val decoder: AudioPcmDecoder,
        private val whisperEngine: WhisperEngine,
        private val lyricsRepository: LocalLyricsRepository,
    ) {
        suspend fun transcribe(
            songId: String,
            overwriteExisting: Boolean = false,
        ): LyricsTranscriptionResult {
            if (!overwriteExisting) {
                val existing = runCatching { lyricsRepository.loadLyrics(songId) }.getOrNull()
                if (!existing.isNullOrBlank()) return LyricsTranscriptionResult.Skipped
            }

            val samples =
                decoder.decode(Uri.parse(songId))
                    ?: return LyricsTranscriptionResult.Failure(LyricsTranscriptionFailure.AUDIO_UNAVAILABLE)
            if (samples.isEmpty()) {
                return LyricsTranscriptionResult.Failure(LyricsTranscriptionFailure.AUDIO_UNAVAILABLE)
            }

            val lyrics =
                whisperEngine.transcribe(samples)
                    ?: return LyricsTranscriptionResult.Failure(LyricsTranscriptionFailure.MODEL_UNAVAILABLE)
            if (lyrics.isBlank()) {
                return LyricsTranscriptionResult.Failure(LyricsTranscriptionFailure.EMPTY_RESULT)
            }

            val saveResult =
                runCatching {
                    lyricsRepository.saveLyrics(
                        songId = songId,
                        lyrics = lyrics,
                        source = LyricsEntity.Source.LOCAL_TRANSCRIPTION,
                    )
                }.getOrNull()
                    ?: return LyricsTranscriptionResult.Failure(LyricsTranscriptionFailure.SAVE_FAILED)

            if (!saveResult.savedToDatabase && !saveResult.savedToFile) {
                return LyricsTranscriptionResult.Failure(LyricsTranscriptionFailure.SAVE_FAILED)
            }

            return LyricsTranscriptionResult.Success(
                lyrics = lyrics,
                savedToFile = saveResult.savedToFile,
            )
        }
    }
