/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compatibility types for the player artwork slots.
 *
 * ArchiveTune no longer fetches animated canvas artwork from remote providers, so
 * [CanvasVideo] is never populated. The types are kept so the player layouts can
 * retain their optional artwork slot without pulling in any network code.
 */
enum class CanvasSource {
    LOCAL,
}

data class CanvasVideo(
    val source: CanvasSource? = null,
    val animated: String? = null,
    val videoUrl: String? = null,
)

@Composable
fun CanvasArtworkPlayer(
    source: CanvasSource?,
    primaryUrl: String?,
    fallbackUrl: String?,
    modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop,
) {
    // Animated artwork is intentionally unavailable in the fully local build.
}
