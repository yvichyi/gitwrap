package io.lanyin.player.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** The handful of hand-picked colours each skin is built from. */
private data class SkinPalette(
    val primaryLight: Color,
    val primaryDark: Color,
    val accent: Color,
    val paper: Color,
    val night: Color,
)

private fun AppSkin.palette(): SkinPalette = when (this) {
    AppSkin.LAN, AppSkin.SYSTEM -> SkinPalette(
        primaryLight = Color(0xFF1F6FC5), primaryDark = Color(0xFF8EC8FF),
        accent = Color(0xFF14A3A0), paper = Color(0xFFF5F9FE), night = Color(0xFF0B1A2E),
    )
    AppSkin.MO -> SkinPalette(
        primaryLight = Color(0xFF2B2B2B), primaryDark = Color(0xFFE6E6E6),
        accent = Color(0xFF8C2F2F), paper = Color(0xFFF7F5F0), night = Color(0xFF000000),
    )
    AppSkin.YING -> SkinPalette(
        primaryLight = Color(0xFFC94770), primaryDark = Color(0xFFFFB0C6),
        accent = Color(0xFF8E6BC9), paper = Color(0xFFFFF7F9), night = Color(0xFF221319),
    )
    AppSkin.ZHU -> SkinPalette(
        primaryLight = Color(0xFF2F7D4E), primaryDark = Color(0xFF9AD8AE),
        accent = Color(0xFF7A8F2E), paper = Color(0xFFF5FAF5), night = Color(0xFF0F1C14),
    )
    AppSkin.MU -> SkinPalette(
        primaryLight = Color(0xFFB4621F), primaryDark = Color(0xFFFFB878),
        accent = Color(0xFF6B5BC0), paper = Color(0xFFFFF8F1), night = Color(0xFF1F160E),
    )
}

internal fun skinColorScheme(skin: AppSkin, dark: Boolean): ColorScheme {
    val p = skin.palette()
    return if (dark) {
        val bg = p.night
        darkColorScheme(
            primary = p.primaryDark,
            onPrimary = lerp(p.primaryDark, Color.Black, 0.75f),
            primaryContainer = lerp(p.primaryLight, Color.Black, 0.45f),
            onPrimaryContainer = lerp(p.primaryDark, Color.White, 0.6f),
            secondary = lerp(p.primaryDark, Color.Gray, 0.35f),
            onSecondary = Color.Black,
            secondaryContainer = lerp(bg, p.primaryDark, 0.22f),
            onSecondaryContainer = lerp(p.primaryDark, Color.White, 0.7f),
            tertiary = lerp(p.accent, Color.White, 0.45f),
            background = bg,
            onBackground = Color(0xFFE8ECF2),
            surface = bg,
            onSurface = Color(0xFFE8ECF2),
            surfaceVariant = lerp(bg, p.primaryDark, 0.14f),
            onSurfaceVariant = Color(0xFFB9C2CF),
            surfaceContainerLowest = lerp(bg, Color.Black, 0.3f),
            surfaceContainerLow = lerp(bg, p.primaryDark, 0.05f),
            surfaceContainer = lerp(bg, p.primaryDark, 0.08f),
            surfaceContainerHigh = lerp(bg, p.primaryDark, 0.12f),
            surfaceContainerHighest = lerp(bg, p.primaryDark, 0.16f),
            outline = lerp(bg, Color.White, 0.35f),
            outlineVariant = lerp(bg, Color.White, 0.15f),
        )
    } else {
        val bg = p.paper
        lightColorScheme(
            primary = p.primaryLight,
            onPrimary = Color.White,
            primaryContainer = lerp(p.primaryLight, Color.White, 0.82f),
            onPrimaryContainer = lerp(p.primaryLight, Color.Black, 0.6f),
            secondary = lerp(p.primaryLight, Color.Gray, 0.4f),
            onSecondary = Color.White,
            secondaryContainer = lerp(p.primaryLight, Color.White, 0.88f),
            onSecondaryContainer = lerp(p.primaryLight, Color.Black, 0.65f),
            tertiary = p.accent,
            background = bg,
            onBackground = Color(0xFF1A1C1F),
            surface = bg,
            onSurface = Color(0xFF1A1C1F),
            surfaceVariant = lerp(bg, p.primaryLight, 0.08f),
            onSurfaceVariant = Color(0xFF4A505A),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(bg, p.primaryLight, 0.03f),
            surfaceContainer = lerp(bg, p.primaryLight, 0.05f),
            surfaceContainerHigh = lerp(bg, p.primaryLight, 0.08f),
            surfaceContainerHighest = lerp(bg, p.primaryLight, 0.11f),
            outline = lerp(bg, Color.Black, 0.4f),
            outlineVariant = lerp(bg, Color.Black, 0.14f),
        )
    }
}

/** A skin's signature colour, used for the swatches in settings. */
fun AppSkin.swatch(dark: Boolean): Color = palette().let { if (dark) it.primaryDark else it.primaryLight }

@Composable
fun LanyinTheme(
    skin: AppSkin,
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = if (skin == AppSkin.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        skinColorScheme(skin, dark)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
