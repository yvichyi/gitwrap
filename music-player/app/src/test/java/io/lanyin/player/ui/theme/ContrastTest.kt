package io.lanyin.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG contrast checks so a palette tweak can't quietly make text unreadable. */
class ContrastTest {

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    private fun forEachPalette(block: (String, LanyinColors) -> Unit) {
        AppTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val style = lanyinStyle(theme, dark)
                block("${theme.label}/${if (style.dark) "dark" else "light"}", style.colors)
            }
        }
    }

    @Test
    fun `body text is readable on the background and on cards`() = forEachPalette { name, c ->
        val onBg = contrast(c.content, c.bg)
        val onSurface = contrast(c.content, c.surface)
        assertTrue("$name content/bg = $onBg", onBg >= 7f)
        assertTrue("$name content/surface = $onSurface", onSurface >= 7f)
    }

    @Test
    fun `secondary text meets AA`() = forEachPalette { name, c ->
        val ratio = contrast(c.muted, c.bg)
        assertTrue("$name muted/bg = $ratio", ratio >= 4.5f)
    }

    @Test
    fun `glyphs on accent fills stay visible`() = forEachPalette { name, c ->
        val ratio = contrast(c.onAccent, c.accent)
        assertTrue("$name onAccent/accent = $ratio", ratio >= 3f)
    }

    @Test
    fun `accent is distinguishable from the background`() = forEachPalette { name, c ->
        val ratio = contrast(c.accent, c.bg)
        assertTrue("$name accent/bg = $ratio", ratio >= 3f)
    }

    @Test
    fun `inactive lyric lines remain legible`() = forEachPalette { name, c ->
        val theme = AppTheme.entries.first { name.startsWith(it.label) }
        val alpha = lanyinStyle(theme, dark = name.endsWith("dark")).lyric.inactiveAlpha
        val ratio = contrast(c.content.copy(alpha = alpha).compositeOver(c.bg), c.bg)
        assertTrue("$name inactive lyric = $ratio", ratio >= 1.9f)
    }
}
