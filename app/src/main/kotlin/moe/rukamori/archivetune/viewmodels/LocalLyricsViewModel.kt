/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.EnableLrcLibKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.lyrics.LocalLyricsRepository
import moe.rukamori.archivetune.lyrics.LocalLyricsTranscriber
import moe.rukamori.archivetune.lyrics.LyricsTranscriptionResult
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.lyrics.OnlineLyricsRepository
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.getAsync
import javax.inject.Inject

sealed interface LocalLyricsMessage {
    data object Saved : LocalLyricsMessage

    data object SavedToFile : LocalLyricsMessage

    data object FileExportFailed : LocalLyricsMessage

    data object LoadFailed : LocalLyricsMessage

    data object OnlineLyricsFound : LocalLyricsMessage

    data object OnlineLyricsNotFound : LocalLyricsMessage

    data object WhisperSyncCompleted : LocalLyricsMessage

    data object WhisperSyncFailed : LocalLyricsMessage

    data object WhisperSyncNoLyrics : LocalLyricsMessage
}

@HiltViewModel
class LocalLyricsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: LocalLyricsRepository,
        private val onlineLyricsRepository: OnlineLyricsRepository,
        private val transcriber: LocalLyricsTranscriber,
    ) : ViewModel() {
        private val _lyrics = MutableStateFlow<String?>(null)
        val lyrics: StateFlow<String?> = _lyrics.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _isSyncing = MutableStateFlow(false)
        val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

        private val _messages = MutableSharedFlow<LocalLyricsMessage>(extraBufferCapacity = 8)
        val messages: SharedFlow<LocalLyricsMessage> = _messages.asSharedFlow()

        fun load(mediaMetadata: MediaMetadata) {
            viewModelScope.launch {
                _isLoading.value = true
                _lyrics.value = null
                val local = runCatching { repository.loadLyrics(mediaMetadata.id) }.getOrNull()
                _lyrics.value =
                    if (!local.isNullOrBlank()) {
                        local
                    } else if (isOnlineLyricsEnabled()) {
                        runCatching { onlineLyricsRepository.fetchAndSave(mediaMetadata) }.getOrNull()
                    } else {
                        null
                    }
                _isLoading.value = false
            }
        }

        fun updateLyrics(
            mediaMetadata: MediaMetadata,
            lyrics: String,
        ) {
            viewModelScope.launch {
                val result =
                    runCatching {
                        repository.saveLyrics(
                            songId = mediaMetadata.id,
                            lyrics = lyrics,
                            source = LyricsEntity.Source.USER_EDIT,
                        )
                    }.getOrNull()
                if (result == null) {
                    _messages.emit(LocalLyricsMessage.LoadFailed)
                    return@launch
                }
                _lyrics.value = LyricsUtils.normalizeLyricsText(lyrics)
                _messages.emit(LocalLyricsMessage.Saved)
            }
        }

        fun exportToFile(mediaMetadata: MediaMetadata) {
            viewModelScope.launch {
                val exported = runCatching { repository.exportSidecar(mediaMetadata.id) }.getOrDefault(false)
                _messages.emit(
                    if (exported) LocalLyricsMessage.SavedToFile else LocalLyricsMessage.FileExportFailed,
                )
            }
        }

        fun fetchOnlineLyrics(mediaMetadata: MediaMetadata) {
            viewModelScope.launch {
                if (!isOnlineLyricsEnabled()) {
                    _messages.emit(LocalLyricsMessage.OnlineLyricsNotFound)
                    return@launch
                }
                _isLoading.value = true
                val fetched =
                    runCatching { onlineLyricsRepository.fetchAndSave(mediaMetadata) }
                        .getOrNull()
                if (!fetched.isNullOrBlank()) {
                    _lyrics.value = fetched
                    _messages.emit(LocalLyricsMessage.OnlineLyricsFound)
                } else {
                    _messages.emit(LocalLyricsMessage.OnlineLyricsNotFound)
                }
                _isLoading.value = false
            }
        }

        fun syncWithWhisper(mediaMetadata: MediaMetadata) {
            viewModelScope.launch {
                val text =
                    _lyrics.value?.takeIf(String::isNotBlank)
                        ?: runCatching { repository.loadLyrics(mediaMetadata.id) }.getOrNull()
                if (text.isNullOrBlank()) {
                    _messages.emit(LocalLyricsMessage.WhisperSyncNoLyrics)
                    return@launch
                }
                if (LyricsUtils.isLineSyncedLrc(text)) {
                    _messages.emit(LocalLyricsMessage.WhisperSyncCompleted)
                    return@launch
                }
                _isSyncing.value = true
                val result =
                    runCatching { transcriber.align(mediaMetadata.id, text) }
                        .getOrNull()
                _isSyncing.value = false
                when (result) {
                    is LyricsTranscriptionResult.Success -> {
                        _lyrics.value = result.lyrics
                        _messages.emit(LocalLyricsMessage.WhisperSyncCompleted)
                    }
                    else -> _messages.emit(LocalLyricsMessage.WhisperSyncFailed)
                }
            }
        }

        fun refresh(mediaMetadata: MediaMetadata) {
            viewModelScope.launch {
                _lyrics.value = runCatching { repository.loadLyrics(mediaMetadata.id) }.getOrNull()
            }
        }

        private suspend fun isOnlineLyricsEnabled(): Boolean =
            context.dataStore.getAsync(EnableLrcLibKey, true)
    }
