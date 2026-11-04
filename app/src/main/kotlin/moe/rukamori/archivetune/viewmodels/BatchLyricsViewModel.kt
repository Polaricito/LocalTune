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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.lyrics.LocalLyricsTranscriber
import moe.rukamori.archivetune.lyrics.LyricsTranscriptionResult
import javax.inject.Inject

data class BatchLyricsPlaylist(
    val id: String,
    val name: String,
)

data class BatchLyricsUiState(
    val playlists: List<BatchLyricsPlaylist> = emptyList(),
    val selectedPlaylistId: String? = null,
    val selectedPlaylistName: String? = null,
    val songCount: Int = 0,
    val overwriteExisting: Boolean = false,
    val isRunning: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val succeeded: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val currentTitle: String? = null,
    val finished: Boolean = false,
)

@HiltViewModel
class BatchLyricsViewModel
    @Inject
    constructor(
        database: MusicDatabase,
        private val transcriber: LocalLyricsTranscriber,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(BatchLyricsUiState())
        private val selectedPlaylistId = MutableStateFlow<String?>(null)

        private val playlists =
            database
                .playlistsByCreateDateAsc()
                .map { playlists ->
                    playlists.map { playlist ->
                        BatchLyricsPlaylist(
                            id = playlist.playlist.id,
                            name = playlist.playlist.name,
                        )
                    }
                }

        private val selectedSongs =
            selectedPlaylistId
                .flatMapLatest { playlistId ->
                    if (playlistId == null) {
                        database.localSongs()
                    } else {
                        database.playlistSongs(playlistId).map { songs -> songs.map { it.song } }
                    }
                }.stateIn(
                    viewModelScope,
                    SharingStarted.Eagerly,
                    emptyList(),
                )

        val state: StateFlow<BatchLyricsUiState> =
            combine(mutableState, playlists, selectedSongs) { current, playlists, songs ->
                current.copy(
                    playlists = playlists,
                    selectedPlaylistName =
                        current.selectedPlaylistId?.let { id ->
                            playlists.firstOrNull { it.id == id }?.name
                        },
                    songCount = songs.size,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                BatchLyricsUiState(),
            )

        private var batchJob: Job? = null

        fun selectPlaylist(playlistId: String?) {
            if (mutableState.value.isRunning) return
            selectedPlaylistId.value = playlistId
            mutableState.update { it.copy(selectedPlaylistId = playlistId, finished = false) }
        }

        fun setOverwriteExisting(overwrite: Boolean) {
            if (mutableState.value.isRunning) return
            mutableState.update { it.copy(overwriteExisting = overwrite) }
        }

        fun start() {
            if (mutableState.value.isRunning) return
            val songs = selectedSongs.value
            if (songs.isEmpty()) return

            val overwrite = mutableState.value.overwriteExisting
            batchJob =
                viewModelScope.launch {
                    mutableState.update {
                        it.copy(
                            isRunning = true,
                            completed = 0,
                            total = songs.size,
                            succeeded = 0,
                            skipped = 0,
                            failed = 0,
                            currentTitle = null,
                            finished = false,
                        )
                    }

                    var succeeded = 0
                    var skipped = 0
                    var failed = 0
                    var completed = 0

                    try {
                        for (song in songs) {
                            if (!isActive) break
                            mutableState.update { it.copy(currentTitle = song.displayName()) }

                            when (transcriber.transcribe(song.id, overwrite)) {
                                is LyricsTranscriptionResult.Success -> succeeded += 1
                                LyricsTranscriptionResult.Skipped -> skipped += 1
                                is LyricsTranscriptionResult.Failure -> failed += 1
                            }

                            completed += 1
                            mutableState.update {
                                it.copy(
                                    completed = completed,
                                    succeeded = succeeded,
                                    skipped = skipped,
                                    failed = failed,
                                )
                            }
                        }
                    } finally {
                        mutableState.update {
                            it.copy(
                                isRunning = false,
                                currentTitle = null,
                                finished = true,
                            )
                        }
                        batchJob = null
                    }
                }
        }

        fun cancel() {
            batchJob?.cancel()
        }

        private fun Song.displayName(): String {
            val artist = artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() }
            return if (artist == null) song.title else "${song.title} — $artist"
        }
    }
