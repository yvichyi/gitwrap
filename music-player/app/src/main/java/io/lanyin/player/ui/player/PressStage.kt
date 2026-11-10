package io.lanyin.player.ui.player

import io.lanyin.player.ui.theme.PlayerSkin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.design.drawHalftone
import io.lanyin.player.ui.design.rememberHalftonePainter
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Where things sit in 报's turntable photo, for a photo [w] by [h]: the record's centre and
 * radius (the plinth and arm are placed from these).
 */
private class Deck(w: Float, h: Float) {
    val r = minOf(w * 0.33f, h * 0.4f)
    val c = Offset(w * 0.44f, h * 0.5f)
    val plinthTopLeft = Offset(c.x - r * 1.24f, c.y - r * 1.2f)
    val plinthSize = Size(r * 2.78f, r * 2.4f)
    val pivot = Offset(c.x + r * 1.18f, c.y - r * 0.84f)
}

/**
 * 报's record: a press photograph of the turntable from above, printed in halftone. The table
 * lit from the upper left, the plinth and its shadow, the platter's rim are screened once into a
 * picture; the record turns on top of it, its label the cover in halftone ringed in the red
 * plate (a hair out of register); the arm swings onto the record while it plays and back to its
 * rest when it's paused. Swipe it to change song; tap for the lyrics.
 */
@Composable
internal fun PressPhotoStage(stage: StageContext, modifier: Modifier, onClick: (() -> Unit)?) {
    val style = Lanyin.style
    val c = style.colors
    val state = stage.state
    val on by animateFloatAsState(if (state.isPlaying) 1f else 0f, tween(900), label = "arm")
    if (stage.skin == PlayerSkin.COVER) {
        // The square cover chosen: the cover itself is the photograph, in halftone.
        BoxWithConstraints(modifier.clipToBounds().border(0.75.dp, c.content.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            val tap = if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier
            SwipeToSkip(state, Modifier.fillMaxSize().then(tap).then(stage.dismissDrag)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    io.lanyin.player.ui.design.HalftoneCover(
                        state.coverUrl, state.coverSeed,
                        Modifier.size(side).sharedPlayer("artwork", zIndex = 1f).semantics { contentDescription = "封面：${state.title}" },
                    )
                }
            }
        }
        return
    }
    BoxWithConstraints(modifier.clipToBounds().border(0.75.dp, c.content.copy(alpha = 0.6f))) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val deck = remember(wPx, hPx) { Deck(wPx, hPx) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val disc = with(density) { (deck.r * 2f).toDp() }
        val tap = if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier
        // The photo: table, plinth, platter; screened once for its size.
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val picture = screenPhoto(this, style, deck)
                onDrawBehind {
                    drawImage(picture)
                    drawDeckFittings(style, deck)
                }
            },
        )
        SwipeToSkip(state, Modifier.fillMaxSize().then(tap).then(stage.dismissDrag)) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .offset(with(density) { (deck.c.x - deck.r).toDp() }, with(density) { (deck.c.y - deck.r).toDp() })
                        .size(disc)
                        .sharedPlayer("artwork", zIndex = 1f)
                        .semantics { contentDescription = "唱片：${state.title}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = stage.spin.angle }) { drawPressRecord(style) }
                    val label = rememberHalftonePainter(state.coverUrl, state.coverSeed)
                    if (label != null) {
                        Image(
                            label, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(disc * 0.38f).clip(CircleShape).graphicsLayer { rotationZ = stage.spin.angle },
                        )
                    }
                    // What stays still on a turning record: the lamp's sheen, the red ring, the spindle.
                    Canvas(Modifier.fillMaxSize()) { drawPressSheen(style) }
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) { drawPressArm(style, deck, 92f + 27f * on) }
    }
}

/**
 * The photograph without the record or the arm, in halftone: the table brighter towards the
 * lamp, the plinth's shadow on it, the plinth catching the light on its upper edge, the platter's
 * rim. Made into a bitmap once for the photo's size.
 */
private fun screenPhoto(scope: androidx.compose.ui.draw.CacheDrawScope, style: LanyinStyle, deck: Deck): ImageBitmap {
    val w = scope.size.width
    val h = scope.size.height
    val image = ImageBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    val pl = deck.plinthTopLeft
    val ps = deck.plinthSize
    val r = deck.r
    val corner = r * 0.08f
    fun inPlinth(x: Float, y: Float, grow: Float = 0f): Float {
        // Signed distance (negative inside) to the plinth's rounded rectangle, in pixels.
        val dx = maxOf(pl.x - grow - x, x - (pl.x + ps.width + grow), 0f)
        val dy = maxOf(pl.y - grow - y, y - (pl.y + ps.height + grow), 0f)
        return if (dx == 0f && dy == 0f) -minOf(x - pl.x, pl.x + ps.width - x, y - pl.y, pl.y + ps.height - y) else hypot(dx, dy)
    }
    CanvasDrawScope().draw(scope, scope.layoutDirection, androidx.compose.ui.graphics.Canvas(image), scope.size) {
        drawRect(style.colors.surface)
        val lines = minOf(w, h) / (3.4f * density)
        drawHalftone(style, lines) { u, v ->
            val x = u * w
            val y = v * h
            // The table: lit from the upper left.
            var l = 0.9f - 0.28f * (u * 0.6f + v * 0.4f)
            val inside = inPlinth(x, y)
            if (inside > 0f) {
                // The plinth's soft shadow, down and to the right.
                val sh = inPlinth(x - r * 0.06f, y - r * 0.1f, corner)
                if (sh < r * 0.14f) l -= 0.32f * (1f - (sh / (r * 0.14f)).coerceIn(0f, 1f))
            } else {
                // The plinth: mid-grey, its upper and left edges catching the light.
                l = 0.7f - 0.14f * v
                if (-inside < r * 0.05f && (y - pl.y < r * 0.05f || x - pl.x < r * 0.05f)) l = 0.92f
                if (-inside < r * 0.03f && (pl.y + ps.height - y < r * 0.03f || pl.x + ps.width - x < r * 0.03f)) l = 0.35f
                // The platter's rim, bright metal, round the record.
                val d = hypot(x - deck.c.x, y - deck.c.y)
                if (d < r * 1.07f) l = if (d > r * 1.0f) 0.88f - 0.3f * ((x - deck.c.x) / r + 1f) * 0.5f else 0.1f
                // The arm's base plate.
                if (hypot(x - deck.pivot.x, y - deck.pivot.y) < r * 0.24f) l = 0.86f
            }
            // The night edition prints the photo darker, so it doesn't glare off the dark stock.
            (if (style.dark) l * 0.6f else l).coerceIn(0f, 1f)
        }
    }
    return image
}

/**
 * The plinth's fittings, drawn sharp over the photo as the retoucher would: the strobe dots round
 * the platter's rim, the start button and the speed keys with the red lamp lit by 33.
 */
private fun DrawScope.drawDeckFittings(style: LanyinStyle, deck: Deck) {
    val c = style.colors
    val r = deck.r
    val ink = c.content
    for (k in 0 until 90) {
        val a = k * 2f * PI.toFloat() / 90f
        val p = deck.c + Offset(cos(a), sin(a)) * (r * 1.035f)
        drawRect(ink.copy(alpha = 0.55f), p - Offset(0.6f * density, 0.6f * density), Size(1.2f * density, 1.2f * density))
    }
    val base = Offset(deck.plinthTopLeft.x + r * 0.16f, deck.plinthTopLeft.y + deck.plinthSize.height - r * 0.16f)
    drawCircle(ink, r * 0.085f, base, style = Stroke(1.2f * density))
    drawCircle(ink.copy(alpha = 0.6f), r * 0.05f, base)
    for (k in 0..1) {
        val key = Offset(base.x + r * (0.22f + k * 0.2f), base.y - r * 0.035f)
        drawRoundRect(ink, key, Size(r * 0.15f, r * 0.07f), CornerRadius(r * 0.02f), style = Stroke(1f * density))
    }
    drawCircle(c.accent, r * 0.022f, Offset(base.x + r * 0.295f, base.y - r * 0.1f))
}

/** The record itself, turning: black vinyl, its grooves catching a little light in rings, the run-in and run-out. */
private fun DrawScope.drawPressRecord(style: LanyinStyle) {
    val c = style.colors
    val r = size.minDimension / 2f
    drawCircle(c.vinyl, r)
    var gr = r * 0.97f
    var k = 0
    while (gr > r * 0.42f) {
        drawCircle(c.content.copy(alpha = if (k % 7 == 0) 0.12f else 0.045f), gr, style = Stroke(0.5f * density))
        gr -= r * 0.018f
        k++
    }
    // The gap between two songs on the side.
    drawCircle(c.vinyl, r * 0.7f, style = Stroke(r * 0.012f))
    // A mark on the label's edge so the turning shows.
    drawLine(c.content.copy(alpha = 0.5f), center + Offset(0f, -r * 0.4f), center + Offset(0f, -r * 0.44f), 1.4f * density)
}

/** The light that stays where it is while the record turns, the red plate's ring round the label, the spindle. */
private fun DrawScope.drawPressSheen(style: LanyinStyle) {
    val c = style.colors
    val r = size.minDimension / 2f
    drawCircle(
        Brush.sweepGradient(
            0f to Color.Transparent, 0.1f to c.content.copy(alpha = 0.14f), 0.2f to Color.Transparent,
            0.55f to Color.Transparent, 0.62f to c.content.copy(alpha = 0.1f), 0.72f to Color.Transparent, 1f to Color.Transparent,
            center = center,
        ),
        r * 0.98f,
    )
    // The label ringed in the red plate, printed a hair off.
    drawCircle(c.accent, r * 0.385f, center + Offset(0.9f * density, -0.7f * density), style = Stroke(1.6f * density))
    drawCircle(c.content, r * 0.035f)
    drawCircle(c.bg.copy(alpha = 0.6f), r * 0.012f, center - Offset(r * 0.01f, r * 0.01f))
}

/**
 * The tone arm, from its pivot at the plinth's upper right: the counterweight behind, the arm
 * tube with a line of light along it, the headshell set at its angle with the red finger lift.
 * [deg] is the arm's direction (90 points straight down the page).
 */
private fun DrawScope.drawPressArm(style: LanyinStyle, deck: Deck, deg: Float) {
    val c = style.colors
    val r = deck.r
    val p = deck.pivot
    val ink = c.content
    val a = deg * PI.toFloat() / 180f
    val dir = Offset(cos(a), sin(a))
    val length = r * 1.3f
    val end = p + dir * length
    // The rest post the arm lies on when it's lifted.
    val rest = p + Offset(cos(92f * PI.toFloat() / 180f), sin(92f * PI.toFloat() / 180f)) * (length * 0.72f)
    drawCircle(ink, r * 0.04f, rest)
    drawCircle(c.bg, r * 0.018f, rest)
    // Shadow of the arm on the plinth.
    drawLine(Color.Black.copy(alpha = if (style.dark) 0.3f else 0.18f), p + Offset(r * 0.04f, r * 0.06f), end + Offset(r * 0.04f, r * 0.06f), r * 0.05f, StrokeCap.Round)
    // Counterweight.
    val back = p - dir * (r * 0.26f)
    drawLine(ink, p, back, r * 0.14f, StrokeCap.Butt)
    drawLine(c.bg.copy(alpha = 0.35f), p - dir * (r * 0.05f), back, r * 0.03f, StrokeCap.Butt)
    // The pivot.
    drawCircle(ink, r * 0.11f, p)
    drawCircle(c.bg.copy(alpha = 0.5f), r * 0.11f, p, style = Stroke(1f * density))
    drawCircle(c.bg.copy(alpha = 0.45f), r * 0.04f, p - Offset(r * 0.02f, r * 0.02f))
    // The tube, and the light along it.
    drawLine(ink, p, end, r * 0.035f, StrokeCap.Round)
    drawLine(c.bg.copy(alpha = 0.5f), p + Offset(-dir.y, dir.x) * (r * 0.008f), end + Offset(-dir.y, dir.x) * (r * 0.008f), r * 0.01f, StrokeCap.Round)
    // The headshell at its offset angle, the finger lift in red.
    rotate(deg + 22f, end) {
        drawRoundRect(ink, end - Offset(r * 0.02f, r * 0.055f), Size(r * 0.17f, r * 0.11f), CornerRadius(r * 0.02f))
        drawLine(c.accent, end + Offset(r * 0.08f, -r * 0.055f), end + Offset(r * 0.13f, -r * 0.1f), r * 0.02f, StrokeCap.Round)
    }
    // The cue lever beside the pivot.
    drawRoundRect(ink, p + Offset(-r * 0.3f, r * 0.12f), Size(r * 0.12f, r * 0.05f), CornerRadius(r * 0.02f))
}
