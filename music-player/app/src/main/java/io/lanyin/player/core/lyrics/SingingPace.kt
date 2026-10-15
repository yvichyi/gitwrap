package io.lanyin.player.core.lyrics

import io.lanyin.player.core.model.LyricLine

/**
 * How fast a song is sung, for lyrics that only say when each line starts. Each line's text is
 * counted in sung units (a Chinese, Japanese or Korean character, or a word of anything else) and
 * set against the time until the next line. Lines followed by a long pause read slow, so the pace
 * is taken from the quicker lines (the 70th percentile): those are mostly singing. Each line is
 * then reckoned to take its units at that pace, never less than a third of a second nor more
 * than nearly all the time before the next line.
 */
object SingingPace {
    /** Units a second when a song gives too little to go on: a typical Mandarin pop line. */
    const val DEFAULT_RATE = 3.8f

    private val latinWord = Regex("[A-Za-z0-9\u00C0-\u024F']+")

    fun units(text: String): Int {
        var n = 0
        for (c in text) if (isCjk(c)) n++
        n += latinWord.findAll(text).count()
        return n
    }

    private fun isCjk(c: Char): Boolean = Character.UnicodeScript.of(c.code).let {
        it == Character.UnicodeScript.HAN || it == Character.UnicodeScript.HIRAGANA ||
            it == Character.UnicodeScript.KATAKANA || it == Character.UnicodeScript.HANGUL
    }

    /** Units a second for these lines. */
    fun rate(lines: List<LyricLine>): Float {
        val rates = lines.mapNotNull { l ->
            val u = units(l.text)
            val gap = l.endMs - l.startMs
            if (u >= 3 && gap in 700..12_000) u * 1000f / gap else null
        }.sorted()
        if (rates.size < 4) return DEFAULT_RATE
        return rates[(rates.size * 0.7f).toInt().coerceAtMost(rates.lastIndex)].coerceIn(1.6f, 7f)
    }

    /** How long [text] takes to sing at [rate] units a second, within a line [lineMs] long. */
    fun estimate(text: String, lineMs: Long, rate: Float = DEFAULT_RATE): Long {
        val sung = (units(text).coerceAtLeast(1) / rate * 1000f).toLong()
        val most = (lineMs * 0.92f).toLong().coerceAtLeast(1)
        return sung.coerceIn(minOf(330L, most), most)
    }

    /** [lines] with [LyricLine.sungMs] set from their own pace. */
    fun apply(lines: List<LyricLine>): List<LyricLine> {
        val r = rate(lines)
        return lines.map { if (it.words != null) it else it.copy(sungMs = estimate(it.text, it.endMs - it.startMs, r)) }
    }
}
