package io.lanyin.player.ui.player

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.core.audio.pulseAfter
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.theme.Lanyin
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 点: the stage in the manner of Nothing's phones. Round the record, the light strips of their
 * Glyph backs (an arc that lights with the bass, one with the middle, a short bar with the
 * treble, all flashing on the beat, and the red recording dot); beside it the line being sung,
 * spelled out on a dot-matrix display that fills from left to right as it is sung, the line
 * before above it and the lines to come below.
 */
@Composable
internal fun GlyphStage(stage: StageContext) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.42f).fillMaxHeight()) {
            GlyphLights(stage.state, Modifier.matchParentSize())
            StageArtwork(stage, Modifier.fillMaxSize().then(stage.dismissDrag), heightFraction = 0.72f)
            GlyphTempo(stage.state, Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(4.dp))
        }
        Spacer(Modifier.width(30.dp))
        DotMatrixLyrics(stage, Modifier.weight(0.58f).fillMaxHeight().enter(stage.entrance, 1))
    }
}

/** The tempo in dots, 「96 BPM」, or 「PAUSED」 while stopped. */
@Composable
internal fun GlyphTempo(state: PlayerState, modifier: Modifier = Modifier) {
    val bpm = rememberBpm(state)
    val text = when {
        !state.isPlaying -> "PAUSED"
        bpm != null -> "$bpm BPM"
        else -> return
    }
    Text(
        text,
        style = androidx.compose.ui.text.TextStyle(fontFamily = io.lanyin.player.ui.theme.DotoFamily, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = fixedSp(14f)),
        color = Lanyin.colors.muted,
        modifier = modifier,
    )
}

/** The Glyph strips round the artwork, lit by the music. */
@Composable
internal fun GlyphLights(state: PlayerState, modifier: Modifier) {
    val c = Lanyin.colors
    val clock = rememberFrameClock(state.isPlaying)
    val levels = remember { FloatArray(32) }
    val eased = remember { FloatArray(3) }
    Canvas(modifier) {
        clock.floatValue
        val live = state.isPlaying && state.audioFeed.levels(levels)
        val ranges = listOf(0 until 6, 6 until 16, 16 until 30)
        val pulse = if (state.isPlaying) pulseAfter(state.audioFeed.sinceBeat(), state.audioFeed.beatPeriod(), 0.35f) else 0f
        for (k in 0..2) {
            var v = 0f
            if (live) for (i in ranges[k]) v += levels[i] / ranges[k].count()
            eased[k] += (v - eased[k]) * if (v > eased[k]) 0.6f else 0.15f
        }
        // The same geometry as the artwork: a record fitted to the box, with room for its halo.
        val art = minOf(size.width - 40.dp.toPx(), size.height * 0.72f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val ring = art / 2f + 30.dp.toPx()
        val stroke = 5.dp.toPx()
        fun lit(level: Float) = (0.25f + 0.75f * maxOf(level * 1.4f, pulse * 0.85f)).coerceIn(0f, 1f)
        glyphArc(center, ring, -78f, 84f, stroke, lit(eased[0]), c.content)
        glyphArc(center, ring, 102f, 76f, stroke, lit(eased[1]), c.content)
        // A short bar off the lower right, for the treble.
        val a = 45f / 180f * PI.toFloat()
        val from = center + Offset(cos(a), sin(a)) * (ring + 10.dp.toPx())
        val to = center + Offset(cos(a), sin(a)) * (ring + 34.dp.toPx())
        glyphLine(from, to, stroke, lit(eased[2]), c.content)
        // The red dot, upper left, lit while recording (playing), pulsing on the beat.
        val b = -135f / 180f * PI.toFloat()
        val dot = center + Offset(cos(b), sin(b)) * ring
        if (state.isPlaying) drawCircle(c.accentAlt.copy(alpha = 0.25f * (0.5f + pulse)), 9.dp.toPx(), dot)
        drawCircle(if (state.isPlaying) c.accentAlt else c.accentAlt.copy(alpha = 0.35f), 4.dp.toPx(), dot)
    }
}

private operator fun Offset.times(k: Float) = Offset(x * k, y * k)

/** A Glyph strip along an arc: dark when off, white with a soft bloom as it lights. */
private fun DrawScope.glyphArc(center: Offset, radius: Float, start: Float, sweep: Float, stroke: Float, light: Float, white: androidx.compose.ui.graphics.Color) {
    val topLeft = center - Offset(radius, radius)
    val box = Size(radius * 2, radius * 2)
    drawArc(white.copy(alpha = 0.1f), start, sweep, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
    drawArc(white.copy(alpha = 0.16f * light), start, sweep, false, topLeft, box, style = Stroke(stroke * 3.2f, cap = StrokeCap.Round))
    drawArc(white.copy(alpha = light), start, sweep, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
}

private fun DrawScope.glyphLine(from: Offset, to: Offset, stroke: Float, light: Float, white: androidx.compose.ui.graphics.Color) {
    drawLine(white.copy(alpha = 0.1f), from, to, stroke, StrokeCap.Round)
    drawLine(white.copy(alpha = 0.16f * light), from, to, stroke * 3.2f, StrokeCap.Round)
    drawLine(white.copy(alpha = light), from, to, stroke, StrokeCap.Round)
}

/** A line of text as a dot matrix: [cols] × [rows] cells, each lit 0..1. */
internal class DotText(val cols: Int, val rows: Int, val cells: FloatArray) {
    fun at(col: Int, row: Int): Float = if (col in 0 until cols) cells[row * cols + col] else 0f
}

/** Renders [text] in bold type [rows] dots tall and reads off how much ink falls in each dot. */
internal fun dotText(text: String, rows: Int): DotText {
    val cell = 8
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = rows * cell * 0.84f
        color = android.graphics.Color.BLACK
    }
    val metrics = paint.fontMetrics
    val width = (ceil(paint.measureText(text)).toInt() + cell).coerceIn(cell, 8_000)
    val height = rows * cell
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val baseline = (height - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
    android.graphics.Canvas(bitmap).drawText(text, cell / 2f, baseline, paint)
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    bitmap.recycle()
    val cols = width / cell
    val cells = FloatArray(cols * rows)
    for (r in 0 until rows) for (col in 0 until cols) {
        var sum = 0
        for (y in r * cell until (r + 1) * cell) for (x in col * cell until (col + 1) * cell) sum += pixels[y * width + x] ushr 24
        cells[r * cols + col] = sum / (cell * cell * 255f)
    }
    return DotText(cols, rows, cells)
}

/** The line being sung on a dot-matrix display, with the line before above and the next two below. */
@Composable
internal fun DotMatrixLyrics(stage: StageContext, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val state = stage.state
    val lines = state.lyrics
    val index = stage.scrubMs?.let { lyricIndexAt(lines, it) } ?: state.currentLyricIndex
    val current = lines.getOrNull(index)
    val shown = current?.text?.takeIf { it.isNotBlank() } ?: when {
        lines.isEmpty() && state.lyricsLoading -> "· · ·"
        lines.isEmpty() -> state.title
        else -> "♪"
    }
    // On a short stage (a small phone, large text) the display shrinks and only one line follows.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val roomy = maxHeight > 270.dp
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Text(
                lines.getOrNull(index - 1)?.text.orEmpty(),
                style = style.type.body, color = c.muted.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            DotMatrixLine(shown, state, sweep = current != null && current.text.isNotBlank(), Modifier.fillMaxWidth().height(if (roomy) 92.dp else 70.dp))
            val note = current?.translation ?: when {
                lines.isEmpty() && !state.lyricsLoading -> when (state.lyricsGap) {
                    LyricsGap.Instrumental -> "纯音乐，请欣赏"
                    LyricsGap.Missing -> "暂无歌词"
                    LyricsGap.Failed -> "歌词没能加载"
                }
                else -> null
            }
            if (note != null) Text(note, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            for (k in 1..(if (roomy) 2 else 1)) {
                val next = lines.getOrNull(index + k)?.text ?: break
                Text(
                    next,
                    style = if (k == 1) style.type.body else style.type.caption,
                    color = c.content.copy(alpha = if (k == 1) 0.55f else 0.3f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (k == 1) 10.dp else 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (stage.chromeVisible) StageFooter(state, centered = false)
        }
    }
}

/**
 * [text] on the display: dots lit white up to how far the line has been sung (grey beyond), the
 * unlit dots faintly there. A new line is spelled in from the left; a line too long for the
 * display pans to keep the sung part in view.
 */
@Composable
private fun DotMatrixLine(text: String, state: PlayerState, sweep: Boolean, modifier: Modifier) {
    val c = Lanyin.colors
    val rows = 16
    val dots by produceState<DotText?>(null, text) { value = withContext(Dispatchers.Default) { dotText(text, rows) } }
    val reveal = remember(text) { Animatable(0f) }
    LaunchedEffect(text) { reveal.animateTo(1f, tween(380, easing = LinearEasing)) }
    val pan = remember { floatArrayOf(0f) }
    val clock = rememberFrameClock(state.isPlaying)
    Canvas(modifier.clip(RoundedCornerShape(16.dp)).background(c.surface)) {
        clock.floatValue
        val d = dots ?: return@Canvas
        val pad = 10.dp.toPx()
        val pitch = (size.height - pad * 2) / rows
        val visible = ((size.width - pad * 2) / pitch).toInt()
        val sung = if (sweep) state.lineProgress().coerceIn(0f, 1f) else 1f
        // Where the window onto the text sits: centred when it fits, following the singing when not.
        val target = if (d.cols <= visible) -(visible - d.cols) / 2f else (sung * d.cols - visible * 0.55f).coerceIn(0f, (d.cols - visible).toFloat())
        pan[0] += (target - pan[0]) * 0.12f
        val first = pan[0].toInt()
        val shift = (pan[0] - first) * pitch
        val r = pitch * 0.36f
        val shown = reveal.value * d.cols
        for (row in 0 until rows) {
            for (k in -1..visible) {
                val col = first + k
                val x = pad + (k + 0.5f) * pitch - shift
                if (x < pad - pitch * 0.5f || x > size.width - pad + pitch * 0.5f) continue
                val y = pad + (row + 0.5f) * pitch
                val ink = if (col.toFloat() <= shown) d.at(col, row) else 0f
                if (ink > 0.32f) {
                    val done = col < sung * d.cols
                    drawCircle(if (done) c.content else c.content.copy(alpha = 0.38f), r, Offset(x, y))
                } else {
                    drawCircle(c.content.copy(alpha = 0.07f), r * 0.8f, Offset(x, y))
                }
            }
        }
    }
}
