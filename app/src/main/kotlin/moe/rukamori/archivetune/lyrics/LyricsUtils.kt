/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import java.util.Locale

/**
 * Local-only LRC utilities. ArchiveTune no longer downloads lyrics, so only
 * plain text and line-synced LRC files are supported.
 */
@Suppress("RegExpRedundantEscape")
object LyricsUtils {
    private val LINE_REGEX = Regex("""((\[\d{1,3}:\d{2}(?:[.:]\d{2,3})?\]\s*)+)(.*)""")
    private val TIME_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?\]""")
    private val WORD_TIMESTAMP_REGEX = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{2,3})?>""")
    private val LEADING_VOICE_REGEX = Regex("""^[vV]\d+:\s*""")
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun normalizeLyricsText(raw: String?): String =
        raw
            ?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            ?.trim()
            .orEmpty()

    fun isLineSyncedLrc(raw: String?): Boolean {
        val text = raw ?: return false
        return text.lineSequence().any { LINE_REGEX.matches(it.trim()) }
    }

    /**
     * True when [raw] contains at least one line with actual lyric text. This
     * filters out stale database rows such as [LyricsEntity.LYRICS_NOT_FOUND]
     * and malformed files that only contain timestamps.
     */
    fun hasRenderableText(raw: String?): Boolean {
        val text = normalizeLyricsText(raw)
        if (text.isEmpty() || text == "LYRICS_NOT_FOUND") return false
        return parseLyrics(text).any { it.text.isNotBlank() }
    }

    /**
     * True when [raw] uses enhanced-LRC word timestamps (`<mm:ss.xx>`). Some
     * older imports stored these raw tags in the lyric text; they must never
     * outrank a clean `.lrc` sidecar.
     */
    fun hasWordTimestampMarkup(raw: String?): Boolean =
        raw != null && WORD_TIMESTAMP_REGEX.containsMatchIn(raw)

    fun isWordSyncedLrc(@Suppress("UNUSED_PARAMETER") raw: String?): Boolean = false

    /**
     * TTML was only used by the removed online providers. Embedded/local lyrics
     * are plain text or LRC, so this always returns false.
     */
    fun isTtml(@Suppress("UNUSED_PARAMETER") raw: String?): Boolean = false

    fun parseTtml(@Suppress("UNUSED_PARAMETER") raw: String?): List<LyricsEntry> = emptyList()

    fun parseLyrics(raw: String?): List<LyricsEntry> {
        val text = normalizeLyricsText(raw)
        if (text.isEmpty()) return emptyList()

        val entries = mutableListOf<LyricsEntry>()
        var offset = 0L
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val match = LINE_REGEX.matchEntire(line)
            if (match == null) {
                if (line.startsWith("[")) return@forEach
                val hasWordTags = WORD_TIMESTAMP_REGEX.containsMatchIn(line)
                val cleaned =
                    line
                        .replace(WORD_TIMESTAMP_REGEX, "")
                        .let { if (hasWordTags) it.replace(LEADING_VOICE_REGEX, "") else it }
                        .trim()
                if (cleaned.isNotEmpty()) entries += LyricsEntry(time = offset, text = cleaned)
                return@forEach
            }

            val timestamps = match.groupValues[1]
            // Group 1 is the whole timestamp sequence, group 2 the last single
            // timestamp tag and group 3 the actual lyric text.
            val rawContent = match.groupValues[3]
            val hasWordTags = WORD_TIMESTAMP_REGEX.containsMatchIn(rawContent)
            val content =
                rawContent
                    .replace(WORD_TIMESTAMP_REGEX, "")
                    .let { if (hasWordTags) it.replace(LEADING_VOICE_REGEX, "") else it }
                    .trim()
            val times =
                TIME_REGEX.findAll(timestamps).mapNotNull { timeMatch ->
                    val minutes = timeMatch.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                    val seconds = timeMatch.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                    val fractionRaw = timeMatch.groupValues[3]
                    val fraction =
                        when (fractionRaw.length) {
                            0 -> 0L
                            1 -> fractionRaw.toLongOrNull()?.times(100L) ?: 0L
                            2 -> fractionRaw.toLongOrNull()?.times(10L) ?: 0L
                            else -> fractionRaw.take(3).toLongOrNull() ?: 0L
                        }
                    (minutes * 60_000L) + (seconds * 1_000L) + fraction
                }.toList()

            if (times.isEmpty()) return@forEach
            times.forEach { time ->
                offset = maxOf(offset, time)
                entries += LyricsEntry(time = time, text = content)
            }
        }

        return entries.sortedBy { it.time }
    }

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
        leadMs: Long = 0L,
    ): Int {
        if (lines.isEmpty()) return -1
        val target = position + leadMs
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (lines[mid].time <= target) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun formatLrc(entries: List<LyricsEntry>): String =
        entries.joinToString("\n") { entry ->
            val minutes = entry.time / 60_000L
            val seconds = (entry.time % 60_000L) / 1_000L
            val centiseconds = (entry.time % 1_000L) / 10L
            "[%02d:%02d.%02d]%s".format(Locale.US, minutes, seconds, centiseconds, entry.text)
        }

    fun formatLrcTimestamp(timeMs: Long): String {
        val minutes = timeMs / 60_000L
        val seconds = (timeMs % 60_000L) / 1_000L
        val centiseconds = (timeMs % 1_000L) / 10L
        return "[%02d:%02d.%02d]".format(Locale.US, minutes, seconds, centiseconds)
    }

    fun cleanLine(text: String): String = text.replace(WHITESPACE_REGEX, " ").trim()
}
