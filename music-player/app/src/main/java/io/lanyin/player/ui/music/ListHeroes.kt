package io.lanyin.player.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.design.Airmail
import io.lanyin.player.ui.design.ChromeBrush
import io.lanyin.player.ui.design.CoverImage
import io.lanyin.player.ui.design.DitheredCover
import io.lanyin.player.ui.design.DotRule
import io.lanyin.player.ui.design.DoubleRule
import io.lanyin.player.ui.design.HalftoneCover
import io.lanyin.player.ui.design.NeonText
import io.lanyin.player.ui.design.Postmark
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
import io.lanyin.player.ui.theme.FilmStamp
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.SpaceMonoFamily
import kotlin.math.sin

/*
 * The head of a song list in each theme's own medium, so a playlist, an album, an artist or
 * today's picks open the way the theme's other pages do: a hanging scroll for 墨, a sign in a
 * tube frame for 霓, a print with its postmark for 暮, a poster block for 格, a frame of film for
 * 影, an LED panel for 点, a screen in its case for 机, the paper's lead story for 报, a game's
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
        BackdropKind.Film -> FilmHero(title, subtitle, header, modifier)
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
        header is ListHeader.Cover && style.backdrop == BackdropKind.Dot -> CoverImage(header.url, header.seed, size, modifier.size(size).then(if (header.round) Modifier.clip(CircleShape) else Modifier).ledScreen())
        header is ListHeader.Cover -> CoverImage(header.url, header.seed, size, modifier.size(size).then(if (header.round) Modifier.clip(CircleShape) else Modifier))
        else -> Box(modifier.size(size)) { HeaderArt(header, size) }
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

/** 墨: the art mounted as a hanging scroll between its rollers, the title beside it and a seal under. */
@Composable
private fun InkHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Roller(120.dp)
            Box(Modifier.background(lerp(c.bg, c.content, if (style.dark) 0.08f else 0.05f)).padding(horizontal = 8.dp, vertical = 10.dp)) {
                HeroArt(header, 96.dp)
            }
            Roller(120.dp)
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            HeroText(title, subtitle, titleStyle = style.type.display.copy(fontSize = style.type.title.fontSize * 1.25f, lineHeight = style.type.title.lineHeight * 1.25f))
            // The collector's seal.
            Box(Modifier.padding(top = 12.dp).size(26.dp).background(c.accentAlt, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                Text("藏", style = TextStyle(fontFamily = style.display, fontSize = fixed(15f), lineHeight = fixed(16f)), color = c.bg)
            }
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

/** 霓: the art in a tube frame throwing its light on the wall, the title lit as a sign. */
@Composable
private fun NeonHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .lightSpill(c.accentAlt, reach = 0.75f, strength = 0.2f)
                .drawWithContent {
                    drawContent()
                    val inset = 3.dp.toPx()
                    drawTubeFrame(Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), 14.dp.toPx(), c.accentAlt, 2.dp.toPx())
                }
                .padding(10.dp),
        ) {
            HeroArt(header, 100.dp, Modifier.clip(RoundedCornerShape(8.dp)))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            NeonText(title, style.type.title.copy(fontWeight = FontWeight.Bold), c.accent, maxLines = 2)
            if (subtitle != null) Text(subtitle, style = style.type.caption, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** 暮: the art as a white-bordered print taped down at an angle, a postmark struck over its corner. */
@Composable
private fun DuskHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val paper = if (style.dark) Color(0xFF2A2F48) else Color(0xFFFFFCF6)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Box(
                Modifier
                    .padding(top = 8.dp, end = 10.dp)
                    .graphicsLayer { rotationZ = -3f }
                    .drawBehind {
                        drawRoundRect(Color.Black.copy(alpha = if (style.dark) 0.35f else 0.1f), Offset(2.dp.toPx(), 4.dp.toPx()), size, CornerRadius(2.dp.toPx()))
                        drawRoundRect(paper, cornerRadius = CornerRadius(2.dp.toPx()))
                    }
                    .drawWithContent {
                        drawContent()
                        rotate(5f, Offset(size.width / 2f, 0f)) {
                            drawRect(c.accent.copy(alpha = 0.3f), Offset(size.width * 0.3f, -7.dp.toPx()), Size(size.width * 0.4f, 14.dp.toPx()))
                        }
                    }
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 16.dp),
            ) { HeroArt(header, 100.dp, Modifier.clip(RectangleShape)) }
            Postmark("澜音", "♪", "LIST", (if (style.dark) c.accent else Airmail.Red).copy(alpha = 0.7f), Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp), diameter = 40.dp, waves = false, angle = -14f)
        }
        Spacer(Modifier.width(18.dp))
        HeroText(title, subtitle, Modifier.weight(1f), titleStyle = style.type.title.copy(fontSize = style.type.title.fontSize * 1.15f))
    }
}

/** 格: a poster block. The art boxed in ink on its hard shadow, a yellow label, the title heavy. */
@Composable
private fun GridHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HeroArt(header, 112.dp, Modifier.retroShadow(RectangleShape, c.outline, 6.dp).border(2.5.dp, c.outline))
        Spacer(Modifier.width(20.dp))
        HeroText(
            title, subtitle, Modifier.weight(1f),
            titleStyle = style.type.title.copy(fontWeight = FontWeight.Black, fontSize = style.type.title.fontSize * 1.25f, lineHeight = style.type.title.lineHeight * 1.2f),
            above = {
                Text(
                    "LIST", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(10f), letterSpacing = 0.12.em), color = c.content,
                    modifier = Modifier.padding(bottom = 8.dp).background(GridYellow).border(2.dp, c.outline).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            },
        )
    }
}

/** 影: the art as a frame on a strip of film, its sprockets and edge code, the title as a slate. */
@Composable
private fun FilmHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Box(
                Modifier
                    .background(Color(0xFF141210), RoundedCornerShape(2.dp))
                    .drawBehind {
                        val hole = Size(6.dp.toPx(), 4.dp.toPx())
                        var x = 4.dp.toPx()
                        while (x + hole.width < size.width) {
                            for (y in listOf(3.dp.toPx(), size.height - 3.dp.toPx() - hole.height)) {
                                drawRoundRect(Color(0xFFEDE6DA).copy(alpha = 0.85f), Offset(x, y), hole, CornerRadius(1.dp.toPx()))
                            }
                            x += 11.dp.toPx()
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 12.dp),
            ) { HeroArt(header, 100.dp) }
            Text("▸ 01A  LANYIN 400", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(9f), letterSpacing = 0.1.em), color = FilmStamp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(18.dp))
        HeroText(
            title, subtitle, Modifier.weight(1f),
            above = {
                Text("SCENE 1 · TAKE 1", style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(9.5f), letterSpacing = 0.14.em), color = style.colors.muted, modifier = Modifier.padding(bottom = 6.dp))
            },
        )
    }
}

/** 点: the art shown through the LED matrix, a dotted label over the title and a dot rule under. */
@Composable
private fun DotHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroArt(header, 112.dp, Modifier.clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(18.dp))
            HeroText(
                title, subtitle, Modifier.weight(1f),
                above = { Text("PLAYLIST", style = TextStyle(fontFamily = DotoFamily, fontWeight = FontWeight.Black, fontSize = fixed(14f)), color = c.accentAlt, modifier = Modifier.padding(bottom = 4.dp)) },
            )
        }
        DotRule(Modifier.padding(top = 16.dp))
    }
}

/** 机: the art on a little screen set into the case, a printed label over the title. */
@Composable
private fun DeviceHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .background(c.bgDeep, RoundedCornerShape(12.dp))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (style.dark) 0.35f else 0.12f), Color.Transparent), endY = 8.dp.toPx()))
                }
                .padding(7.dp),
        ) { HeroArt(header, 100.dp, Modifier.clip(RoundedCornerShape(6.dp))) }
        Spacer(Modifier.width(18.dp))
        HeroText(
            title, subtitle, Modifier.weight(1f),
            above = {
                Text(
                    "TRACK LIST", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(9f), letterSpacing = 0.1.em), color = c.bg,
                    modifier = Modifier.padding(bottom = 8.dp).background(c.content, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            },
        )
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

/** 游: the art in the screen's frame and a dialogue window announcing the list. */
@Composable
private fun GameHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HeroArt(header, 96.dp, Modifier.border(3.dp, c.content))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).gameWindow(c.surface, c.content).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("▶ $title", style = style.type.body.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = style.type.caption, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** 酸: a CD in its jewel case, the disc slid half out with its rainbow; the title in chrome. */
@Composable
private fun AcidHero(title: String, subtitle: String?, header: ListHeader, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(142.dp, 112.dp)) {
            // The disc, behind the case.
            Canvas(Modifier.align(Alignment.CenterEnd).size(100.dp)) { drawDisc(c.bg) }
            Box(
                Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, ChromeBrush, RoundedCornerShape(6.dp))
                    .drawWithContent { drawContent(); drawGloss(0.22f) },
            ) { HeroArt(header, 112.dp) }
        }
        Spacer(Modifier.width(14.dp))
        HeroText(title, subtitle, Modifier.weight(1f), titleStyle = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = style.type.title.fontSize * 1.1f, lineHeight = style.type.title.lineHeight * 1.1f, brush = ChromeBrush))
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

/** A row's number in the theme's hand: Chinese numerals for 墨, frame numbers for 影, zero-padded where figures are machine-set. */
@Composable
internal fun RowNumber(number: Int) {
    val style = Lanyin.style
    val c = style.colors
    val base = style.type.caption.copy(fontFamily = style.numeric)
    when (style.backdrop) {
        BackdropKind.Ink -> Text(if (number < 100) io.lanyin.player.ui.home.chineseNumber(number) else "$number", style = base, color = c.muted, maxLines = 1)
        BackdropKind.Film -> Text("%02dA".format(number), style = base.copy(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(10.5f)), color = FilmStamp.copy(alpha = 0.85f), maxLines = 1)
        BackdropKind.Dusk -> Text("$number", style = base.copy(fontStyle = FontStyle.Italic, fontSize = fixed(15f)), color = c.muted, maxLines = 1)
        BackdropKind.Grid, BackdropKind.Device, BackdropKind.Dot, BackdropKind.Acid -> Text("%02d".format(number), style = base.copy(fontWeight = FontWeight.Bold), color = c.muted, maxLines = 1)
        else -> Text("$number", style = base, color = c.muted, maxLines = 1)
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
            BackdropKind.Neon -> drawTubeLine(Offset(x, size.height), Offset(x, size.height * (1f - h)), color, w * 0.6f)
            else -> drawLine(color, Offset(x, size.height), Offset(x, size.height * (1f - h)), w, StrokeCap.Round)
        }
    }
}
