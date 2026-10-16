package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LyricSize
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.sin

/**
 * 暮: the stage as a postcard from the evening. The artwork is a polaroid hung by a strip of tape,
 * swaying a little, the title written under the photo by hand; a postmark is struck over its
 * corner with the tempo (or, before there is one, the time and date). Beside it the line being
 * sung sits on the horizon in the sunset's colours as it is sung, and the sea holds its
 * reflection, broken into ripples. Above, how long until the sun is down: the song ends as it
 * sets in the sky behind.
 */
@Composable
internal fun PostcardStage(stage: StageContext) {
    val state = stage.state
    Row(Modifier.fillMaxSize()) {
        Polaroid(stage, Modifier.weight(0.36f).fillMaxHeight())
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(0.64f).fillMaxHeight().enter(stage.entrance, 1)) {
            Sunset(state)
            HorizonLyrics(stage, Modifier.weight(1f).fillMaxWidth())
            AnimatedVisibility(stage.chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                StageFooter(state, centered = true)
            }
        }
    }
}

@Composable
internal fun Polaroid(stage: StageContext, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val state = stage.state
    val style = Lanyin.style
    val c = style.colors
    val clock = rememberFrameClock(state.isPlaying)
    // Paper: warm white by day; at night a dim card that doesn't glare.
    val card = if (style.dark) lerp(c.bg, c.content, 0.14f) else Color(0xFFFFFBF4)
    val pen = if (style.dark) c.content else Color(0xFF3B2A24)
    val photo = Brush.verticalGradient(
        listOf(lerp(c.accent, Color.White, 0.55f), lerp(c.accent, c.accentAlt, 0.5f), lerp(c.accentAlt, Color.Black, 0.35f)),
    )
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val margin = 9.dp
        val caption = 44.dp
        val width = minOf(maxWidth * 0.86f, maxHeight * 0.94f + margin - caption)
        val height = width - margin + caption
        Box(
            Modifier
                .size(width, height)
                // Hung from the tape at the top, it sways a little in the evening air.
                .graphicsLayer {
                    rotationZ = -2.5f + 1.2f * sin(clock.floatValue * 0.8f)
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .drawBehind {
                    drawRoundRect(Color.Black.copy(alpha = if (style.dark) 0.3f else 0.12f), Offset(3.dp.toPx(), 6.dp.toPx()), size, CornerRadius(3.dp.toPx()))
                    drawRoundRect(card, cornerRadius = CornerRadius(3.dp.toPx()))
                }
                .drawWithContent {
                    drawContent()
                    // The tape, translucent, over the top edge.
                    rotate(4f, Offset(size.width / 2f, 0f)) {
                        drawRect(c.accent.copy(alpha = 0.32f), Offset(size.width * 0.32f, -9.dp.toPx()), Size(size.width * 0.36f, 18.dp.toPx()))
                    }
                },
        ) {
            Box(
                Modifier.padding(start = margin, end = margin, top = margin).size(width - margin * 2).clipToBounds().background(photo)
                    .drawBehind {
                        // A low sun on the sea in the photo, round the record.
                        val sea = size.height * 0.72f
                        drawCircle(Color.White.copy(alpha = 0.35f), size.width * 0.16f, Offset(size.width * 0.72f, sea))
                        drawRect(lerp(c.accentAlt, Color.Black, 0.25f).copy(alpha = 0.55f), Offset(0f, sea), Size(size.width, size.height - sea))
                    },
            ) {
                StageArtwork(stage, Modifier.fillMaxSize().then(stage.dismissDrag), halo = false, heightFraction = 0.9f, onClick = onClick)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = margin + 2.dp, end = margin, bottom = 5.dp)) {
                Text(state.title, style = TextStyle(fontFamily = style.display, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, fontSize = fixedSp(15f)), color = pen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("— ${state.artist}", style = TextStyle(fontFamily = style.display, fontStyle = FontStyle.Italic, fontSize = fixedSp(11f)), color = pen.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Struck over the top corner, running off the card's edge a little.
            Postmark(state, Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = (-2).dp))
        }
    }
}

/** A round postmark and its wavy cancellation lines, struck at an angle. */
@Composable
private fun Postmark(state: PlayerState, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val measurer = rememberTextMeasurer()
    val bpm = rememberBpm(state)
    val now by rememberMinuteClock()
    val today = remember { LocalDate.now() }
    val ink = c.accentAlt.copy(alpha = if (style.dark) 0.8f else 0.72f)
    val small = TextStyle(fontFamily = style.display, fontWeight = FontWeight.Bold, fontSize = fixedSp(7.5f), letterSpacing = 0.12.em, color = ink)
    val middle = TextStyle(fontFamily = style.numeric, fontWeight = FontWeight.Bold, fontSize = fixedSp(if (bpm != null) 15f else 11f), color = ink)
    val (top, mid, bottom) = if (bpm != null) Triple("澜音", "$bpm", "BPM") else Triple("澜音", "%02d:%02d".format(now.hour, now.minute), "%02d·%02d".format(today.monthValue, today.dayOfMonth))
    Canvas(modifier.size(140.dp, 64.dp)) {
        val center = Offset(size.width - 32.dp.toPx(), size.height / 2f)
        rotate(-12f, center) {
            val r = 25.dp.toPx()
            drawCircle(ink, r, center, style = Stroke(1.6.dp.toPx()))
            drawCircle(ink, r - 4.dp.toPx(), center, style = Stroke(0.8.dp.toPx()))
            val t = measurer.measure(top, small)
            val m = measurer.measure(mid, middle)
            val b = measurer.measure(bottom, small)
            drawText(t, topLeft = Offset(center.x - t.size.width / 2f, center.y - r + 6.dp.toPx()))
            drawText(m, topLeft = Offset(center.x - m.size.width / 2f, center.y - m.size.height / 2f))
            drawText(b, topLeft = Offset(center.x - b.size.width / 2f, center.y + r - 6.dp.toPx() - b.size.height))
            // Cancellation: three waves running off to the left.
            for (k in -1..1) {
                val path = Path()
                val y0 = center.y + k * 9.dp.toPx()
                val end = center.x - r - 4.dp.toPx()
                var x = 0f
                path.moveTo(x, y0)
                while (x < end) {
                    x += 2.dp.toPx()
                    path.lineTo(x, y0 + 2.5.dp.toPx() * sin(x / 7.dp.toPx() * PI.toFloat()))
                }
                drawPath(path, ink.copy(alpha = ink.alpha * 0.8f), style = Stroke(1.2.dp.toPx()))
            }
        }
    }
}

/** How long until the sun is down: a small sun sinking to its line, and the time left in the song. */
@Composable
internal fun Sunset(state: PlayerState) {
    val style = Lanyin.style
    val c = style.colors
    // Recomposes once a second, not every frame.
    val left by remember(state) { derivedStateOf { ((state.durationMs - state.positionMs) / 1000).coerceAtLeast(0) } }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp, 12.dp)) {
            val f = (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
            val sea = size.height * 0.8f
            clipRect(bottom = sea) { drawCircle(c.accent, size.height * 0.4f, Offset(size.width / 2f, sea - size.height * 0.45f * (1f - f) + size.height * 0.1f)) }
            drawLine(c.content.copy(alpha = 0.5f), Offset(0f, sea), Offset(size.width, sea), 1.dp.toPx())
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (state.isPlaying) "距日落  " else "日落暂停  ") + "%d:%02d".format(left / 60, left % 60),
            style = style.type.caption.copy(fontFamily = style.numeric, letterSpacing = 0.08.em),
            color = c.muted,
        )
        Spacer(Modifier.weight(1f))
        Text("第 ${state.trackNumber} / ${state.trackCount} 封", style = style.type.caption, color = c.muted)
    }
}

@Composable
internal fun HorizonLyrics(stage: StageContext, modifier: Modifier) {
    val state = stage.state
    val style = Lanyin.style
    val c = style.colors
    val lines = state.lyrics
    val index = stage.scrubMs?.let { lyricIndexAt(lines, it) } ?: state.currentLyricIndex
    val clock = rememberFrameClock(state.isPlaying)
    val big = lyricTextStyle(LyricSize.BASE_SP * 1.25f, stage.lyricScale).copy(
        fontFamily = style.display, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
        color = c.content.copy(alpha = if (style.dark) 0.4f else 0.36f),
    )
    val sunset = Brush.horizontalGradient(listOf(c.accent, c.accentAlt))
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            lines.getOrNull(index - 1)?.text.orEmpty(),
            style = style.type.caption.copy(textAlign = TextAlign.Center), color = c.muted.copy(alpha = 0.55f),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 10.dp),
        )
        AnimatedContent(
            index,
            transitionSpec = { (fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 5 }) togetherWith fadeOut(tween(300)) },
            label = "horizon",
        ) { i ->
            val line = lines.getOrNull(i)
            val text = line?.text?.takeIf { it.isNotBlank() } ?: if (lines.isEmpty()) emptyLyricsNote(state) else "♪"
            val reflection = big.lineHeight
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SungText(
                    text, big,
                    progress = {
                        when {
                            lines.isEmpty() -> 1f
                            stage.scrubMs != null || i != state.currentLyricIndex -> if (i < state.currentLyricIndex) 1f else 0f
                            else -> state.lineProgress()
                        }
                    },
                    paintSung = { drawText(it, sunset) },
                    modifier = Modifier.padding(bottom = with(androidx.compose.ui.platform.LocalDensity.current) { reflection.toDp() } * 0.62f),
                    decorate = { l ->
                        // The horizon the line stands on, and the line again upside down in the sea
                        // beneath it: flattened, as water foreshortens it, in slices that drift
                        // apart and fade with depth.
                        val t = clock.floatValue
                        val horizon = l.size.height + 2.dp.toPx()
                        drawLine(
                            Brush.horizontalGradient(listOf(Color.Transparent, c.accent.copy(alpha = 0.45f), Color.Transparent), -size.width * 0.1f, size.width * 1.1f),
                            Offset(-size.width * 0.1f, horizon), Offset(size.width * 1.1f, horizon), 1.dp.toPx(),
                        )
                        val squash = 0.5f
                        val depth = minOf(l.size.height.toFloat(), reflection.toPx() * 1.2f) * squash
                        val slices = 6
                        for (s in 0 until slices) {
                            val y0 = horizon + 2.dp.toPx() + depth * s / slices
                            val y1 = horizon + 2.dp.toPx() + depth * (s + 1) / slices
                            val drift = sin(t * 1.6f + s * 1.4f) * (1.2f + 3f * s / slices) * density
                            clipRect(left = -size.width, top = y0, right = size.width * 2f, bottom = y1) {
                                translate(drift, 0f) {
                                    scale(1f, -squash, Offset(0f, horizon)) {
                                        drawText(l, c.accent.copy(alpha = 0.2f * (1f - s / slices.toFloat())))
                                    }
                                }
                            }
                        }
                    },
                )
                line?.translation?.let {
                    Text(it, style = style.type.body.copy(textAlign = TextAlign.Center), color = c.content.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        lines.getOrNull(index + 1)?.text?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = style.type.caption.copy(textAlign = TextAlign.Center), color = c.muted.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
