package io.lanyin.player.playback

import io.lanyin.player.core.model.Quality
import io.lanyin.player.core.netease.MemorySessionStore
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.core.netease.NeteaseClient
import java.net.URLDecoder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamResolverTest {
    private lateinit var server: MockWebServer
    private lateinit var resolver: StreamResolver
    private var clock = 0L

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = NeteaseApi(NeteaseClient(OkHttpClient(), MemorySessionStore(), apiBase = server.url("/"), webBase = server.url("/")))
        resolver = StreamResolver(api) { clock }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun url(level: String, url: String?, fee: Int = 0, code: Int = 200) = server.enqueue(
        MockResponse().setBody(
            """{"code":200,"data":[{"id":1,"url":${url?.let { "\"$it\"" } ?: "null"},"br":320000,"size":1,"code":$code,"type":"mp3","level":"$level","fee":$fee}]}""",
        ),
    )

    /** The quality level each request asked for, read back out of the encrypted body. */
    private fun requestedLevels(): List<String> = List(server.requestCount) {
        val body = server.takeRequest().body.readUtf8().substringAfter("params=")
        val hex = URLDecoder.decode(body, "UTF-8")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES")) }
        val plain = String(cipher.doFinal(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()))
        Regex(""""level":"(\w+)"""").find(plain)!!.groupValues[1]
    }

    @Test
    fun `steps down when the preferred quality has no address`() = runBlocking {
        url("lossless", null, fee = 1)
        url("exhigh", "http://m.music.126.net/a.mp3")
        val s = resolver.resolve(1, Quality.LOSSLESS)
        assertEquals("http://m.music.126.net/a.mp3", s.url)
        assertEquals(Quality.EXHIGH, s.quality)
        assertEquals(listOf("lossless", "exhigh"), requestedLevels())
        assertEquals(s, resolver.resolved.value[1L])
    }

    @Test
    fun `keeps an address for a while, then asks again`() = runBlocking {
        url("lossless", "http://m.music.126.net/a.flac")
        url("lossless", "http://m.music.126.net/b.flac")
        resolver.resolve(1, Quality.LOSSLESS)
        clock += 60_000
        assertEquals("http://m.music.126.net/a.flac", resolver.resolve(1, Quality.LOSSLESS).url)
        clock += StreamResolver.TTL_MS
        assertEquals("http://m.music.126.net/b.flac", resolver.resolve(1, Quality.LOSSLESS).url)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `says why a song can't play`() = runBlocking {
        repeat(3) { url("standard", null, fee = 1) }
        val e = runCatching { resolver.resolve(1, Quality.HIRES) }.exceptionOrNull()
        assertTrue(e is UnplayableException)
        assertEquals("需要黑胶 VIP 才能播放", e!!.message)
        assertEquals(listOf("hires", "exhigh", "standard"), requestedLevels())
    }

    @Test
    fun `the ladder never climbs above the choice`() {
        assertEquals(listOf(Quality.STANDARD), StreamResolver.ladder(Quality.STANDARD))
        assertEquals(listOf(Quality.EXHIGH, Quality.STANDARD), StreamResolver.ladder(Quality.EXHIGH))
        assertEquals(listOf(Quality.MASTER, Quality.EXHIGH, Quality.STANDARD), StreamResolver.ladder(Quality.MASTER))
    }
}
