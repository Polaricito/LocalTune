/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin Kotlin wrapper around the vendored whisper.cpp build.
 *
 * The model is bundled as an asset, copied once to internal storage and then
 * loaded lazily. Transcription is serialized because a whisper context is not
 * safe to use from multiple threads at the same time.
 */
data class WhisperSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

@Singleton
class WhisperEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val mutex = Mutex()

        @Volatile
        private var contextPointer: Long = 0L

        suspend fun transcribe(
            samples: FloatArray,
            language: String? = null,
        ): String? =
            mutex.withLock {
                withContext(Dispatchers.Default) {
                    val pointer = ensureContext() ?: return@withContext null
                    runCatching {
                        nativeTranscribe(pointer, samples, threadCount(), language)
                    }.onFailure {
                        Timber.tag(TAG).e(it, "whisper.cpp transcription failed")
                    }.getOrNull()
                }
            }

        suspend fun transcribeSegments(
            samples: FloatArray,
            language: String? = null,
        ): List<WhisperSegment>? =
            mutex.withLock {
                withContext(Dispatchers.Default) {
                    val pointer = ensureContext() ?: return@withContext null
                    val raw =
                        runCatching {
                            nativeTranscribeSegments(pointer, samples, threadCount(), language)
                        }.onFailure {
                            Timber.tag(TAG).e(it, "whisper.cpp segment transcription failed")
                        }.getOrNull()
                    raw
                        ?.lineSequence()
                        ?.mapNotNull { line ->
                            val parts = line.split('\t', limit = 3)
                            if (parts.size != 3) return@mapNotNull null
                            val start = parts[0].toLongOrNull() ?: return@mapNotNull null
                            val end = parts[1].toLongOrNull() ?: return@mapNotNull null
                            val text = parts[2].trim()
                            if (text.isEmpty()) null else WhisperSegment(start, end, text)
                        }?.toList()
                        ?.takeIf { it.isNotEmpty() }
                        ?.also { segments ->
                            Timber
                                .tag(TAG)
                                .i(
                                    "Transcribed %d segments, span 0..%d ms",
                                    segments.size,
                                    segments.last().endMs,
                                )
                        }
                }
            }

        private fun threadCount(): Int =
            (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, MaxThreads)

        private suspend fun ensureContext(): Long? {
            if (contextPointer != 0L) return contextPointer

            val modelFile =
                withContext(Dispatchers.IO) {
                    ensureModelFile()
                } ?: return null

            val pointer = nativeInit(modelFile.absolutePath)
            if (pointer == 0L) {
                Timber.tag(TAG).e("Could not initialize whisper.cpp")
                return null
            }
            contextPointer = pointer
            return pointer
        }

        private fun ensureModelFile(): File? {
            val modelDir = File(context.noBackupFilesDir, "whisper")
            if (!modelDir.exists() && !modelDir.mkdirs()) {
                Timber.tag(TAG).e("Could not create whisper model directory")
                return null
            }

            val modelFile = File(modelDir, MODEL_FILE_NAME)
            if (modelFile.length() == MODEL_SIZE_BYTES) return modelFile

            val temporaryFile = File(modelDir, "$MODEL_FILE_NAME.tmp")
            val copied =
                runCatching {
                    context.assets.open(MODEL_ASSET).use { input ->
                        temporaryFile.outputStream().use { output ->
                            input.copyTo(output, CopyBufferSize)
                        }
                    }
                    if (temporaryFile.length() != MODEL_SIZE_BYTES) {
                        error("Incomplete whisper model copy")
                    }
                    if (modelFile.exists()) modelFile.delete()
                    if (!temporaryFile.renameTo(modelFile)) {
                        temporaryFile.copyTo(modelFile, overwrite = true)
                        temporaryFile.delete()
                    }
                }.onFailure {
                    Timber.tag(TAG).e(it, "Could not copy whisper model")
                    temporaryFile.delete()
                }.isSuccess

            return if (copied && modelFile.length() == MODEL_SIZE_BYTES) modelFile else null
        }

        @Suppress("unused")
        fun release() {
            if (contextPointer != 0L) {
                nativeRelease(contextPointer)
                contextPointer = 0L
            }
        }

        private external fun nativeInit(modelPath: String): Long

        private external fun nativeRelease(contextPointer: Long)

        private external fun nativeTranscribe(
            contextPointer: Long,
            samples: FloatArray,
            threadCount: Int,
            language: String?,
        ): String?

        private external fun nativeTranscribeSegments(
            contextPointer: Long,
            samples: FloatArray,
            threadCount: Int,
            language: String?,
        ): String?

        companion object {
            private const val TAG = "WhisperEngine"
            private const val MODEL_ASSET = "whisper/ggml-tiny-q5_1.bin"
            private const val MODEL_FILE_NAME = "ggml-tiny-q5_1.bin"
            private const val MODEL_SIZE_BYTES = 32_152_673L
            private const val CopyBufferSize = 64 * 1024
            private const val MaxThreads = 4

            init {
                System.loadLibrary("archivetune_whisper")
            }
        }
    }
