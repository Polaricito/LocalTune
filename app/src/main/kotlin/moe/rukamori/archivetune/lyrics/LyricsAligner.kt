/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

/**
 * Aligns plain-text lyric lines with whisper.cpp segment timestamps.
 *
 * whisper.cpp does not do true forced alignment, so this uses a monotonic
 * token-overlap match and fills the remaining gaps by interpolation. The
 * result is good enough for karaoke-style line highlighting and is fully
 * on-device.
 */
object LyricsAligner {
    private const val MatchWindow = 10
    private const val MinimumScore = 0.34
    private const val FallbackLineGapMs = 3_000L

    fun align(
        plainLyrics: String?,
        segments: List<WhisperSegment>,
    ): List<LyricsEntry>? {
        val lines =
            LyricsUtils
                .normalizeLyricsText(plainLyrics)
                .lineSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("[") }
                .toList()
        if (lines.isEmpty() || segments.isEmpty()) return null

        val endTime = segments.maxOf { it.endMs }.coerceAtLeast(0L)

        val lineTokens = lines.map(::tokens)
        val segmentTokens = segments.map { tokens(it.text) }
        val matches = IntArray(lines.size) { -1 }
        var cursor = 0
        var matchedCount = 0

        lines.indices.forEach { index ->
            val line = lineTokens[index]
            if (line.isEmpty()) return@forEach
            var bestIndex = -1
            var bestScore = 0.0
            val last = minOf(segments.lastIndex, cursor + MatchWindow)
            for (segmentIndex in cursor..last) {
                val score = similarity(line, segmentTokens[segmentIndex])
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = segmentIndex
                }
            }
            if (bestIndex >= 0 && bestScore >= MinimumScore) {
                matches[index] = bestIndex
                cursor = bestIndex + 1
                matchedCount++
            }
        }

        if (matchedCount == 0) return null

        // When whisper only matches a handful of lines, extrapolating the rest
        // with a fixed gap can push timestamps far past the end of the song.
        // Distribute every line evenly across the detected audio span instead.
        val minimumMatches = max(1, (lines.size * 0.2).toInt())
        if (matchedCount < minimumMatches) {
            val span = endTime.coerceAtLeast(lines.size.toLong())
            return lines.indices.map { index ->
                LyricsEntry(
                    time = span * index / lines.size,
                    text = lines[index],
                )
            }
        }

        val times = LongArray(lines.size) { -1L }
        lines.indices.forEach { index ->
            val match = matches[index]
            if (match >= 0) times[index] = segments[match].startMs.coerceAtLeast(0L)
        }

        val matchedTimes = times.filter { it >= 0L }.sorted()
        val lineGap =
            if (matchedTimes.size >= 2) {
                val gaps = matchedTimes.zipWithNext { first, second -> second - first }.filter { it > 0L }
                gaps.takeIf { it.isNotEmpty() }?.sorted()?.let { it[it.size / 2] } ?: FallbackLineGapMs
            } else {
                FallbackLineGapMs
            }

        // Fill leading unmatched lines before the first confident match.
        val firstMatch = times.indexOfFirst { it >= 0L }
        for (index in firstMatch - 1 downTo 0) {
            times[index] = (times[index + 1] - lineGap).coerceAtLeast(0L)
        }

        // Fill internal runs by linear interpolation and trailing lines by a
        // steady line gap.
        var index = firstMatch
        while (index < lines.size) {
            if (times[index] >= 0L) {
                index++
                continue
            }
            val start = index
            var end = index
            while (end < lines.size && times[end] < 0L) end++
            val previousTime = times[start - 1]
            if (end < lines.size) {
                val nextTime = times[end]
                val span = end - start + 1
                val step = max(1L, (nextTime - previousTime) / span)
                for (offset in 0 until end - start) {
                    times[start + offset] = previousTime + step * (offset + 1)
                }
            } else {
                val remaining = end - start
                val available = (endTime - previousTime).coerceAtLeast(0L)
                val step =
                    if (available > 0L) {
                        max(1L, available / (remaining + 1))
                    } else {
                        lineGap
                    }
                for (offset in 0 until remaining) {
                    times[start + offset] = previousTime + step * (offset + 1)
                }
            }
            index = end
        }

        var previous = -1L
        return lines.indices.map { lineIndex ->
            val time = max(times[lineIndex], previous + 1L).coerceAtLeast(0L)
            previous = time
            LyricsEntry(time = time, text = lines[lineIndex])
        }
    }

    private fun similarity(
        line: Set<String>,
        segment: Set<String>,
    ): Double {
        if (line.isEmpty() || segment.isEmpty()) return 0.0
        val common = line.count(segment::contains)
        return common.toDouble() / line.size
    }

    private fun tokens(value: String): Set<String> {
        val normalized =
            Normalizer
                .normalize(value, Normalizer.Form.NFD)
                .replace(CombiningMarks, "")
                .lowercase(Locale.ROOT)
        return normalized
            .split(NonWord)
            .filter { it.isNotBlank() }
            .toSet()
    }

    private val CombiningMarks = Regex("\\p{Mn}+")
    private val NonWord = Regex("[^\\p{L}\\p{N}]+")
}
