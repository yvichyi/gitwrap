package io.lanyin.player.core.netease

import io.lanyin.player.core.model.Quality
import java.net.URLDecoder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The transport against a fake server: what goes over the wire, and how replies are read. */
class NeteaseClientTest {

    private lateinit var server: MockWebServer
    private lateinit var store: MemorySessionStore
    private lateinit var api: NeteaseApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        store = MemorySessionStore()
        val client = NeteaseClient(OkHttpClient(), store, apiBase = server.url("/"), webBase = server.url("/"), now = { 1_700_000_000_123L })
        api = NeteaseApi(client)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun reply(json: String, vararg cookies: String) {
        server.enqueue(MockResponse().setBody(json).apply { cookies.forEach { addHeader("Set-Cookie", it) } })
    }

    private fun form(r: RecordedRequest): Map<String, String> = r.body.readUtf8().split('&').associate {
        val (k, v) = it.split('=', limit = 2)
        k to URLDecoder.decode(v, "UTF-8")
    }

    /** Reverses eapi to see the plaintext that was sent. */
    private fun eapiPlain(hex: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES"))
        return String(cipher.doFinal(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()))
    }

    @Test
    fun `eapi sends the app identity and an encrypted body`() = runBlocking {
        reply("""{"code":200,"unikey":"abc-123"}""")
        assertEquals("abc-123", api.qrKey())

        val r = server.takeRequest()
        assertEquals("/eapi/login/qrcode/unikey", r.path)
        val cookie = r.getHeader("Cookie")!!
        val deviceId = store.read()!!.deviceId
        assertTrue(cookie, "deviceId=$deviceId" in cookie && "os=pc" in cookie && "buildver=1700000000" in cookie)
        assertTrue(r.getHeader("User-Agent")!!.startsWith("NeteaseMusic"))

        val (path, json, digest) = eapiPlain(form(r).getValue("params")).split("-36cd479b6b5-")
        assertEquals("/api/login/qrcode/unikey", path)
        val body = Json.parseToJsonElement(json).jsonObject
        assertEquals("3", body.getValue("type").jsonPrimitive.content)
        assertEquals(deviceId, body.getValue("header").jsonObject.getValue("deviceId").jsonPrimitive.content)
        assertEquals(32, digest.length)
    }

    @Test
    fun `weapi goes to the web host with the full cookie set`() = runBlocking {
        reply("""{"code":200,"account":{"id":1},"profile":null}""")
        assertNull(api.account())
        val r = server.takeRequest()
        assertEquals("/weapi/w/nuser/account/get", r.path)
        assertTrue(r.getHeader("Cookie")!!.contains("_ntes_nuid="))
        assertTrue(r.getHeader("Referer")!!.startsWith("http"))
        val f = form(r)
        assertEquals(256, f.getValue("encSecKey").length)
        assertTrue(f.getValue("params").isNotEmpty())
    }

    @Test
    fun `qr login walks through scan and confirm, then keeps the cookie`() = runBlocking {
        reply("""{"code":801,"message":"等待扫码"}""")
        reply("""{"code":802,"message":"授权中","nickname":"小澜","avatarUrl":"http://p1.music.126.net/a.jpg"}""")
        reply(
            """{"code":803,"message":"授权登陆成功"}""",
            "MUSIC_U=secret-login; Max-Age=1296000; Expires=Tue, 1 Jan 2030 00:00:00 GMT; Path=/;",
            "__csrf=token123; Max-Age=1296010; Path=/;",
        )
        assertEquals(QrStatus.Waiting, api.qrCheck("k"))
        assertEquals(QrStatus.Scanned("小澜", "https://p1.music.126.net/a.jpg"), api.qrCheck("k"))
        assertFalse(store.read()!!.loggedIn)
        assertEquals(QrStatus.Confirmed, api.qrCheck("k"))
        val s = store.read()!!
        assertEquals("secret-login", s.musicU)
        assertEquals("token123", s.csrf)

        // The login then rides along on app calls.
        reply("""{"code":200,"data":[]}""")
        api.stream(1, Quality.LOSSLESS)
        repeat(3) { server.takeRequest() }
        assertTrue(server.takeRequest().getHeader("Cookie")!!.contains("MUSIC_U=secret-login"))
    }

    @Test
    fun `expired and unexpected qr codes are reported, not thrown`() = runBlocking {
        reply("""{"code":800,"message":"二维码不存在或已过期"}""")
        reply("""{"code":8821,"message":"需要行为验证码验证"}""")
        assertEquals(QrStatus.Expired, api.qrCheck("k"))
        assertEquals(QrStatus.Failed(8821, "需要行为验证码验证"), api.qrCheck("k"))
    }

    @Test
    fun `logout keeps the device and the anonymous account`() = runBlocking {
        store.write(NeteaseSession.fresh().withCookies(mapOf("MUSIC_U" to "x", "__csrf" to "y", "MUSIC_A" to "anon")))
        reply("""{"code":200}""")
        api.logout()
        val s = store.read()!!
        assertNull(s.musicU)
        assertEquals("", s.csrf)
        assertEquals("anon", s.musicA)
    }

    @Test
    fun `songs read both the current and the old wire shapes`() = runBlocking {
        reply(
            """{"code":200,"songs":[{"id":1,"name":"晴天","ar":[{"id":2,"name":"周杰伦"}],"al":{"id":3,"name":"叶惠美","picUrl":"http://p2.music.126.net/c.jpg"},"dt":269000,"fee":8,"alia":["Sunny Day"]}]}""",
        )
        reply("""{"code":200,"data":[{"id":4,"name":"FM","artists":[{"id":5,"name":"A"},{"id":6,"name":"B"}],"album":{"id":7,"name":"Al","picUrl":null},"duration":1000}]}""")
        val s = api.songs(listOf(1)).single()
        assertEquals("周杰伦", s.artistLine)
        assertEquals("https://p2.music.126.net/c.jpg", s.album.coverUrl)
        assertEquals(269_000, s.durationMs)
        assertEquals("Sunny Day", s.alias)
        val fm = api.personalFm().single()
        assertEquals("A / B", fm.artistLine)
        assertEquals(1000, fm.durationMs)
    }

    @Test
    fun `a playlist fills in the tracks the detail call leaves out, in order`() = runBlocking {
        reply(
            """{"code":200,"playlist":{"id":9,"name":"我喜欢的音乐","specialType":5,"trackCount":3,
               "tracks":[{"id":1,"name":"a"}],"trackIds":[{"id":1},{"id":2},{"id":3}]}}""",
        )
        reply("""{"code":200,"songs":[{"id":3,"name":"c"},{"id":2,"name":"b"}]}""")
        val d = api.playlist(9)
        assertEquals(listOf("a", "b", "c"), d.songs.map { it.name })
        assertEquals(5, d.playlist.specialType)
    }

    @Test
    fun `stream reports quality and trial clips`() = runBlocking {
        reply("""{"code":200,"data":[{"id":1,"url":"http://m701.music.126.net/x.flac","br":999000,"size":30000000,"code":200,"type":"FLAC","level":"lossless","fee":1,"freeTrialInfo":null}]}""")
        reply("""{"code":200,"data":[{"id":2,"url":"http://m701.music.126.net/y.mp3","br":128000,"size":1,"code":200,"type":"mp3","level":"standard","fee":1,"freeTrialInfo":{"start":0,"end":30}}]}""")
        val full = api.stream(1, Quality.LOSSLESS)
        assertEquals(Quality.LOSSLESS, full.quality)
        // Audio addresses stay as given: some CDN nodes only speak plain http.
        assertEquals("http://m701.music.126.net/x.flac", full.url)
        assertFalse(full.trialOnly)
        assertTrue(api.stream(2, Quality.LOSSLESS).trialOnly)
    }

    @Test
    fun `errors carry NetEase's code`() = runBlocking {
        reply("""{"code":301,"msg":"需要登录"}""")
        val e = runCatching { api.dailySongs() }.exceptionOrNull() as NeteaseException
        assertEquals(301, e.code)
        assertEquals("需要登录", e.message)
    }

    @Test
    fun `a device id NetEase refuses is replaced until registration works`() = runBlocking {
        reply("""{"code":400}""")
        reply("""{"code":200,"userId":1}""", "MUSIC_A=anon-token; Path=/")
        val first = api.currentSession().deviceId
        api.ensureAnonymous()
        val s = store.read()!!
        assertEquals("anon-token", s.musicA)
        assertTrue(s.deviceId != first)
        assertEquals(2, server.requestCount)
        // Registered: nothing more to do.
        api.ensureAnonymous()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `cookie values are encoded like encodeURIComponent`() {
        assertEquals("a%20b%3Bc%E6%BE%9C-_.!~*'()", NeteaseClient.encodeURIComponent("a b;c澜-_.!~*'()"))
        assertEquals(mapOf("MUSIC_U" to "v2"), NeteaseClient.parseSetCookies(listOf("MUSIC_U=; Max-Age=0", "MUSIC_U=v2; Path=/")))
    }
}
