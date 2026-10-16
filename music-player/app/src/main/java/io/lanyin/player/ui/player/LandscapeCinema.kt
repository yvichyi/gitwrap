package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.design.hash01
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.theme.FilmStamp
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LyricSize
import kotlin.math.sin

private val FilmBase = Color(0xFF0B0A09)
private val Holes = Color(0xFFE9E1D3)

/**
 * 影: the stage as a screening. On the left a strip of film runs down through the gate, its
 * sprocket holes and frame lines sliding past while it plays, the artwork held in the gate; on
 * the right the screen, lit by the projector's beam with dust drifting in it, the line being sung
 * set as a subtitle, the reel and scene in the corner and the timecode running beside them.
 */
@Composable
internal fun CinemaStage(stage: StageContext) {
    Row(Modifier.fillMaxSize()) {
        FilmGate(stage, Modifier.weight(0.36f).fillMaxHeight())
        Spacer(Modifier.width(20.dp))
        CinemaScreen(stage, Modifier.weight(0.64f).fillMaxHeight().enter(stage.entrance, 1))
    }
}

@Composable
private fun FilmGate(stage: StageContext, modifier: Modifier) {
    val state = stage.state
    val clock = rememberFrameClock(state.isPlaying)
    // How far the film has run, and the clock at the last frame.
    val run = remember { floatArrayOf(0f, -1f) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val band = 18.dp
        val window = minOf(maxWidth - band * 2, maxHeight * 0.86f)
        Canvas(Modifier.size(window + band * 2, maxHeight)) {
            val t = clock.floatValue
            if (run[1] >= 0f && t > run[1] && state.isPlaying) run[0] += (t - run[1]) * 46.dp.toPx()
            run[1] = t
            drawRect(FilmBase)
            val b = band.toPx()
            val hole = Size(7.dp.toPx(), 5.dp.toPx())
            val pitch = 14.dp.toPx()
            val offset = run[0] % pitch
            var y = -pitch + offset
            while (y < size.height + pitch) {
                drawRoundRect(Holes, Offset((b - hole.width) / 2f, y), hole, CornerRadius(1.5.dp.toPx()))
                drawRoundRect(Holes, Offset(size.width - (b + hole.width) / 2f, y), hole, CornerRadius(1.5.dp.toPx()))
                y += pitch
            }
            // Frame lines between the frames, a frame's height apart, running with the holes.
            val frame = window.toPx() + 6.dp.toPx()
            var fy = -frame + run[0] % frame
            while (fy < size.height + frame) {
                drawRect(Color.Black, Offset(b, fy), Size(size.width - b * 2, 3.dp.toPx()))
                // The edge code beside each frame, in the camera's orange.
                drawRect(FilmStamp.copy(alpha = 0.8f), Offset(2.dp.toPx(), fy + frame * 0.3f), Size(2.dp.toPx(), 10.dp.toPx()))
                drawRect(FilmStamp.copy(alpha = 0.8f), Offset(2.dp.toPx(), fy + frame * 0.3f + 13.dp.toPx()), Size(2.dp.toPx(), 4.dp.toPx()))
                fy += frame
            }
            // The gate: the frame being projected, lit from behind.
            val gate = Size(size.width - b * 2, window.toPx())
            val top = (size.height - gate.height) / 2f
            drawRect(Brush.radialGradient(listOf(Color(0x33FFE6C0), Color.Transparent), Offset(size.width / 2f, size.height / 2f), gate.width * 0.8f), Offset(b, top), gate)
        }
        StageArtwork(stage, Modifier.size(window, window).then(stage.dismissDrag), halo = false, heightFraction = 0.94f)
    }
}

@Composable
private fun CinemaScreen(stage: StageContext, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val state = stage.state
    val clock = rememberFrameClock(state.isPlaying)
    val measurer = rememberTextMeasurer()
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 0.12.em)
    val lines = state.lyrics
    val index = stage.scrubMs?.let { lyricIndexAt(lines, it) } ?: state.currentLyricIndex
    val bpm = rememberBpm(state)
    val stamp = mono.copy(fontSize = 10.sp, color = FilmStamp)
    // The reel line changes with the scene; the timecode 24 times a second, so its figures are
    // measured once and set one by one.
    val reel = remember(state.trackNumber, index, bpm, stamp) {
        measurer.measure("REEL ${state.trackNumber} · SCENE ${(index + 1).coerceAtLeast(1)}" + (bpm?.let { " · BPM $it" } ?: ""), stamp)
    }
    val figures = remember(stamp) { "0123456789:".associateWith { measurer.measure(it.toString(), stamp) } }
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val t = clock.floatValue
            // The screen, a little lighter than the dark room, brighter at its middle, flickering faintly.
            val flicker = if (state.isPlaying) 0.012f * sin(t * 47f) + 0.008f * sin(t * 113f) else 0f
            drawRoundRect(Color.White.copy(alpha = 0.035f + flicker), cornerRadius = CornerRadius(4.dp.toPx()))
            drawRect(Brush.radialGradient(listOf(Color(0x24FFE8C8), Color.Transparent), Offset(size.width * 0.5f, size.height * 0.45f), size.maxDimension * 0.6f))
            // The beam: a soft wedge of light from the upper left, dust turning in it.
            val beam = Path().apply {
                moveTo(-size.width * 0.04f, size.height * 0.08f)
                lineTo(size.width * 0.9f, -size.height * 0.05f)
                lineTo(size.width * 0.9f, size.height * 0.55f)
                close()
            }
            drawPath(beam, Brush.linearGradient(listOf(Color(0x1AFFE6C0), Color.Transparent), Offset.Zero, Offset(size.width, size.height * 0.3f)))
            for (k in 0 until 22) {
                val u = (hash01(k, 3) + t * (0.006f + 0.01f * hash01(k, 4))) % 1f
                val v = 0.04f + 0.42f * hash01(k, 5) * u + 0.04f * sin(t * (0.7f + hash01(k, 6)) + k)
                val twinkle = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * (2f + 3f * hash01(k, 7)) + k * 1.3f))
                drawCircle(Color(0xFFFFEBD0).copy(alpha = 0.35f * twinkle), (0.8f + 1.4f * hash01(k, 8)) * density, Offset(size.width * u, size.height * v))
            }
            // Reel and scene at the top left, the running timecode at the top right.
            drawText(reel, topLeft = Offset(14.dp.toPx(), 12.dp.toPx()))
            // HH:MM:SS:FF, set from the right.
            val ms = state.positionMs
            val fields = longArrayOf(ms / 3_600_000, ms / 60_000 % 60, ms / 1_000 % 60, (ms % 1_000) * 24 / 1_000)
            val zero = figures.getValue('0')
            val colon = figures.getValue(':')
            val width = zero.size.width * 8f + colon.size.width * 3f
            var x = size.width - width - 14.dp.toPx()
            val y = 12.dp.toPx()
            fields.forEachIndexed { i, v ->
                if (i > 0) {
                    drawText(colon, topLeft = Offset(x, y))
                    x += colon.size.width
                }
                for (d in longArrayOf(v / 10 % 10, v % 10)) {
                    drawText(figures.getValue('0' + d.toInt()), topLeft = Offset(x, y))
                    x += zero.size.width
                }
            }
            drawCircle(if (state.isPlaying) Color(0xFFE5484D) else Color(0xFF5A2A2A), 3.dp.toPx(), Offset(size.width - width - 22.dp.toPx(), y + zero.size.height / 2f))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            val subtitle = lyricTextStyle(LyricSize.BASE_SP * 1.08f, stage.lyricScale).copy(
                fontFamily = style.display, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 6f),
            )
            AnimatedContent(
                index,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
                label = "subtitle",
            ) { i ->
                val line = lines.getOrNull(i)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        line?.text?.takeIf { it.isNotBlank() } ?: if (lines.isEmpty()) emptyLyricsNote(state) else "♪",
                        style = subtitle,
                        color = c.content,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    line?.translation?.let {
                        Text(it, style = style.type.body.copy(textAlign = TextAlign.Center, shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 1f), 4f)), color = c.content.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            lines.getOrNull(index + 1)?.text?.let {
                Text(it, style = style.type.caption.copy(textAlign = TextAlign.Center), color = c.muted.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp))
            }
            Spacer(Modifier.weight(0.6f))
            if (stage.chromeVisible) StageFooter(state, centered = true) else Spacer(Modifier.height(4.dp))
        }
    }
}

/** What to show in place of lyrics when there are none (or they haven't come yet). */
internal fun emptyLyricsNote(state: PlayerState): String = when {
    state.lyricsLoading -> "· · ·"
    state.lyricsGap == LyricsGap.Instrumental -> "纯音乐，请欣赏"
    state.lyricsGap == LyricsGap.Missing -> "暂无歌词"
    else -> "歌词没能加载"
}
