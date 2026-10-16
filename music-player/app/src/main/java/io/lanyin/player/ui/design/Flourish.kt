package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.player.PlayerState
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.DotoFamily
import io.lanyin.player.ui.theme.FilmStamp
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.SpaceMonoFamily
import java.time.LocalDateTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.delay

/*
 * The pieces that make a page its theme's beyond colour and type: 点's dot-matrix clock widget, 酸's
 * sticker-slapped ticker, the rules and marks on section and page titles.
 */

/** The time, moved on at each minute. */
@Composable
internal fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        val now = LocalDateTime.now()
        value = now
        delay(((60 - now.second) * 1000L - now.nano / 1_000_000).coerceAtLeast(200L))
    }
}

/** Text that is part of a drawing, held at its size whatever the system font scale. */
@Composable
private fun fixed(size: Float): androidx.compose.ui.unit.TextUnit = with(LocalDensity.current) { size.dp.toSp() }

// 点 ------------------------------------------------------------------------------------------

/**
 * 点: a widget as the phone would put on its home screen. The time in dot-matrix figures, the
 * date beneath, the day as a grid of 24 dots filling hour by hour with the red one now, and a
 * line saying what's playing.
 */
@Composable
internal fun DotWidget(player: PlayerState, modifier: Modifier) {
    val c = Lanyin.colors
    val now by rememberNow()
    Column(modifier.fillMaxWidth().themedSurface(tone = SurfaceTone.Flat).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Doto's own colon is a column of dots; the clock wants two, as a dot-matrix display shows it.
                val figures = TextStyle(fontFamily = DotoFamily, fontWeight = FontWeight.Black, fontSize = fixed(46f), lineHeight = fixed(48f), letterSpacing = 0.02.em)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d".format(now.hour), style = figures, color = c.content)
                    Canvas(Modifier.padding(horizontal = 5.dp).size(width = 6.dp, height = 30.dp)) {
                        val r = 2.6.dp.toPx()
                        drawCircle(c.accentAlt, r, Offset(size.width / 2f, size.height * 0.3f))
                        drawCircle(c.accentAlt, r, Offset(size.width / 2f, size.height * 0.72f))
                    }
                    Text("%02d".format(now.minute), style = figures, color = c.content)
                }
                Text(
                    "%s %02d %s".format(
                        now.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase(),
                        now.dayOfMonth,
                        now.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase(),
                    ),
                    style = TextStyle(fontFamily = DotoFamily, fontWeight = FontWeight.Black, fontSize = fixed(14f), letterSpacing = 0.12.em),
                    color = c.muted,
                )
            }
            val hour = now.hour
            Canvas(Modifier.size(width = 112.dp, height = 40.dp)) {
                // Two rows of twelve: the hours gone lit, this one red, the rest dim.
                val pitch = size.width / 12f
                val r = pitch * 0.26f
                for (h in 0 until 24) {
                    val x = pitch * (h % 12 + 0.5f)
                    val y = size.height * (if (h < 12) 0.28f else 0.72f)
                    when {
                        h == hour -> drawCircle(c.accentAlt, r * 1.25f, Offset(x, y))
                        h < hour -> drawCircle(c.content, r, Offset(x, y))
                        else -> drawCircle(c.content.copy(alpha = 0.18f), r, Offset(x, y))
                    }
                }
            }
        }
        DotRule(Modifier.padding(top = 12.dp, bottom = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Nothing's red recording dot, lit while music plays.
            Box(Modifier.size(7.dp).background(if (player.isPlaying) c.accentAlt else c.muted.copy(alpha = 0.5f), CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                if (player.isEmpty) "还没有在播放的歌" else "${if (player.isPlaying) "正在播放" else "已暂停"} · ${player.title} — ${player.artist}",
                style = Lanyin.style.type.caption,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A rule of small dots across the width. */
@Composable
fun DotRule(modifier: Modifier = Modifier, color: Color = Lanyin.colors.content.copy(alpha = 0.32f)) {
    Canvas(modifier.fillMaxWidth().height(4.dp)) { drawDotLeader(color, square = false) }
}

/** Dots (or 游's square pixels) along the middle of the draw area, every 6dp. */
private fun DrawScope.drawDotLeader(color: Color, square: Boolean) {
    val pitch = 6.dp.toPx()
    val r = 1.dp.toPx()
    var x = r
    val y = size.height / 2f
    while (x < size.width - r) {
        if (square) drawRect(color, Offset(x - r, y - r), Size(r * 2, r * 2)) else drawCircle(color, r, Offset(x, y))
        x += pitch
    }
}

// 报 ------------------------------------------------------------------------------------------

/** A newspaper's rule: a heavy line over a fine one. */
@Composable
fun DoubleRule(modifier: Modifier = Modifier, color: Color = Lanyin.colors.content) {
    Canvas(modifier.fillMaxWidth().height(5.dp)) {
        drawRect(color, size = Size(size.width, 2.dp.toPx()))
        drawRect(color, Offset(0f, size.height - 0.75f.dp.toPx()), Size(size.width, 0.75f.dp.toPx()))
    }
}

// 游 ------------------------------------------------------------------------------------------

/** A role-playing game's window: [fill], a [frame] inset from its edge, a shadow under it. */
fun Modifier.gameWindow(fill: Color, frame: Color): Modifier = drawBehind {
    val corner = CornerRadius(6.dp.toPx())
    drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(3.dp.toPx(), 3.dp.toPx()), size, corner)
    drawRoundRect(fill, size = size, cornerRadius = corner)
    val inset = 4.dp.toPx()
    drawRoundRect(frame, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(3.dp.toPx()), style = Stroke(2.dp.toPx()))
}

/** [count] pixel hearts in a row, as a game shows lives. */
@Composable
fun PixelHearts(count: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width / count
        for (k in 0 until count) translateHeart(Offset(k * w, 0f), Size(w, size.height), color)
    }
}

/** A pixel heart filling [area] at [topLeft]. */
private fun DrawScope.translateHeart(topLeft: Offset, area: Size, color: Color) {
    val rows = listOf("0110110", "1111111", "1111111", "0111110", "0011100", "0001000")
    val px = minOf(area.width / 8f, area.height / rows.size)
    val left = topLeft.x + (area.width - px * 7) / 2f
    val top = topLeft.y + (area.height - px * rows.size) / 2f
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch -> if (ch == '1') drawRect(color, Offset(left + x * px, top + y * px), Size(px + 0.5f, px + 0.5f)) }
    }
}

// Titles -------------------------------------------------------------------------------------

/**
 * The mark before a section's title, in the theme's hand: dot-matrix figures for 点, a printed
 * label for 机, a frame number for 影, a menu cursor for 游 and a gel bead for 酸. Null for
 * themes without one.
 */
@Composable
internal fun SectionMark(index: Int?) {
    val style = Lanyin.style
    val c = style.colors
    when (style.backdrop) {
        BackdropKind.Dot -> if (index != null) {
            Text("%02d".format(index), style = TextStyle(fontFamily = DotoFamily, fontWeight = FontWeight.Black, fontSize = fixed(17f)), color = c.accentAlt, modifier = Modifier.padding(end = 10.dp))
        }
        BackdropKind.Device -> if (index != null) {
            Text(
                "A%d".format(index),
                style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(10f)),
                color = c.bg,
                modifier = Modifier.padding(end = 10.dp).background(c.content, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        BackdropKind.Film -> if (index != null) {
            Text("▸ %02dA".format(index), style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = fixed(11f), letterSpacing = 0.1.em), color = FilmStamp, modifier = Modifier.padding(end = 10.dp))
        }
        BackdropKind.Game -> Canvas(Modifier.padding(end = 8.dp).size(width = 9.dp, height = 12.dp)) { drawMenuCursor(c.accent) }
        // 暮: a stamp, each section in the next of the set.
        BackdropKind.Dusk -> Box(Modifier.padding(end = 10.dp)) { StampMark(index) }
        // 酸: a gel bead, each section in the next flavour.
        BackdropKind.Acid -> Canvas(Modifier.padding(end = 8.dp).size(14.dp)) {
            drawGelBead(Flavor.all[((index ?: 1) - 1).mod(Flavor.all.size)])
        }
        else -> Unit
    }
}

/** What runs between a section's title and its note: dots in 点, pixels in 游. */
@Composable
internal fun SectionLeader(modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    when (style.backdrop) {
        BackdropKind.Dot -> Canvas(modifier.height(4.dp)) { drawDotLeader(c.content.copy(alpha = 0.3f), square = false) }
        BackdropKind.Game -> Canvas(modifier.height(4.dp)) { drawDotLeader(c.content.copy(alpha = 0.4f), square = true) }
        else -> Spacer(modifier)
    }
}

/** Whether section titles here run a leader to their note. */
internal val hasSectionLeader: Boolean
    @Composable get() = Lanyin.style.backdrop == BackdropKind.Dot || Lanyin.style.backdrop == BackdropKind.Game

/** 报's kicker over a section: 「第一版」 in red. */
@Composable
internal fun PaperKicker(index: Int) {
    val style = Lanyin.style
    Text(
        "第${io.lanyin.player.ui.home.chineseNumber(index)}版",
        style = style.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.12.em),
        color = style.colors.accent,
    )
}

/** What goes under a page's title: a dot rule in 点, sprocket holes in 影, a double rule in 报. */
@Composable
internal fun PageTitleRule(modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    when (style.backdrop) {
        BackdropKind.Dot -> DotRule(modifier.padding(top = 10.dp))
        BackdropKind.Film -> Canvas(modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp)) {
            val hole = Size(7.dp.toPx(), 5.dp.toPx())
            var x = 0f
            while (x < size.width - hole.width) {
                drawRoundRect(c.content.copy(alpha = 0.22f), Offset(x, 0f), hole, CornerRadius(1.5f.dp.toPx()))
                x += 14.dp.toPx()
            }
        }
        BackdropKind.Paper -> DoubleRule(modifier.padding(top = 8.dp))
        // 暮: a short run of airmail stripes.
        BackdropKind.Dusk -> Canvas(modifier.padding(top = 8.dp).size(54.dp, 5.dp)) { drawAirmailStripes(7.dp.toPx()) }
        else -> Unit
    }
}

/** 机's model plate, beside a page's title. */
@Composable
internal fun ModelPlate(modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    Text(
        "LANYIN LY-01",
        style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(9f), letterSpacing = 0.1.em),
        color = c.muted,
        modifier = modifier.border(1.dp, c.muted.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 游's titles cast a hard magenta shadow, as a game's logo does. */
internal fun pixelShadow(color: Color, offset: Dp, density: Float) = Shadow(color, Offset(offset.value * density, offset.value * density), 0f)

