package io.lanyin.player.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import io.lanyin.player.core.model.Quality
import io.lanyin.player.core.netease.MemorySessionStore
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.core.netease.NeteaseClient
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The whole chain the player reads through: queue address → NetEase → audio host → cache. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioPipelineTest {
    private lateinit var server: MockWebServer
    private val audio = ByteArray(64 * 1024) { (it * 31 % 251).toByte() }
    private var urlCalls = 0
    private var audioCalls = 0

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.startsWith("/eapi/song/enhance/player/url/v1") -> {
                    urlCalls++
                    val audioUrl = server.url("/audio/1.mp3")
                    MockResponse().setBody("""{"code":200,"data":[{"id":1,"url":"$audioUrl","br":320000,"size":${audio.size},"code":200,"type":"mp3","level":"exhigh","fee":0}]}""")
                }
                request.path!!.startsWith("/audio/") -> {
                    audioCalls++
                    MockResponse().setBody(Buffer().write(audio)).setHeader("Content-Type", "audio/mpeg")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun readAll(pipeline: AudioPipeline, id: Long): ByteArray {
        val source = pipeline.dataSourceFactory.createDataSource()
        source.open(DataSpec(AudioPipeline.uriFor(id.toString())))
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = source.read(buf, 0, buf.size)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        source.close()
        return out.toByteArray()
    }

    @Test
    fun `a queued song streams through NetEase and plays again from cache`() {
        val http = OkHttpClient()
        val api = NeteaseApi(NeteaseClient(http, MemorySessionStore(), apiBase = server.url("/"), webBase = server.url("/")))
        val resolver = StreamResolver(api)
        val pipeline = AudioPipeline(ApplicationProvider.getApplicationContext(), http, resolver)
        pipeline.preferred = Quality.EXHIGH

        assertArrayEquals(audio, readAll(pipeline, 1))
        assertEquals(1, urlCalls)
        assertEquals(1, audioCalls)
        assertTrue(resolver.resolved.value.containsKey(1L))

        // Heard once, kept: the replay needs neither NetEase nor the audio host.
        assertArrayEquals(audio, readAll(pipeline, 1))
        assertEquals(1, urlCalls)
        assertEquals(1, audioCalls)
    }
}
