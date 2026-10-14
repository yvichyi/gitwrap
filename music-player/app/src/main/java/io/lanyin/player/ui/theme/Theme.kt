package io.lanyin.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat

/** Shorthand for the active style tokens. */
object Lanyin {
    val style: LanyinStyle
        @Composable @ReadOnlyComposable get() = LocalLanyinStyle.current
    val colors: LanyinColors
        @Composable @ReadOnlyComposable get() = LocalLanyinStyle.current.colors
}

fun AppTheme.isDark(mode: ThemeMode, systemDark: Boolean): Boolean = darkOnly || when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Provides [LanyinStyle] plus a matching Material scheme for the few stock components still used.
 * Set [applySystemBars] to false for nested previews so they don't fight over the status bar.
 */
@Composable
fun LanyinTheme(
    theme: AppTheme,
    mode: ThemeMode,
    applySystemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = theme.isDark(mode, isSystemInDarkTheme())
    val style = remember(theme, dark) { lanyinStyle(theme, dark) }

    if (applySystemBars) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !style.dark
                    isAppearanceLightNavigationBars = !style.dark
                }
            }
        }
    }

    CompositionLocalProvider(LocalLanyinStyle provides style) {
        MaterialTheme(
            colorScheme = remember(style) { style.materialScheme() },
            typography = remember(style) { style.typography() },
            content = content,
        )
    }
}

private fun LanyinStyle.materialScheme(): ColorScheme {
    val c = colors
    val container = c.accent.copy(alpha = 0.16f).compositeOver(c.bg)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = container, onPrimaryContainer = c.content,
        secondary = c.accentAlt, onSecondary = c.onAccent,
        secondaryContainer = container, onSecondaryContainer = c.content,
        tertiary = c.accentAlt,
        background = c.bg, onBackground = c.content,
        surface = c.bg, onSurface = c.content,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.muted,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceAlt, surfaceContainerHighest = c.surfaceAlt,
        outline = c.outline, outlineVariant = c.outline,
        error = c.like,
    )
}

private fun LanyinStyle.typography(): Typography {
    val t = Typography()
    fun TextStyle.display() = copy(fontFamily = display, fontWeight = displayWeight, letterSpacing = displayTracking)
    fun TextStyle.body() = copy(fontFamily = body)
    return t.copy(
        displayLarge = t.displayLarge.display(), displayMedium = t.displayMedium.display(), displaySmall = t.displaySmall.display(),
        headlineLarge = t.headlineLarge.display(), headlineMedium = t.headlineMedium.display(), headlineSmall = t.headlineSmall.display(),
        titleLarge = t.titleLarge.display(), titleMedium = t.titleMedium.body(), titleSmall = t.titleSmall.body(),
        bodyLarge = t.bodyLarge.body(), bodyMedium = t.bodyMedium.body(), bodySmall = t.bodySmall.body(),
        labelLarge = t.labelLarge.body(), labelMedium = t.labelMedium.body(), labelSmall = t.labelSmall.body(),
    )
}
