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
enum class EdgeKind { Glass, Hairline, Soft, Glow, Retro }

enum class BackdropKind { Tide, Ink, Sakura, Neon, Dusk }

enum class SeekKind { Wave, Hairline, Pill, Segments, Tape }

enum class ButtonKind { Glow, Outline, Blob, NeonRing, Retro }

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
    val isDark = dark || theme.darkOnly
    return when (theme) {
        AppTheme.TIDE -> LanyinStyle(
            theme = theme, dark = isDark,
            colors = if (isDark) tideDark else tideLight,
            display = FontFamily.SansSerif, displayWeight = FontWeight.Bold, displayTracking = (-0.02).em,
            body = FontFamily.SansSerif, numeric = FontFamily.SansSerif,
            cardShape = RoundedCornerShape(24.dp), chipShape = CircleShape, artShape = RoundedCornerShape(20.dp),
            edge = EdgeKind.Glass, backdrop = BackdropKind.Tide, seek = SeekKind.Wave,
            button = ButtonKind.Glow, nav = NavKind.Pill,
            lyric = LyricStyle(centered = false, activeScale = 1.04f, inactiveAlpha = 0.42f, blurStep = 0.7.dp, staggerMs = 38, glow = false, serif = false),
            motion = Motion(0.82f, 420f), bouncy = Motion(0.58f, 380f), pressScale = 0.94f,
            iconStroke = 1.8f,
        )
        AppTheme.INK -> LanyinStyle(
            theme = theme, dark = isDark,
            colors = if (isDark) inkDark else inkLight,
            display = FontFamily.Serif, displayWeight = FontWeight.Normal, displayTracking = 0.04.em,
            body = FontFamily.SansSerif, numeric = FontFamily.Serif,
            cardShape = RectangleShape, chipShape = RectangleShape, artShape = RectangleShape,
            edge = EdgeKind.Hairline, backdrop = BackdropKind.Ink, seek = SeekKind.Hairline,
            button = ButtonKind.Outline, nav = NavKind.Text,
            lyric = LyricStyle(centered = true, activeScale = 1f, inactiveAlpha = 0.34f, blurStep = 0.dp, staggerMs = 0, glow = false, serif = true),
            motion = Motion(1f, 700f), bouncy = Motion(0.9f, 600f), pressScale = 0.98f,
            iconStroke = 1.3f, dock = DockKind.Docked, margin = 24.dp, quietSections = true,
        )
        AppTheme.SAKURA -> LanyinStyle(
            theme = theme, dark = isDark,
            colors = if (isDark) sakuraDark else sakuraLight,
            display = FontFamily.SansSerif, displayWeight = FontWeight.SemiBold, displayTracking = 0.em,
            body = FontFamily.SansSerif, numeric = FontFamily.SansSerif,
            cardShape = RoundedCornerShape(28.dp), chipShape = CircleShape, artShape = RoundedCornerShape(32.dp),
            edge = EdgeKind.Soft, backdrop = BackdropKind.Sakura, seek = SeekKind.Pill,
            button = ButtonKind.Blob, nav = NavKind.Pill,
            lyric = LyricStyle(centered = true, activeScale = 1.08f, inactiveAlpha = 0.5f, blurStep = 0.5.dp, staggerMs = 55, glow = false, serif = false),
            motion = Motion(0.7f, 450f), bouncy = Motion(0.42f, 420f), pressScale = 0.9f,
            iconStroke = 2f,
        )
        AppTheme.NEON -> LanyinStyle(
            theme = theme, dark = true,
            colors = neon,
            display = FontFamily.SansSerif, displayWeight = FontWeight.Black, displayTracking = 0.06.em,
            body = FontFamily.SansSerif, numeric = FontFamily.Monospace,
            cardShape = CutCornerShape(14.dp), chipShape = CutCornerShape(10.dp), artShape = CutCornerShape(18.dp),
            edge = EdgeKind.Glow, backdrop = BackdropKind.Neon, seek = SeekKind.Segments,
            button = ButtonKind.NeonRing, nav = NavKind.Pill,
            lyric = LyricStyle(centered = false, activeScale = 1f, inactiveAlpha = 0.35f, blurStep = 0.6.dp, staggerMs = 18, glow = true, serif = false),
            motion = Motion(0.85f, 1100f), bouncy = Motion(0.55f, 900f), pressScale = 0.95f,
            iconStroke = 1.6f,
        )
        AppTheme.DUSK -> LanyinStyle(
            theme = theme, dark = isDark,
            colors = if (isDark) duskDark else duskLight,
            display = FontFamily.Serif, displayWeight = FontWeight.Bold, displayTracking = (-0.01).em,
            body = FontFamily.SansSerif, numeric = FontFamily.Serif,
            cardShape = RoundedCornerShape(18.dp), chipShape = CircleShape, artShape = RoundedCornerShape(14.dp),
            edge = EdgeKind.Retro, backdrop = BackdropKind.Dusk, seek = SeekKind.Tape,
            button = ButtonKind.Retro, nav = NavKind.Pill,
            lyric = LyricStyle(centered = false, activeScale = 1.03f, inactiveAlpha = 0.5f, blurStep = 0.dp, staggerMs = 45, glow = false, serif = true),
            motion = Motion(0.85f, 320f), bouncy = Motion(0.6f, 300f), pressScale = 1f,
            iconStroke = 2.2f,
        )
    }
}

private val tideLight = LanyinColors(
    bg = Color(0xFFF2F6FB), bgDeep = Color(0xFFD5E5F7), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFE6EFFA),
    content = Color(0xFF0D1B2A), muted = Color(0xFF56677D), accent = Color(0xFF1F6FD8), accentAlt = Color(0xFF10A8A3),
    onAccent = Color(0xFFFFFFFF), outline = Color(0x1F0D1B2A), like = Color(0xFFF0466A), vinyl = Color(0xFF0E1116),
)
private val tideDark = LanyinColors(
    bg = Color(0xFF06101D), bgDeep = Color(0xFF0C2646), surface = Color(0xFF0F1E33), surfaceAlt = Color(0xFF15283F),
    content = Color(0xFFEAF2FF), muted = Color(0xFF8FA3BD), accent = Color(0xFF6EB8FF), accentAlt = Color(0xFF3DDCCB),
    onAccent = Color(0xFF03101F), outline = Color(0x24FFFFFF), like = Color(0xFFFF6B88), vinyl = Color(0xFF0A0D12),
)
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
private val sakuraLight = LanyinColors(
    bg = Color(0xFFFFF4F7), bgDeep = Color(0xFFF3E4FF), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFFFE6EF),
    content = Color(0xFF3B2330), muted = Color(0xFF85616F), accent = Color(0xFFE5578A), accentAlt = Color(0xFF9B7BF0),
    onAccent = Color(0xFFFFFFFF), outline = Color(0x1A3B2330), like = Color(0xFFE5578A), vinyl = Color(0xFF2A1D25),
)
private val sakuraDark = LanyinColors(
    bg = Color(0xFF1D1118), bgDeep = Color(0xFF2B1835), surface = Color(0xFF2A1922), surfaceAlt = Color(0xFF35202C),
    content = Color(0xFFFDEAF2), muted = Color(0xFFC49AAE), accent = Color(0xFFFF8DB6), accentAlt = Color(0xFFC1A6FF),
    onAccent = Color(0xFF2A0E1A), outline = Color(0x1FFFFFFF), like = Color(0xFFFF8DB6), vinyl = Color(0xFF120A0F),
)
private val neon = LanyinColors(
    bg = Color(0xFF07060E), bgDeep = Color(0xFF1C0B33), surface = Color(0xFF110F1F), surfaceAlt = Color(0xFF1A1730),
    content = Color(0xFFECEAFF), muted = Color(0xFF8E8AB5), accent = Color(0xFF00EAFF), accentAlt = Color(0xFFFF2E97),
    onAccent = Color(0xFF03030A), outline = Color(0x5500EAFF), like = Color(0xFFFF2E97), vinyl = Color(0xFF050508),
)
private val duskLight = LanyinColors(
    bg = Color(0xFFF5E8D3), bgDeep = Color(0xFFF1C795), surface = Color(0xFFFFF4E2), surfaceAlt = Color(0xFFF0DFC4),
    content = Color(0xFF2B1A10), muted = Color(0xFF725546), accent = Color(0xFFD8561F), accentAlt = Color(0xFFE5A532),
    onAccent = Color(0xFFFFF4E2), outline = Color(0xFF2B1A10), like = Color(0xFFCC3F25), vinyl = Color(0xFF2A1A12),
)
private val duskDark = LanyinColors(
    bg = Color(0xFF1A110C), bgDeep = Color(0xFF3A1D14), surface = Color(0xFF26180F), surfaceAlt = Color(0xFF302016),
    content = Color(0xFFF7E4C6), muted = Color(0xFFB7957A), accent = Color(0xFFFF7A3D), accentAlt = Color(0xFFF2B84B),
    onAccent = Color(0xFF1A110C), outline = Color(0xFFF7E4C6), like = Color(0xFFFF6A4D), vinyl = Color(0xFF0F0906),
)
