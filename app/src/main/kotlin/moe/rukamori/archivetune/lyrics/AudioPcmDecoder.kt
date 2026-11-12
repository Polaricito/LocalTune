/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/**
 * Decodes any audio track supported by the platform into the mono 16 kHz
 * floating point PCM that whisper.cpp expects.
 */
@Singleton
class AudioPcmDecoder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun decode(uri: Uri): FloatArray? =
            withContext(Dispatchers.IO) {
                runCatching { decodeInternal(uri) }
                    .onFailure { Timber.tag(TAG).e(it, "Could not decode %s", uri) }
                    .getOrNull()
            }

        private fun decodeInternal(uri: Uri): FloatArray? {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null

            try {
                extractor.setDataSource(context, uri, null)
                val trackIndex =
                    (0 until extractor.trackCount).firstOrNull { index ->
                        extractor
                            .getTrackFormat(index)
                            .getString(MediaFormat.KEY_MIME)
                            ?.startsWith("audio/") == true
                    } ?: return null

                extractor.selectTrack(trackIndex)
                val inputFormat = extractor.getTrackFormat(trackIndex)
                val mimeType = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
                val sourceSampleRate =
                    inputFormat
                        .getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        .takeIf { it > 0 } ?: DefaultSampleRate
                val sourceChannelCount =
                    inputFormat
                        .getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        .coerceAtLeast(1)

                val decoder = MediaCodec.createDecoderByType(mimeType)
                codec = decoder
                decoder.configure(inputFormat, null, null, 0)
                decoder.start()

                var outputSampleRate = sourceSampleRate
                var outputChannelCount = sourceChannelCount
                var outputEncoding = AudioFormat.ENCODING_PCM_16BIT
                var resampler: MonoResampler? = null

                val bufferInfo = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var idleOutputPolls = 0
                var totalSourceFrames = 0L

                while (!outputDone) {
                    if (!inputDone) {
                        val inputIndex = decoder.dequeueInputBuffer(CodecTimeoutUs)
                        if (inputIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputIndex)
                            if (inputBuffer == null) {
                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputDone = true
                            } else {
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    decoder.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                    )
                                    inputDone = true
                                } else {
                                    decoder.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        sampleSize,
                                        extractor.sampleTime.coerceAtLeast(0),
                                        0,
                                    )
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, CodecTimeoutUs)
                    when {
                        outputIndex >= 0 -> {
                            idleOutputPolls = 0
                            val outputBuffer = decoder.getOutputBuffer(outputIndex)
                            if (outputBuffer != null &&
                                bufferInfo.size > 0 &&
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val mono = decodeToMono(outputBuffer, outputEncoding, outputChannelCount)
                                if (mono.isNotEmpty()) {
                                    totalSourceFrames += mono.size
                                    val activeResampler =
                                        resampler ?: MonoResampler(outputSampleRate).also { resampler = it }
                                    activeResampler.feed(mono)
                                }
                            }
                            decoder.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                            if ((resampler?.durationSeconds ?: 0.0) >= MaxDurationSeconds) {
                                outputDone = true
                            }
                        }

                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = decoder.outputFormat
                            outputSampleRate =
                                outputFormat
                                    .getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                    .takeIf { it > 0 } ?: sourceSampleRate
                            outputChannelCount =
                                outputFormat
                                    .getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                    .coerceAtLeast(1)
                            outputEncoding =
                                if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                    outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                } else {
                                    AudioFormat.ENCODING_PCM_16BIT
                                }
                            Timber
                                .tag(TAG)
                                .i(
                                    "Decoder format: %d Hz, %d ch, encoding %d (source %d Hz, %d ch)",
                                    outputSampleRate,
                                    outputChannelCount,
                                    outputEncoding,
                                    sourceSampleRate,
                                    sourceChannelCount,
                                )
                        }

                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (inputDone) {
                                idleOutputPolls += 1
                                if (idleOutputPolls > MaxIdleOutputPolls) {
                                    outputDone = true
                                }
                            }
                        }
                    }
                }

                val output = resampler?.toFloatArray()?.takeIf { it.isNotEmpty() }
                Timber
                    .tag(TAG)
                    .i(
                        "Decoded %d source frames -> %d samples at %d Hz (%.1f s)",
                        totalSourceFrames,
                        output?.size ?: 0,
                        TargetSampleRate,
                        (output?.size ?: 0).toDouble() / TargetSampleRate,
                    )
                return output
            } finally {
                codec?.runCatching { stop() }
                codec?.runCatching { release() }
                extractor.release()
            }
        }

        private fun decodeToMono(
            buffer: ByteBuffer,
            encoding: Int,
            channelCount: Int,
        ): FloatArray {
            val channels = channelCount.coerceAtLeast(1)
            return when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> {
                    val floats = buffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    val frames = floats.remaining() / channels
                    val mono = FloatArray(frames)
                    for (frame in 0 until frames) {
                        var sum = 0f
                        repeat(channels) { sum += floats.get() }
                        mono[frame] = sum / channels
                    }
                    mono
                }

                AudioFormat.ENCODING_PCM_8BIT -> {
                    val bytes = buffer
                    val frames = bytes.remaining() / channels
                    val mono = FloatArray(frames)
                    for (frame in 0 until frames) {
                        var sum = 0f
                        repeat(channels) {
                            val value = (bytes.get().toInt() and 0xFF) - 128
                            sum += value / 128f
                        }
                        mono[frame] = sum / channels
                    }
                    mono
                }

                else -> {
                    val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val frames = shorts.remaining() / channels
                    val mono = FloatArray(frames)
                    for (frame in 0 until frames) {
                        var sum = 0f
                        repeat(channels) { sum += shorts.get() / 32768f }
                        mono[frame] = sum / channels
                    }
                    mono
                }
            }
        }

        private class MonoResampler(
            sourceSampleRate: Int,
        ) {
            private val step = sourceSampleRate.coerceAtLeast(1).toDouble() / TargetSampleRate
            private var nextSourcePosition = 0.0
            private var processedSourceSamples = 0L
            private val output = FloatArrayBuilder()

            val durationSeconds: Double
                get() = output.size.toDouble() / TargetSampleRate

            fun feed(chunk: FloatArray) {
                if (chunk.isEmpty()) return
                val chunkStart = processedSourceSamples
                val chunkEnd = chunkStart + chunk.size
                while (nextSourcePosition < chunkEnd) {
                    val local = (nextSourcePosition - chunkStart).toInt()
                    if (local < 0 || local >= chunk.size) {
                        nextSourcePosition += step
                        continue
                    }
                    val nextLocal = (local + 1).coerceAtMost(chunk.lastIndex)
                    val fraction = (nextSourcePosition - floor(nextSourcePosition)).toFloat()
                    val current = chunk[local]
                    val next = chunk[nextLocal]
                    output.add(current + (next - current) * fraction)
                    nextSourcePosition += step
                }
                processedSourceSamples = chunkEnd
            }

            fun toFloatArray(): FloatArray = output.toFloatArray()
        }

        private class FloatArrayBuilder(
            initialCapacity: Int = TargetSampleRate * 30,
        ) {
            private var values = FloatArray(initialCapacity)

            var size: Int = 0
                private set

            fun add(value: Float) {
                if (size == values.size) {
                    values = values.copyOf(values.size * 2)
                }
                values[size] = value
                size += 1
            }

            fun toFloatArray(): FloatArray = values.copyOf(size)
        }

        private companion object {
            const val TAG = "AudioPcmDecoder"
            const val TargetSampleRate = 16_000
            const val DefaultSampleRate = 44_100
            const val CodecTimeoutUs = 10_000L
            const val MaxIdleOutputPolls = 500
            const val MaxDurationSeconds = 15.0 * 60.0
        }
    }
