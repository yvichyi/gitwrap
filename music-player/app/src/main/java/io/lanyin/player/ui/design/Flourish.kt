package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.player.PlayerState
import io.lanyin.player.ui.theme.ArchivoBlackFamily
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.DotoFamily
import io.lanyin.player.ui.theme.FilmStamp
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PixelFamily
import io.lanyin.player.ui.theme.SpaceMonoFamily
import java.time.LocalDateTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.delay

/*
 * The pieces that make a page its theme's beyond colour and type: a strip under the home page's
 * header, and the marks on section and page titles. 点 gets a dot-matrix clock, 机 a panel with a
 * screen and a speaker grille, 影 a strip of contact prints, 报 the day's headline and almanac,
 * 游 a dialogue box and 酸 a sticker-slapped ticker.
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

/** The theme's strip under the home page's header; nothing for the themes whose page is quiet by design. */
@Composable
fun HomeFlourish(player: PlayerState, modifier: Modifier = Modifier) {
    when (Lanyin.style.backdrop) {
        BackdropKind.Dot -> DotWidget(player, modifier)
        BackdropKind.Device -> DevicePanel(player, modifier)
        BackdropKind.Film -> ContactStrip(modifier)
        BackdropKind.Paper -> PaperBrief(player, modifier)
        BackdropKind.Game -> GameDialog(player, modifier)
        BackdropKind.Acid -> AcidTicker(player, modifier)
        else -> Unit
    }
}

/** Whether [HomeFlourish] draws anything for the current theme. */
val hasHomeFlourish: Boolean
    @Composable get() = when (Lanyin.style.backdrop) {
        BackdropKind.Dot, BackdropKind.Device, BackdropKind.Film, BackdropKind.Paper, BackdropKind.Game, BackdropKind.Acid -> true
        else -> false
    }

/** Text that is part of a drawing, held at its size whatever the system font scale. */
@Composable
private fun fixed(size: Float): androidx.compose.ui.unit.TextUnit = (size / LocalDensity.current.fontScale).sp

// 点 ------------------------------------------------------------------------------------------

/**
 * 点: a widget as the phone would put on its home screen. The time in dot-matrix figures, the
 * date beneath, the day as a grid of 24 dots filling hour by hour with the red one now, and a
 * line saying what's playing.
 */
@Composable
private fun DotWidget(player: PlayerState, modifier: Modifier) {
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

// 机 ------------------------------------------------------------------------------------------

/**
 * 机: the front panel. A little screen with the time and what the deck is doing, an LED that
 * glows while it plays, the model's name, and a speaker grille drilled into the case.
 */
@Composable
private fun DevicePanel(player: PlayerState, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val screen = style.screenStyle().colors
    val now by rememberNow()
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).themedSurface().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .drawBehind {
                    drawRoundRect(Color.White.copy(alpha = if (style.dark) 0.06f else 0.75f), Offset(0f, 1.dp.toPx()), size, CornerRadius(12.dp.toPx()))
                }
                .clip(RoundedCornerShape(12.dp))
                .background(screen.bg)
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), endY = 8.dp.toPx()))
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%02d:%02d".format(now.hour, now.minute),
                    style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(26f), lineHeight = fixed(30f)),
                    color = screen.content,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (player.isPlaying) "▶ PLAY" else "■ STOP",
                    style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(11f)),
                    color = if (player.isPlaying) c.accent else screen.muted,
                )
            }
            Text(
                if (player.isEmpty) "%02d.%02d · NO TAPE".format(now.monthValue, now.dayOfMonth) else player.title,
                style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(11f)),
                color = screen.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LY-01", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(10f), letterSpacing = 0.08.em), color = c.muted)
                Spacer(Modifier.width(6.dp))
                Canvas(Modifier.size(8.dp)) {
                    val lit = player.isPlaying
                    if (lit) drawCircle(c.accent.copy(alpha = 0.35f), size.minDimension)
                    drawCircle(if (lit) c.accent else c.content.copy(alpha = 0.2f), size.minDimension / 2f)
                }
            }
            Canvas(Modifier.size(width = 64.dp, height = 40.dp)) { drawGrille(style.dark, c.content) }
        }
    }
}

/** Holes drilled in a grid, each a dark well with a lighter lip below it. */
private fun DrawScope.drawGrille(dark: Boolean, ink: Color) {
    val cols = 8
    val rows = 5
    val pitch = minOf(size.width / cols, size.height / rows)
    val r = pitch * 0.26f
    val left = size.width - pitch * cols
    for (y in 0 until rows) {
        for (x in 0 until cols) {
            val center = Offset(left + pitch * (x + 0.5f), pitch * (y + 0.5f))
            drawCircle(Color.White.copy(alpha = if (dark) 0.06f else 0.7f), r, center + Offset(0f, density * 0.8f))
            drawCircle(ink.copy(alpha = if (dark) 0.55f else 0.7f), r, center)
        }
    }
}

// 影 ------------------------------------------------------------------------------------------

/**
 * 影: a strip cut from a contact sheet. Four frames of the theme's own photographs between rows
 * of sprocket holes, the stock's name and the frame numbers printed along the edge in orange.
 */
@Composable
private fun ContactStrip(modifier: Modifier) {
    val now by rememberNow()
    val first = now.dayOfMonth
    val edge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = fixed(8f), letterSpacing = 0.14.em)
    val base = Color(0xFF0B0A09)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = -0.8f }
            .background(base)
            .drawBehind {
                // Sprocket holes along both edges, the light of the sheet showing through.
                val hole = Size(7.dp.toPx(), 5.dp.toPx())
                val pitch = 14.dp.toPx()
                val radius = CornerRadius(1.5f.dp.toPx())
                var x = 5.dp.toPx()
                while (x < size.width - hole.width) {
                    drawRoundRect(Color(0xFFE9E1D3), Offset(x, 4.dp.toPx()), hole, radius)
                    drawRoundRect(Color(0xFFE9E1D3), Offset(x, size.height - 4.dp.toPx() - hole.height), hole, radius)
                    x += pitch
                }
            }
            .padding(top = 11.dp, bottom = 11.dp),
    ) {
        Text(
            "LANYIN 400  ·  SAFETY FILM  ·  %02d%02d".format(now.monthValue, now.dayOfMonth),
            style = edge, color = FilmStamp, maxLines = 1,
            modifier = Modifier.padding(start = 10.dp, bottom = 3.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (k in 0 until 4) {
                CoverArt(40 + first * 4 + k, Modifier.weight(1f).height(62.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp)) {
            for (k in 0 until 4) {
                Text(
                    "▸ ${first + k / 2}${if (k % 2 == 1) "A" else ""}",
                    style = edge, color = FilmStamp, maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
            }
        }
    }
}

// 报 ------------------------------------------------------------------------------------------

private val Fitting = listOf("单曲循环", "听一张新专辑", "戴好耳机", "跟着唱", "慢慢听完一首", "收藏一个歌单", "翻翻老歌", "听现场版")
private val Avoid = listOf("切歌太快", "只听前奏", "外放", "开得太响", "错过副歌", "熬夜听歌", "跳过尾奏")

/**
 * 报: the day's lead story, which is whatever is playing, and beside it, in a box as an almanac
 * would print it, what the day is good for and what to avoid.
 */
@Composable
private fun PaperBrief(player: PlayerState, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val now by rememberNow()
    val day = now.dayOfYear + now.year
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.weight(1f)) {
            Text("头条", style = style.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.2.em), color = c.accent)
            Text(
                if (player.isEmpty) "今夜无大事，宜听歌" else "《${player.title}》",
                style = style.type.title.copy(fontFamily = style.display, fontWeight = FontWeight.Black, fontSize = 21.sp, lineHeight = 27.sp),
                color = c.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                if (player.isEmpty) "本报讯　从一首歌开始今天。" else "本报讯　${player.artist}${if (player.isPlaying) "，正在播放。" else "，暂停待续。"}",
                style = style.type.caption,
                color = c.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(Modifier.padding(horizontal = 14.dp).width(1.dp).fillMaxHeight().background(c.outline))
        Column(Modifier.width(104.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("今日宜忌", style = style.type.caption.copy(fontWeight = FontWeight.Bold), color = c.content)
            AlmanacLine("宜", Fitting[day % Fitting.size], c.accent)
            AlmanacLine("忌", Avoid[(day * 3) % Avoid.size], c.content)
        }
    }
}

@Composable
private fun AlmanacLine(mark: String, text: String, ink: Color) {
    val style = Lanyin.style
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            mark,
            style = style.type.caption.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            color = style.colors.bg,
            modifier = Modifier.background(ink).padding(horizontal = 3.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = style.type.caption, color = style.colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A newspaper's rule: a heavy line over a fine one. */
@Composable
fun DoubleRule(modifier: Modifier = Modifier, color: Color = Lanyin.colors.content) {
    Canvas(modifier.fillMaxWidth().height(5.dp)) {
        drawRect(color, size = Size(size.width, 2.dp.toPx()))
        drawRect(color, Offset(0f, size.height - 0.75f.dp.toPx()), Size(size.width, 0.75f.dp.toPx()))
    }
}

// 游 ------------------------------------------------------------------------------------------

/**
 * 游: a role-playing game's dialogue box, navy with a white double frame. The player's hearts
 * and level across its top, then the line, with the ▼ that blinks for the next page.
 */
@Composable
private fun GameDialog(player: PlayerState, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val now by rememberNow()
    val blink by produceState(true) {
        while (true) {
            delay(560)
            value = !value
        }
    }
    val box = c.accentAlt
    val frame = c.onAccent
    val pixel = TextStyle(fontFamily = PixelFamily, fontSize = fixed(13f), lineHeight = fixed(18f))
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val corner = CornerRadius(6.dp.toPx())
                drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(3.dp.toPx(), 3.dp.toPx()), size, corner)
                drawRoundRect(box, size = size, cornerRadius = corner)
                val inset = 4.dp.toPx()
                drawRoundRect(frame, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(3.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("1P", style = pixel, color = frame)
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.size(width = 42.dp, height = 12.dp)) {
                val w = size.width / 3f
                for (k in 0 until 3) {
                    translateHeart(Offset(k * w, 0f), Size(w, size.height), Color(0xFFFF6B9A))
                }
            }
            Spacer(Modifier.weight(1f))
            Text("LV.%02d".format(now.dayOfMonth), style = pixel, color = frame)
        }
        Text(
            if (player.isEmpty) "勇者，今晚想听点什么？" else if (player.isPlaying) "正在播放「${player.title}」。" else "「${player.title}」暂停中，要继续吗？",
            style = pixel.copy(fontSize = fixed(15f), lineHeight = fixed(22f)),
            color = frame,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (player.isEmpty) "▶ 去发现　　  设置" else "▶ 继续冒险　  换一首", style = pixel, color = frame.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            Text("▼", style = pixel, color = frame.copy(alpha = if (blink) 1f else 0f))
        }
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

// 酸 ------------------------------------------------------------------------------------------

/**
 * 酸: a lime ribbon of news running along under the header, tipped up at one end, with a hot
 * pink starburst sticker slapped over its corner.
 */
@Composable
private fun AcidTicker(player: PlayerState, modifier: Modifier) {
    val c = Lanyin.colors
    Box(modifier.fillMaxWidth().height(58.dp)) {
        Marquee(
            listOfNotNull(
                "LANYIN 澜音",
                "正在播放 ${player.title} — ${player.artist}".takeIf { !player.isEmpty },
                "Y2K FOREVER",
                "今晚也要大声听歌",
            ),
            Modifier
                .align(Alignment.CenterStart)
                .padding(end = 26.dp)
                .graphicsLayer { rotationZ = 1.4f }
                .clip(RoundedCornerShape(50))
                .border(1.5.dp, c.content.copy(alpha = 0.8f), RoundedCornerShape(50)),
            height = 30.dp,
            fill = c.accent,
            ink = c.onAccent,
            marks = c.accentAlt to c.onAccent,
            font = SpaceMonoFamily,
        )
        Box(Modifier.align(Alignment.CenterEnd).size(58.dp).graphicsLayer { rotationZ = 14f }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(58.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawPath(starPath(center + Offset(2.dp.toPx(), 2.dp.toPx()), size.minDimension / 2f, size.minDimension * 0.36f, 14), Color.Black.copy(alpha = 0.5f))
                drawPath(starPath(center, size.minDimension / 2f, size.minDimension * 0.36f, 14), c.accentAlt)
            }
            Text("NEW!", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixed(12f)), color = c.content)
        }
    }
}

// Titles -------------------------------------------------------------------------------------

/**
 * The mark before a section's title, in the theme's hand: dot-matrix figures for 点, a printed
 * label for 机, a frame number for 影, a menu cursor for 游 and a lime spark for 酸. Null for
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
        BackdropKind.Acid -> Canvas(Modifier.padding(end = 8.dp).size(16.dp)) {
            drawPath(starPath(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, size.minDimension * 0.14f, 4), c.accent)
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

