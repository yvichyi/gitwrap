package io.lanyin.player.core.lyrics

import io.lanyin.player.core.model.LyricsSource
import io.lanyin.player.core.model.LyricLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Turns NetEase's LRC into timed lines:
 * - `[mm:ss.xx]text`, with any number of stamps per line and 1–3 fraction digits;
 * - the JSON credit lines NetEase puts at the top (`{"t":0,"c":[{"tx":"作词: "},{"tx":"…"}]}`);
 * - tags such as `[ar:…]` are skipped;
 * - a blank stamped line ends the line before it (an instrumental break) and is not shown.
 * The translation, itself an LRC, is matched to lines by time.
 */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(source: LyricsSource, durationMs: Long): List<LyricLine> {
        if (source.instrumental) return emptyList()
        val main = timed(source.lrc ?: return emptyList())
        if (main.none { it.second.isNotBlank() }) return emptyList()
        val translations = source.translation?.let(::timed).orEmpty().filter { it.second.isNotBlank() }
        val lines = ArrayList<LyricLine>()
        for ((i, entry) in main.withIndex()) {
            val (start, text) = entry
            if (text.isBlank()) continue
            val end = main.getOrNull(i + 1)?.first ?: maxOf(durationMs, start + 1)
            lines += LyricLine(text, translationAt(translations, start), start, end.coerceAtLeast(start + 1))
        }
        // NetEase marks some instrumentals with a single line instead of the flag.
        if (lines.size == 1 && lines[0].text.contains("纯音乐")) return emptyList()
        return lines
    }

    /** (time, text) pairs, sorted; a line with several stamps appears once per stamp. */
    internal fun timed(lrc: String): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        for (raw in lrc.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("{")) {
                credit(line)?.let(out::add)
                continue
            }
            var rest = line
            val times = ArrayList<Long>()
            while (true) {
                val m = stamp.matchAt(rest, 0) ?: break
                val (mm, ss, frac) = m.destructured
                val fracMs = when (frac.length) {
                    0 -> 0
                    1 -> frac.toInt() * 100
                    2 -> frac.toInt() * 10
                    else -> frac.toInt()
                }
                times += mm.toLong() * 60_000 + ss.toLong() * 1_000 + fracMs
                rest = rest.substring(m.range.last + 1)
            }
            if (times.isEmpty()) continue // a tag like [ar:…] or a stray line
            val text = rest.trim()
            times.forEach { out += it to text }
        }
        out.sortBy { it.first }
        return out
    }

    private fun credit(line: String): Pair<Long, String>? = runCatching {
        val o: JsonObject = json.parseToJsonElement(line).jsonObject
        val t = o["t"]?.jsonPrimitive?.longOrNull ?: return null
        val text = o["c"]?.jsonArray?.joinToString("") { it.jsonObject["tx"]?.jsonPrimitive?.content.orEmpty() }.orEmpty().trim()
        t to text
    }.getOrNull()

    /** The translation stamped at (or within a beat of) [start]. */
    private fun translationAt(translations: List<Pair<Long, String>>, start: Long): String? {
        if (translations.isEmpty()) return null
        var lo = 0
        var hi = translations.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (translations[mid].first < start) lo = mid + 1 else hi = mid
        }
        val candidates = listOfNotNull(translations.getOrNull(lo), translations.getOrNull(lo - 1))
        val best = candidates.minByOrNull { kotlin.math.abs(it.first - start) } ?: return null
        return best.second.takeIf { kotlin.math.abs(best.first - start) <= 400 && it.isNotBlank() && it != "//" }
    }
}
