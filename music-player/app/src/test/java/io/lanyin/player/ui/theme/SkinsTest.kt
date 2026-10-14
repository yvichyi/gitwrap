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
    }

    @Test
    fun `unknown, missing or retired ids fall back to defaults`() {
        assertEquals(AppTheme.TIDE, AppTheme.fromId(null))
        // M0 had 竹 ("zhu") and wallpaper colour ("system"); both are gone now.
        assertEquals(AppTheme.TIDE, AppTheme.fromId("zhu"))
        assertEquals(AppTheme.TIDE, AppTheme.fromId("system"))
        assertEquals(PlayerSkin.VINYL, PlayerSkin.fromId(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId(""))
    }

    @Test
    fun `ids are unique so stored settings stay unambiguous`() {
        assertEquals(AppTheme.entries.size, AppTheme.entries.map { it.id }.toSet().size)
        assertEquals(PlayerSkin.entries.size, PlayerSkin.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `dark-only themes are dark whatever the mode`() {
        ThemeMode.entries.forEach { mode ->
            assertTrue(AppTheme.NEON.isDark(mode, systemDark = false))
            assertTrue(lanyinStyle(AppTheme.NEON, dark = false).dark)
        }
        assertEquals(false, AppTheme.TIDE.isDark(ThemeMode.LIGHT, systemDark = true))
        assertEquals(true, AppTheme.TIDE.isDark(ThemeMode.SYSTEM, systemDark = true))
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
