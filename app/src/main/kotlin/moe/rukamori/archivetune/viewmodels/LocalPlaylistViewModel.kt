/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.extensions.reversed
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.playlist.RemovePlaylistCoverUseCase
import moe.rukamori.archivetune.playlist.UpdatePlaylistCoverUseCase
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val updatePlaylistCover: UpdatePlaylistCoverUseCase,
        private val removePlaylistCover: RemovePlaylistCoverUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val playlistId = savedStateHandle.get<String>("playlistId")!!
        val playlist =
            database
                .playlist(playlistId)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)

        val sortType: StateFlow<PlaylistSongSortType> =
            playlist
                .map { it?.playlist?.songSortType.toEnum(PlaylistSongSortType.CUSTOM) }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistSongSortType.CUSTOM)

        val sortDescending: StateFlow<Boolean> =
            playlist
                .map { it?.playlist?.songSortDescending ?: true }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, true)

        val playlistSongs: StateFlow<List<PlaylistSong>> =
            combine(
                database.playlistSongs(playlistId),
                sortType,
                sortDescending,
                context.dataStore.data
                    .map { preferences -> preferences[HideVideoKey] ?: false }
                    .distinctUntilChanged(),
            ) { songs, sortType, sortDescending, hideVideo ->
                val visibleSongs =
                    songs
                        .filter { playlistSong -> playlistSong.song.artists.none { it.blockedAt != null } }
                        .filter { playlistSong -> !hideVideo || !playlistSong.song.song.isMusicVideo }
                when (sortType) {
                    PlaylistSongSortType.CUSTOM -> {
                        visibleSongs
                    }

                    PlaylistSongSortType.CREATE_DATE -> {
                        visibleSongs.sortedBy { it.map.id }
                    }

                    PlaylistSongSortType.NAME -> {
                        visibleSongs.sortedBy { it.song.song.title }
                    }

                    PlaylistSongSortType.ARTIST -> {
                        val collator = Collator.getInstance(Locale.getDefault())
                        collator.strength = Collator.PRIMARY
                        visibleSongs.sortedWith(
                            compareBy(collator) { song ->
                                song.song.artists.joinToString("") { artist -> artist.name }
                            },
                        )
                    }

                    PlaylistSongSortType.PLAY_TIME -> {
                        visibleSongs.sortedBy { it.song.song.totalPlayTime }
                    }
                }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun updateSortPreference(
            newSortType: PlaylistSongSortType,
            newSortDescending: Boolean,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                database.updatePlaylistSortPreference(
                    playlistId = playlistId,
                    sortType = newSortType.name,
                    descending = newSortDescending,
                )
            }
        }

        // Refresh is kept for pull-to-refresh compatibility: re-normalize positions locally.
        fun refresh() {
            viewModelScope.launch(Dispatchers.IO) {
                val sortedSongs =
                    database.playlistSongs(playlistId).first().sortedWith(compareBy({ it.map.position }, { it.map.id }))
                database.withTransaction {
                    sortedSongs.forEachIndexed { index, playlistSong ->
                        if (playlistSong.map.position != index) {
                            update(playlistSong.map.copy(position = index))
                        }
                    }
                }
            }
        }

        private val _coverState = MutableStateFlow<PlaylistCoverState>(PlaylistCoverState.Empty)
        val coverState = _coverState.asStateFlow()

        private val coverEventChannel = kotlinx.coroutines.channels.Channel<PlaylistCoverEvent>(kotlinx.coroutines.channels.Channel.BUFFERED)
        val coverEvents = coverEventChannel.receiveAsFlow()

        private var coverMutationJob: kotlinx.coroutines.Job? = null

        fun updateCover(uri: android.net.Uri) {
            mutateCover { updatePlaylistCover(playlistId, uri) }
        }

        fun removeCover() {
            mutateCover { removePlaylistCover(playlistId) }
        }

        private fun mutateCover(mutation: suspend () -> Unit) {
            if (coverMutationJob?.isActive == true) return
            coverMutationJob =
                viewModelScope.launch(Dispatchers.IO) {
                    _coverState.value = PlaylistCoverState.Loading
                    try {
                        mutation()
                        _coverState.value = PlaylistCoverState.Success
                        coverEventChannel.send(PlaylistCoverEvent.ShowMessage(R.string.playlist_cover_updated))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (throwable: Throwable) {
                        reportException(throwable)
                        _coverState.value = PlaylistCoverState.Error(R.string.playlist_cover_update_failed)
                        coverEventChannel.send(PlaylistCoverEvent.ShowMessage(R.string.playlist_cover_update_failed))
                    }
                }
        }
    }

@Immutable
sealed interface PlaylistCoverState {
    data object Loading : PlaylistCoverState

    data object Success : PlaylistCoverState

    data object Empty : PlaylistCoverState

    data class Error(
        @StringRes val messageRes: Int,
    ) : PlaylistCoverState
}

sealed interface PlaylistCoverEvent {
    data class ShowMessage(
        @StringRes val messageRes: Int,
    ) : PlaylistCoverEvent
}

