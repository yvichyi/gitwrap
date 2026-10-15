package io.lanyin.player.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Immutable
data class LanyinColors(
    val bg: Color,
    /** Second backdrop colour, for gradients and drifting blobs. */
    val bgDeep: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val content: Color,
    val muted: Color,
    val accent: Color,
    val accentAlt: Color,
    val onAccent: Color,
    val outline: Color,
    val like: Color,
    /** Body colour of the vinyl record. */
    val vinyl: Color,
)

/** How cards, the mini player and the tab bar are drawn. */
enum class EdgeKind { Hairline, Soft, Glow, Brutal }

enum class BackdropKind { Ink, Neon, Dusk, Grid }

enum class SeekKind { Hairline, Pill, Segments, Block }

enum class ButtonKind { Outline, Blob, NeonRing, Brutal }

enum class NavKind { Pill, Text }

/** Floating pills over the content, or a bar docked to the bottom edge. */
enum class DockKind { Floating, Docked }

/**
 * The whole type scale: four sizes plus a tracked label. Pages use only these, so hierarchy
 * comes from a consistent rhythm instead of ad-hoc sizes.
 */
@Immutable
data class LanyinType(
    val display: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    /** Small tracked label for section headings in quiet themes and for tags like 「无损」. */
    val label: TextStyle,
)

@Immutable
data class LyricStyle(
    val centered: Boolean,
    val activeScale: Float,
    val inactiveAlpha: Float,
    /** Extra blur per line of distance from the active line (Android 12+ only). */
    val blurStep: Dp,
    /** Delay between neighbouring lines when the list moves, for a rippling scroll. */
    val staggerMs: Int,
    val glow: Boolean,
    val serif: Boolean,
)

@Immutable
data class Motion(val dampingRatio: Float, val stiffness: Float) {
    fun <T> spec(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = dampingRatio, stiffness = stiffness, visibilityThreshold = visibilityThreshold)
}

@Immutable
data class LanyinStyle(
    val theme: AppTheme,
    val dark: Boolean,
    val colors: LanyinColors,
    val display: FontFamily,
    val displayWeight: FontWeight,
    val displayTracking: TextUnit,
    val body: FontFamily,
    val numeric: FontFamily,
    val cardShape: Shape,
    val chipShape: Shape,
    val artShape: Shape,
    val edge: EdgeKind,
    val backdrop: BackdropKind,
    val seek: SeekKind,
    val button: ButtonKind,
    val nav: NavKind,
    val lyric: LyricStyle,
    /** Everyday UI movement. */
    val motion: Motion,
    /** Playful elements: the tone arm, like button, active tab. */
    val bouncy: Motion,
    val pressScale: Float,
    /** Line weight of [io.lanyin.player.ui.design.LineIcon]s, in 24-unit grid units. */
    val iconStroke: Float = 1.8f,
    val dock: DockKind = DockKind.Floating,
    /** Horizontal page margin. */
    val margin: Dp = 20.dp,
    /** Section headings as small tracked labels rather than titles. */
    val quietSections: Boolean = false,
) {
    val type: LanyinType by lazy { typeScale() }

    /** Thick outline plus a hard, offset shadow: 格's brutalist cards. */
    val hardEdge: Boolean get() = edge == EdgeKind.Brutal

    /** Small round things (icon buttons, avatars, dots); 格 squares them off. */
    val roundShape: Shape get() = if (edge == EdgeKind.Brutal) RectangleShape else CircleShape

    /** Outline width of hard-edged surfaces. */
    val strokeWidth: Dp get() = if (edge == EdgeKind.Brutal) 2.5.dp else 2.dp

    /** Offset of a hard-edged card's shadow. */
    val shadowOffset: Dp get() = if (edge == EdgeKind.Brutal) 6.dp else 4.dp

    private fun typeScale(): LanyinType {
        val serif = display == FontFamily.Serif
        val titleWeight = when {
            displayWeight >= FontWeight.Bold -> FontWeight.Bold
            serif -> FontWeight.Medium
            else -> FontWeight.SemiBold
        }
        return LanyinType(
            display = TextStyle(fontFamily = display, fontWeight = displayWeight, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = displayTracking),
            title = TextStyle(fontFamily = if (serif) display else body, fontWeight = titleWeight, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = if (serif) 0.02.em else 0.em),
            body = TextStyle(fontFamily = body, fontSize = 15.sp, lineHeight = 22.sp),
            caption = TextStyle(fontFamily = body, fontSize = 12.5.sp, lineHeight = 18.sp, letterSpacing = 0.02.em),
            label = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.18.em),
        )
    }
}

val LocalLanyinStyle = staticCompositionLocalOf { lanyinStyle(AppTheme.Default, dark = false) }

fun lanyinStyle(theme: AppTheme, dark: Boolean): LanyinStyle {
    val isDark = (dark || theme.darkOnly) && !theme.lightOnly
    return when (theme) {
        AppTheme.INK -> LanyinStyle(
            theme = theme, dark = isDark,
            colors = if (isDark) inkDark else inkLight,
            display = FontFamily.Serif, displayWeight = FontWeight.Normal, displayTracking = 0.04.em,
            body = FontFamily.SansSerif, numeric = FontFamily.Serif,
            cardShape = RectangleShape, chipShape = RectangleShape, artShape = RectangleShape,
            edge = EdgeKind.Hairline, backdrop = BackdropKind.Ink, seek = SeekKind.Hairline,
            button = ButtonKind.Outline, nav = NavKind.Text,
            lyric = LyricStyle(centered = true, activeScale = 1.08f, inactiveAlpha = 0.34f, blurStep = 0.dp, staggerMs = 0, glow = false, serif = true),
            motion = Motion(1f, 700f), bouncy = Motion(0.9f, 600f), pressScale = 0.98f,
            iconStroke = 1.3f, dock = DockKind.Docked, margin = 24.dp, quietSections = true,
        )
        AppTheme.NEON -> LanyinStyle(
            theme = theme, dark = true,
            colors = neon,
            display = FontFamily.SansSerif, displayWeight = FontWeight.Black, displayTracking = 0.06.em,
            body = FontFamily.SansSerif, numeric = FontFamily.Monospace,
            cardShape = CutCornerShape(14.dp), chipShape = CutCornerShape(10.dp), artShape = CutCornerShape(18.dp),
            edge = EdgeKind.Glow, backdrop = BackdropKind.Neon, seek = SeekKind.Segments,
            button = ButtonKind.NeonRing, nav = NavKind.Pill,
            lyric = LyricStyle(centered = false, activeScale = 1.1f, inactiveAlpha = 0.35f, blurStep = 0.6.dp, staggerMs = 18, glow = true, serif = false),
            motion = Motion(0.85f, 1100f), bouncy = Motion(0.55f, 900f), pressScale = 0.95f,
            iconStroke = 1.6f,
        )
        // 暮: an evening sky. Warm paper (plum by night), terracotta fading to rose, soft rounded
        // cards that float on a gentle shadow, a low sun; nothing outlined.
        AppTheme.DUSK -> LanyinStyle(
            theme = theme, dark = isDark,
            colors = if (isDark) duskDark else duskLight,
            display = FontFamily.SansSerif, displayWeight = FontWeight.SemiBold, displayTracking = 0.em,
            body = FontFamily.SansSerif, numeric = FontFamily.SansSerif,
            cardShape = RoundedCornerShape(22.dp), chipShape = CircleShape, artShape = RoundedCornerShape(16.dp),
            edge = EdgeKind.Soft, backdrop = BackdropKind.Dusk, seek = SeekKind.Pill,
            button = ButtonKind.Blob, nav = NavKind.Pill,
            lyric = LyricStyle(centered = true, activeScale = 1.1f, inactiveAlpha = 0.42f, blurStep = 0.5.dp, staggerMs = 40, glow = false, serif = false),
            motion = Motion(0.85f, 380f), bouncy = Motion(0.6f, 340f), pressScale = 0.96f,
            iconStroke = 1.9f,
        )
        // 格: Swiss grid meets brutalism. Paper, ink, three primaries; heavy grotesk headlines,
        // monospaced labels, square corners, thick rules and hard shadows that you press into.
        AppTheme.GRID -> LanyinStyle(
            theme = theme, dark = false,
            colors = grid,
            display = FontFamily.SansSerif, displayWeight = FontWeight.Black, displayTracking = (-0.02).em,
            body = FontFamily.SansSerif, numeric = FontFamily.Monospace,
            cardShape = RectangleShape, chipShape = RectangleShape, artShape = RectangleShape,
            edge = EdgeKind.Brutal, backdrop = BackdropKind.Grid, seek = SeekKind.Block,
            button = ButtonKind.Brutal, nav = NavKind.Pill,
            lyric = LyricStyle(centered = false, activeScale = 1.06f, inactiveAlpha = 0.42f, blurStep = 0.dp, staggerMs = 22, glow = false, serif = false),
            motion = Motion(0.72f, 900f), bouncy = Motion(0.42f, 700f), pressScale = 1f,
            iconStroke = 2.2f,
        )
    }
}

private val inkLight = LanyinColors(
    bg = Color(0xFFF4F1EA), bgDeep = Color(0xFFE4DFD3), surface = Color(0xFFF4F1EA), surfaceAlt = Color(0xFFEBE7DE),
    content = Color(0xFF151515), muted = Color(0xFF66625A), accent = Color(0xFF151515), accentAlt = Color(0xFFA8322A),
    onAccent = Color(0xFFF4F1EA), outline = Color(0x33151515), like = Color(0xFFA8322A), vinyl = Color(0xFF151515),
)
private val inkDark = LanyinColors(
    bg = Color(0xFF0B0B0B), bgDeep = Color(0xFF181818), surface = Color(0xFF0B0B0B), surfaceAlt = Color(0xFF161616),
    content = Color(0xFFECEAE4), muted = Color(0xFF8E8B82), accent = Color(0xFFECEAE4), accentAlt = Color(0xFFD4493F),
    onAccent = Color(0xFF0B0B0B), outline = Color(0x3DECEAE4), like = Color(0xFFD4493F), vinyl = Color(0xFF1C1C1C),
)
// Light on near-black: electric cyan for what you touch, a warm amber for the sun and the horizon,
// greys with a cold cast. No purple haze.
private val neon = LanyinColors(
    bg = Color(0xFF05070A), bgDeep = Color(0xFF0A1820), surface = Color(0xFF0A0F14), surfaceAlt = Color(0xFF101820),
    content = Color(0xFFE4F4FA), muted = Color(0xFF7C909E), accent = Color(0xFF34E4FF), accentAlt = Color(0xFFFF8A2A),
    onAccent = Color(0xFF02090C), outline = Color(0x5534E4FF), like = Color(0xFFFF6A3D), vinyl = Color(0xFF030405),
)
// Paper is a warm off-white and the sky a dusty peach, so mustard stays an accent rather than
// the colour of the whole page.
private val grid = LanyinColors(
    bg = Color(0xFFF5F2EB), bgDeep = Color(0xFFE9E4D8), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFF5F2EB),
    content = Color(0xFF111111), muted = Color(0xFF4F4C47), accent = Color(0xFFFF3B1D), accentAlt = Color(0xFF1D4ED8),
    onAccent = Color(0xFFFFFFFF), outline = Color(0xFF111111), like = Color(0xFFFF3B1D), vinyl = Color(0xFF111111),
)

/** 格's third primary, for stickers and highlights. */
val GridYellow = Color(0xFFFACC15)

// Evening light on warm paper: terracotta into rose, a brown that is almost black.
private val duskLight = LanyinColors(
    bg = Color(0xFFF7F1EA), bgDeep = Color(0xFFF0D3C3), surface = Color(0xFFFFFBF7), surfaceAlt = Color(0xFFF2E7DD),
    content = Color(0xFF2A1D1A), muted = Color(0xFF7D6A62), accent = Color(0xFFD2603A), accentAlt = Color(0xFFC24E63),
    onAccent = Color(0xFFFFF7F2), outline = Color(0x1F2A1D1A), like = Color(0xFFC9403A), vinyl = Color(0xFF241A17),
)
// …and once the sun is down: plum and ember.
private val duskDark = LanyinColors(
    bg = Color(0xFF17110F), bgDeep = Color(0xFF2E1B26), surface = Color(0xFF231917), surfaceAlt = Color(0xFF2D211E),
    content = Color(0xFFF3E6DA), muted = Color(0xFFB09A8E), accent = Color(0xFFEE7A4F), accentAlt = Color(0xFFDB6280),
    onAccent = Color(0xFF1A0F0C), outline = Color(0x24F3E6DA), like = Color(0xFFF06A5A), vinyl = Color(0xFF0E0A09),
)
