/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.lyrics.LocalLyricsRepository
import moe.rukamori.archivetune.models.MediaMetadata
import javax.inject.Inject

sealed interface LocalLyricsMessage {
    data object Saved : LocalLyricsMessage

    data object SavedToFile : LocalLyricsMessage

    data object FileExportFailed : LocalLyricsMessage

    data object LoadFailed : LocalLyricsMessage
}

@HiltViewModel
class LocalLyricsViewModel
    @Inject
    constructor(
        private val repository: LocalLyricsRepository,
    ) : ViewModel() {
        private val _lyrics = MutableStateFlow<String?>(null)
        val lyrics: StateFlow<String?> = _lyrics.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _messages = MutableSharedFlow<LocalLyricsMessage>(extraBufferCapacity = 4)
        val messages: SharedFlow<LocalLyricsMessage> = _messages.asSharedFlow()

        fun load(mediaMetadata: MediaMetadata) {
            viewModelScope.launch {
                _isLoading.value = true
                val loaded = runCatching { repository.loadLyrics(mediaMetadata.id) }.getOrNull()
                _lyrics.value = loaded
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
                _lyrics.value = lyrics
                _messages.emit(if (result.savedToFile) LocalLyricsMessage.Saved else LocalLyricsMessage.Saved)
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

        fun refresh(mediaMetadata: MediaMetadata) {
            viewModelScope.launch {
                _lyrics.value = runCatching { repository.loadLyrics(mediaMetadata.id) }.getOrNull()
            }
        }
    }
