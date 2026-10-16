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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.lanyin.player.R

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

/**
 * How cards, the mini player and the tab bar are drawn: hairlines (墨), soft shadows (暮), neon
 * (霓), hard shadows (格), a print's thin rim (影), flat grey tiles on black (点), the recessed
 * panels of a machine's case (机), a newspaper's column rules (报), little LCD screens in their
 * bezels (游) and chrome rims (酸).
 */
enum class EdgeKind { Hairline, Soft, Glow, Brutal, Film, Dot, Panel, Rule, Bezel, Chrome }

enum class BackdropKind { Ink, Neon, Dusk, Grid, Film, Dot, Device, Paper, Game, Acid }

enum class SeekKind { Hairline, Pill, Tube, Block, Film, Dots, Fader, Ruler, Blocks, Stripe }

enum class ButtonKind { Outline, Blob, NeonRing, Brutal, Aperture, Disc, Key, Stamp, Pad, Burst }

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
    /** Lyric size relative to the setting. */
    val sizeScale: Float = 1f,
    /** The sung part in the text colour rather than the accent (影's subtitles, 点's white). */
    val inkSweep: Boolean = false,
    val weight: FontWeight = FontWeight.Bold,
    /** A highlighter stroke under the sung part (报's red pen). */
    val marker: Boolean = false,
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
            display = FontFamily.SansSerif, displayWeight = FontWeight.Bold, displayTracking = 0.02.em,
            body = FontFamily.SansSerif, numeric = TiltNeonFamily,
            cardShape = RoundedCornerShape(16.dp), chipShape = CircleShape, artShape = RoundedCornerShape(12.dp),
            edge = EdgeKind.Glow, backdrop = BackdropKind.Neon, seek = SeekKind.Tube,
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
        // 点: after Nothing's dot-matrix language. True black and white with one red dot; numbers
        // and the odd Latin label in a dot-matrix face, flat grey tiles, the cover shown through
        // an LED screen and a ring of glyph lights round it that answer the music.
        AppTheme.DOT -> LanyinStyle(
            theme = theme, dark = true,
            colors = dot,
            display = FontFamily.SansSerif, displayWeight = FontWeight.Bold, displayTracking = 0.01.em,
            body = FontFamily.SansSerif, numeric = DotoFamily,
            cardShape = RoundedCornerShape(20.dp), chipShape = CircleShape, artShape = RoundedCornerShape(14.dp),
            edge = EdgeKind.Dot, backdrop = BackdropKind.Dot, seek = SeekKind.Dots,
            button = ButtonKind.Disc, nav = NavKind.Text,
            lyric = LyricStyle(
                centered = false, activeScale = 1.04f, inactiveAlpha = 0.26f, blurStep = 0.dp, staggerMs = 24, glow = false, serif = false,
                sizeScale = 1.08f, inkSweep = true, weight = FontWeight.Bold,
            ),
            motion = Motion(0.9f, 700f), bouncy = Motion(0.7f, 600f), pressScale = 0.95f,
            iconStroke = 1.5f, dock = DockKind.Docked,
        )
        // 机: a piece of hardware, after Teenage Engineering and Braun. Warm grey plastic, a screen
        // set into the case that holds the artwork and the lyrics, keys that sink when pressed, an
        // orange play key, a fader for the progress and a VU needle that swings with the music;
        // labels in a typewriter mono.
        AppTheme.DEVICE -> LanyinStyle(
            theme = theme, dark = isDark,
            colors = if (isDark) deviceDark else deviceLight,
            display = FontFamily.SansSerif, displayWeight = FontWeight.Black, displayTracking = (-0.01).em,
            body = FontFamily.SansSerif, numeric = SpaceMonoFamily,
            cardShape = RoundedCornerShape(18.dp), chipShape = RoundedCornerShape(10.dp), artShape = RoundedCornerShape(8.dp),
            edge = EdgeKind.Panel, backdrop = BackdropKind.Device, seek = SeekKind.Fader,
            button = ButtonKind.Key, nav = NavKind.Pill,
            lyric = LyricStyle(centered = false, activeScale = 1.04f, inactiveAlpha = 0.34f, blurStep = 0.dp, staggerMs = 20, glow = false, serif = false, weight = FontWeight.Bold),
            motion = Motion(0.85f, 800f), bouncy = Motion(0.6f, 700f), pressScale = 0.97f,
            iconStroke = 1.9f,
        )
        // 影: a roll of film in a dark room. Warm black, grain that never sits still, light
        // leaking in at the edges, sprocket holes, an aperture for a play button and the orange
        // date stamp of a point-and-shoot; lyrics as subtitles.
        AppTheme.FILM -> LanyinStyle(
            theme = theme, dark = true,
            colors = film,
            display = FontFamily.Serif, displayWeight = FontWeight.Medium, displayTracking = 0.02.em,
            body = FontFamily.SansSerif, numeric = FontFamily.Monospace,
            cardShape = RoundedCornerShape(6.dp), chipShape = RoundedCornerShape(4.dp), artShape = RoundedCornerShape(2.dp),
            edge = EdgeKind.Film, backdrop = BackdropKind.Film, seek = SeekKind.Film,
            button = ButtonKind.Aperture, nav = NavKind.Pill,
            lyric = LyricStyle(centered = true, activeScale = 1.04f, inactiveAlpha = 0.3f, blurStep = 0.5.dp, staggerMs = 30, glow = false, serif = true, inkSweep = true, weight = FontWeight.Medium),
            motion = Motion(0.92f, 480f), bouncy = Motion(0.7f, 420f), pressScale = 0.95f,
            iconStroke = 1.6f, dock = DockKind.Docked,
        )
        // 报: a broadsheet. Newsprint and ink with one red, everything set in serif, headlines
        // heavy; columns ruled off rather than boxed, photos printed in halftone, a ruler for the
        // progress, a block of ink for the play key printed a hair off its red plate, and the
        // line being sung marked in red pen. The night edition prints on dark stock.
        AppTheme.PAPER -> LanyinStyle(
            theme = theme, dark = isDark,
            colors = if (isDark) paperNight else paper,
            display = FontFamily.Serif, displayWeight = FontWeight.Black, displayTracking = 0.04.em,
            body = FontFamily.Serif, numeric = FontFamily.Serif,
            cardShape = RectangleShape, chipShape = RectangleShape, artShape = RectangleShape,
            edge = EdgeKind.Rule, backdrop = BackdropKind.Paper, seek = SeekKind.Ruler,
            button = ButtonKind.Stamp, nav = NavKind.Text,
            lyric = LyricStyle(
                centered = false, activeScale = 1f, inactiveAlpha = 0.48f, blurStep = 0.dp, staggerMs = 25, glow = false, serif = true,
                inkSweep = true, marker = true,
            ),
            motion = Motion(0.95f, 650f), bouncy = Motion(0.8f, 550f), pressScale = 0.97f,
            iconStroke = 1.6f, dock = DockKind.Docked, margin = 22.dp, quietSections = false,
        )
        // 游: a pocket games console. The pages are its menus, on the grey case: cards are little
        // LCD screens in dark bezels and everything is set in a pixel face; the player is the whole
        // console, its screen showing the cover dithered into four greens, a D-pad for skipping and
        // a magenta A button to play.
        AppTheme.GAME -> LanyinStyle(
            theme = theme, dark = false,
            colors = game,
            display = PixelFamily, displayWeight = FontWeight.Normal, displayTracking = 0.02.em,
            body = PixelFamily, numeric = PixelFamily,
            cardShape = CutCornerShape(6.dp), chipShape = CutCornerShape(4.dp), artShape = RectangleShape,
            edge = EdgeKind.Bezel, backdrop = BackdropKind.Game, seek = SeekKind.Blocks,
            button = ButtonKind.Pad, nav = NavKind.Text,
            lyric = LyricStyle(centered = false, activeScale = 1f, inactiveAlpha = 0.5f, blurStep = 0.dp, staggerMs = 0, glow = false, serif = false, weight = FontWeight.Normal),
            motion = Motion(1f, 1400f), bouncy = Motion(0.75f, 1100f), pressScale = 0.94f,
            iconStroke = 2.2f, dock = DockKind.Docked,
        )
        // 酸: the millennium in candy plastic and chrome. A grape night, translucent shells in
        // Bondi blue and tangerine with a gloss across the top, chrome rims and a chrome wordmark;
        // lime kept for the one thing that matters on a screen: what's playing, what to press.
        AppTheme.ACID -> LanyinStyle(
            theme = theme, dark = true,
            colors = acid,
            display = ArchivoBlackFamily, displayWeight = FontWeight.Black, displayTracking = (-0.01).em,
            body = FontFamily.SansSerif, numeric = SpaceMonoFamily,
            cardShape = RoundedCornerShape(24.dp), chipShape = CircleShape, artShape = RoundedCornerShape(20.dp),
            edge = EdgeKind.Chrome, backdrop = BackdropKind.Acid, seek = SeekKind.Stripe,
            button = ButtonKind.Burst, nav = NavKind.Pill,
            lyric = LyricStyle(centered = false, activeScale = 1.06f, inactiveAlpha = 0.3f, blurStep = 0.4.dp, staggerMs = 30, glow = true, serif = false, weight = FontWeight.Black),
            motion = Motion(0.72f, 700f), bouncy = Motion(0.45f, 600f), pressScale = 0.92f,
            iconStroke = 2f,
        )
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
// A street at night after rain: walls near black with a violet cast, matte, and the only colour
// the light of the signs, a hot pink tube and an ice cyan one.
private val neon = LanyinColors(
    bg = Color(0xFF0D0B12), bgDeep = Color(0xFF1B1526), surface = Color(0xFF17141E), surfaceAlt = Color(0xFF201B29),
    content = Color(0xFFF3EEF7), muted = Color(0xFF968CA6), accent = Color(0xFFFF4D9A), accentAlt = Color(0xFF3DE3FF),
    onAccent = Color(0xFF1A0610), outline = Color(0x1FFFFFFF), like = Color(0xFFFF4D9A), vinyl = Color(0xFF08070B),
)
// Paper is a warm off-white and the sky a dusty peach, so mustard stays an accent rather than
// the colour of the whole page.
private val grid = LanyinColors(
    bg = Color(0xFFF5F2EB), bgDeep = Color(0xFFE9E4D8), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFF5F2EB),
    content = Color(0xFF111111), muted = Color(0xFF4F4C47), accent = Color(0xFFFF3B1D), accentAlt = Color(0xFF1D4ED8),
    onAccent = Color(0xFFFFFFFF), outline = Color(0xFF111111), like = Color(0xFFFF3B1D), vinyl = Color(0xFF111111),
)

// Nothing's palette: black, white, greys and the red of its recording dot.
private val dot = LanyinColors(
    bg = Color(0xFF000000), bgDeep = Color(0xFF0B0B0B), surface = Color(0xFF131313), surfaceAlt = Color(0xFF1C1C1C),
    content = Color(0xFFF5F5F5), muted = Color(0xFF8C8C8C), accent = Color(0xFFF5F5F5), accentAlt = Color(0xFFD71921),
    onAccent = Color(0xFF000000), outline = Color(0x29FFFFFF), like = Color(0xFFD71921), vinyl = Color(0xFF0A0A0A),
)
// Warm grey case, near-black print, the orange of a play key (deep enough to read as text on the
// grey) and a blue for the second colour.
private val deviceLight = LanyinColors(
    bg = Color(0xFFE4E1D9), bgDeep = Color(0xFFD8D4CB), surface = Color(0xFFEFECE5), surfaceAlt = Color(0xFFE9E6DF),
    content = Color(0xFF1A1A1A), muted = Color(0xFF5F5B53), accent = Color(0xFFE04A12), accentAlt = Color(0xFF2B6CB0),
    onAccent = Color(0xFFFFFFFF), outline = Color(0x261A1A1A), like = Color(0xFFE04A12), vinyl = Color(0xFF1A1A1A),
)
// …and the graphite edition.
private val deviceDark = LanyinColors(
    bg = Color(0xFF2A2B2E), bgDeep = Color(0xFF202124), surface = Color(0xFF34353A), surfaceAlt = Color(0xFF303135),
    content = Color(0xFFECEAE4), muted = Color(0xFF9C9990), accent = Color(0xFFEA581C), accentAlt = Color(0xFF6A9BE8),
    onAccent = Color(0xFFFFFFFF), outline = Color(0x2EECEAE4), like = Color(0xFFEA581C), vinyl = Color(0xFF121214),
)
// A darkroom: near-black with only a breath of warmth (not brown), greys with a little
// warmth in them, the paper white of a print, burnt orange and a red light leak.
private val film = LanyinColors(
    bg = Color(0xFF0E0D0C), bgDeep = Color(0xFF191512), surface = Color(0xFF1A1817), surfaceAlt = Color(0xFF24211F),
    content = Color(0xFFF2EBDF), muted = Color(0xFFA0988C), accent = Color(0xFFE8743B), accentAlt = Color(0xFFD9484A),
    onAccent = Color(0xFF1A0F08), outline = Color(0x24F2EBDF), like = Color(0xFFE5484D), vinyl = Color(0xFF0B0A09),
)

/** 影's date stamp: the orange of a point-and-shoot's printed date. */
val FilmStamp = Color(0xFFFF8A1F)

// Newsprint, ink and the red of a sub-editor's pen.
private val paper = LanyinColors(
    bg = Color(0xFFF1ECE1), bgDeep = Color(0xFFE6DFD0), surface = Color(0xFFF6F2E9), surfaceAlt = Color(0xFFEAE4D7),
    content = Color(0xFF1B1A17), muted = Color(0xFF59554C), accent = Color(0xFFC8102E), accentAlt = Color(0xFF23395B),
    onAccent = Color(0xFFF6F2E9), outline = Color(0x401B1A17), like = Color(0xFFC8102E), vinyl = Color(0xFF1B1A17),
)
// The night edition: dark stock, warm white ink, a brighter red.
private val paperNight = LanyinColors(
    bg = Color(0xFF171614), bgDeep = Color(0xFF1F1D1A), surface = Color(0xFF1C1A17), surfaceAlt = Color(0xFF25221E),
    content = Color(0xFFE9E3D6), muted = Color(0xFFA39C8E), accent = Color(0xFFF0555A), accentAlt = Color(0xFF8FB0E0),
    onAccent = Color(0xFF171614), outline = Color(0x40E9E3D6), like = Color(0xFFF0555A), vinyl = Color(0xFF0E0D0C),
)
// A grey console: its case, the green of its screen for cards, near-black green print, the
// magenta of the A and B buttons and the navy of the lettering.
private val game = LanyinColors(
    bg = Color(0xFFD6D3CA), bgDeep = Color(0xFFC8C4BA), surface = Color(0xFFA9C06C), surfaceAlt = Color(0xFF9BB45E),
    content = Color(0xFF10260F), muted = Color(0xFF3B5230), accent = Color(0xFF9A2257), accentAlt = Color(0xFF2F3A8F),
    onAccent = Color(0xFFF4F0E8), outline = Color(0x5910260F), like = Color(0xFF9A2257), vinyl = Color(0xFF2D2C33),
)
// Black, white, a lime that glows and a hot pink.
private val acid = LanyinColors(
    bg = Color(0xFF0E0B1A), bgDeep = Color(0xFF1B1430), surface = Color(0xFF1C1830), surfaceAlt = Color(0xFF262040),
    content = Color(0xFFFFFFFF), muted = Color(0xFFA9A3C0), accent = Color(0xFFC6FF00), accentAlt = Color(0xFFFF3EA5),
    onAccent = Color(0xFF0E0B1A), outline = Color(0x33FFFFFF), like = Color(0xFFFF3EA5), vinyl = Color(0xFF07060D),
)

/** Fusion Pixel (SIL OFL), 游's face for everything, Chinese included. One weight, so bold stays crisp rather than smeared. */
val PixelFamily = FontFamily(
    Font(R.font.fusion_pixel, FontWeight.Normal),
    Font(R.font.fusion_pixel, FontWeight.Medium),
    Font(R.font.fusion_pixel, FontWeight.SemiBold),
    Font(R.font.fusion_pixel, FontWeight.Bold),
    Font(R.font.fusion_pixel, FontWeight.Black),
)

/** Archivo Black (SIL OFL), 酸's heading face; Chinese falls back to the system sans. */
val ArchivoBlackFamily = FontFamily(
    Font(R.font.archivo_black, FontWeight.Normal),
    Font(R.font.archivo_black, FontWeight.Bold),
    Font(R.font.archivo_black, FontWeight.Black),
)

/** Doto with round dots (SIL OFL), for 点's numbers and Latin labels; Chinese falls back to the system sans. */
val DotoFamily = FontFamily(
    Font(R.font.doto_bold, FontWeight.Normal),
    Font(R.font.doto_bold, FontWeight.Medium),
    Font(R.font.doto_black, FontWeight.SemiBold),
    Font(R.font.doto_black, FontWeight.Bold),
    Font(R.font.doto_black, FontWeight.Black),
)

/** Tilt Neon (SIL OFL), letters bent from a single tube: 霓's signs and numbers. Chinese falls back to the system sans. */
val TiltNeonFamily = FontFamily(
    Font(R.font.tilt_neon, FontWeight.Normal),
    Font(R.font.tilt_neon, FontWeight.Medium),
    Font(R.font.tilt_neon, FontWeight.Bold),
)

/** Space Mono (SIL OFL), 机's label and counter face. */
val SpaceMonoFamily = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_regular, FontWeight.Medium),
    Font(R.font.space_mono_bold, FontWeight.SemiBold),
    Font(R.font.space_mono_bold, FontWeight.Bold),
    Font(R.font.space_mono_bold, FontWeight.Black),
)

/** 游's screen: the four greens of the original LCD, lightest to darkest. */
val GameLcd = listOf(Color(0xFF9BBC0F), Color(0xFF8BAC0F), Color(0xFF306230), Color(0xFF082808))

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
