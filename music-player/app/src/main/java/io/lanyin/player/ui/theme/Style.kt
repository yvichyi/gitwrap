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
import androidx.compose.ui.text.font.FontStyle
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
 * How cards, the mini player and the tab bar are drawn: hairlines (墨), sheets on the cutting mat (暮), neon
 * (霓), hard shadows (格), flat grey tiles on black (点), the recessed
 * panels of a machine's case (机), a newspaper's column rules (报), little LCD screens in their
 * bezels (游) and chrome rims (酸).
 */
enum class EdgeKind { Hairline, Soft, Glow, Brutal, Dot, Panel, Rule, Bezel, Chrome }

enum class BackdropKind { Ink, Neon, Dusk, Grid, Dot, Device, Paper, Game, Acid }

enum class SeekKind { Hairline, Pill, Tube, Block, Dots, Fader, Ruler, Blocks, Stripe }

enum class ButtonKind { Outline, Blob, NeonRing, Brutal, Disc, Key, Stamp, Pad, Burst }

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
    /** The sung part in the text colour rather than the accent (点's white). */
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
        // 霓: a night drive, in pixel art. You're in the car on the expressway in the rain; the
        // pages are the centre console's screen, set in a pixel face on the night's blue-black,
        // sodium amber for what to press, the teal of a sign beside it, brake-light red for a
        // heart; the radio's LCD is the mini player and its preset keys are the tabs.
        AppTheme.NEON -> LanyinStyle(
            theme = theme, dark = true,
            colors = neon,
            display = PixelFamily, displayWeight = FontWeight.Normal, displayTracking = 0.02.em,
            body = PixelFamily, numeric = PixelFamily,
            cardShape = CutCornerShape(3.dp), chipShape = CutCornerShape(3.dp), artShape = RectangleShape,
            edge = EdgeKind.Glow, backdrop = BackdropKind.Neon, seek = SeekKind.Tube,
            button = ButtonKind.NeonRing, nav = NavKind.Text,
            lyric = LyricStyle(centered = true, activeScale = 1.06f, inactiveAlpha = 0.4f, blurStep = 0.dp, staggerMs = 0, glow = true, serif = false, weight = FontWeight.Normal),
            motion = Motion(1f, 1100f), bouncy = Motion(0.75f, 900f), pressScale = 0.95f,
            iconStroke = 2f, dock = DockKind.Docked,
        )
        // 暮: a riso print studio at dusk. A green cutting mat with its centimetre grid under the
        // whole app (by night a black one under the lamp); prints in three inks (fluorescent pink,
        // sunflower, medium blue) taped to it, a hair out of register; pencil notes in a hand;
        // a steel rule for the progress and rubber stamps to press. Paper is a style of its own
        // inside the sheets, see [paperStyle].
        AppTheme.DUSK -> LanyinStyle(
            theme = theme, dark = isDark,
            colors = if (isDark) duskNight else duskMat,
            display = FontFamily.Serif, displayWeight = FontWeight.Black, displayTracking = 0.02.em,
            body = FontFamily.SansSerif, numeric = PostcardSerifFamily,
            cardShape = RoundedCornerShape(3.dp), chipShape = RoundedCornerShape(50), artShape = RectangleShape,
            edge = EdgeKind.Soft, backdrop = BackdropKind.Dusk, seek = SeekKind.Pill,
            button = ButtonKind.Blob, nav = NavKind.Text,
            lyric = LyricStyle(centered = true, activeScale = 1.08f, inactiveAlpha = 0.42f, blurStep = 0.dp, staggerMs = 30, glow = false, serif = true, weight = FontWeight.Bold),
            motion = Motion(0.85f, 420f), bouncy = Motion(0.62f, 380f), pressScale = 0.97f,
            iconStroke = 1.7f, dock = DockKind.Docked,
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
        // 机: a hi-fi stack in a walnut cabinet. Champagne-silver brushed aluminium (by night the
        // black anodised finish), engraved in near black; displays are black glass with a cyan
        // fluorescent screen behind; knobs are machined, keys are aluminium, meters lamp-lit, the
        // progress a lamp-lit tape scale. REC red (by night the screen's cyan) for what to press.
        AppTheme.DEVICE -> LanyinStyle(
            theme = theme, dark = isDark,
            colors = if (isDark) deviceDark else deviceLight,
            display = FontFamily.SansSerif, displayWeight = FontWeight.Black, displayTracking = 0.01.em,
            body = FontFamily.SansSerif, numeric = SpaceMonoFamily,
            cardShape = RoundedCornerShape(4.dp), chipShape = RoundedCornerShape(4.dp), artShape = RoundedCornerShape(2.dp),
            edge = EdgeKind.Panel, backdrop = BackdropKind.Device, seek = SeekKind.Fader,
            button = ButtonKind.Key, nav = NavKind.Pill,
            lyric = LyricStyle(centered = false, activeScale = 1.04f, inactiveAlpha = 0.34f, blurStep = 0.dp, staggerMs = 20, glow = false, serif = false, weight = FontWeight.Bold),
            motion = Motion(0.85f, 800f), bouncy = Motion(0.6f, 700f), pressScale = 0.97f,
            iconStroke = 1.8f, dock = DockKind.Docked,
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
        // 游: a game in one bit, full screen, no console round it. Ink and paper and nothing between,
        // greys by dithering; every word in a pixel face. Cards are game windows with a double
        // border; the home page is a world map whose places are levels, the player a side-scrolling
        // stage where the cat runs to the flag as the song plays, the moon the cover in one bit.
        AppTheme.GAME -> LanyinStyle(
            theme = theme, dark = false,
            colors = game,
            display = PixelFamily, displayWeight = FontWeight.Normal, displayTracking = 0.02.em,
            body = PixelFamily, numeric = PixelFamily,
            cardShape = RectangleShape, chipShape = RectangleShape, artShape = RectangleShape,
            edge = EdgeKind.Bezel, backdrop = BackdropKind.Game, seek = SeekKind.Blocks,
            button = ButtonKind.Pad, nav = NavKind.Text,
            lyric = LyricStyle(centered = false, activeScale = 1f, inactiveAlpha = 0.5f, blurStep = 0.dp, staggerMs = 0, glow = false, serif = false, weight = FontWeight.Normal),
            motion = Motion(1f, 1400f), bouncy = Motion(0.75f, 1100f), pressScale = 0.94f,
            iconStroke = 2.2f, dock = DockKind.Docked,
        )
        // 酸: a wall of rave flyers. Wheat-pasted on grey concrete, layer over layer, old ones torn;
        // flyers of black stock and copier paper, the headline flyer in acid lime; covers
        // photocopied in coarse halftone; tape, staples, smiley stickers and barcodes. Headlines in
        // Archivo Black, small print in mono; the player is the song's own poster.
        AppTheme.ACID -> LanyinStyle(
            theme = theme, dark = true,
            colors = acid,
            display = ArchivoBlackFamily, displayWeight = FontWeight.Black, displayTracking = (-0.01).em,
            body = FontFamily.SansSerif, numeric = SpaceMonoFamily,
            cardShape = RectangleShape, chipShape = CircleShape, artShape = RectangleShape,
            edge = EdgeKind.Chrome, backdrop = BackdropKind.Acid, seek = SeekKind.Stripe,
            button = ButtonKind.Burst, nav = NavKind.Pill,
            lyric = LyricStyle(centered = false, activeScale = 1.06f, inactiveAlpha = 0.32f, blurStep = 0.dp, staggerMs = 30, glow = false, serif = false, weight = FontWeight.Black),
            motion = Motion(0.72f, 700f), bouncy = Motion(0.45f, 600f), pressScale = 0.95f,
            iconStroke = 2.2f, dock = DockKind.Docked,
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
// 霓's palette, from the pixel art: the night's blue-black, the white of a headlight for text, the
// sodium lamps' amber to press, a sign's teal, brake-light red.
private val neon = LanyinColors(
    bg = Color(0xFF0A0D14), bgDeep = Color(0xFF05060A), surface = Color(0xFF10141E), surfaceAlt = Color(0xFF171D2B),
    content = Color(0xFFD8E4EE), muted = Color(0xFF9FB2C8), accent = Color(0xFFE89A3E), accentAlt = Color(0xFF6FE0C8),
    onAccent = Color(0xFF1A1210), outline = Color(0xFF2B354B), like = Color(0xFFFF6A4A), vinyl = Color(0xFF05060A),
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
// Champagne-silver aluminium, engraved near black; recessed panels a shade darker; REC red.
private val deviceLight = LanyinColors(
    bg = Color(0xFFCFC8BA), bgDeep = Color(0xFFBDB6A7), surface = Color(0xFFBFB8A9), surfaceAlt = Color(0xFFC7C0B1),
    content = Color(0xFF151413), muted = Color(0xFF4C473F), accent = Color(0xFFA3221A), accentAlt = Color(0xFF0E6B5E),
    onAccent = Color(0xFFFFFFFF), outline = Color(0x33151413), like = Color(0xFFA3221A), vinyl = Color(0xFF151413),
)
// …and the black anodised edition: warm white engraving, the screen's cyan to press.
private val deviceDark = LanyinColors(
    bg = Color(0xFF1B1C1E), bgDeep = Color(0xFF121314), surface = Color(0xFF141516), surfaceAlt = Color(0xFF222326),
    content = Color(0xFFECEAE4), muted = Color(0xFF9C9A92), accent = Color(0xFF5FEFCF), accentAlt = Color(0xFFFFB03A),
    onAccent = Color(0xFF06231D), outline = Color(0x2EECEAE4), like = Color(0xFFFF5A45), vinyl = Color(0xFF0B0B0C),
)
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
// Ink and paper: one bit. The muted grey is for small print only.
private val game = LanyinColors(
    bg = Color(0xFFE8E5D8), bgDeep = Color(0xFFE8E5D8), surface = Color(0xFFE8E5D8), surfaceAlt = Color(0xFFE8E5D8),
    content = Color(0xFF1C1B18), muted = Color(0xFF524F47), accent = Color(0xFF1C1B18), accentAlt = Color(0xFF1C1B18),
    onAccent = Color(0xFFE8E5D8), outline = Color(0xFF1C1B18), like = Color(0xFF1C1B18), vinyl = Color(0xFF1C1B18),
)
// Concrete grey, black stock, copier white and acid lime.
private val acid = LanyinColors(
    bg = Color(0xFF3A3A38), bgDeep = Color(0xFF2F2F2D), surface = Color(0xFF141413), surfaceAlt = Color(0xFF1E1E1C),
    content = Color(0xFFEFEDE6), muted = Color(0xFFB3B1A9), accent = Color(0xFFD4FF26), accentAlt = Color(0xFFEFEDE6),
    onAccent = Color(0xFF111110), outline = Color(0x33EFEDE6), like = Color(0xFFD4FF26), vinyl = Color(0xFF0B0B0A),
)

/** Fusion Pixel (SIL OFL), 游's face for everything, Chinese included. One weight, so bold stays crisp rather than smeared. */
val PixelFamily = FontFamily(
    Font(R.font.fusion_pixel, FontWeight.Normal),
    Font(R.font.fusion_pixel, FontWeight.Medium),
    Font(R.font.fusion_pixel, FontWeight.SemiBold),
    Font(R.font.fusion_pixel, FontWeight.Bold),
    Font(R.font.fusion_pixel, FontWeight.Black),
)

/** DM Serif Display (SIL OFL), 暮's hand on a postcard: dates, figures and the odd Latin word. */
val PostcardSerifFamily = FontFamily(
    Font(R.font.dm_serif_display, FontWeight.Normal),
    Font(R.font.dm_serif_display_italic, FontWeight.Normal, FontStyle.Italic),
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
val GameLcd = listOf(Color(0xFFE8E5D8), Color(0xFFE8E5D8), Color(0xFF1C1B18), Color(0xFF1C1B18))

/** 格's third primary, for stickers and highlights. */
val GridYellow = Color(0xFFFACC15)

// 暮's cutting mat, green, its grid in a pale line; the print's pink for what to press and the
// sunflower yellow beside it. Glyphs on the pink are in the violet of pink over blue.
private val duskMat = LanyinColors(
    bg = Color(0xFF2B5745), bgDeep = Color(0xFF244C3C), surface = Color(0xFF244B3B), surfaceAlt = Color(0xFF285140),
    content = Color(0xFFF6F1E6), muted = Color(0xFFBFD0C4), accent = Color(0xFFFF6EC0), accentAlt = Color(0xFFFFB511),
    onAccent = Color(0xFF241848), outline = Color(0x3DF6F1E6), like = Color(0xFFFF6EC0), vinyl = Color(0xFF1D1B22),
)
// …and by night the black mat under the lamp.
private val duskNight = LanyinColors(
    bg = Color(0xFF171C1A), bgDeep = Color(0xFF101412), surface = Color(0xFF1F2522), surfaceAlt = Color(0xFF232A26),
    content = Color(0xFFEDE6D8), muted = Color(0xFFA3AEA7), accent = Color(0xFFFF6EC0), accentAlt = Color(0xFFFFB511),
    onAccent = Color(0xFF241848), outline = Color(0x3DEDE6D8), like = Color(0xFFFF6EC0), vinyl = Color(0xFF0E100F),
)
// The paper the prints are on: printing in the near-black of the three inks over each other, a
// deeper pink to press, the blue beside it. A little dimmer under the lamp at night.
private val duskPaper = LanyinColors(
    bg = Color(0xFFF2EBDD), bgDeep = Color(0xFFE8DECB), surface = Color(0xFFF7F2E7), surfaceAlt = Color(0xFFEAE2D2),
    content = Color(0xFF2A2550), muted = Color(0xFF5A5263), accent = Color(0xFFCC2E83), accentAlt = Color(0xFF3255A4),
    onAccent = Color(0xFFFFF8F0), outline = Color(0x332A2550), like = Color(0xFFCC2E83), vinyl = Color(0xFF2A2550),
)
private val duskPaperNight = duskPaper.copy(
    bg = Color(0xFFDDD3C1), bgDeep = Color(0xFFD2C7B3), surface = Color(0xFFE3DACA), surfaceAlt = Color(0xFFD5CAB6),
)

/** 霓's radio LCD: teal segments on the dark teal glass, amber for what's pressed. */
fun LanyinStyle.radioLcdStyle(): LanyinStyle = copy(
    colors = colors.copy(bg = Color(0xFF0C2E33), surface = Color(0xFF0C2E33), surfaceAlt = Color(0xFF103A40), content = Color(0xFF6FE0C8), muted = Color(0xFF2A9A8E), outline = Color(0xFF16595A)),
)

/**
 * 暮's paper, for what sits on a print or a sheet taped to the mat: the same theme with the
 * paper's colours (ink on cream rather than cream on the mat).
 */
fun LanyinStyle.paperStyle(): LanyinStyle = copy(colors = if (dark) duskPaperNight else duskPaper, dark = false)

/** Long Cang (SIL OFL), 暮's pencil: notes, numbers and the song's name written on a print's margin. */
val LongCangFamily = FontFamily(
    Font(R.font.long_cang, FontWeight.Normal),
    Font(R.font.long_cang, FontWeight.Medium),
    Font(R.font.long_cang, FontWeight.Bold),
)
