package io.lanyin.player.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinsTest {

    @Test
    fun `stored ids round-trip`() {
        AppTheme.entries.forEach { assertEquals(it, AppTheme.fromId(it.id)) }
        PlayerSkin.entries.forEach { assertEquals(it, PlayerSkin.fromId(it.id)) }
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromId(it.id)) }
        LyricSize.entries.forEach { assertEquals(it, LyricSize.fromId(it.id)) }
        assertEquals(LyricSize.MEDIUM, LyricSize.fromId(null))
    }

    @Test
    fun `unknown, missing or retired ids fall back to defaults`() {
        assertEquals(AppTheme.INK, AppTheme.fromId(null))
        // M0 had 竹 ("zhu") and wallpaper colour ("system"); both are gone now.
        assertEquals(AppTheme.INK, AppTheme.fromId("zhu"))
        assertEquals(AppTheme.INK, AppTheme.fromId("system"))
        assertEquals(PlayerSkin.VINYL, PlayerSkin.fromId(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId(""))
    }

    @Test
    fun `ids are unique so stored settings stay unambiguous`() {
        assertEquals(AppTheme.entries.size, AppTheme.entries.map { it.id }.toSet().size)
        assertEquals(PlayerSkin.entries.size, PlayerSkin.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `dark-only and light-only themes ignore the mode`() {
        ThemeMode.entries.forEach { mode ->
            assertTrue(AppTheme.NEON.isDark(mode, systemDark = false))
            assertTrue(lanyinStyle(AppTheme.NEON, dark = false).dark)
        }
        ThemeMode.entries.forEach { mode ->
            assertEquals(false, AppTheme.GRID.isDark(mode, systemDark = true))
            assertEquals(false, lanyinStyle(AppTheme.GRID, dark = true).dark)
        }
        assertEquals(false, AppTheme.DUSK.isDark(ThemeMode.LIGHT, systemDark = true))
        assertEquals(true, AppTheme.DUSK.isDark(ThemeMode.SYSTEM, systemDark = true))
    }

    @Test
    fun `default lyric size is compact and sizes ascend`() {
        assertEquals(18f, LyricSize.Default.sp, 0.01f)
        assertTrue(LyricSize.entries.zipWithNext().all { (a, b) -> a.sp < b.sp })
        // Even the large size stays at the old default so a line still fits beside the artwork.
        assertTrue(LyricSize.LARGE.sp <= 22f)
    }

    @Test
    fun `chinese numerals for the ink date`() {
        assertEquals("一", io.lanyin.player.ui.home.chineseNumber(1))
        assertEquals("十", io.lanyin.player.ui.home.chineseNumber(10))
        assertEquals("十四", io.lanyin.player.ui.home.chineseNumber(14))
        assertEquals("二十", io.lanyin.player.ui.home.chineseNumber(20))
        assertEquals("三十一", io.lanyin.player.ui.home.chineseNumber(31))
    }

    @Test
    fun `themes differ in more than colour`() {
        val styles = AppTheme.entries.map { lanyinStyle(it, dark = true) }
        assertEquals(styles.size, styles.map { it.backdrop }.toSet().size)
        assertEquals(styles.size, styles.map { it.seek }.toSet().size)
        assertEquals(styles.size, styles.map { it.button }.toSet().size)
        assertEquals(styles.size, styles.map { it.edge }.toSet().size)
    }
}
