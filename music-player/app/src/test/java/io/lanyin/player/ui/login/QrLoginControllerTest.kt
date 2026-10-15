package io.lanyin.player.ui.login

import io.lanyin.player.core.netease.MemorySessionStore
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.core.netease.NeteaseClient
import io.lanyin.player.core.netease.QrStatus
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QrLoginControllerTest {
    private lateinit var server: MockWebServer
    private lateinit var store: MemorySessionStore
    private lateinit var api: NeteaseApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        store = MemorySessionStore()
        api = NeteaseApi(NeteaseClient(OkHttpClient(), store, apiBase = server.url("/"), webBase = server.url("/")))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun reply(json: String, vararg cookies: String) =
        server.enqueue(MockResponse().setBody(json).apply { cookies.forEach { addHeader("Set-Cookie", it) } })

    @Test
    fun `scan then confirm signs in and reports back`() = runTest {
        reply("""{"code":200,"unikey":"k1"}""")
        reply("""{"code":801}""")
        reply("""{"code":802,"nickname":"潮汐里的鲸"}""")
        reply("""{"code":803}""", "MUSIC_U=u; Path=/")
        var confirmed = false
        val controller = QrLoginController(api) { confirmed = true }
        controller.run()
        assertTrue(confirmed)
        assertEquals(QrPhase.Success, controller.phase)
        assertEquals("u", store.read()!!.musicU)
    }

    @Test
    fun `an expired code is replaced, and after a few it waits for a tap`() = runTest {
        repeat(QrLoginController.MAX_CODES) { i ->
            reply("""{"code":200,"unikey":"k$i"}""")
            reply("""{"code":800}""")
        }
        val controller = QrLoginController(api) {}
        controller.run()
        assertEquals(QrPhase.Error("二维码过期了", expired = true), controller.phase)
        assertEquals(QrLoginController.MAX_CODES * 2, server.requestCount)
    }

    @Test
    fun `a refusal is shown with NetEase's message`() = runTest {
        reply("""{"code":200,"unikey":"k"}""")
        reply("""{"code":8821,"message":"需要行为验证码验证"}""")
        val controller = QrLoginController(api) {}
        controller.run()
        assertEquals(QrPhase.Error("需要行为验证码验证"), controller.phase)
    }

    @Test
    fun `the code encodes the login link`() {
        val url = "https://music.163.com/login?codekey=abc"
        val m = QrMatrix.encode(url)
        assertTrue(m.size >= 21 && (m.size - 17) % 4 == 0)
        // Finder pattern corners are dark, the separator next to them light.
        assertTrue(m[0, 0] && m[6, 6] && !m[7, 7])
    }
}
