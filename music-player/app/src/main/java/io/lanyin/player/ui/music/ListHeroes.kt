package io.lanyin.player.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.design.DeviceScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.design.ChromeBrush
import io.lanyin.player.ui.design.CoverImage
import io.lanyin.player.ui.design.DitheredCover
import io.lanyin.player.ui.design.DotRule
import io.lanyin.player.ui.design.DoubleRule
import io.lanyin.player.ui.design.HalftoneCover
import io.lanyin.player.ui.design.NeonText
import io.lanyin.player.ui.design.drawGloss
import io.lanyin.player.ui.design.drawTubeFrame
import io.lanyin.player.ui.design.drawTubeLine
import io.lanyin.player.ui.design.gameWindow
import io.lanyin.player.ui.design.ledScreen
import io.lanyin.player.ui.design.lightSpill
import io.lanyin.player.ui.design.retroShadow
import io.lanyin.player.ui.theme.ArchivoBlackFamily
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.DotoFamily
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.SpaceMonoFamily
import kotlin.math.sin

/*
 * The head of a song list in each theme's own medium, so a playlist, an album, an artist or
 * today's picks open the way the theme's other pages do: a hanging scroll for 墨, a sign in a
 * tube frame for 霓, a print with its postmark for 暮, a poster block for 格,
 * an LED panel for 点, a screen in its case for 机, the paper's lead story for 报, a game's
 * window for 游, a CD in its jewel case for 酸.
 */

/** Type held at its size whatever the font scale, for lettering that is part of a drawing. */
@Composable
private fun fixed(size: Float): TextUnit = with(LocalDensity.current) { size.dp.toSp() }

/** The list's head: [title] and [subtitle] beside, or under, its [header] art, in the theme's medium. */
@Composable
internal fun ListHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier = Modifier) {
    when (Lanyin.style.backdrop) {
        BackdropKind.Ink -> InkHero(title, subtitle, header, modifier)
        BackdropKind.Neon -> NeonHero(title, subtitle, header, modifier)
        BackdropKind.Dusk -> DuskHero(title, subtitle, header, modifier)
        BackdropKind.Grid -> GridHero(title, subtitle, header, modifier)
        BackdropKind.Dot -> DotHero(title, subtitle, header, modifier)
        BackdropKind.Device -> DeviceHero(title, subtitle, header, modifier)
        BackdropKind.Paper -> PaperHero(title, subtitle, header, modifier)
        BackdropKind.Game -> GameHero(title, subtitle, header, modifier)
        BackdropKind.Acid -> AcidHero(title, subtitle, header, modifier)
    }
}

/** The art itself, at [size]: a cover as the theme shows pictures, or the header's glyph or day. */
@Composable
private fun HeroArt(header: ListHeader, size: Dp, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    when {
        header is ListHeader.Cover && style.backdrop == BackdropKind.Paper -> HalftoneCover(header.url, header.seed, modifier.size(size))
        header is ListHeader.Cover && style.backdrop == BackdropKind.Game -> DitheredCover(header.url, header.seed, modifier.size(size))
        // An artist's picture stays round, as it is everywhere else; the paper and the game's screen print it square.
        header is ListHeader.Cover && style.backdrop == BackdropKind.Dot -> CoverImage(header.url, header.seed, size, modifier.size(size).then(if (header.round) Modifier.clip(CircleShape) else Modifier).ledScreen(enabled = io.lanyin.player.ui.design.LocalCoverMedium.current))
        header is ListHeader.Cover -> CoverImage(header.url, header.seed, size, modifier.size(size).then(if (header.round) Modifier.clip(CircleShape) else Modifier))
        else -> Box(modifier.size(size)) { HeaderArt(header, size) }
    }
}

/** The art filling the box [modifier] gives it, cropped to it: a cover, or the header's glyph or day centred. */
@Composable
private fun HeroFill(header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        when {
            header is ListHeader.Cover && style.backdrop == BackdropKind.Game -> DitheredCover(header.url, header.seed, Modifier.fillMaxSize())
            header is ListHeader.Cover -> CoverImage(header.url, header.seed, maxOf(maxWidth, maxHeight), Modifier.fillMaxSize())
            else -> HeaderArt(header, minOf(maxWidth, maxHeight) * 0.78f)
        }
    }
}

/** Title and subtitle in the theme's type, beside the art. */
@Composable
private fun HeroText(title: String, subtitle: String?, modifier: Modifier = Modifier, titleStyle: TextStyle = Lanyin.style.type.title, above: (@Composable () -> Unit)? = null) {
    val c = Lanyin.colors
    Column(modifier) {
        above?.invoke()
        Text(title, style = titleStyle, color = c.content, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = Lanyin.style.type.caption, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * 墨: the art mounted as a hanging scroll and hung in the middle of the page between its
 * rollers, the collector's seal stamped on the mount's corner; the title written beneath it,
 * centred and spaced, as a label is.
 */
@Composable
private fun InkHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Roller(220.dp)
        Box(Modifier.background(lerp(c.bg, c.content, if (style.dark) 0.08f else 0.05f)).padding(horizontal = 16.dp, vertical = 18.dp)) {
            HeroArt(header, 172.dp)
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 12.dp).size(22.dp).background(c.accentAlt, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("藏", style = TextStyle(fontFamily = style.display, fontSize = fixed(13f), lineHeight = fixed(14f)), color = c.bg)
            }
        }
        Roller(220.dp)
        Text(
            title, style = style.type.display.copy(fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = 0.12.em, textAlign = TextAlign.Center),
            color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 20.dp),
        )
        if (subtitle != null) {
            Text(subtitle, style = style.type.caption.copy(textAlign = TextAlign.Center), color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** A scroll's roller: a dark rod with knobbed ends. */
@Composable
private fun Roller(width: Dp) {
    val c = Lanyin.colors
    Canvas(Modifier.size(width, 7.dp)) {
        val y = size.height / 2f
        drawLine(c.content.copy(alpha = 0.75f), Offset(5.dp.toPx(), y), Offset(size.width - 5.dp.toPx(), y), 4.dp.toPx(), StrokeCap.Round)
        drawCircle(c.content.copy(alpha = 0.85f), 3.2.dp.toPx(), Offset(3.2.dp.toPx(), y))
        drawCircle(c.content.copy(alpha = 0.85f), 3.2.dp.toPx(), Offset(size.width - 3.2.dp.toPx(), y))
    }
}

/**
 * 霓: the list on the console's screen: its cover in pixels in the screen's black bezel, the
 * title set in the pixel face under it, a status line above (what kind of list, how it's
 * marked) and the note under the title.
 */
@Composable
private fun NeonHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.fillMaxWidth().themedSurface().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(c.accent))
            Text("  歌单 · PLAYLIST", style = style.type.caption.copy(fontSize = fixed(11f)), color = c.content, modifier = Modifier.weight(1f))
            Text("▸▸", style = style.type.caption.copy(fontSize = fixed(11f)), color = c.accentAlt)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(150.dp).background(Color(0xFF05060A)).padding(3.dp)) {
                when (header) {
                    is ListHeader.Cover -> io.lanyin.player.ui.design.PixelCover(header.url, header.seed, Modifier.fillMaxSize(), cells = 44)
                    is ListHeader.Day -> {
                        io.lanyin.player.ui.design.PixelCover(null, header.day, Modifier.fillMaxSize(), cells = 44)
                        Text("${header.day}", style = style.type.display.copy(fontSize = fixed(44f), lineHeight = fixed(46f)), color = c.accent, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
                    }
                    is ListHeader.Glyph -> {
                        io.lanyin.player.ui.design.PixelCover(null, 3, Modifier.fillMaxSize(), cells = 44)
                        io.lanyin.player.ui.design.ThemedIcon(header.icon, null, tint = c.accent, size = 40.dp, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = style.type.display.copy(fontSize = 24.sp, lineHeight = 30.sp), color = c.content, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = style.type.caption, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/**
 * 暮: the list as a print taped to the mat: its cover screened in the three inks (or, with no
 * cover of its own, a sleeve with the day or the list's mark printed over it), the title
 * overprinted on the margin and the note under it in pencil, the studio's stamp in the corner.
 */
@Composable
private fun DuskHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    Box(modifier.fillMaxWidth()) {
        io.lanyin.player.ui.design.PrintFrame(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            tilt = -0.9f,
            margin = 11.dp,
            picture = {
                Box(Modifier.fillMaxWidth().aspectRatio(1.45f)) {
                    when (header) {
                        is ListHeader.Cover -> io.lanyin.player.ui.design.RisoCover(header.url, header.seed, Modifier.fillMaxSize())
                        else -> {
                            io.lanyin.player.ui.design.RisoCover(null, if (header is ListHeader.Day) header.day else 5, Modifier.fillMaxSize())
                            Column(Modifier.align(Alignment.BottomEnd).padding(14.dp), horizontalAlignment = Alignment.End) {
                                when (header) {
                                    is ListHeader.Day -> {
                                        io.lanyin.player.ui.design.Overprint("${header.day}", TextStyle(fontFamily = io.lanyin.player.ui.theme.PostcardSerifFamily, fontSize = 58.sp, lineHeight = 60.sp), under = io.lanyin.player.ui.design.Riso.Yellow, over = io.lanyin.player.ui.design.Riso.Blue)
                                        Text(header.caption, style = Lanyin.type.caption.copy(fontWeight = FontWeight.Bold), color = io.lanyin.player.ui.design.Riso.Blue)
                                    }
                                    is ListHeader.Glyph -> io.lanyin.player.ui.design.ThemedIcon(header.icon, null, tint = io.lanyin.player.ui.design.Riso.Blue, size = 44.dp)
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            },
            caption = {
                Column(Modifier.padding(top = 2.dp, end = 52.dp)) {
                    io.lanyin.player.ui.design.Overprint(title, TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 32.sp), maxLines = 2)
                    if (subtitle != null) io.lanyin.player.ui.design.PencilNote(subtitle, size = 16.sp, modifier = Modifier.padding(top = 2.dp))
                }
            },
        )
        io.lanyin.player.ui.design.InkStamp(
            "歌单", "♪", "LANYIN", io.lanyin.player.ui.design.Riso.Pink.copy(alpha = 0.8f),
            Modifier.align(Alignment.BottomEnd).offset(x = (-12).dp, y = (-10).dp), diameter = 50.dp, angle = -14f,
        )
    }
}

/**
 * 格: a poster. The art as a block across the full measure, boxed in heavy ink on its hard
 * shadow, a yellow LIST label struck over its corner; the title beneath in the heaviest weight,
 * set tight and flush left.
 */
@Composable
private fun GridHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.padding(end = 8.dp, bottom = 8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f).retroShadow(RectangleShape, c.outline, 8.dp).border(3.dp, c.outline)) {
                HeroFill(header, Modifier.fillMaxSize().padding(3.dp))
            }
            Text(
                "LIST", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(11f), letterSpacing = 0.14.em), color = c.content,
                modifier = Modifier.offset(x = (-6).dp, y = (-8).dp).graphicsLayer { rotationZ = -4f }.background(GridYellow).border(2.dp, c.outline).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            title, style = style.type.title.copy(fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.02).em),
            color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.dp),
        )
        if (subtitle != null) {
            Text(subtitle, style = style.type.caption, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/**
 * 点: the art large on the LED matrix, its corners rounded as the screen's are; PLAYLIST in red
 * dots over the title beside it, flush to the art's foot; a dot rule under the whole.
 */
@Composable
private fun DotHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            HeroArt(header, 164.dp, Modifier.clip(RoundedCornerShape(20.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("PLAYLIST", style = TextStyle(fontFamily = DotoFamily, fontWeight = FontWeight.Black, fontSize = fixed(15f)), color = c.accentAlt)
                Text(title, style = style.type.title.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold), color = c.content, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                if (subtitle != null) {
                    Text(subtitle, style = style.type.caption, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        DotRule(Modifier.padding(top = 18.dp))
    }
}

/**
 * 机: the list on the device's screen. A status line along the top (TRACK LIST, a lit READY),
 * the art large on the glass with the title in lit white beside it, and the screen's row of
 * LED segments along its foot.
 */
@Composable
private fun DeviceHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val c = Lanyin.colors
    val vfd = io.lanyin.player.ui.design.Hifi.Vfd
    val glow = androidx.compose.ui.graphics.Shadow(vfd.copy(alpha = 0.6f), Offset.Zero, 9f)
    io.lanyin.player.ui.design.HifiUnit(
        "CD-1 · COMPACT DISC PLAYER",
        modifier.fillMaxWidth(),
        trailing = { io.lanyin.player.ui.design.Led(io.lanyin.player.ui.design.Hifi.Red, on = true) },
    ) {
        Row(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // The tray, open, with the disc in it.
            Box(
                Modifier
                    .size(128.dp)
                    .background(Color(0xFF1C1A19), RoundedCornerShape(3.dp))
                    .padding(6.dp),
            ) {
                io.lanyin.player.ui.design.CompactDisc(Modifier.fillMaxSize()) { HeroArt(header, 116.dp, Modifier) }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .height(128.dp)
                    .background(io.lanyin.player.ui.design.Hifi.Glass, RoundedCornerShape(3.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DISC", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(8f)), color = vfd)
                    Spacer(Modifier.weight(1f))
                    Text("READY", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(8f)), color = io.lanyin.player.ui.design.Hifi.Amber)
                }
                Spacer(Modifier.weight(1f))
                Text(title, style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(18f), lineHeight = fixed(22f), shadow = glow), color = vfd, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(10.5f), lineHeight = fixed(14f)), color = vfd.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** 报: the list as the lead story: a kicker, the headline, the byline, and the picture in halftone across the column. */
@Composable
private fun PaperHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.fillMaxWidth()) {
        DoubleRule()
        Text("歌单 · 澜音日报", style = style.type.caption.copy(fontWeight = FontWeight.Bold), color = c.accent, modifier = Modifier.padding(top = 8.dp))
        Text(title, style = style.type.display.copy(fontWeight = FontWeight.Black), color = c.content, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text("本报讯　$subtitle", style = style.type.caption.copy(fontStyle = FontStyle.Italic), color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        if (header is ListHeader.Cover) {
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().aspectRatio(2.1f).border(1.dp, c.content)) {
                HalftoneCover(header.url, header.seed, Modifier.fillMaxSize())
            }
            Text("图：《$title》", style = style.type.caption.copy(fontStyle = FontStyle.Italic), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        } else {
            Box(Modifier.padding(top = 12.dp)) { HeroArt(header, 88.dp) }
        }
    }
}

/**
 * 游: the list as a game cartridge. A grey cart with its grip ridges and the arrow moulded into
 * it, the art printed on its label in the screen's four greens; beside it the box's words: NEW
 * GAME, the title in pixels, PRESS Ⓐ.
 */
@Composable
private fun GameHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val pixel = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Cartridge(header, Modifier.width(164.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("★ NEW GAME", style = pixel.copy(fontSize = fixed(10f)), color = c.accent)
            Text(title, style = pixel.copy(fontSize = 24.sp, lineHeight = 29.sp), color = c.content, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            if (subtitle != null) {
                Text(subtitle, style = style.type.caption, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
            Text("PRESS Ⓐ TO PLAY", style = pixel.copy(fontSize = fixed(10f)), color = c.accentAlt, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** A cartridge: grey plastic with a clipped corner, grip ridges, a moulded arrow and the label. */
@Composable
private fun Cartridge(header: ListHeader, modifier: Modifier) {
    val c = Lanyin.colors
    val body = Color(0xFF8F8D99)
    Box(
        modifier
            .aspectRatio(0.86f)
            .drawBehind {
                val w = size.width
                val h = size.height
                val clip = w * 0.12f
                val r = 6.dp.toPx()
                val shell = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f); lineTo(w - clip, 0f); lineTo(w, clip); lineTo(w, h - r)
                    quadraticTo(w, h, w - r, h); lineTo(r, h); quadraticTo(0f, h, 0f, h - r); close()
                }
                translate(3.dp.toPx(), 4.dp.toPx()) { drawPath(shell, Color.Black.copy(alpha = 0.22f)) }
                drawPath(shell, Brush.verticalGradient(listOf(lerp(body, Color.White, 0.16f), body, lerp(body, Color.Black, 0.12f))))
                // The grip: ridges across the top.
                for (k in 0 until 4) {
                    val y = h * 0.035f + k * 3.dp.toPx()
                    drawLine(Color.Black.copy(alpha = 0.2f), Offset(w * 0.1f, y), Offset(w * 0.72f, y), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.22f), Offset(w * 0.1f, y + 1.dp.toPx()), Offset(w * 0.72f, y + 1.dp.toPx()), 1.dp.toPx())
                }
                // The arrow moulded in at the foot.
                val tip = Offset(w / 2f, h - 5.dp.toPx())
                val arrow = androidx.compose.ui.graphics.Path().apply {
                    moveTo(tip.x, tip.y); lineTo(tip.x - 7.dp.toPx(), tip.y - 7.dp.toPx()); lineTo(tip.x + 7.dp.toPx(), tip.y - 7.dp.toPx()); close()
                }
                drawPath(arrow, Color.Black.copy(alpha = 0.22f))
            }
            .padding(start = 13.dp, end = 13.dp, top = 22.dp, bottom = 18.dp),
    ) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)).background(Color(0xFFEFEDE6)).padding(5.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f).border(1.5.dp, c.content)) { HeroFill(header, Modifier.fillMaxSize()) }
            Text(
                "LANYIN", style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(8f), letterSpacing = 0.1.em),
                color = c.accentAlt, modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/**
 * 酸: the list as a CD on the shelf. The jewel case large and tilted, its chrome edge and the
 * shine across the plastic, the hinge down its side; the disc slid out to the right with its
 * rainbow; a starburst stuck on the corner; the title in chrome beneath, centred.
 */
@Composable
private fun AcidHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(272.dp, 210.dp).lightSpill(c.accent, reach = 0.8f, strength = 0.14f)) {
            Canvas(Modifier.align(Alignment.CenterEnd).size(184.dp)) { drawDisc(c.bg) }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(190.dp)
                    .graphicsLayer { rotationZ = -4f }
                    .drawBehind { drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(4.dp.toPx(), 7.dp.toPx()), size, CornerRadius(8.dp.toPx())) }
                    .clip(RoundedCornerShape(8.dp))
                    .border(3.dp, ChromeBrush, RoundedCornerShape(8.dp))
                    .drawWithContent {
                        drawContent()
                        drawGloss(0.3f)
                        // The hinge: a clear strip down the left edge.
                        drawRect(Color.White.copy(alpha = 0.16f), size = Size(12.dp.toPx(), size.height))
                        drawLine(Color.White.copy(alpha = 0.45f), Offset(12.dp.toPx(), 0f), Offset(12.dp.toPx(), size.height), 1.dp.toPx())
                    },
            ) { HeroArt(header, 190.dp) }
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-34).dp, y = 2.dp).size(58.dp).graphicsLayer { rotationZ = 12f }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    drawPath(io.lanyin.player.ui.design.starPath(center + Offset(2.dp.toPx(), 2.dp.toPx()), r, r * 0.68f, 16), Color.Black.copy(alpha = 0.4f))
                    drawPath(io.lanyin.player.ui.design.starPath(center, r, r * 0.68f, 16), Brush.verticalGradient(listOf(Color(0xFFE6FF7A), c.accent, Color(0xFF8BC400)), 0f, size.height))
                    drawPath(io.lanyin.player.ui.design.starPath(center, r, r * 0.68f, 16), ChromeBrush, style = Stroke(1.dp.toPx()))
                }
                Text("NEW!", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixed(12f)), color = Color(0xFF1A0A2E))
            }
        }
        Text(
            title, style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = 28.sp, lineHeight = 33.sp, brush = ChromeBrush, textAlign = TextAlign.Center),
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
        )
        if (subtitle != null) {
            Text(subtitle, style = style.type.caption.copy(textAlign = TextAlign.Center), color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** A compact disc: rainbow on its data side, a chrome rim, the clear hub and its hole. */
private fun DrawScope.drawDisc(hole: Color) {
    val r = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(Color(0xFFD7D9E0), r, center)
    drawCircle(Brush.sweepGradient(listOf(Color(0x00FFFFFF), Color(0x88FF4F7B), Color(0x888B5CF6), Color(0x8800A9C9), Color(0x88C6FF00), Color(0x00FFFFFF), Color(0x55FFFFFF), Color(0x00FFFFFF)), center), r, center)
    drawCircle(ChromeBrush, r - 1.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
    drawCircle(Color.White.copy(alpha = 0.5f), r * 0.3f, center, style = Stroke(1.dp.toPx()))
    drawCircle(hole, r * 0.12f, center)
}

/**
 * A row's number in the theme's hand: Chinese numerals for 墨, zero-padded
 * where figures are machine-set. [strong] sets it in the accent, for the head of a chart.
 */
@Composable
internal fun RowNumber(number: Int, strong: Boolean = false) {
    val style = Lanyin.style
    val c = style.colors
    val base = style.type.caption.copy(fontFamily = style.numeric)
    val tint = if (strong) c.accent else c.muted
    when (style.backdrop) {
        BackdropKind.Ink -> Text(if (number < 100) io.lanyin.player.ui.home.chineseNumber(number) else "$number", style = base, color = tint, maxLines = 1)
        BackdropKind.Dusk -> Text("$number", style = base.copy(fontStyle = FontStyle.Italic, fontSize = fixed(15f)), color = tint, maxLines = 1)
        BackdropKind.Grid, BackdropKind.Device, BackdropKind.Dot, BackdropKind.Acid -> Text("%02d".format(number), style = base.copy(fontWeight = FontWeight.Bold), color = tint, maxLines = 1)
        else -> Text("$number", style = if (strong) base.copy(fontWeight = FontWeight.Bold) else base, color = tint, maxLines = 1)
    }
}

/**
 * Bars bouncing beside the song that's playing, in the theme's hand: dots for 点, stacked blocks
 * for 格, 机 and 游, lit tubes for 霓, rounded bars elsewhere. [t] is the clock, [playing] whether
 * they move.
 */
internal fun DrawScope.drawPlayingBars(t: Float, playing: Boolean, backdrop: BackdropKind, color: Color) {
    val w = size.width / 5f
    for (i in 0 until 3) {
        val h = if (playing) 0.35f + 0.65f * ((sin(t * (5f + i * 1.7f) + i * 1.3f) + 1f) / 2f) else 0.35f + i * 0.15f
        val x = w / 2 + i * w * 2
        when (backdrop) {
            BackdropKind.Dot -> {
                val n = (h * 4f).toInt().coerceIn(1, 4)
                for (k in 0 until n) drawCircle(color, w * 0.55f, Offset(x, size.height - w * 0.6f - k * w * 1.25f))
            }
            BackdropKind.Grid, BackdropKind.Device, BackdropKind.Game -> {
                val n = (h * 4f).toInt().coerceIn(1, 4)
                for (k in 0 until n) drawRect(color, Offset(x - w / 2f, size.height - (k + 1) * w * 1.2f + w * 0.2f), Size(w, w))
            }
            BackdropKind.Neon -> drawRect(color, Offset(x - w * 0.4f, size.height * (1f - h)), Size(w * 0.8f, size.height * h))
            else -> drawLine(color, Offset(x, size.height), Offset(x, size.height * (1f - h)), w, StrokeCap.Round)
        }
    }
}
