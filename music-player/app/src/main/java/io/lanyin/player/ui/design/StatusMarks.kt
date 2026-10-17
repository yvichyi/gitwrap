package io.lanyin.player.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.FilmStamp
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import io.lanyin.player.ui.theme.SpaceMonoFamily
import io.lanyin.player.ui.theme.TiltNeonFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** What a placeholder is saying: something is on its way, something went wrong, or there's nothing here. */
enum class StatusKind { Loading, Failed, Empty }

@Composable
private fun fixed(size: Float): TextUnit = with(LocalDensity.current) { size.dp.toSp() }

/**
 * The mark over a placeholder's words, in the theme's medium. Loading moves: an ink drop spreading
 * (墨), a tube lighting round (霓), the sun bobbing on the horizon (暮), a yellow block stepping
 * along (格), a countdown leader (影), a dot running round (点), an LED meter sweeping (机), lines
 * being printed (报), NOW LOADING (游), a disc spinning (酸). Failed is the theme's way of saying
 * so: a broken circle, a CLOSED sign, a returned stamp, a struck box, NO SIGNAL, a dot-matrix !,
 * ERR on the display, 停刊, GAME OVER, a cracked bead. Empty keeps the theme's quiet motif.
 */
@Composable
fun StatusMark(kind: StatusKind, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    when (kind) {
        StatusKind.Empty -> ThemeMotif(c.content.copy(alpha = 0.18f), modifier.size(56.dp))
        StatusKind.Loading -> {
            val clock by rememberFrameClock(true)
            Canvas(modifier.size(64.dp)) { drawLoading(style, clock) }
        }
        StatusKind.Failed -> FailedMark(style, modifier)
    }
}

private fun DrawScope.drawLoading(style: LanyinStyle, t: Float) {
    val c = style.colors
    val w = size.width
    val h = size.height
    val mid = Offset(w / 2f, h / 2f)
    when (style.backdrop) {
        BackdropKind.Ink -> {
            // A drop of ink lands and spreads into the paper, then another.
            val p = (t / 1.6f) % 1f
            drawCircle(c.content.copy(alpha = 0.22f * (1f - p)), w * (0.12f + 0.32f * p), mid)
            drawCircle(c.content.copy(alpha = 0.7f * (1f - p)), w * (0.1f + 0.24f * p), mid, style = Stroke(1.6.dp.toPx() * (1.5f - p)))
            drawCircle(c.content.copy(alpha = 0.85f), w * 0.08f * (1f - 0.4f * p), mid)
        }
        BackdropKind.Neon -> {
            // Eight short tubes in a ring, lit one after another, the newest brightest.
            val head = (t * 8f) % 8f
            for (k in 0 until 8) {
                val a = -PI / 2 + k * PI / 4
                val age = ((head - k) + 8f) % 8f
                val on = (1f - age / 4f).coerceIn(0.12f, 1f)
                val s = Offset(mid.x + cos(a).toFloat() * w * 0.22f, mid.y + sin(a).toFloat() * h * 0.22f)
                val e = Offset(mid.x + cos(a).toFloat() * w * 0.38f, mid.y + sin(a).toFloat() * h * 0.38f)
                drawLine(c.accent.copy(alpha = on * 0.35f), s, e, 7.dp.toPx(), StrokeCap.Round)
                drawLine(lerp(c.accent, Color.White, 0.4f * on).copy(alpha = on), s, e, 2.5.dp.toPx(), StrokeCap.Round)
            }
        }
        BackdropKind.Dusk -> {
            // The sun bobbing on the horizon, its reflection under it.
            val y = h * 0.56f
            val bob = sin(t * 2.2f) * h * 0.08f
            val sun = Offset(mid.x, y - h * 0.08f + bob)
            // Only what's above the sea shows.
            clipRect(bottom = y) {
                drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFC08A), Color(0xFFE07A50)), sun.y - w * 0.16f, sun.y + w * 0.16f), w * 0.16f, sun)
            }
            drawLine(c.content.copy(alpha = 0.5f), Offset(w * 0.12f, y), Offset(w * 0.88f, y), 1.dp.toPx())
            for (k in 0 until 3) {
                val ry = y + (k + 1) * h * 0.07f
                val half = w * (0.12f - k * 0.03f) * (1f - bob / h)
                drawLine(Color(0xFFE07A50).copy(alpha = 0.6f - k * 0.15f), Offset(mid.x - half, ry), Offset(mid.x + half, ry), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
        BackdropKind.Grid -> {
            // Three boxes; the yellow one steps along, hard, a step at a time.
            val step = floor(t * 3f).toInt() % 3
            val box = w * 0.22f
            val gap = w * 0.06f
            val x0 = (w - box * 3 - gap * 2) / 2f
            val stroke = 2.dp.toPx()
            for (k in 0 until 3) {
                val tl = Offset(x0 + k * (box + gap), (h - box) / 2f)
                drawRect(c.outline, tl + Offset(3.dp.toPx(), 3.dp.toPx()), Size(box, box))
                drawRect(if (k == step) GridYellow else c.surface, tl, Size(box, box))
                drawRect(c.outline, tl, Size(box, box), style = Stroke(stroke))
            }
        }
        BackdropKind.Film -> {
            // The countdown leader: rings, a cross, the sweep, and the number.
            val r = w * 0.42f
            drawCircle(Color(0xFF2A2622), r, mid)
            val sweep = (t % 1f) * 360f
            drawArc(Color(0xFF4A433C), -90f, sweep, true, mid - Offset(r, r), Size(r * 2, r * 2))
            drawCircle(Color(0xFFEDE6DA).copy(alpha = 0.8f), r * 0.82f, mid, style = Stroke(1.dp.toPx()))
            drawCircle(Color(0xFFEDE6DA).copy(alpha = 0.8f), r * 0.62f, mid, style = Stroke(1.dp.toPx()))
            drawLine(Color(0xFFEDE6DA).copy(alpha = 0.6f), Offset(mid.x - r, mid.y), Offset(mid.x + r, mid.y), 1.dp.toPx())
            drawLine(Color(0xFFEDE6DA).copy(alpha = 0.6f), Offset(mid.x, mid.y - r), Offset(mid.x, mid.y + r), 1.dp.toPx())
            val n = 3 - (floor(t).toInt() % 3)
            drawLeaderNumber(n, mid, r * 0.42f)
        }
        BackdropKind.Dot -> {
            // Twelve dots in a ring; the red one runs round, those behind it fading.
            val head = (t * 12f) % 12f
            for (k in 0 until 12) {
                val a = -PI / 2 + k * PI / 6
                val p = Offset(mid.x + cos(a).toFloat() * w * 0.32f, mid.y + sin(a).toFloat() * h * 0.32f)
                val age = ((head - k) + 12f) % 12f
                if (age < 1f) drawCircle(c.accentAlt, 3.2.dp.toPx(), p) else drawCircle(c.content.copy(alpha = (0.9f - age / 12f).coerceAtLeast(0.15f)), 2.2.dp.toPx(), p)
            }
        }
        BackdropKind.Device -> {
            // An LED bar meter in its well; the lit stretch sweeps up and back like a level.
            val n = 10
            val well = Rect(Offset(w * 0.08f, h * 0.38f), Size(w * 0.84f, h * 0.24f))
            drawRoundRect(Color(0xFF1C1C1F), well.topLeft, well.size, androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            val lit = ((sin(t * 3f) + 1f) / 2f) * n
            val seg = (well.width - 6.dp.toPx()) / n
            for (k in 0 until n) {
                val on = k < lit
                val color = if (k >= n - 2) c.accent else Color(0xFFB8E986)
                drawRect(if (on) color else color.copy(alpha = 0.16f), Offset(well.left + 3.dp.toPx() + k * seg, well.top + 3.dp.toPx()), Size(seg - 2.dp.toPx(), well.height - 6.dp.toPx()))
            }
        }
        BackdropKind.Paper -> {
            // Lines of type coming off the press, left to right, a line at a time; the roller over them.
            val p = (t / 2.4f) % 1f
            val rows = 4
            val lineH = h * 0.1f
            val top = h * 0.24f
            for (k in 0 until rows) {
                val start = k / rows.toFloat()
                val f = ((p - start) * rows).coerceIn(0f, 1f)
                val full = w * (if (k == rows - 1) 0.5f else 0.78f)
                if (f > 0f) drawRect(c.content.copy(alpha = 0.75f), Offset(w * 0.11f, top + k * lineH * 1.6f), Size(full * f, lineH * 0.55f))
            }
            val ry = top + (p * rows).coerceAtMost(rows - 0.01f).toInt() * lineH * 1.6f + lineH * 0.27f
            drawLine(c.accent, Offset(w * 0.06f, ry), Offset(w * 0.94f, ry), 1.5.dp.toPx())
        }
        BackdropKind.Game -> {
            // A health bar filling a block at a time, the newest block in magenta.
            val n = 8
            val frame = Rect(Offset(w * 0.06f, h * 0.42f), Size(w * 0.88f, h * 0.16f))
            drawRect(c.content, frame.topLeft, frame.size, style = Stroke(2.dp.toPx()))
            val filled = floor(t * 6f).toInt() % (n + 1)
            val seg = (frame.width - 6.dp.toPx()) / n
            for (k in 0 until filled) {
                drawRect(if (k == filled - 1) c.accent else c.content, Offset(frame.left + 3.dp.toPx() + k * seg, frame.top + 3.dp.toPx()), Size(seg - 2.dp.toPx(), frame.height - 6.dp.toPx()))
            }
        }
        BackdropKind.Acid -> {
            // A disc spinning, its rainbow catching the light as it turns.
            val r = w * 0.4f
            drawCircle(Color(0xFFD7D9E0), r, mid)
            rotate(t * 220f, mid) {
                drawCircle(Brush.sweepGradient(listOf(Color(0x00FFFFFF), Color(0x99FF4F7B), Color(0x998B5CF6), Color(0x9900A9C9), Color(0x99C6FF00), Color(0x00FFFFFF)), mid), r, mid)
            }
            drawCircle(ChromeBrush, r - 1.dp.toPx(), mid, style = Stroke(1.5.dp.toPx()))
            drawCircle(c.bg, r * 0.14f, mid)
        }
    }
}

/** A leader's countdown figure, drawn as strokes so it needs no font. */
private fun DrawScope.drawLeaderNumber(n: Int, at: Offset, size: Float) {
    val ink = Color(0xFFEDE6DA)
    val s = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
    val l = at.x - size * 0.5f
    val r = at.x + size * 0.5f
    val top = at.y - size
    val bottom = at.y + size
    val path = androidx.compose.ui.graphics.Path()
    when (n) {
        3 -> {
            path.moveTo(l, top); path.lineTo(r, top); path.lineTo(at.x, at.y - size * 0.1f)
            path.quadraticTo(r + size * 0.2f, at.y + size * 0.1f, r, at.y + size * 0.6f)
            path.quadraticTo(at.x, bottom + size * 0.2f, l, at.y + size * 0.7f)
        }
        2 -> {
            path.moveTo(l, top + size * 0.4f)
            path.quadraticTo(at.x, top - size * 0.3f, r, top + size * 0.4f)
            path.lineTo(l, bottom); path.lineTo(r, bottom)
        }
        else -> {
            path.moveTo(at.x - size * 0.3f, top + size * 0.3f); path.lineTo(at.x, top); path.lineTo(at.x, bottom)
        }
    }
    drawPath(path, ink, style = s)
}

@Composable
private fun FailedMark(style: LanyinStyle, modifier: Modifier) {
    val c = style.colors
    when (style.backdrop) {
        BackdropKind.Ink -> Canvas(modifier.size(56.dp)) {
            // The circle of the brush, broken off before it closed.
            drawArc(c.content.copy(alpha = 0.7f), 120f, 250f, false, Offset(size.width * 0.12f, size.height * 0.12f), Size(size.width * 0.76f, size.height * 0.76f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(c.accentAlt, 3.dp.toPx(), Offset(size.width * 0.78f, size.height * 0.3f))
        }
        BackdropKind.Neon -> NeonText("CLOSED", TextStyle(fontFamily = TiltNeonFamily, fontSize = fixed(22f), letterSpacing = 0.1.em), c.accentAlt.copy(alpha = 0.6f), modifier, ignite = false)
        BackdropKind.Dusk -> Box(modifier.rotate(-8f).border(2.dp, io.lanyin.player.ui.design.Airmail.Red, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text("RETURN TO SENDER", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(10f), letterSpacing = 0.08.em), color = io.lanyin.player.ui.design.Airmail.Red)
        }
        BackdropKind.Grid -> Canvas(modifier.size(48.dp)) {
            val stroke = 2.5.dp.toPx()
            drawRect(c.outline, Offset(4.dp.toPx(), 4.dp.toPx()), size)
            drawRect(c.accent)
            drawRect(c.outline, style = Stroke(stroke))
            drawLine(c.surface, Offset(size.width * 0.28f, size.height * 0.28f), Offset(size.width * 0.72f, size.height * 0.72f), 4.dp.toPx())
            drawLine(c.surface, Offset(size.width * 0.72f, size.height * 0.28f), Offset(size.width * 0.28f, size.height * 0.72f), 4.dp.toPx())
        }
        BackdropKind.Film -> Box(modifier.size(84.dp, 52.dp), contentAlignment = Alignment.Center) {
            // The colour bars of a test card, NO SIGNAL across them.
            Canvas(Modifier.matchParentSize()) {
                val bars = listOf(0xFFC0C0C0, 0xFFC0C000, 0xFF00C0C0, 0xFF00C000, 0xFFC000C0, 0xFFC00000, 0xFF0000C0)
                val bw = size.width / bars.size
                bars.forEachIndexed { i, v -> drawRect(Color(v).copy(alpha = 0.75f), Offset(i * bw, 0f), Size(bw + 1f, size.height)) }
            }
            Text("NO SIGNAL", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(10f)), color = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 4.dp))
        }
        BackdropKind.Dot -> Canvas(modifier.size(40.dp, 56.dp)) {
            // ! in the matrix's dots, the point in red.
            val pitch = size.width / 5f
            for (row in 0 until 5) drawCircle(c.content, pitch * 0.38f, Offset(size.width / 2f, pitch * (row + 0.5f)))
            drawCircle(c.accentAlt, pitch * 0.42f, Offset(size.width / 2f, pitch * 6.5f))
        }
        BackdropKind.Device -> Box(modifier.background(c.bgDeep, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("ERR · 404", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(14f), letterSpacing = 0.1.em), color = c.accent)
        }
        BackdropKind.Paper -> Box(modifier.rotate(-4f).border(2.dp, c.accent).padding(horizontal = 12.dp, vertical = 3.dp)) {
            Text("停 刊", style = TextStyle(fontFamily = style.display, fontWeight = FontWeight.Black, fontSize = fixed(20f)), color = c.accent)
        }
        BackdropKind.Game -> Box(modifier.gameWindow(c.surface, c.content).padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text("GAME OVER", style = TextStyle(fontFamily = style.display, fontSize = fixed(14f)), color = c.content)
        }
        BackdropKind.Acid -> Canvas(modifier.size(52.dp)) {
            // A gel bead with a crack across it.
            drawGelBead(Flavor.Tangerine)
            val crack = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.3f, size.height * 0.2f); lineTo(size.width * 0.48f, size.height * 0.45f)
                lineTo(size.width * 0.4f, size.height * 0.55f); lineTo(size.width * 0.62f, size.height * 0.82f)
            }
            drawPath(crack, Color.White.copy(alpha = 0.9f), style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** Which kind a placeholder's words are: on its way (正在…), failed (with a way to try again), or empty. */
internal fun statusKindOf(text: String, hasAction: Boolean): StatusKind = when {
    text.startsWith("正在") -> StatusKind.Loading
    hasAction -> StatusKind.Failed
    else -> StatusKind.Empty
}

/**
 * The foot of a list once everything is there, in the theme's hand: 「— 终 —」 and the seal (墨),
 * LAST CALL in a dim tube (霓), the signature on a postcard (暮), a black bar with the count (格),
 * THE END (影), a dotted line run out to the red dot (点), ■ STOP on the display (机), （本版完）
 * (报), - THE END - (游), a chrome sparkle (酸). [count] is how many there were.
 */
@Composable
fun ListEnd(count: Int, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Box(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 28.dp), contentAlignment = Alignment.Center) {
        when (style.backdrop) {
            BackdropKind.Ink -> androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text("—　终　—", style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = fixed(13f), letterSpacing = 0.2.em), color = c.muted)
                Box(Modifier.padding(start = 8.dp).size(16.dp).background(c.accentAlt, RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
                    Text("澜", style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = fixed(9f)), color = c.bg)
                }
            }
            BackdropKind.Neon -> NeonText("LAST CALL", TextStyle(fontFamily = TiltNeonFamily, fontSize = fixed(15f), letterSpacing = 0.12.em), c.accent.copy(alpha = 0.55f), ignite = false)
            BackdropKind.Dusk -> Text(
                "— wish you were here · $count 首 —",
                style = TextStyle(fontFamily = style.display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = fixed(13f)), color = c.muted,
            )
            BackdropKind.Grid -> Text(
                "END · ${"%02d".format(count)} ITEMS", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(11f), letterSpacing = 0.1.em),
                color = c.surface, modifier = Modifier.background(c.outline).padding(horizontal = 10.dp, vertical = 3.dp),
            )
            BackdropKind.Film -> androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("THE END", style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = fixed(18f), letterSpacing = 0.3.em), color = Color(0xFFEDE6DA).copy(alpha = 0.85f))
                Text("REEL 1 · $count SHOTS", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(9f)), color = FilmStamp, modifier = Modifier.padding(top = 2.dp))
            }
            BackdropKind.Dot -> Canvas(Modifier.size(160.dp, 10.dp)) {
                val pitch = 7.dp.toPx()
                var x = pitch / 2f
                while (x < size.width - pitch) {
                    drawCircle(c.content.copy(alpha = 0.35f), 1.6.dp.toPx(), Offset(x, size.height / 2f))
                    x += pitch
                }
                drawCircle(c.accentAlt, 3.dp.toPx(), Offset(size.width - pitch / 2f, size.height / 2f))
            }
            BackdropKind.Device -> Text(
                "■ STOP · ${"%02d".format(count)} TRK", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(11f), letterSpacing = 0.1.em),
                color = c.accent, modifier = Modifier.background(Color(0xFF1C1C1F), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            BackdropKind.Paper -> Text("（本版完）", style = TextStyle(fontFamily = style.display, fontSize = fixed(13f), letterSpacing = 0.1.em), color = c.muted)
            BackdropKind.Game -> Text("- THE END -", style = TextStyle(fontFamily = style.display, fontSize = fixed(13f)), color = c.content)
            BackdropKind.Acid -> androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(12.dp)) { drawPath(starPath(center, size.minDimension / 2f, size.minDimension / 9f, 4), ChromeBrush) }
                Text("  that's all  ", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(11f), brush = ChromeBrush))
                Canvas(Modifier.size(12.dp)) { drawPath(starPath(center, size.minDimension / 2f, size.minDimension / 9f, 4), ChromeBrush) }
            }
        }
    }
}
