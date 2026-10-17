package io.lanyin.player.ui.share

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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.design.Airmail
import io.lanyin.player.ui.design.BoxSign
import io.lanyin.player.ui.design.ChromeBrush
import io.lanyin.player.ui.design.CoverImage
import io.lanyin.player.ui.design.DitheredCover
import io.lanyin.player.ui.design.DotRule
import io.lanyin.player.ui.design.DoubleRule
import io.lanyin.player.ui.design.Flavor
import io.lanyin.player.ui.design.HalftoneCover
import io.lanyin.player.ui.design.NeonText
import io.lanyin.player.ui.design.Postmark
import io.lanyin.player.ui.design.brushStrokePath
import io.lanyin.player.ui.design.candyPlastic
import io.lanyin.player.ui.design.drawAirmailBorder
import io.lanyin.player.ui.design.drawBackdropFrame
import io.lanyin.player.ui.design.drawStamp
import io.lanyin.player.ui.design.gameWindow
import io.lanyin.player.ui.design.ledScreen
import io.lanyin.player.ui.design.screenStyle
import io.lanyin.player.ui.design.starPath
import io.lanyin.player.ui.theme.ArchivoBlackFamily
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.DotoFamily
import io.lanyin.player.ui.theme.FilmStamp
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PostcardSerifFamily
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
 * seal (墨), a neon sign on the wall (霓), a postcard (暮), a Swiss poster (格), a film frame with
 * its subtitle (影), the dot-matrix screen (点), the device's screen (机), a newspaper clipping
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
            BackdropKind.Film -> FilmCard(lyric)
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

/** 墨: the line written large on rice paper, a dry stroke of the brush under it, the song set down the side, and the seal. */
@Composable
private fun InkCard(lyric: CardLyric) {
    val style = Lanyin.style
    val c = style.colors
    Box(Modifier.fillMaxSize().padding(28.dp)) {
        Column(Modifier.align(Alignment.CenterStart).padding(end = 44.dp)) {
            Text(lyric.line, style = TextStyle(fontFamily = FontFamily.Serif, fontSize = fixed(26f), lineHeight = fixed(40f), letterSpacing = 0.08.em), color = c.content)
            Canvas(Modifier.padding(top = 14.dp).size(140.dp, 10.dp)) {
                drawPath(brushStrokePath(Offset(0f, size.height / 2f), size.width, size.height * 0.7f), c.content.copy(alpha = 0.7f))
            }
            lyric.translation?.let { Text(it, style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = fixed(12f), lineHeight = fixed(18f)), color = c.muted, modifier = Modifier.padding(top = 10.dp)) }
        }
        // The inscription down the right edge, and the seal under it.
        Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.CenterHorizontally) {
            for (ch in "《${lyric.title}》".take(12)) Text(ch.toString(), style = TextStyle(fontFamily = FontFamily.Serif, fontSize = fixed(13f), lineHeight = fixed(16f)), color = c.content)
            Spacer(Modifier.height(6.dp))
            for (ch in lyric.artist.take(6)) Text(ch.toString(), style = TextStyle(fontFamily = FontFamily.Serif, fontSize = fixed(11f), lineHeight = fixed(14f)), color = c.muted)
            Box(Modifier.padding(top = 10.dp).size(30.dp).background(c.accentAlt, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                Text("澜音", style = TextStyle(fontFamily = FontFamily.Serif, fontSize = fixed(10f), lineHeight = fixed(11f), textAlign = TextAlign.Center), color = c.bg)
            }
        }
    }
}

/** 霓: the line lit as a sign on the wall, an ON AIR box over it and the song in a small sign beneath. */
@Composable
private fun NeonCard(lyric: CardLyric) {
    val style = Lanyin.style
    val c = style.colors
    Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.Center) {
        BoxSign("ON AIR", c.accentAlt, ignite = false)
        Spacer(Modifier.height(22.dp))
        NeonText(lyric.line, TextStyle(fontWeight = FontWeight.Bold, fontSize = fixed(28f), lineHeight = fixed(40f)), c.accent, ignite = false, maxLines = 5)
        lyric.translation?.let { Text(it, style = TextStyle(fontSize = fixed(13f), lineHeight = fixed(19f)), color = c.content.copy(alpha = 0.75f), modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(26.dp))
        Text(lyric.title, style = TextStyle(fontFamily = TiltNeonFamily, fontSize = fixed(15f)), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(lyric.artist, style = TextStyle(fontSize = fixed(11f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 暮: a postcard. The picture above with 「Greetings from」, the back below: the line written by hand, the address lines, a stamp and its postmark. */
@Composable
private fun DuskCard(lyric: CardLyric) {
    val style = Lanyin.style
    val c = style.colors
    val paper = if (style.dark) Color(0xFF2A2F48) else Color(0xFFFFFCF6)
    val pen = if (style.dark) c.content else Color(0xFF3B2A24)
    Box(
        Modifier
            .fillMaxSize()
            .padding(18.dp)
            .drawBehind {
                drawRoundRect(Color.Black.copy(alpha = if (style.dark) 0.35f else 0.12f), Offset(2.dp.toPx(), 5.dp.toPx()), size, CornerRadius(4.dp.toPx()))
                drawRoundRect(paper, cornerRadius = CornerRadius(4.dp.toPx()))
                drawAirmailBorder(6.dp.toPx(), 4.dp.toPx())
            }
            .padding(14.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(2.dp))) {
                CoverImage(lyric.coverUrl, lyric.coverSeed, 320.dp, Modifier.fillMaxSize())
                Text("Greetings from", style = TextStyle(fontFamily = PostcardSerifFamily, fontStyle = FontStyle.Italic, fontSize = fixed(16f), shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 6f)), color = Color.White, modifier = Modifier.padding(10.dp))
            }
            Row(Modifier.padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(lyric.line, style = TextStyle(fontFamily = style.display, fontStyle = FontStyle.Italic, fontSize = fixed(17f), lineHeight = fixed(25f)), color = pen)
                    lyric.translation?.let { Text(it, style = TextStyle(fontFamily = PostcardSerifFamily, fontStyle = FontStyle.Italic, fontSize = fixed(12f), lineHeight = fixed(16f)), color = pen.copy(alpha = 0.65f), modifier = Modifier.padding(top = 6.dp)) }
                    Text("—《${lyric.title}》${lyric.artist}", style = TextStyle(fontFamily = style.display, fontStyle = FontStyle.Italic, fontSize = fixed(11f)), color = pen.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                }
                // The address side: lines to write on.
                Canvas(Modifier.padding(start = 12.dp, top = 30.dp).size(80.dp, 60.dp)) {
                    for (k in 0..2) drawLine(pen.copy(alpha = 0.25f), Offset(0f, k * size.height / 3f + size.height / 3f), Offset(size.width, k * size.height / 3f + size.height / 3f), 1.dp.toPx())
                }
            }
        }
        Canvas(Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 8.dp).size(36.dp, 44.dp).graphicsLayer { rotationZ = 4f }) {
            drawStamp(Rect(Offset(2.dp.toPx(), 2.dp.toPx()), Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx())), Airmail.Red, paper, Color.Transparent)
        }
        Postmark("澜音", "♪", "LANYIN", Color.White.copy(alpha = 0.85f), Modifier.align(Alignment.TopEnd).offset(x = (-26).dp, y = 26.dp), diameter = 46.dp, angle = -12f)
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

/** 影: a frame of film with its sprockets and edge code, the picture in it and the line as its subtitle. */
@Composable
private fun FilmCard(lyric: CardLyric) {
    val stock = Color(0xFF141210)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 26.dp), verticalArrangement = Arrangement.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(stock, RoundedCornerShape(2.dp))
                .drawBehind {
                    val hole = Size(8.dp.toPx(), 5.dp.toPx())
                    var x = 5.dp.toPx()
                    while (x + hole.width < size.width) {
                        for (y in listOf(4.dp.toPx(), size.height - 4.dp.toPx() - hole.height)) drawRoundRect(Color(0xFFEDE6DA).copy(alpha = 0.85f), Offset(x, y), hole, CornerRadius(1.dp.toPx()))
                        x += 14.dp.toPx()
                    }
                }
                .padding(horizontal = 8.dp, vertical = 16.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.33f)) {
                CoverImage(lyric.coverUrl, lyric.coverSeed, 320.dp, Modifier.fillMaxSize())
                // The subtitle, over the foot of the picture.
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(lyric.line, style = TextStyle(fontSize = fixed(16f), lineHeight = fixed(22f), textAlign = TextAlign.Center, shadow = Shadow(Color.Black, Offset(0f, 1f), 4f)), color = Color(0xFFFFF4D6))
                    lyric.translation?.let { Text(it, style = TextStyle(fontSize = fixed(11f), lineHeight = fixed(15f), textAlign = TextAlign.Center), color = Color.White.copy(alpha = 0.8f)) }
                }
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            Text("▸ 12A  LANYIN 400", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(10f), letterSpacing = 0.1.em), color = FilmStamp)
            Spacer(Modifier.weight(1f))
            Text("《${lyric.title}》", style = TextStyle(fontFamily = FontFamily.Serif, fontSize = fixed(11f)), color = Color(0xFFEDE6DA), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** 机: the device's screen, the line on it as a readout, a printed label above and the model plate below. */
@Composable
private fun DeviceCard(lyric: CardLyric) {
    val style = Lanyin.style
    val c = style.colors
    val screen = style.screenStyle().colors
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(
            "LYRIC · L-01", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(9f), letterSpacing = 0.1.em), color = c.bg,
            modifier = Modifier.background(c.content, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        )
        Column(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .background(c.bgDeep, RoundedCornerShape(16.dp))
                .padding(8.dp)
                .background(screen.bg, RoundedCornerShape(10.dp))
                .padding(18.dp),
        ) {
            Text("▶ ${lyric.title.uppercase()}", style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(10f)), color = screen.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(lyric.line, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = fixed(22f), lineHeight = fixed(30f)), color = screen.content, modifier = Modifier.padding(top = 14.dp))
            lyric.translation?.let { Text(it, style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(10f), lineHeight = fixed(15f)), color = screen.muted, modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(lyric.artist, style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(10f)), color = screen.muted, maxLines = 1, modifier = Modifier.weight(1f))
                Box(Modifier.size(8.dp).background(screen.accent, CircleShape))
            }
        }
        Text("LANYIN  L-01", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(9f), letterSpacing = 0.12.em), color = c.muted, modifier = Modifier.padding(top = 10.dp).align(Alignment.End))
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
    val c = Lanyin.colors
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Canvas(Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-10).dp).size(150.dp)) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFFD7D9E0), r)
            drawCircle(Brush.sweepGradient(listOf(Color(0x00FFFFFF), Color(0x88FF4F7B), Color(0x888B5CF6), Color(0x8800A9C9), Color(0x88C6FF00), Color(0x00FFFFFF)), center), r)
            drawCircle(ChromeBrush, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            drawCircle(c.bg, r * 0.12f)
        }
        CoverImage(lyric.coverUrl, lyric.coverSeed, 140.dp, Modifier.size(130.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, ChromeBrush, RoundedCornerShape(10.dp)))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { rotationZ = -2f }
                .candyPlastic(Flavor.Grape, RoundedCornerShape(26.dp))
                .padding(20.dp),
        ) {
            Text(lyric.line, style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixed(24f), lineHeight = fixed(32f), brush = ChromeBrush))
            lyric.translation?.let { Text(it, style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(11f), lineHeight = fixed(16f)), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 6.dp)) }
            Text("✦ ${lyric.title} — ${lyric.artist}", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(10f)), color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
        }
        Canvas(Modifier.fillMaxSize()) {
            for ((fx, fy, s) in listOf(Triple(0.1f, 0.42f, 9f), Triple(0.6f, 0.36f, 6f), Triple(0.88f, 0.5f, 11f))) {
                val p = Offset(size.width * fx, size.height * fy)
                drawPath(starPath(p, s * density, s * density * 0.22f, 4), Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
