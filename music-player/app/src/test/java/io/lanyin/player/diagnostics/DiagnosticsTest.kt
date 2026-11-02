package io.lanyin.player.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `frames are counted per screen and theme, dropped past twice the budget, stalls listed`() {
        val d = Diagnostics(null)
        d.where = "首页"
        d.theme = "墨"
        repeat(10) { d.frame(8f, 16.7f) }
        d.frame(40f, 16.7f)
        d.frame(140f, 16.7f)
        d.where = "播放页"
        d.frame(20f, 16.7f)
        val s = d.summary()
        assertEquals(13, s.frames)
        assertEquals(2, s.slow)
        assertEquals(1, s.stalls)
        val report = d.report(listOf("版本 test"))
        assertTrue(report.contains("首页\t墨\t12\t2 (16.7%)\t1\t140 ms"))
        assertTrue(report.contains("首页\t墨\t140 ms"))
        assertTrue(report.contains("播放页\t墨\t1\t0 (0.0%)\t0\t20 ms"))
    }

    @Test
    fun `cookies and tokens never reach the report`() {
        val d = Diagnostics(null)
        d.error("加载失败", "IOException: Cookie: MUSIC_U=00A1B2C3D4E5F6; __csrf=abcdef0123 failed")
        d.error("加载失败", "token 5f1c2b9a8e7d6c5b4a39281706f5e4d3c2b1a0f9e8 expired")
        val report = d.report(listOf("about"))
        assertFalse(report.contains("00A1B2C3D4E5F6"))
        assertFalse(report.contains("abcdef0123"))
        assertFalse(report.contains("5f1c2b9a8e7d6c5b4a39281706f5e4d3c2b1a0f9e8"))
        assertTrue(report.contains("Cookie=…"))
        assertEquals("a=1 MUSIC_U=… b", Diagnostics.redact("a=1 MUSIC_U=xyz b"))
    }

    @Test
    fun `what was recorded survives the app being killed, and clearing forgets it`() {
        val dir = tmp.newFolder("diag")
        val first = Diagnostics(dir)
        first.where = "搜索"
        first.theme = "霓"
        first.frame(180f, 8.3f)
        first.error("播放出错", "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: timeout")
        first.crash(Thread.currentThread(), IllegalStateException("boom"))
        first.flush()

        val again = Diagnostics(dir)
        val s = again.summary()
        assertEquals(1, s.frames)
        assertEquals(1, s.stalls)
        assertEquals(1, s.errors)
        assertEquals(1, s.crashes)
        val report = again.report(emptyList())
        assertTrue(report.contains("IllegalStateException: boom"))
        assertTrue(report.contains("搜索\t霓\t180 ms"))

        again.clear()
        val s2 = Diagnostics(dir).summary()
        assertEquals(0, s2.frames)
        assertEquals(0, s2.errors)
        assertEquals(0, s2.crashes)
    }
}
