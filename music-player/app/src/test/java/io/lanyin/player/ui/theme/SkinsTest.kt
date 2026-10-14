package io.lanyin.player.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class SkinsTest {

    @Test
    fun `stored ids round-trip`() {
        AppSkin.entries.forEach { assertEquals(it, AppSkin.fromId(it.id)) }
        PlayerSkin.entries.forEach { assertEquals(it, PlayerSkin.fromId(it.id)) }
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromId(it.id)) }
    }

    @Test
    fun `unknown or missing ids fall back to defaults`() {
        assertEquals(AppSkin.LAN, AppSkin.fromId(null))
        assertEquals(AppSkin.LAN, AppSkin.fromId("removed-skin"))
        assertEquals(PlayerSkin.VINYL, PlayerSkin.fromId(null))
        assertEquals(PlayerSkin.VINYL, PlayerSkin.fromId("removed-skin"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId(""))
    }

    @Test
    fun `ids are unique so stored settings stay unambiguous`() {
        assertEquals(AppSkin.entries.size, AppSkin.entries.map { it.id }.toSet().size)
        assertEquals(PlayerSkin.entries.size, PlayerSkin.entries.map { it.id }.toSet().size)
    }
}
