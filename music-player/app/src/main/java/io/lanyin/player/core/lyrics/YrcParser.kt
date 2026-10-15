package io.lanyin.player.core.lyrics

import io.lanyin.player.core.model.LyricLine
import io.lanyin.player.core.model.LyricWord

/**
 * NetEase's word-timed lyrics: `[lineStart,lineDuration](wordStart,wordDuration,0)word…`, with
 * the same JSON credit lines as LRC at the top. Gives lines whose sweep follows the singing.
 */
object YrcParser {
    private val lineHead = Regex("""^\[(\d+),(\d+)]""")
    private val word = Regex("""\((\d+),(\d+),\d+\)([^(]*)""")

    fun parse(yrc: String, durationMs: Long): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in yrc.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("{")) {
                LrcParser.timed(line).firstOrNull()?.let { (t, text) -> if (text.isNotBlank()) out += LyricLine(text, null, t, t + 1) }
                continue
            }
            val head = lineHead.find(line) ?: continue
            val start = head.groupValues[1].toLong()
            val length = head.groupValues[2].toLong()
            val words = word.findAll(line.substring(head.range.last + 1)).map {
                LyricWord(it.groupValues[3], it.groupValues[1].toLong(), it.groupValues[2].toLong())
            }.filter { it.text.isNotEmpty() }.toList()
            val text = words.joinToString("") { it.text }
            if (text.isBlank()) continue
            out += LyricLine(text.trim(), null, start, start + length, words)
        }
        out.sortBy { it.startMs }
        // Credits and lines end where the next begins (or the song does), so nothing lingers.
        return out.mapIndexed { i, l ->
            val next = out.getOrNull(i + 1)?.startMs ?: maxOf(durationMs, l.endMs)
            if (l.words == null) l.copy(endMs = maxOf(l.startMs + 1, next)) else l.copy(endMs = maxOf(l.endMs, minOf(next, l.endMs + 1_500)))
        }
    }
}
