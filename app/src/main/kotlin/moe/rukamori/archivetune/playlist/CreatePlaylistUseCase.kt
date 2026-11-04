/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playlist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import java.time.LocalDateTime
import javax.inject.Inject

class CreatePlaylistUseCase
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        suspend operator fun invoke(
            name: String,
        ) {
            val normalizedName = name.trim()
            require(normalizedName.isNotEmpty())

            withContext(Dispatchers.IO) {
                database.withTransaction {
                    insert(
                        PlaylistEntity(
                            name = normalizedName,
                            browseId = null,
                            bookmarkedAt = LocalDateTime.now(),
                            isEditable = true,
                        ),
                    )
                }
            }
        }
    }
