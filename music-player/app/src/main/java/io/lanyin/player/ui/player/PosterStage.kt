package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.design.Acid
import io.lanyin.player.ui.design.PhotocopyCover
import io.lanyin.player.ui.design.drawAsterisk
import io.lanyin.player.ui.design.drawBarcode
import io.lanyin.player.ui.design.drawCrosshair
import io.lanyin.player.ui.design.drawFlyer
import io.lanyin.player.ui.design.drawSmiley
import io.lanyin.player.ui.design.drawTapeStrip
import io.lanyin.player.ui.design.drawTextRing
import io.lanyin.player.ui.design.formatTime
import io.lanyin.player.ui.design.rememberArchivo
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.theme.ArchivoBlackFamily
import io.lanyin.player.ui.theme.SpaceMonoFamily

private val mono = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold)

/**
 * 酸's portrait stage: the song's own poster, pasted up and taped at the top. Who's presenting
 * and which track of the night; the title huge in lime, the act and the album; the record turning
 * (the cover photocopied onto its label in lime, its name, side and speed set round it), with the
 * tempo and the length beside it and a smiley stuck on; the line being sung on a strip of lime
 * tape across the poster, the next under it; along its foot, what's on next, torn off a sheet,
 * and the barcode. Tap the poster for the lyrics; swipe the record to change song.
 */
@Composable
internal fun PosterPortraitStage(stage: StageContext, onShowLyrics: () -> Unit) {
    Poster(stage, Modifier.fillMaxSize().padding(top = 4.dp).enter(stage.entrance, 1), onShowLyrics, wide = false)
}

/** 酸 on its side: the same poster, the record at the left and the words to its right. */
@Composable
internal fun PosterWideStage(stage: StageContext) {
    Poster(stage, Modifier.fillMaxSize().padding(vertical = 4.dp).enter(stage.entrance, 1), onShowLyrics = null, wide = true)
}

@Composable
private fun Poster(stage: StageContext, modifier: Modifier, onShowLyrics: (() -> Unit)?, wide: Boolean) {
    val state = stage.state
    Box(
        modifier
            .drawBehind {
                drawFlyer(Acid.Black, seed = 18, torn = true)
                drawTapeStrip(Offset(size.width / 2f, 3.dp.toPx()), 64.dp.toPx(), 16.dp.toPx(), -1.5f, seed = 4)
            }
            .then(if (onShowLyrics != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onShowLyrics) else Modifier)
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
    ) {
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Record(stage, Modifier.fillMaxHeight().aspectRatio(1f))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Presents(state)
                    Title(state, shared = false, size = 40f)
                    Live(state)
                    Spacer(Modifier.weight(1f))
                    Facts(state, horizontal = true)
                    LyricTape(stage, Modifier.padding(top = 8.dp))
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Presents(state)
                Title(state, shared = true, size = 52f)
                Live(state)
                Row(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SwipeToSkip(state, Modifier.weight(1f).fillMaxHeight().then(stage.dismissDrag)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Record(stage, Modifier.fillMaxSize())
                        }
                    }
                    Facts(state, horizontal = false, modifier = Modifier.padding(start = 6.dp))
                }
                LyricTape(stage, Modifier.padding(top = 8.dp))
                NextUp(state, Modifier.padding(top = 10.dp))
            }
        }
    }
}

/** The presenter's line with its crosshairs, and the track's number in the night. */
@Composable
private fun Presents(state: PlayerState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) { drawCrosshair(center, size.minDimension / 2f, Acid.Paper) }
        Text("  LANYIN SOUND SYSTEM PRESENTS", style = mono.copy(fontSize = fixedSp(8f), letterSpacing = 0.08.em), color = Acid.Paper, maxLines = 1, modifier = Modifier.weight(1f))
        Text("%02d / %02d".format(state.trackNumber, state.trackCount), style = mono.copy(fontSize = fixedSp(9f)), color = Acid.Lime)
        Canvas(Modifier.padding(start = 6.dp).size(12.dp)) { drawCrosshair(center, size.minDimension / 2f, Acid.Paper) }
    }
}

/** The title, huge in lime: the shorter it is, the bigger. */
@Composable
private fun Title(state: PlayerState, shared: Boolean, size: Float) {
    TrackSwitch(state, Modifier.padding(top = 6.dp).then(if (shared) Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true) else Modifier)) { track ->
        val n = track.title.length
        val sp = when {
            n <= 4 -> size
            n <= 7 -> size * 0.78f
            n <= 12 -> size * 0.6f
            else -> size * 0.48f
        }
        Text(
            track.title,
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = fixedSp(sp), lineHeight = fixedSp(sp * 1.05f), letterSpacing = (-0.02).em),
            color = Acid.Lime, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** LIVE: the act, and the album. */
@Composable
private fun Live(state: PlayerState) {
    TrackSwitch(state, Modifier.padding(top = 2.dp)) { track ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LIVE: ", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(13f)), color = Acid.Paper)
            Text(track.artist, style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixedSp(14f)), color = Acid.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text("  –  《${track.album}》", style = mono.copy(fontSize = fixedSp(9f), fontWeight = FontWeight.Normal), color = Acid.Grey, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The record: black vinyl turning with the music, the cover photocopied in lime on its label,
 * the song's name, side and speed set round it; the smiley stuck on its edge.
 */
@Composable
private fun Record(stage: StageContext, modifier: Modifier) {
    val state = stage.state
    val face = rememberArchivo()
    val bpm = rememberBpm(state)
    val ring = remember(state.title, bpm) { "${state.title.uppercase()} ✱ SIDE A ✱ 33⅓ RPM ✱ LANYIN ✱ ${bpm ?: "--"} BPM ✱ ${state.artist} ✱ " }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val d = minOf(maxWidth, maxHeight)
        Box(Modifier.size(d).graphicsLayer { rotationZ = stage.spin.angle }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(Acid.Lime, r, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawCircle(androidx.compose.ui.graphics.Color(0xFF0B0B0A), r - 3.dp.toPx())
                // Grooves.
                var g = r * 0.62f
                while (g < r - 6.dp.toPx()) {
                    drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f), g, style = androidx.compose.ui.graphics.drawscope.Stroke(0.6f * density))
                    g += 2.5f * density
                }
                drawTextRing(ring, center, r * 0.8f, Acid.Lime, face, r * 0.085f)
            }
            Box(Modifier.size(d * 0.56f).clip(CircleShape)) {
                PhotocopyCover(state.coverUrl, state.coverSeed, Modifier.fillMaxSize(), ink = Acid.Lime, paper = Acid.Black, lines = 30f, positive = true)
            }
            Canvas(Modifier.size(d * 0.07f)) {
                drawCircle(androidx.compose.ui.graphics.Color(0xFF0B0B0A))
            }
        }
        Canvas(Modifier.size(d * 0.3f).align(Alignment.BottomEnd).offset(x = d * 0.02f, y = d * 0.02f)) {
            drawSmiley(center, size.minDimension * 0.42f, Acid.Lime, Acid.Black)
        }
    }
}

/** The tempo, the length and the side, printed as a flyer prints its facts. */
@Composable
private fun Facts(state: PlayerState, horizontal: Boolean, modifier: Modifier = Modifier) {
    val bpm = rememberBpm(state)
    val facts = listOf("BPM" to (bpm?.toString() ?: "--"), "TIME" to formatTime(state.durationMs), "SIDE" to "A")
    if (horizontal) {
        Row(modifier) { facts.forEach { (k, v) -> Fact(k, v, Modifier.padding(end = 18.dp)) } }
    } else {
        Column(modifier) { facts.forEach { (k, v) -> Fact(k, v, Modifier.padding(bottom = 8.dp)) } }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = mono.copy(fontSize = fixedSp(7.5f)), color = Acid.Grey)
        Text(value, style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(22f), lineHeight = fixedSp(24f)), color = Acid.Lime)
    }
}

/**
 * The line being sung on a strip of lime tape stuck across the poster at a slant, an asterisk
 * at its end; the next line under it in grey.
 */
@Composable
private fun LyricTape(stage: StageContext, modifier: Modifier) {
    val state = stage.state
    val index = stage.scrubMs?.let { lyricIndexAt(state.lyrics, it) } ?: state.currentLyricIndex
    val now = state.lyrics.getOrNull(index)?.text ?: emptyLyricsNote(state)
    val next = state.lyrics.getOrNull(index + 1)?.text
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .offset(x = (-6).dp)
                .graphicsLayer { rotationZ = -2f }
                .background(Acid.Lime)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clipToBounds(),
        ) {
            AnimatedContent(
                now,
                transitionSpec = { (fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 4 }) togetherWith fadeOut(tween(120)) },
                label = "tapeLine",
            ) { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        line, style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixedSp(18f * stage.lyricScale), lineHeight = fixedSp(23f * stage.lyricScale)),
                        color = Acid.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Canvas(Modifier.padding(start = 8.dp).size(14.dp)) { drawAsterisk(center, size.minDimension / 2f, Acid.Black) }
                }
            }
        }
        Text(next ?: " ", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = fixedSp(12.5f)), color = Acid.Grey, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
    }
}

/** What's on next, on a scrap torn from a sheet, with the barcode and the poster's number. */
@Composable
private fun NextUp(state: PlayerState, modifier: Modifier) {
    val upcoming = (1..3).mapNotNull { k -> state.queue.getOrNull(state.index + k)?.let { "%02d %s".format(state.index + k + 1, it.title) } }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(
            Modifier
                .weight(1f)
                .graphicsLayer { rotationZ = 0.8f }
                .drawBehind { drawFlyer(Acid.Paper, seed = 63, torn = true) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("NEXT · 接下来", style = mono.copy(fontSize = fixedSp(7.5f)), color = Acid.Black)
            Text(
                if (upcoming.isEmpty()) "就这一首" else upcoming.joinToString(" · "),
                style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixedSp(11.5f)), color = Acid.Black, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.End) {
            Canvas(Modifier.size(56.dp, 22.dp)) { drawBarcode(Acid.Paper, state.trackNumber * 7) }
            Text("LY-%03d-%02d".format(state.trackCount, state.trackNumber), style = mono.copy(fontSize = fixedSp(7f), fontWeight = FontWeight.Normal), color = Acid.Grey)
        }
    }
}
