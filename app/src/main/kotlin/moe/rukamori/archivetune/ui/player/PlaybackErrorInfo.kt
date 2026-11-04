/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.media3.common.PlaybackException
import java.io.FileNotFoundException

internal enum class PlaybackErrorKind {
    FileUnavailable,
    NoStream,
    MalformedStream,
    Decoder,
    Unknown,
}

internal data class PlaybackErrorInfo(
    val kind: PlaybackErrorKind,
)

internal fun PlaybackException.toPlaybackErrorInfo(): PlaybackErrorInfo {
    val kind =
        when {
            findCause<FileNotFoundException>() != null -> PlaybackErrorKind.FileUnavailable

            errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackErrorKind.FileUnavailable

            errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackErrorKind.FileUnavailable

            errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> PlaybackErrorKind.MalformedStream

            errorCode in
                setOf(
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                )
            -> PlaybackErrorKind.Decoder

            else -> PlaybackErrorKind.Unknown
        }

    return PlaybackErrorInfo(kind = kind)
}

private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var throwable: Throwable? = this
    while (throwable != null) {
        if (throwable is T) return throwable
        throwable = throwable.cause
    }
    return null
}
