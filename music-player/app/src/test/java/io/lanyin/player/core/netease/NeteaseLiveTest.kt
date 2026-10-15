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
        // As the app does: the address as given, then over the other scheme.
        val first = freeStream.url!!
        val second = if (first.startsWith("https://")) "http://" + first.removePrefix("https://") else "https://" + first.removePrefix("http://")
        fun fetchHead(url: String): ByteArray? = runCatching {
            OkHttpClient().newCall(okhttp3.Request.Builder().url(url).header("Range", "bytes=0-15").build()).execute().use { r -> r.body!!.bytes().takeIf { r.isSuccessful } }
        }.getOrNull()
        val head = fetchHead(first) ?: fetchHead(second) ?: error("audio unreachable over both schemes")
        println("fetched ${first.substringBefore("?").take(48)}")
        val magic = String(head.copyOfRange(0, 4), Charsets.ISO_8859_1)
        println("audio: ${freeSong.name} → ${freeStream.format} ${freeStream.bitrate} starts with ${magic.take(3)}")
        assertTrue("not audio: $magic", magic.startsWith("ID3") || magic.startsWith("fLaC") || (head[0].toInt() and 0xff) == 0xff)

        // Artists, albums and playlists by name, and their pages.
        val artistHit = api.searchArtists("周杰伦", limit = 3).first()
        val artist = api.artist(artistHit.id)
        println("artist: ${artist.name} ${artist.songCount} songs, ${artist.albumCount} albums, top: ${artist.hotSongs.take(3).map { it.name }}")
        assertTrue(artist.hotSongs.isNotEmpty())
        val albumHit = api.searchAlbums("叶惠美", limit = 3).first()
        val album = api.album(albumHit.id)
        println("album: ${album.name} — ${album.artistName}, ${album.songs.size} songs, cover=${album.songs.first().album.coverUrl != null}")
        assertTrue(album.songs.isNotEmpty())
        val lists = api.searchPlaylists("学习", limit = 3)
        println("playlists: ${lists.map { "${it.name} (${it.trackCount})" }}")
        assertTrue(lists.isNotEmpty())

        // Word timings (only some songs have them; the free chart song above does), through the parser.
        val sunny = api.lyrics(freeSong.id)
        val timed = io.lanyin.player.core.lyrics.LrcParser.parse(sunny, freeSong.durationMs)
        val worded = timed.count { it.words != null }
        println("yrc: ${sunny.wordTimed != null}, ${timed.size} lines, $worded word-timed, sample=${timed.firstOrNull { it.words != null }?.words?.take(4)}")
        if (sunny.wordTimed != null) assertTrue(worded > 10)

        val chart = api.playlist(3778678) // 热歌榜
        println("chart: ${chart.playlist.name}, ${chart.songs.size} songs")
        assertTrue(chart.songs.isNotEmpty())
    }
    @Test
    fun `comments read before login, in every order`() = runBlocking {
        assumeTrue("set NETEASE_LIVE=1 to run", System.getenv("NETEASE_LIVE") == "1")
        val api = NeteaseApi(NeteaseClient(OkHttpClient(), MemorySessionStore()))
        api.ensureAnonymous()
        for (sort in io.lanyin.player.core.model.CommentSort.entries) {
            val page = api.comments(186016, sort)
            println("comments $sort: total=${page.total} more=${page.hasMore} cursor=${page.cursor} first=${page.comments.firstOrNull()?.let { "${it.user.nickname}: ${it.content.take(20)} ♥${it.likedCount} ${it.location}" }}")
            assertTrue(page.comments.isNotEmpty())
            if (sort == io.lanyin.player.core.model.CommentSort.NEWEST) {
                println("newest times: " + page.comments.take(5).map { java.time.Instant.ofEpochMilli(it.timeMs) })
                val now = api.comments(186016, sort, cursor = System.currentTimeMillis().toString())
                println("newest from now: " + now.comments.take(5).map { java.time.Instant.ofEpochMilli(it.timeMs).toString() + " " + it.content.take(10) } + " cursor=" + now.cursor)
                val next = api.comments(186016, sort, pageNo = 2, cursor = page.cursor)
                assertTrue(next.comments.isNotEmpty())
                assertTrue(next.comments.first().timeMs <= page.comments.last().timeMs)
            }
        }
    }
}
