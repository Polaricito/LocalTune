/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.AlbumFilter
import moe.rukamori.archivetune.constants.AlbumFilterKey
import moe.rukamori.archivetune.constants.AlbumSortDescendingKey
import moe.rukamori.archivetune.constants.AlbumSortType
import moe.rukamori.archivetune.constants.AlbumSortTypeKey
import moe.rukamori.archivetune.constants.ArtistFilter
import moe.rukamori.archivetune.constants.ArtistFilterKey
import moe.rukamori.archivetune.constants.ArtistSongSortDescendingKey
import moe.rukamori.archivetune.constants.ArtistSongSortType
import moe.rukamori.archivetune.constants.ArtistSongSortTypeKey
import moe.rukamori.archivetune.constants.ArtistSortDescendingKey
import moe.rukamori.archivetune.constants.ArtistSortType
import moe.rukamori.archivetune.constants.ArtistSortTypeKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.PlaylistSortDescendingKey
import moe.rukamori.archivetune.constants.PlaylistSortType
import moe.rukamori.archivetune.constants.PlaylistSortTypeKey
import moe.rukamori.archivetune.constants.SongFilter
import moe.rukamori.archivetune.constants.SongFilterKey
import moe.rukamori.archivetune.constants.SongSortDescendingKey
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.constants.SongSortTypeKey
import moe.rukamori.archivetune.constants.TopSize
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.extensions.filterExplicit
import moe.rukamori.archivetune.extensions.filterExplicitAlbums
import moe.rukamori.archivetune.extensions.filterVideo
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.library.LibrarySyncFailure
import moe.rukamori.archivetune.library.LibrarySyncTarget
import moe.rukamori.archivetune.library.RefreshLibraryResult
import moe.rukamori.archivetune.library.RefreshLibraryUseCase
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

@androidx.compose.runtime.Immutable
sealed interface LibraryRefreshState {
    data object Loading : LibraryRefreshState

    data object Success : LibraryRefreshState

    data object Empty : LibraryRefreshState

    data class Error(val failure: LibrarySyncFailure) : LibraryRefreshState
}

abstract class LibraryRefreshViewModel(
    private val refreshLibraryUseCase: RefreshLibraryUseCase,
) : ViewModel() {
    private val _refreshState = MutableStateFlow<LibraryRefreshState>(LibraryRefreshState.Empty)
    val refreshState = _refreshState.asStateFlow()

    @Volatile
    private var refreshJob: Job? = null

    @Volatile
    private var activeTarget: LibrarySyncTarget? = null

    @Volatile
    private var refreshGeneration = 0L

    protected fun refreshLibrary(target: LibrarySyncTarget) {
        if (activeTarget == target && refreshJob?.isActive == true) return

        refreshJob?.cancel()
        activeTarget = target
        val generation = ++refreshGeneration
        _refreshState.value = LibraryRefreshState.Loading
        refreshJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val result = refreshLibraryUseCase(target)
                    if (generation == refreshGeneration) {
                        _refreshState.value = when (result) {
                            RefreshLibraryResult.Success -> LibraryRefreshState.Success
                            is RefreshLibraryResult.Failure -> LibraryRefreshState.Error(result.reason)
                        }
                    }
                } catch (e: CancellationException) {
                    if (generation == refreshGeneration) {
                        _refreshState.value = LibraryRefreshState.Empty
                    }
                    throw e
                } finally {
                    if (generation == refreshGeneration) {
                        refreshJob = null
                        activeTarget = null
                    }
                }
            }
    }

    fun onRefreshErrorShown() {
        if (_refreshState.value is LibraryRefreshState.Error) {
            _refreshState.value = LibraryRefreshState.Empty
        }
    }
}

@HiltViewModel
class LibrarySongsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        refreshLibrary: RefreshLibraryUseCase,
    ) : LibraryRefreshViewModel(refreshLibrary) {
        val allSongs =
            context.dataStore.data
                .map {
                    Triple(
                        Triple(
                            it[SongFilterKey].toEnum(SongFilter.LIKED),
                            it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                            (it[SongSortDescendingKey] ?: true),
                        ),
                        it[HideExplicitKey] ?: false,
                        it[HideVideoKey] ?: false,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (filterSort, hideExplicit, hideVideo) ->
                    val (filter, sortType, descending) = filterSort
                    when (filter) {
                        SongFilter.LIBRARY -> {
                            database.songs(sortType, descending, hideVideo).map { it.filterExplicit(hideExplicit) }
                        }

                        SongFilter.LIKED -> {
                            database.likedSongs(sortType, descending, hideVideo).map { it.filterExplicit(hideExplicit) }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun refresh(filter: SongFilter) {
            when (filter) {
                SongFilter.LIKED -> refreshLibrary(LibrarySyncTarget.LikedSongs)
                SongFilter.LIBRARY -> refreshLibrary(LibrarySyncTarget.Songs)
            }
        }
    }

@HiltViewModel
class LibraryArtistsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        refreshLibrary: RefreshLibraryUseCase,
    ) : LibraryRefreshViewModel(refreshLibrary) {
        val allArtists =
            context.dataStore.data
                .map {
                    Triple(
                        it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                        it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                        it[ArtistSortDescendingKey] ?: true,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (filter, sortType, descending) ->
                    when (filter) {
                        ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                        ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun refresh(filter: ArtistFilter) {
            refreshLibrary(
                if (filter == ArtistFilter.LIBRARY) LibrarySyncTarget.All else LibrarySyncTarget.Artists,
            )
        }
    }

@HiltViewModel
class LibraryAlbumsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        refreshLibrary: RefreshLibraryUseCase,
    ) : LibraryRefreshViewModel(refreshLibrary) {
        val allAlbums =
            context.dataStore.data
                .map {
                    Pair(
                        Triple(
                            it[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                            it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                            it[AlbumSortDescendingKey] ?: true,
                        ),
                        it[HideExplicitKey] ?: false,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (filterSort, hideExplicit) ->
                    val (filter, sortType, descending) = filterSort
                    when (filter) {
                        AlbumFilter.LIBRARY -> {
                            database.albums(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                        }

                        AlbumFilter.LIKED -> {
                            database.albumsLiked(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun refresh(filter: AlbumFilter) {
            when (filter) {
                AlbumFilter.LIBRARY -> refreshLibrary(LibrarySyncTarget.All)
                AlbumFilter.LIKED -> refreshLibrary(LibrarySyncTarget.Albums)
            }
        }
    }

@HiltViewModel
class LibraryPlaylistsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val database: MusicDatabase,
        refreshLibrary: RefreshLibraryUseCase,
    ) : LibraryRefreshViewModel(refreshLibrary) {
        val allPlaylists =
            context.dataStore.data
                .map {
                    it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CUSTOM) to (
                        it[PlaylistSortDescendingKey]
                            ?: true
                        )
                }.distinctUntilChanged()
                .flatMapLatest { (sortType, descending) ->
                    database.playlists(sortType, descending)
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun sync() {
            refreshLibrary(LibrarySyncTarget.Playlists)
        }

        fun updateCustomPlaylistOrder(playlists: List<Playlist>) {
            if (playlists.isEmpty()) return
            viewModelScope.launch(Dispatchers.IO) {
                database.withTransaction {
                    playlists.forEachIndexed { index, playlist ->
                        setPlaylistCustomOrder(playlist.id, index)
                    }
                }
            }
        }

        val topValue =
            context.dataStore.data
                .map { it[TopSize] ?: "50" }
                .distinctUntilChanged()
    }

@HiltViewModel
class ArtistSongsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val artistId = savedStateHandle.get<String>("artistId")!!
        val artist =
            database
                .artist(artistId)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)

        val songs =
            context.dataStore.data
                .map {
                    Triple(
                        it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (
                            it[ArtistSongSortDescendingKey]
                                ?: true
                        ),
                        it[HideExplicitKey] ?: false,
                        it[HideVideoKey] ?: false,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (sortDesc, hideExplicit, hideVideo) ->
                    val (sortType, descending) = sortDesc
                    database.artistSongs(artistId, sortType, descending).map {
                        it.filterExplicit(hideExplicit).filterVideo(hideVideo)
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor() : ViewModel() {
        private val curScreen = mutableStateOf(LibraryFilter.PLAYLISTS)
        val filter: MutableState<LibraryFilter> = curScreen
    }
