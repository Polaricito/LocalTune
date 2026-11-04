/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.library

import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import moe.rukamori.archivetune.localmedia.LocalSongScanConfig
import moe.rukamori.archivetune.localmedia.ScanLocalMusicUseCase
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
        private val scanLocalMusic: ScanLocalMusicUseCase,
    ) {
        suspend operator fun invoke(target: LibrarySyncTarget): RefreshLibraryResult =
            try {
                scanLocalMusic(LocalSongScanConfig())
                RefreshLibraryResult.Success
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Local library refresh failed for %s", target)
                RefreshLibraryResult.Failure(LibrarySyncFailure.RequestFailed)
            }
    }
