package io.lanyin.player.core.netease

import io.lanyin.player.core.model.Quality
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Calls the real NetEase servers without logging in, to prove the whole request path (crypto,
 * headers, cookies, parsing) works end to end. Off by default; run with `NETEASE_LIVE=1`.
 */
class NeteaseLiveTest {

    @Test
    fun `anonymous calls work against the real servers`() = runBlocking {
        assumeTrue("set NETEASE_LIVE=1 to run", System.getenv("NETEASE_LIVE") == "1")
        val api = NeteaseApi(NeteaseClient(OkHttpClient(), MemorySessionStore()))

        api.ensureAnonymous()
        assertNotNull("anonymous account", api.session.value?.musicA)

        val key = api.qrKey()
        assertTrue(key.isNotEmpty())
        val status = api.qrCheck(key)
        println("qr: $key → $status")
        assertEquals(QrStatus.Waiting, status)

        val found = api.search("晴天 周杰伦", limit = 5)
        println("search: " + found.joinToString { "${it.name} — ${it.artistLine}" })
        assertTrue(found.isNotEmpty())
        val song = found.first()

        val detail = api.songs(listOf(song.id)).single()
        assertEquals(song.id, detail.id)
        assertNotNull(detail.album.coverUrl)

        val lyrics = api.lyrics(song.id)
        println("lyrics: ${lyrics.lrc?.lines()?.take(3)} yrc=${lyrics.wordTimed != null} translation=${lyrics.translation != null}")
        assertNotNull(lyrics.lrc)

        val stream = api.stream(song.id, Quality.STANDARD)
        println("stream: code=${stream.code} quality=${stream.quality} trial=${stream.trialOnly} url=${stream.url != null}")

        // A free song resolves to a real audio file, even before login.
        val free = api.playlist(3778678).songs.filter { it.fee == 0 || it.fee == 8 }.take(5)
        val resolver = io.lanyin.player.playback.StreamResolver(api)
        val playable = free.firstNotNullOfOrNull { s -> runCatching { resolver.resolve(s.id, Quality.STANDARD) }.getOrNull()?.let { s to it } }
        assertNotNull("no free song resolved", playable)
        val (freeSong, freeStream) = playable!!
        val head = OkHttpClient().newCall(okhttp3.Request.Builder().url(freeStream.url!!).header("Range", "bytes=0-15").build()).execute().use { it.body!!.bytes() }
        val magic = String(head.copyOfRange(0, 4), Charsets.ISO_8859_1)
        println("audio: ${freeSong.name} → ${freeStream.format} ${freeStream.bitrate} starts with ${magic.take(3)}")
        assertTrue("not audio: $magic", magic.startsWith("ID3") || magic.startsWith("fLaC") || (head[0].toInt() and 0xff) == 0xff)

        val chart = api.playlist(3778678) // 热歌榜
        println("chart: ${chart.playlist.name}, ${chart.songs.size} songs")
        assertTrue(chart.songs.isNotEmpty())
    }
}
