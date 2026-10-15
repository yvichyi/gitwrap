package io.lanyin.player.playback

import android.app.Notification
import io.lanyin.player.core.model.LyricLine
import io.lanyin.player.core.model.LyricWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatusLyricsTest {

    private fun line(text: String, start: Long, end: Long, words: List<LyricWord>? = null) = LyricLine(text, null, start, end, words)

    private val lines = listOf(
        line("", 0, 1_000),
        line("把今晚调成横屏", 1_000, 4_000),
        line("让歌词一行一行亮起，唱片在掌心慢慢转动", 4_000, 10_000),
        line("Let the lyrics light up line by line", 10_000, 14_000),
    )

    @Test
    fun `the line being sung, a moment early`() {
        assertNull("nothing before the first line", LyricTicker.at(lines.drop(1), 500))
        assertNull("a blank line clears the bar", LyricTicker.at(lines, 200))
        assertEquals("把今晚调成横屏", LyricTicker.at(lines, 2_000)!!.line)
        // 150 ms ahead, so the status bar has redrawn by the time the line is sung.
        assertEquals("让歌词一行一行亮起，唱片在掌心慢慢转动", LyricTicker.at(lines, 3_900)!!.line)
    }

    @Test
    fun `long lines are cut into pieces a capsule can show whole`() {
        assertEquals(listOf("把今晚调", "成横屏"), LyricTicker.pieces("把今晚调成横屏"))
        assertEquals(listOf("让歌词一行", "一行亮起", "唱片在掌心", "慢慢转动"), LyricTicker.pieces("让歌词一行一行亮起，唱片在掌心慢慢转动"))
        assertEquals(listOf("晴天"), LyricTicker.pieces("晴天"))
        assertEquals(listOf("Let the", "lyrics", "light up", "line by", "line"), LyricTicker.pieces("Let the lyrics light up line by line"))
        for (p in LyricTicker.pieces("让歌词一行一行亮起，唱片在掌心慢慢转动")) assertTrue(p, p.length <= 6)
    }

    @Test
    fun `the capsule's piece follows the singing through the line`() {
        val long = lines[2]
        assertEquals("让歌词一行", LyricTicker.at(lines, long.startMs + 100)!!.piece)
        // At the song's pace (about four characters a second) the third piece is sung by 2.5 s.
        assertEquals("唱片在掌心", LyricTicker.at(lines, long.startMs + 2_500)!!.piece)
        assertEquals("慢慢转动", LyricTicker.at(lines, long.endMs - 300)!!.piece)
        // With word timings, the words decide.
        val timed = line(
            "一二三四五六七八", 0, 8_000,
            words = listOf(LyricWord("一二三四五六", 0, 600), LyricWord("七八", 7_000, 900)),
        )
        assertEquals("五六七八", LyricTicker.at(listOf(timed), 1_000)!!.piece)
    }

    private class Recorder : LyricSink {
        val seen = mutableListOf<String?>()
        override fun show(shown: LyricTicker.Shown?, title: String, artist: String) {
            seen += shown?.piece
        }
    }

    @Test
    fun `sinks hear only changes, and a pause clears them`() {
        var position = 1_300L
        var playing = true
        val sink = Recorder()
        val feed = MutableStateFlow<NowPlayingLyrics.Lyrics?>(NowPlayingLyrics.Lyrics(1, "演示", "澜音", lines))
        val status = StatusLyrics(CoroutineScope(Dispatchers.Unconfined), feed, { position }, { playing }, listOf(sink))
        status.tick()
        status.tick()
        position = 1_500
        status.tick()
        assertEquals(listOf<String?>("把今晚调"), sink.seen)
        position = 2_400
        status.tick()
        assertEquals("成横屏", sink.seen.last())
        playing = false
        status.tick()
        assertNull(sink.seen.last())
        // A song without lyrics shows nothing: still cleared, so nothing new is sent.
        playing = true
        feed.value = NowPlayingLyrics.Lyrics(2, "纯音乐", "", emptyList())
        status.tick()
        assertEquals(listOf("把今晚调", "成横屏", null), sink.seen)
    }

    @Test
    fun `the ticker carries Meizu's flags with the line, and loses them without one`() {
        val n = Notification()
        TickerLyrics.apply(n, "让歌词一行一行亮起", lyricOnly = true)
        assertEquals("让歌词一行一行亮起", n.tickerText)
        assertEquals(TickerLyrics.FLAG_ALWAYS_SHOW_TICKER, n.flags and TickerLyrics.FLAG_ALWAYS_SHOW_TICKER)
        assertEquals(TickerLyrics.FLAG_ONLY_UPDATE_TICKER, n.flags and TickerLyrics.FLAG_ONLY_UPDATE_TICKER)
        // A post for anything else (a new song, play/pause) must not claim only the ticker changed.
        TickerLyrics.apply(n, "下一句", lyricOnly = false)
        assertEquals(0, n.flags and TickerLyrics.FLAG_ONLY_UPDATE_TICKER)
        TickerLyrics.apply(n, null, lyricOnly = false)
        assertNull(n.tickerText)
        assertEquals(0, n.flags and (TickerLyrics.FLAG_ALWAYS_SHOW_TICKER or TickerLyrics.FLAG_ONLY_UPDATE_TICKER))
    }

    @Test
    fun `before Android 16 the capsule stays away`() {
        val capsule = LyricCapsule(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        assertEquals(false, capsule.supported)
        capsule.show(LyricTicker.Shown("让歌词一行一行亮起", "让歌词一行"), "演示", "澜音")
        val manager = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getSystemService(android.app.NotificationManager::class.java)
        assertEquals(0, manager.activeNotifications.size)
    }
}
