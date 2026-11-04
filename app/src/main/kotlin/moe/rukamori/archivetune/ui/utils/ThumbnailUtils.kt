/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

/**
 * Kept for API compatibility with the former YouTube thumbnail pipeline.
 * ArchiveTune is fully local, so artwork URLs are always used as-is.
 */
enum class YtimgResizePolicy {
    PreserveOriginal,
    AllowAnyAspect,
    MatchSourceAspect,
}

@Suppress("UNUSED_PARAMETER")
fun String?.resize(
    width: Int? = null,
    height: Int? = null,
    sizeBuckets: List<Int>? = null,
    maxresAllowed: Boolean = false,
    ytimgResizePolicy: YtimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
    sourceAspectRatio: Float? = null,
): String? = this

fun String?.highRes(): String? = this
