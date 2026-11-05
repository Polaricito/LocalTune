/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.constants.LocalSongsExcludedFoldersKey
import moe.rukamori.archivetune.constants.LocalSongsIncludedFoldersKey
import moe.rukamori.archivetune.constants.LocalSongsMinDurationSecondsKey
import moe.rukamori.archivetune.localmedia.LocalSongScanConfig
import moe.rukamori.archivetune.localmedia.ScanLocalMusicUseCase
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber

enum class LibrarySyncTarget {
    All,
    LikedSongs,
    Songs,
    Artists,
    Albums,
    Playlists,
    Podcasts,
}

enum class LibrarySyncFailure {
    RequestFailed,
}

sealed interface RefreshLibraryResult {
    data object Success : RefreshLibraryResult

    data class Failure(val reason: LibrarySyncFailure) : RefreshLibraryResult
}

class RefreshLibraryUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val scanLocalMusic: ScanLocalMusicUseCase,
    ) {
        suspend operator fun invoke(target: LibrarySyncTarget): RefreshLibraryResult =
            try {
                val preferences = context.dataStore.data.first()
                scanLocalMusic(
                    LocalSongScanConfig(
                        minimumDurationSeconds = preferences[LocalSongsMinDurationSecondsKey] ?: 0,
                        includedFolders =
                            preferences[LocalSongsIncludedFoldersKey]
                                ?: LocalSongScanConfig.DefaultIncludedFolders,
                        excludedFolders = preferences[LocalSongsExcludedFoldersKey] ?: emptySet(),
                    ),
                )
                RefreshLibraryResult.Success
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Local library refresh failed for %s", target)
                RefreshLibraryResult.Failure(LibrarySyncFailure.RequestFailed)
            }
    }
