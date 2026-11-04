/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import java.time.LocalDateTime
import javax.inject.Inject

class UpdatePlaylistCoverUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        suspend operator fun invoke(
            playlistId: String,
            uri: Uri,
        ) = withContext(Dispatchers.IO) {
            val playlist = requireNotNull(getPlaylist(playlistId))
            persistReadPermission(uri)
            try {
                val previous = updateThumbnail(playlist.id, uri.toString())
                releaseCoverPermission(previous.thumbnailUrl, except = uri.toString())
            } catch (throwable: Throwable) {
                releaseReadPermission(uri)
                throw throwable
            }
        }

        private suspend fun getPlaylist(playlistId: String): PlaylistEntity? =
            database.playlist(playlistId).first()?.playlist

        private suspend fun updateThumbnail(
            playlistId: String,
            thumbnailUrl: String?,
        ): PlaylistEntity {
            val current =
                requireNotNull(getPlaylist(playlistId)) {
                    "Playlist $playlistId no longer exists"
                }
            database.withTransaction {
                update(
                    current.copy(
                        thumbnailUrl = thumbnailUrl,
                        lastUpdateTime = LocalDateTime.now(),
                    ),
                )
            }
            return current
        }

        private fun persistReadPermission(uri: Uri) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }

        private fun releaseReadPermission(uri: Uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }

        private fun releaseCoverPermission(
            thumbnailUrl: String?,
            except: String?,
        ) {
            thumbnailUrl
                ?.takeIf { it != except }
                ?.let(Uri::parse)
                ?.takeIf { it.scheme == "content" }
                ?.let(::releaseReadPermission)
        }
    }

class RemovePlaylistCoverUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        suspend operator fun invoke(playlistId: String) = withContext(Dispatchers.IO) {
            val current =
                requireNotNull(database.playlist(playlistId).first()?.playlist) {
                    "Playlist $playlistId no longer exists"
                }
            database.withTransaction {
                update(
                    current.copy(
                        thumbnailUrl = null,
                        lastUpdateTime = LocalDateTime.now(),
                    ),
                )
            }
            current.thumbnailUrl
                ?.let(Uri::parse)
                ?.takeIf { it.scheme == "content" }
                ?.let { uri ->
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
        }
    }
