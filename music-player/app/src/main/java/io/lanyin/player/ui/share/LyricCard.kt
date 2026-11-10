package io.lanyin.player.ui.share

import io.lanyin.player.ui.design.drawUnitPlate
import io.lanyin.player.ui.design.drawSmiley
import io.lanyin.player.ui.design.drawTapeStrip
import io.lanyin.player.ui.design.drawFlyer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import io.lanyin.player.ui.design.drawCuttingMat
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.design.BoxSign
import io.lanyin.player.ui.design.ChromeBrush
import io.lanyin.player.ui.design.CoverImage
import io.lanyin.player.ui.design.DitheredCover
import io.lanyin.player.ui.design.DotRule
import io.lanyin.player.ui.design.DoubleRule
import io.lanyin.player.ui.design.Flavor
import io.lanyin.player.ui.design.HalftoneCover
import io.lanyin.player.ui.design.NeonText
import io.lanyin.player.ui.design.candyPlastic
import io.lanyin.player.ui.design.drawBackdropFrame
import io.lanyin.player.ui.design.gameWindow
import io.lanyin.player.ui.design.ledScreen
import io.lanyin.player.ui.design.screenStyle
import io.lanyin.player.ui.design.starPath
import io.lanyin.player.ui.theme.ArchivoBlackFamily
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.DotoFamily
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.SpaceMonoFamily
import io.lanyin.player.ui.theme.TiltNeonFamily

/** A line of lyric to put on a card, and the song it's from. */
@Immutable
data class CardLyric(
    val line: String,
    val translation: String?,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String?,
    val coverSeed: Int,
)

/** The card's size: 4:5, as photos are shared. Drawn at the screen's density, about 1000 pixels wide. */
val CardWidth = 360.dp
val CardHeight = 450.dp

/** Type held at its size whatever the font scale: the card is a picture. */
@Composable
private fun fixed(size: Float): TextUnit = with(LocalDensity.current) { size.dp.toSp() }

/**
 * A line of lyric as a picture to share, in the theme's own medium: a calligraphy sheet with its
 * seal (墨), a neon sign on the wall (霓), a postcard (暮), a Swiss poster (格),
 * the dot-matrix screen (点), the device's screen (机), a newspaper clipping
 * (报), a game's dialogue (游), a candy-plastic sticker (酸). Each stands on its theme's page.
 */
@Composable
fun LyricCard(lyric: CardLyric, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val measurer = rememberTextMeasurer()
    Box(modifier.clip(RectangleShape)) {
        Canvas(Modifier.fillMaxSize()) {
            drawBackdropFrame(style, 0f, 0f, page = true, heard = null, progress = 0f, trackNumber = 1, measurer = measurer)
        }
        when (style.backdrop) {
            BackdropKind.Ink -> InkCard(lyric)
            BackdropKind.Neon -> NeonCard(lyric)
            BackdropKind.Dusk -> DuskCard(lyric)
            BackdropKind.Grid -> GridCard(lyric)
            BackdropKind.Dot -> DotCard(lyric)
            BackdropKind.Device -> DeviceCard(lyric)
            BackdropKind.Paper -> PaperCard(lyric)
            BackdropKind.Game -> GameCard(lyric)
            BackdropKind.Acid -> AcidCard(lyric)
        }
    }
}

/** 「澜音」 small at the foot, so a shared card says where it's from. */
@Composable
private fun BoxScope.Credit(color: Color, modifier: Modifier = Modifier) {
    Text(
        "澜音 · LANYIN", style = TextStyle(fontSize = fixed(9f), letterSpacing = 0.2.em),
        color = color, modifier = modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
    )
}

/**
 * 墨: a leaf of an album (册页). A landscape in ink on the xuan, mounted in silk; the line written
 * on it as a painter's inscription is, top to bottom and right to left in the sky above the hills,
 * the song and singer in a smaller column after it and the seals under; a leisure seal in the
 * corner. The translation, if there is one, on the silk below.
 */
@Composable
private fun InkCard(lyric: CardLyric) {
    val style = Lanyin.style
    val c = style.colors
    val silk = if (style.dark) io.lanyin.player.ui.design.InkDesk.SilkNight else io.lanyin.player.ui.design.InkDesk.Silk
    // The line in columns, punctuation dropped as a calligrapher drops it.
    val chars = lyric.line.filter { !it.isWhitespace() && it !in "，。、！？,.!?;；:：" }.take(28)
    // Short lines in columns of five, to stay in the sky above the hills.
    val columns = chars.chunked(if (chars.length <= 10) 5 else 7)
    Column(Modifier.fillMaxSize().background(silk).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                // 隔水: the thin band of paler silk round the painting.
                .border(3.dp, lerp(silk, c.bg, 0.55f))
                .padding(3.dp)
                .drawBehind { io.lanyin.player.ui.design.drawXuanPage(this, style) },
        ) {
            io.lanyin.player.ui.design.ShanShui(Modifier.fillMaxSize(), hour = if (style.dark) 21f else 15f, seed = lyric.coverSeed % 13 + 2)
            Row(Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 16.dp), verticalAlignment = Alignment.Top) {
                // The song and singer, then the seals, in the last (leftmost) column.
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 8.dp, top = 26.dp)) {
                    for (ch in "《${lyric.title.take(8)}》") Text("$ch", style = TextStyle(fontFamily = style.display, fontSize = fixed(11f), lineHeight = fixed(13f)), color = c.content.copy(alpha = 0.8f))
                    for (ch in lyric.artist.take(5)) Text("$ch", style = TextStyle(fontFamily = style.display, fontSize = fixed(10f), lineHeight = fixed(12f)), color = c.muted)
                    io.lanyin.player.ui.design.InkSeal("澜音", 22.dp, Modifier.padding(top = 6.dp))
                    io.lanyin.player.ui.design.InkSeal("听", 16.dp, Modifier.padding(top = 4.dp), carved = false)
                }
                for (col in columns.reversed()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 4.dp)) {
                        for (ch in col) Text("$ch", style = TextStyle(fontFamily = style.display, fontSize = fixed(23f), lineHeight = fixed(27f)), color = c.content)
                    }
                }
            }
            // A leisure seal (闲章) in the lower corner.
            io.lanyin.player.ui.design.InkSeal("清音", 26.dp, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp).graphicsLayer { rotationZ = -3f }, carved = false)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                lyric.translation ?: lyric.album, style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = fixed(11f), lineHeight = fixed(14f)),
                color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text("澜音 · LANYIN", style = TextStyle(fontSize = fixed(9f), letterSpacing = 0.2.em), color = c.muted)
        }
    }
}

/**
 * 霓: the line on the windscreen. The rainy expressway at night in pixels with the line thrown
 * on the glass by the head-up display, and under it the console's panel: the song, its cover,
 * the time.
 */
@Composable
private fun NeonCard(lyric: CardLyric) {
    val style = Lanyin.style
    val c = style.colors
    Column(Modifier.fillMaxSize()) {
        io.lanyin.player.ui.design.NightWindshield(Modifier.fillMaxWidth().weight(1.25f), seed = lyric.coverSeed, running = false) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 36.dp)
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Color.Transparent, 0.12f to Color(0xCC05060A), 0.88f to Color(0xCC05060A), 1f to Color.Transparent))
                    .padding(vertical = 10.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    lyric.line, style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(22f), lineHeight = fixed(30f), textAlign = TextAlign.Center, shadow = Shadow(Color(0xFF6FE0C8).copy(alpha = 0.7f), Offset.Zero, 12f)),
                    color = Color(0xFFD8E4EE), maxLines = 3,
                )
                lyric.translation?.let { Text(it, style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(12f), textAlign = TextAlign.Center), color = Color(0xFF9FB2C8), modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        Row(Modifier.fillMaxWidth().weight(0.55f).background(c.bg).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).background(Color(0xFF05060A)).padding(3.dp)) {
                io.lanyin.player.ui.design.PixelCover(lyric.coverUrl, lyric.coverSeed, Modifier.fillMaxSize(), cells = 36)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("正在播放 ▸▸", style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(11f)), color = c.accent)
                Text(lyric.title, style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(20f)), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${lyric.artist} · 《${lyric.album}》", style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(11f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("LANYIN · 夜行", style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(10f)), color = c.accentAlt, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/**
 * 暮: a print on the cutting mat. The cover screened in the three inks, the line overprinted
 * large on the margin with its translation under it, the song signed in pencil, crop marks, a
 * strip of tape and the studio's stamp.
 */
@Composable
private fun DuskCard(lyric: CardLyric) {
    val style = Lanyin.style
    Box(Modifier.fillMaxSize().drawBehind { drawCuttingMat(style, 18.5f, null) }.padding(22.dp)) {
        io.lanyin.player.ui.design.PrintFrame(
            Modifier.fillMaxWidth().align(Alignment.Center),
            tilt = -1.2f,
            margin = 14.dp,
            picture = { io.lanyin.player.ui.design.RisoCover(lyric.coverUrl, lyric.coverSeed, Modifier.fillMaxWidth().aspectRatio(1.3f)) },
            caption = {
                Column(Modifier.padding(top = 4.dp)) {
                    io.lanyin.player.ui.design.Overprint(lyric.line, TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = fixed(21f), lineHeight = fixed(28f)), maxLines = 3)
                    lyric.translation?.let { Text(it, style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = fixed(12f), lineHeight = fixed(16f)), color = io.lanyin.player.ui.design.Riso.Blue, modifier = Modifier.padding(top = 6.dp)) }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                        io.lanyin.player.ui.design.PencilNote("《${lyric.title}》${lyric.artist}", size = fixed(16f), modifier = Modifier.weight(1f))
                        io.lanyin.player.ui.design.PencilNote("1/1", size = fixed(16f))
                    }
                }
            },
        )
        io.lanyin.player.ui.design.InkStamp(
            "澜音", "♪", "RISO", io.lanyin.player.ui.design.Riso.Pink.copy(alpha = 0.8f),
            Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 20.dp), diameter = 50.dp, angle = 12f,
        )
    }
}

/** 格: a Swiss poster. The line set huge and heavy, a red disc and a yellow label, the song in mono under a thick rule. */
@Composable
private fun GridCard(lyric: CardLyric) {
    val style = Lanyin.style
    val c = style.colors
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Canvas(Modifier.align(Alignment.TopEnd).size(84.dp)) {
            drawCircle(c.outline, size.minDimension / 2f, center + Offset(4.dp.toPx(), 4.dp.toPx()))
            drawCircle(c.accent, size.minDimension / 2f)
            drawCircle(c.outline, size.minDimension / 2f, style = Stroke(2.5.dp.toPx()))
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            Text(
                "LYRIC NO.01", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(10f), letterSpacing = 0.1.em), color = c.content,
                modifier = Modifier.background(GridYellow).border(2.dp, c.outline).padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Text(lyric.line, style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixed(32f), lineHeight = fixed(38f), letterSpacing = (-0.02).em), color = c.content, modifier = Modifier.padding(top = 12.dp))
            lyric.translation?.let { Text(it, style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(11f), lineHeight = fixed(16f)), color = c.muted, modifier = Modifier.padding(top = 8.dp)) }
            Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(4.dp).background(c.outline))
            Row(Modifier.padding(top = 8.dp)) {
                Text(lyric.title, style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixed(13f)), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(lyric.artist.uppercase(), style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(10f)), color = c.content, maxLines = 1)
            }
        }
    }
}

/** 点: the picture through the LED matrix, the line in white under the red dot, a dotted rule. */
@Composable
private fun DotCard(lyric: CardLyric) {
    val c = Lanyin.colors
    Column(Modifier.fillMaxSize().padding(26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(c.accentAlt, CircleShape))
            Text("  NOW PLAYING", style = TextStyle(fontFamily = DotoFamily, fontWeight = FontWeight.Black, fontSize = fixed(14f)), color = c.content)
        }
        CoverImage(lyric.coverUrl, lyric.coverSeed, 160.dp, Modifier.padding(top = 18.dp).size(150.dp).clip(RoundedCornerShape(18.dp)).ledScreen(enabled = io.lanyin.player.ui.design.LocalCoverMedium.current))
        Spacer(Modifier.weight(1f))
        Text(lyric.line, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = fixed(24f), lineHeight = fixed(32f)), color = c.content)
        lyric.translation?.let { Text(it, style = TextStyle(fontSize = fixed(12f), lineHeight = fixed(17f)), color = c.muted, modifier = Modifier.padding(top = 6.dp)) }
        DotRule(Modifier.padding(top = 16.dp))
        Text("${lyric.title} · ${lyric.artist}", style = TextStyle(fontSize = fixed(11f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * 机: a cassette's J-card. The red band with the side and the tape, the song written on the
 * spine by hand, and inside the line in a fluorescent display, set in the deck's brushed plate.
 */
@Composable
private fun DeviceCard(lyric: CardLyric) {
    val style = Lanyin.style
    val c = style.colors
    val vfd = io.lanyin.player.ui.design.Hifi.Vfd
    val glow = androidx.compose.ui.graphics.Shadow(vfd.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset.Zero, 10f)
    Column(
        Modifier
            .fillMaxSize()
            .drawBehind { drawUnitPlate(style) }
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            io.lanyin.player.ui.design.Wordmark(c.content, 15f)
            Text("  TC-18 · LYRIC", style = io.lanyin.player.ui.design.engraved(8f), color = c.muted, maxLines = 1)
        }
        Column(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .background(io.lanyin.player.ui.design.Hifi.Glass, RoundedCornerShape(3.dp))
                .padding(16.dp),
        ) {
            Text("▶ ${lyric.title}", style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(11f), shadow = glow), color = vfd.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(lyric.line, style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(22f), lineHeight = fixed(30f), shadow = glow), color = vfd, modifier = Modifier.padding(top = 12.dp))
            lyric.translation?.let { Text(it, style = TextStyle(fontFamily = io.lanyin.player.ui.theme.PixelFamily, fontSize = fixed(11f), lineHeight = fixed(15f)), color = vfd.copy(alpha = 0.55f), modifier = Modifier.padding(top = 8.dp)) }
        }
        // The J-card's spine: the band, and the song by hand.
        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth().background(io.lanyin.player.ui.design.Hifi.Label).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(" A ", style = TextStyle(fontSize = fixed(14f), fontWeight = FontWeight.Black), color = Color.White, modifier = Modifier.background(io.lanyin.player.ui.design.Hifi.LabelBand).padding(horizontal = 6.dp, vertical = 4.dp))
            Text(
                lyric.title, style = TextStyle(fontFamily = io.lanyin.player.ui.theme.LongCangFamily, fontSize = fixed(18f)), color = io.lanyin.player.ui.design.Hifi.LabelInk,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Text(lyric.artist, style = TextStyle(fontFamily = io.lanyin.player.ui.theme.LongCangFamily, fontSize = fixed(13f)), color = io.lanyin.player.ui.design.Hifi.LabelInk.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}

/** 报: a clipping from the paper. The masthead, the line as the headline, the byline, the picture in halftone. */
@Composable
private fun PaperCard(lyric: CardLyric) {
    val style = Lanyin.style
    val c = style.colors
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Text("澜音日报", style = TextStyle(fontFamily = style.display, fontWeight = FontWeight.Black, fontSize = fixed(22f), textAlign = TextAlign.Center), color = c.content, modifier = Modifier.fillMaxWidth())
        DoubleRule(Modifier.padding(top = 4.dp))
        Text("歌词版 · 今日一句", style = TextStyle(fontFamily = style.display, fontWeight = FontWeight.Bold, fontSize = fixed(10f)), color = c.accent, modifier = Modifier.padding(top = 10.dp))
        Text(lyric.line, style = TextStyle(fontFamily = style.display, fontWeight = FontWeight.Black, fontSize = fixed(26f), lineHeight = fixed(33f)), color = c.content, modifier = Modifier.padding(top = 2.dp))
        lyric.translation?.let { Text(it, style = TextStyle(fontFamily = style.display, fontStyle = FontStyle.Italic, fontSize = fixed(12f), lineHeight = fixed(17f)), color = c.muted, modifier = Modifier.padding(top = 4.dp)) }
        Text("本报讯　《${lyric.title}》 ${lyric.artist}", style = TextStyle(fontFamily = style.display, fontStyle = FontStyle.Italic, fontSize = fixed(10f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().aspectRatio(2f).border(1.dp, c.content)) {
            HalftoneCover(lyric.coverUrl, lyric.coverSeed, Modifier.fillMaxSize())
        }
        Text("图：《${lyric.album.ifEmpty { lyric.title }}》", style = TextStyle(fontFamily = style.display, fontStyle = FontStyle.Italic, fontSize = fixed(9f)), color = c.muted, modifier = Modifier.padding(top = 3.dp))
    }
}

/** 游: the picture dithered on the screen and the line in a game's dialogue window, a ▼ to go on. */
@Composable
private fun GameCard(lyric: CardLyric) {
    val style = Lanyin.style
    val c = style.colors
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DitheredCover(lyric.coverUrl, lyric.coverSeed, Modifier.padding(top = 6.dp).size(170.dp).border(4.dp, c.content))
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().gameWindow(c.surface, c.content).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("${lyric.artist}：", style = TextStyle(fontFamily = style.display, fontSize = fixed(12f)), color = c.accent, maxLines = 1)
            Text(lyric.line, style = TextStyle(fontFamily = style.display, fontSize = fixed(18f), lineHeight = fixed(26f)), color = c.content, modifier = Modifier.padding(top = 4.dp))
            lyric.translation?.let { Text(it, style = TextStyle(fontFamily = style.display, fontSize = fixed(11f), lineHeight = fixed(16f)), color = c.muted, modifier = Modifier.padding(top = 4.dp)) }
            Text("▼", style = TextStyle(fontFamily = style.display, fontSize = fixed(12f)), color = c.content, modifier = Modifier.align(Alignment.End))
        }
    }
}

/** 酸: a sticker of grape plastic, the line in chrome on it, the disc peeking out and sparkles round it. */
@Composable
private fun AcidCard(lyric: CardLyric) {
    val acid = io.lanyin.player.ui.design.Acid
    // A flyer pasted on the wall: the cover photocopied on it, the line on a strip of lime tape,
    // who and what in mono, a smiley stuck on.
    Box(Modifier.fillMaxSize().padding(22.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = -1.5f }
                .drawBehind { drawFlyer(acid.Black, seed = 9, torn = true) }
                .padding(16.dp),
        ) {
            Text("LANYIN SOUND SYSTEM PRESENTS", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(8f)), color = acid.Paper)
            Box(Modifier.padding(top = 10.dp).size(150.dp)) {
                io.lanyin.player.ui.design.PhotocopyCover(lyric.coverUrl, lyric.coverSeed, Modifier.fillMaxSize(), ink = acid.Lime, paper = acid.Black, lines = 34f, positive = true)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().graphicsLayer { rotationZ = -2f }.background(acid.Lime).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(lyric.line, style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixed(22f), lineHeight = fixed(28f)), color = acid.Black)
            }
            lyric.translation?.let { Text(it, style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(10.5f), lineHeight = fixed(15f)), color = acid.Paper.copy(alpha = 0.8f), modifier = Modifier.padding(top = 10.dp)) }
            Text("LIVE: ${lyric.title} — ${lyric.artist}", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(10f)), color = acid.Lime, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        }
        Canvas(Modifier.align(Alignment.TopEnd).size(84.dp)) {
            drawSmiley(center, size.minDimension * 0.42f, acid.Lime, acid.Black)
        }
        Canvas(Modifier.align(Alignment.TopCenter).size(96.dp, 18.dp)) { drawTapeStrip(center, size.width * 0.8f, size.height, -3f, seed = 2) }
    }
}
