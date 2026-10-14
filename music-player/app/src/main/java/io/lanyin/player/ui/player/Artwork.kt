package io.lanyin.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.PlayerSkin
import kotlinx.coroutines.isActive

/** Artwork for the given player skin; [PlayerSkin.LYRICS] has none. */
@Composable
fun PlayerArtwork(skin: PlayerSkin, isPlaying: Boolean, modifier: Modifier = Modifier) {
    when (skin) {
        PlayerSkin.VINYL -> VinylRecord(isPlaying, modifier)
        PlayerSkin.COVER -> SquareCover(isPlaying, modifier)
        PlayerSkin.CASSETTE -> Cassette(isPlaying, modifier)
        PlayerSkin.LYRICS -> Unit
    }
}

/** Placeholder cover art: a diagonal gradient in the current skin's colours. Real covers arrive in M2. */
@Composable
fun placeholderCoverBrush(): Brush {
    val c = MaterialTheme.colorScheme
    return Brush.linearGradient(listOf(c.primary, c.tertiary, c.primaryContainer))
}

/** Keeps a rotation angle that advances only while [running]; pausing freezes it in place. */
@Composable
private fun rememberSpin(running: Boolean, millisPerTurn: Int): Animatable<Float, *> {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (isActive) {
            val start = angle.value % 360f
            angle.snapTo(start)
            angle.animateTo(start + 360f, tween(millisPerTurn, easing = LinearEasing))
        }
    }
    return angle
}

@Composable
fun VinylRecord(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val spin = rememberSpin(isPlaying, millisPerTurn = 24_000)
    // The tone arm swings onto the record when playing and lifts off when paused.
    val armAngle by animateFloatAsState(
        targetValue = if (isPlaying) 0f else -28f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 120f),
        label = "toneArm",
    )
    val cover = placeholderCoverBrush()
    val armColor = MaterialTheme.colorScheme.onSurface

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(12.dp)
                .graphicsLayer { rotationZ = spin.value },
        ) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF0E0E10), radius = r)
            // Grooves, with a soft sheen so the rotation is visible.
            for (i in 1..14) {
                drawCircle(
                    color = Color.White.copy(alpha = if (i % 3 == 0) 0.07f else 0.035f),
                    radius = r * (0.48f + i * 0.034f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                ),
                startAngle = -60f, sweepAngle = 70f, useCenter = true,
                topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
            )
            drawCircle(cover, radius = r * 0.42f)
            drawCircle(Color.Black.copy(alpha = 0.25f), radius = r * 0.42f, style = Stroke(2.dp.toPx()))
            drawCircle(Color(0xFF0E0E10), radius = r * 0.04f)
        }
        Canvas(Modifier.fillMaxSize()) { drawToneArm(armAngle, armColor) }
    }
}

private fun DrawScope.drawToneArm(angle: Float, color: Color) {
    val pivot = Offset(size.width * 0.92f, size.height * 0.06f)
    rotate(degrees = angle, pivot = pivot) {
        val elbow = Offset(size.width * 0.86f, size.height * 0.50f)
        val head = Offset(size.width * 0.70f, size.height * 0.66f)
        drawLine(color.copy(alpha = 0.85f), pivot, elbow, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color.copy(alpha = 0.85f), elbow, head, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(head.x - 7.dp.toPx(), head.y - 5.dp.toPx()),
            size = Size(14.dp.toPx(), 18.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
        drawCircle(color, radius = 9.dp.toPx(), center = pivot)
    }
}

@Composable
fun SquareCover(isPlaying: Boolean, modifier: Modifier = Modifier) {
    // A gentle "breathing" scale: slightly smaller while paused, like QQ 音乐's cover page.
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "coverScale",
    )
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .padding(16.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(24.dp, shape)
            .clip(shape)
            .background(placeholderCoverBrush()),
    )
}

@Composable
fun Cassette(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val spin = rememberSpin(isPlaying, millisPerTurn = 3_000)
    val label = placeholderCoverBrush()
    val body = MaterialTheme.colorScheme.surfaceContainerHighest
    val ink = MaterialTheme.colorScheme.onSurface

    Canvas(modifier.aspectRatio(1.6f).padding(16.dp)) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(h * 0.06f)
        drawRoundRect(body, cornerRadius = corner)
        drawRoundRect(ink.copy(alpha = 0.25f), cornerRadius = corner, style = Stroke(2.dp.toPx()))
        // Label strip.
        drawRoundRect(
            brush = label,
            topLeft = Offset(w * 0.07f, h * 0.08f),
            size = Size(w * 0.86f, h * 0.56f),
            cornerRadius = CornerRadius(h * 0.04f),
        )
        // Tape window.
        val window = Size(w * 0.56f, h * 0.24f)
        val windowTopLeft = Offset((w - window.width) / 2f, h * 0.24f)
        drawRoundRect(
            Color(0xFF151515), topLeft = windowTopLeft, size = window,
            cornerRadius = CornerRadius(window.height / 2f),
        )
        val reelRadius = window.height * 0.38f
        val reelY = windowTopLeft.y + window.height / 2f
        for (cx in listOf(windowTopLeft.x + window.height / 2f + 4.dp.toPx(), windowTopLeft.x + window.width - window.height / 2f - 4.dp.toPx())) {
            val c = Offset(cx, reelY)
            drawCircle(Color(0xFFEDEDED), radius = reelRadius, center = c)
            rotate(spin.value, pivot = c) {
                for (k in 0 until 6) {
                    rotate(k * 60f, pivot = c) {
                        drawRect(
                            Color(0xFF151515),
                            topLeft = Offset(c.x - reelRadius * 0.12f, c.y - reelRadius * 0.85f),
                            size = Size(reelRadius * 0.24f, reelRadius * 0.35f),
                        )
                    }
                }
            }
        }
        // Bottom trapezoid with screw holes.
        val trapTop = h * 0.74f
        drawRoundRect(
            ink.copy(alpha = 0.08f),
            topLeft = Offset(w * 0.2f, trapTop), size = Size(w * 0.6f, h * 0.2f),
            cornerRadius = CornerRadius(h * 0.03f),
        )
        for (x in listOf(w * 0.3f, w * 0.7f)) drawCircle(ink.copy(alpha = 0.3f), radius = h * 0.025f, center = Offset(x, h * 0.84f))
    }
}

/** Small circular thumbnail used by the mini player; spins with the record skin. */
@Composable
fun MiniArtwork(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val spin = rememberSpin(isPlaying, millisPerTurn = 24_000)
    val cover = placeholderCoverBrush()
    Canvas(modifier.rotate(spin.value)) {
        val r = size.minDimension / 2f
        drawCircle(Color(0xFF0E0E10), radius = r)
        drawCircle(cover, radius = r * 0.62f)
        drawCircle(Color(0xFF0E0E10), radius = r * 0.08f)
    }
}
