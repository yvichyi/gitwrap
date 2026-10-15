package io.lanyin.player.ui.music

import io.lanyin.player.playback.MediaPlayerState
import io.lanyin.player.ui.player.commentBadge
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    private val now = at(2026, 10, 15, 14, 0)

    @Test
    fun `when, the way Chinese apps say it`() {
        assertEquals("刚刚", relativeTime(now - 20_000, now, zone))
        assertEquals("5 分钟前", relativeTime(now - 5 * 60_000, now, zone))
        assertEquals("3 小时前", relativeTime(at(2026, 10, 15, 11, 0), now, zone))
        assertEquals("昨天 21:04", relativeTime(at(2026, 10, 14, 21, 4), now, zone))
        assertEquals("3月2日", relativeTime(at(2026, 3, 2, 9, 0), now, zone))
        assertEquals("2021年3月2日", relativeTime(at(2021, 3, 2, 9, 0), now, zone))
    }

    @Test
    fun `counts and sizes`() {
        assertEquals("9999", shortCount(9_999))
        assertEquals("1.2万", shortCount(12_480))
        assertEquals("197万", shortCount(1_970_239))
        assertEquals("3万", shortCount(30_000))
        assertEquals("1.5亿", shortCount(150_000_000))
        assertEquals("512 KB", byteSize(512 * 1024))
        assertEquals("31.4 MB", byteSize((31.4 * (1 shl 20)).toLong()))
        assertEquals("5.0 GB", byteSize(5L shl 30))
    }

    @Test
    fun `the comment badge counts like NetEase`() {
        assertEquals("86", commentBadge(86))
        assertEquals("999+", commentBadge(4_000))
        assertEquals("1w+", commentBadge(12_480))
        assertEquals("10w+", commentBadge(197_000))
        assertEquals("100w+", commentBadge(1_970_239))
    }

    @Test
    fun `a play counts after half a minute, or half of a short song`() {
        assertFalse(MediaPlayerState.worthReporting(29_000, 240_000))
        assertTrue(MediaPlayerState.worthReporting(30_000, 240_000))
        assertTrue(MediaPlayerState.worthReporting(21_000, 40_000))
        assertFalse(MediaPlayerState.worthReporting(10_000, 40_000))
        assertFalse(MediaPlayerState.worthReporting(10_000, 0))
    }
}
