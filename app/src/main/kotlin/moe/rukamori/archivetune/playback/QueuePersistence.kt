/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import moe.rukamori.archivetune.models.MediaMetadata
import timber.log.Timber
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small on-disk snapshot of the playback queue. The queue used to live only in
 * memory, so removing the task (or an OS kill) wiped it. The snapshot is
 * written atomically and restored the next time the service starts.
 */
@Singleton
class QueuePersistence
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val file = File(context.filesDir, FileName)
        private val lock = Any()

        data class Snapshot(
            val items: List<MediaMetadata>,
            val index: Int,
            val positionMs: Long,
            val title: String?,
            val playWhenReady: Boolean,
        ) : Serializable {
            companion object {
                private const val serialVersionUID = 1L
            }
        }

        fun save(snapshot: Snapshot) {
            synchronized(lock) {
                runCatching {
                    val temporary = File(file.parentFile, "${file.name}.tmp")
                    ObjectOutputStream(temporary.outputStream().buffered()).use { output ->
                        output.writeObject(snapshot)
                    }
                    if (file.exists()) file.delete()
                    if (!temporary.renameTo(file)) {
                        temporary.copyTo(file, overwrite = true)
                        temporary.delete()
                    }
                }.onFailure { Timber.tag(TAG).w(it, "Could not persist playback queue") }
            }
        }

        fun load(): Snapshot? =
            synchronized(lock) {
                if (!file.exists()) return@synchronized null
                runCatching {
                    ObjectInputStream(file.inputStream().buffered()).use { input ->
                        input.readObject() as? Snapshot
                    }
                }.onFailure {
                    Timber.tag(TAG).w(it, "Could not restore playback queue")
                    file.delete()
                }.getOrNull()
            }

        fun clear() {
            synchronized(lock) {
                runCatching { file.delete() }
            }
        }

        private companion object {
            const val TAG = "QueuePersistence"
            const val FileName = "playback_queue.ser"
        }
    }
