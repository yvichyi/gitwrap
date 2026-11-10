package io.lanyin.player.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import io.lanyin.player.ui.theme.GameLcd
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import io.lanyin.player.ui.design.drawPaper
import io.lanyin.player.ui.design.drawRegMark
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.design.ChromeBrush
import io.lanyin.player.ui.design.DoubleRule
import io.lanyin.player.ui.design.Flavor
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.brushStrokePath
import io.lanyin.player.ui.design.candyPlastic
import io.lanyin.player.ui.design.gameWindow
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.screenStyle
import io.lanyin.player.ui.design.starPath
import io.lanyin.player.ui.player.SungText
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.SpaceMonoFamily
import kotlin.math.ceil

/**
 * What 桌面歌词 shows: the line being sung and, under it, its translation or the line after.
 * With no lyrics to follow ([timed] false) it is the song's title and artist, shown whole.
 */
@Immutable
data class DesktopLine(val text: String, val second: String?, val timed: Boolean)

/** The type sizes, smallest to largest; held whatever the system's font scale, as the strip is set by its own size control. */
internal val DesktopTypeSizes = floatArrayOf(15f, 17f, 19f, 22f, 25f)

@Composable
private fun fixed(size: Float): TextUnit = with(LocalDensity.current) { size.dp.toSp() }

/**
 * The line floating over other apps. By default (轻) only the words, see [LightLyric]; on a
 * [card], in the theme's medium: a strip of rice paper with its seal
 * (墨), a neon sign on a dark wall (霓), a postcard's back (暮), a boxed label with the highlighter
 * (格), the LED pill (点), the device's screen (机), a strip of
 * newsprint underlined in red (报), a dialogue window typing it out (游), a candy-plastic sticker
 * in chrome (酸). [progress] (0..1 through the line) is read at draw time.
 */
@Composable
fun DesktopLyric(line: DesktopLine, progress: () -> Float, size: Int, modifier: Modifier = Modifier, card: Boolean = false) {
    val type = fixed(DesktopTypeSizes[size.coerceIn(0, DesktopTypeSizes.lastIndex)])
    val sung: () -> Float = if (line.timed) progress else ({ 1f })
    if (!card) {
        LightLyric(line, sung, type, modifier)
        return
    }
    when (Lanyin.style.backdrop) {
        BackdropKind.Ink -> InkStrip(line, sung, type, modifier)
        BackdropKind.Neon -> NeonStrip(line, sung, type, modifier)
        BackdropKind.Dusk -> DuskStrip(line, sung, type, modifier)
        BackdropKind.Grid -> GridStrip(line, sung, type, modifier)
        BackdropKind.Dot -> DotStrip(line, sung, type, modifier)
        BackdropKind.Device -> DeviceStrip(line, sung, type, modifier)
        BackdropKind.Paper -> PaperStrip(line, sung, type, modifier)
        BackdropKind.Game -> GameStrip(line, sung, type, modifier)
        BackdropKind.Acid -> AcidStrip(line, sung, type, modifier)
    }
}

/** How 轻 sets a theme's words: its face, how strong the unsung words are, and what the singing fills them with. */
private class LightLook(val font: FontFamily?, val weight: FontWeight, val italic: Boolean, val unsung: Float, val wholeChars: Boolean, val paint: DrawScope.(TextLayoutResult) -> Unit)

@Composable
private fun lightLook(): LightLook {
    val style = Lanyin.style
    val c = style.colors
    return when (style.backdrop) {
        // 墨: a brush face, the sung words in the seal's vermilion.
        BackdropKind.Ink -> LightLook(FontFamily.Serif, FontWeight.Normal, false, 0.85f, false) { drawText(it, color = Color(0xFFE5533D)) }
        // 霓: the tube lights as it's sung.
        BackdropKind.Neon -> LightLook(io.lanyin.player.ui.theme.PixelFamily, FontWeight.Normal, false, 0.8f, false) {
            drawText(it, color = c.accent, shadow = Shadow(c.accent, Offset.Zero, 16f))
            drawText(it, color = lerp(c.accent, Color.White, 0.4f))
        }
        // 暮: the postcard hand, inked in the sunset.
        BackdropKind.Dusk -> LightLook(style.display, FontWeight.Normal, true, 0.85f, false) {
            drawText(it, brush = Brush.horizontalGradient(listOf(Color(0xFFFFB27A), Color(0xFFFF7FA0))), alpha = 1f)
        }
        // 格: heavy, the highlighter yellow a character at a time.
        BackdropKind.Grid -> LightLook(null, FontWeight.Black, false, 0.85f, true) { drawText(it, color = GridYellow) }
        // 点: white LEDs lighting.
        BackdropKind.Dot -> LightLook(null, FontWeight.Bold, false, 0.45f, false) { drawText(it, color = Color.White) }
        // 机: the display's orange.
        BackdropKind.Device -> LightLook(null, FontWeight.Bold, false, 0.85f, false) { drawText(it, color = c.accent) }
        // 报: the masthead's face, the red pen.
        BackdropKind.Paper -> LightLook(style.display, FontWeight.Black, false, 0.85f, false) { drawText(it, color = Color(0xFFE8443A)) }
        // 游: pixels, typed out in the screen's brightest green.
        BackdropKind.Game -> LightLook(style.display, FontWeight.Normal, false, 0.5f, true) { drawText(it, color = GameLcd[0]) }
        // 酸: the lime it keeps for what's playing.
        BackdropKind.Acid -> LightLook(null, FontWeight.Black, false, 0.85f, false) { drawText(it, color = c.accent) }
    }
}

/**
 * 轻: only the words, centred, with nothing behind them but a faint dark outline that keeps them
 * readable over a white page or a dark photo alike. The theme shows in the face and in the colour
 * the singing fills them with.
 */
@Composable
private fun LightLyric(line: DesktopLine, sung: () -> Float, type: TextUnit, modifier: Modifier) {
    val look = lightLook()
    val base = TextStyle(
        fontFamily = look.font, fontWeight = look.weight, fontStyle = if (look.italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = type, textAlign = TextAlign.Center,
    )
    val outline = Color.Black.copy(alpha = 0.42f)
    // Opaque greys rather than see-through white, so the outline never shows through the letters.
    val unsung = lerp(Color(0xFF7A7A7A), Color.White, look.unsung)
    val progress = if (look.wholeChars) wholeChars(line.text, sung) else sung
    Column(modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The outline, under the same layout as the words.
            Text(
                line.text, style = base.copy(color = outline, drawStyle = Stroke(width = with(LocalDensity.current) { 2.dp.toPx() }, join = androidx.compose.ui.graphics.StrokeJoin.Round)),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
            )
            SungText(
                line.text, base.copy(color = unsung), progress,
                paintSung = look.paint, maxLines = 1, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!line.second.isNullOrBlank()) {
            val small = base.copy(fontSize = type * 0.62f, fontWeight = FontWeight.Normal)
            Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) {
                Text(line.second, style = small.copy(color = outline, drawStyle = Stroke(width = with(LocalDensity.current) { 1.4.dp.toPx() }, join = androidx.compose.ui.graphics.StrokeJoin.Round)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                Text(line.second, style = small.copy(color = Color(0xFFEDEDED)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** The second line: smaller, quieter, one line. */
@Composable
private fun ColumnScope.Second(text: String?, style: TextStyle, color: Color, align: TextAlign = TextAlign.Start) {
    if (text.isNullOrBlank()) return
    Text(
        text, style = style.copy(textAlign = align), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 3.dp).fillMaxWidth(),
    )
}

/** 墨: rice paper, the seal at its head, ink filling the grey characters and a dry stroke of the brush following under them. */
@Composable
private fun InkStrip(line: DesktopLine, sung: () -> Float, type: TextUnit, modifier: Modifier) {
    val c = Lanyin.colors
    Row(
        modifier
            .drawBehind {
                drawRoundRect(Color.Black.copy(alpha = 0.14f), Offset(0f, 2.dp.toPx()), size, CornerRadius(3.dp.toPx()))
                drawRoundRect(c.bg.copy(alpha = 0.95f), cornerRadius = CornerRadius(3.dp.toPx()))
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).background(c.accentAlt, RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
            Text("澜", style = TextStyle(fontFamily = FontFamily.Serif, fontSize = fixed(12f)), color = c.bg)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SungText(
                line.text, TextStyle(fontFamily = FontFamily.Serif, fontSize = type, letterSpacing = 0.06.em, color = c.muted.copy(alpha = 0.6f)), sung,
                paintSung = { drawText(it, color = c.content) }, maxLines = 1,
            )
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val length = size.width * sung().coerceIn(0f, 1f)
                if (length > 4.dp.toPx()) drawPath(brushStrokePath(Offset(0f, size.height / 2f), length, size.height * 0.75f), c.content.copy(alpha = 0.55f))
            }
            Second(line.second, TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = fixed(11f)), c.muted)
        }
    }
}

/** 霓: the car radio's LCD, teal on dark teal glass in pixels, the line lit up brighter as it's sung. */
@Composable
private fun NeonStrip(line: DesktopLine, sung: () -> Float, type: TextUnit, modifier: Modifier) {
    val lcd = Color(0xFF6FE0C8)
    Row(
        modifier
            .background(Color(0xFF05060A))
            .padding(3.dp)
            .background(Color(0xFF0C2E33))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("♪", style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(14f)), color = lcd)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            SungText(
                line.text, TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = type, color = Color(0xFF2A9A8E)), sung,
                paintSung = { drawText(it, color = Color(0xFFB8FFEC), shadow = Shadow(lcd, Offset.Zero, 10f)) },
                maxLines = 1,
            )
            Second(line.second, TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(11f)), Color(0xFF2A9A8E))
        }
    }
}

/** 暮: a strip of paper torn from a sheet, the line inked in pink as it's sung over the pencil, a registration mark at its end. */
@Composable
private fun DuskStrip(line: DesktopLine, sung: () -> Float, type: TextUnit, modifier: Modifier) {
    val paper = io.lanyin.player.ui.design.Riso.Paper
    val pen = io.lanyin.player.ui.design.Riso.Graphite
    val ink = Brush.horizontalGradient(listOf(io.lanyin.player.ui.design.Riso.Pink, Color(0xFFE0379B)))
    Row(
        modifier
            .drawBehind { drawPaper(paper, false) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SungText(
                line.text, TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = type, color = pen.copy(alpha = 0.4f)), sung,
                paintSung = { drawText(it, brush = ink, alpha = 1f) }, maxLines = 1,
            )
            Second(line.second, TextStyle(fontFamily = io.lanyin.player.ui.theme.LongCangFamily, fontSize = fixed(13f)), pen.copy(alpha = 0.75f))
        }
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.size(18.dp)) { drawRegMark(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, pen) }
    }
}

/** 格: a boxed label with a hard shadow; the highlighter runs across the words as they're sung. */
@Composable
private fun GridStrip(line: DesktopLine, sung: () -> Float, type: TextUnit, modifier: Modifier) {
    val c = Lanyin.colors
    Row(
        modifier
            .drawBehind {
                val shadow = 4.dp.toPx()
                drawRect(c.outline, Offset(shadow, shadow), size)
                drawRect(c.surface)
                drawRect(c.outline, style = Stroke(2.5.dp.toPx()))
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "♪", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(12f)), color = c.content,
            modifier = Modifier.background(GridYellow).border(2.dp, c.outline).padding(horizontal = 6.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            SungText(
                line.text, TextStyle(fontWeight = FontWeight.Black, fontSize = type, color = c.content), wholeChars(line.text, sung),
                paintSung = { l ->
                    drawRect(GridYellow)
                    drawText(l, color = c.content)
                },
                maxLines = 1,
            )
            Second(line.second, TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(10f)), c.muted)
        }
    }
}

/** 点: a black pill of LEDs, the red dot at its head; the words light white as they're sung. */
@Composable
private fun DotStrip(line: DesktopLine, sung: () -> Float, type: TextUnit, modifier: Modifier) {
    val c = Lanyin.colors
    Row(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.86f))
                val pitch = 6.dp.toPx()
                var y = pitch / 2f
                while (y < size.height) {
                    var x = pitch / 2f
                    while (x < size.width) {
                        drawCircle(Color.White.copy(alpha = 0.06f), 0.9.dp.toPx(), Offset(x, y))
                        x += pitch
                    }
                    y += pitch
                }
            }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(c.accentAlt, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SungText(
                line.text, TextStyle(fontWeight = FontWeight.Bold, fontSize = type, color = Color.White.copy(alpha = 0.3f)), sung,
                paintSung = { drawText(it, color = Color.White) }, maxLines = 1,
            )
            Second(line.second, TextStyle(fontSize = fixed(11f)), Color.White.copy(alpha = 0.55f))
        }
    }
}

/** 机: the device's screen in its bezel, the line as a readout. */
@Composable
private fun DeviceStrip(line: DesktopLine, sung: () -> Float, type: TextUnit, modifier: Modifier) {
    val style = Lanyin.style
    val screen = style.screenStyle().colors
    Row(
        modifier
            .background(style.colors.bgDeep, RoundedCornerShape(14.dp))
            .padding(6.dp)
            .background(screen.bg, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▶", style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(10f)), color = screen.accent)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            SungText(
                line.text, TextStyle(fontWeight = FontWeight.Bold, fontSize = type, color = screen.muted.copy(alpha = 0.55f)), sung,
                paintSung = { drawText(it, color = screen.content) }, maxLines = 1,
            )
            Second(line.second, TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(10f)), screen.muted)
        }
    }
}

/** 报: a strip of newsprint under a double rule, 「歌词」 in red at its head, the red pen underlining what's been sung. */
@Composable
private fun PaperStrip(line: DesktopLine, sung: () -> Float, type: TextUnit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(
        modifier
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, 2.dp.toPx()), size)
                drawRect(c.bg)
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        DoubleRule()
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("歌词", style = TextStyle(fontFamily = style.display, fontWeight = FontWeight.Black, fontSize = fixed(11f)), color = c.accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                SungText(
                    line.text, TextStyle(fontFamily = style.display, fontWeight = FontWeight.Black, fontSize = type, color = c.content), sung,
                    paintSung = { l ->
                        // The pen's line under the words, a little below the baseline.
                        val y = l.getLineBaseline(0) + 2.dp.toPx()
                        drawRect(c.accent, Offset(0f, y), Size(size.width, 2.dp.toPx()))
                    },
                    maxLines = 1,
                )
                Second(line.second, TextStyle(fontFamily = style.display, fontStyle = FontStyle.Italic, fontSize = fixed(11f)), c.muted)
            }
        }
    }
}

/** 游: a dialogue window; the speaker's name, then the line typed out a character at a time as it's sung, and ▼ once it's done. */
@Composable
private fun GameStrip(line: DesktopLine, sung: () -> Float, type: TextUnit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.gameWindow(c.surface, c.content).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            SungText(
                line.text, TextStyle(fontFamily = style.display, fontSize = type, color = Color.Transparent), wholeChars(line.text, sung),
                paintSung = { drawText(it, color = c.content) }, maxLines = 1, modifier = Modifier.weight(1f),
            )
            Canvas(Modifier.padding(start = 8.dp, bottom = 4.dp).size(9.dp, 7.dp)) {
                if (sung() >= 1f) {
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close() }, c.content)
                }
            }
        }
        Second(line.second, TextStyle(fontFamily = style.display, fontSize = fixed(11f)), c.muted)
    }
}

/**
 * 酸: a sticker of grape plastic (over a dark backing, so it reads on a bright wallpaper too), a
 * chrome sparkle at its head; what's sung turns the lime 酸 keeps for what's playing.
 */
@Composable
private fun AcidStrip(line: DesktopLine, sung: () -> Float, type: TextUnit, modifier: Modifier) {
    val c = Lanyin.colors
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .background(Color(0xFF1C0E3A).copy(alpha = 0.78f), shape)
            .candyPlastic(Flavor.Grape, shape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp)) {
            drawPath(starPath(center, size.minDimension / 2f, size.minDimension / 9f, 4), ChromeBrush)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            SungText(
                line.text, TextStyle(fontWeight = FontWeight.Black, fontSize = type, color = Color.White.copy(alpha = 0.5f)), sung,
                paintSung = { drawText(it, color = c.accent, shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 1.5f), 3f)) }, maxLines = 1,
            )
            Second(line.second, TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(10f)), Color.White.copy(alpha = 0.85f))
        }
    }
}

/** [progress] moved on to the end of the character being sung, for marks that take whole characters (the highlighter, a typed line). */
private fun wholeChars(text: String, progress: () -> Float): () -> Float = {
    val n = text.length.coerceAtLeast(1)
    (ceil(progress().coerceIn(0f, 1f) * n) / n)
}

/**
 * What shows under the strip after a tap, while it isn't locked: back, play or pause, on; type
 * smaller and larger; lock; close. Set on a plain dark pill so it reads over anything.
 */
@Composable
fun DesktopControls(
    playing: Boolean,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
    onLock: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = Color.White
    Row(
        modifier
            .background(Color(0xFF15141A).copy(alpha = 0.82f), RoundedCornerShape(22.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlIcon(LineIcon.Previous, "上一首", onPrevious)
        Box(Modifier.size(40.dp).pressable(onClick = onToggle), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(14.dp)) {
                if (playing) {
                    val w = size.width * 0.3f
                    drawRect(tint, Offset(size.width * 0.12f, 0f), Size(w, size.height))
                    drawRect(tint, Offset(size.width * 0.88f - w, 0f), Size(w, size.height))
                } else {
                    drawPath(Path().apply { moveTo(size.width * 0.15f, 0f); lineTo(size.width, size.height / 2f); lineTo(size.width * 0.15f, size.height); close() }, tint)
                }
            }
        }
        ControlIcon(LineIcon.Next, "下一首", onNext)
        Box(Modifier.padding(horizontal = 4.dp).size(1.dp, 18.dp).background(tint.copy(alpha = 0.25f)))
        ControlText("A−", "字小一点", onSmaller)
        ControlText("A+", "字大一点", onLarger)
        ControlIcon(LineIcon.Lock, "锁定", onLock)
        ControlIcon(LineIcon.Close, "关闭桌面歌词", onClose)
    }
}

@Composable
private fun ControlIcon(icon: LineIcon, label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        ThemedIcon(icon, label, tint = Color.White, size = 18.dp)
    }
}

@Composable
private fun ControlText(text: String, label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = fixed(13f)), color = Color.White, modifier = Modifier.semantics { contentDescription = label })
    }
}
