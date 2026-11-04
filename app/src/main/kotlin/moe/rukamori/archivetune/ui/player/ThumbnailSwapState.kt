/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember

@Immutable
data class ThumbnailSwapState(
    val displayUrl: String?,
    val isYTReady: Boolean,
    val ytUrl: String?,
)

@Composable
@Suppress("UNUSED_PARAMETER")
fun rememberThumbnailSwapState(
    videoId: String?,
    ytmUrl: String?,
    lowDataMode: Boolean,
    isMusicVideo: Boolean = false,
): ThumbnailSwapState =
    remember(ytmUrl) {
        ThumbnailSwapState(displayUrl = ytmUrl, isYTReady = false, ytUrl = null)
    }
