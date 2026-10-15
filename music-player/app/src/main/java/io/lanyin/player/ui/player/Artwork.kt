package io.lanyin.player.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.dateStampWidth
import io.lanyin.player.ui.design.drawDateStamp
import io.lanyin.player.ui.design.drawFilmFrame
import io.lanyin.player.ui.design.hash01
import io.lanyin.player.ui.design.rememberFrameClock
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import io.lanyin.player.ui.design.drawCoverArt
import io.lanyin.player.ui.design.neonGlow
import io.lanyin.player.ui.design.retroShadow
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.design.CoverImage
import io.lanyin.player.ui.design.rememberCoverPainter
import androidx.compose.ui.graphics.painter.Painter
import io.lanyin.player.ui.theme.LanyinStyle
import io.lanyin.player.ui.theme.PlayerSkin

/** Artwork for the given player skin; [PlayerSkin.LYRICS] has none. */
@Composable
fun PlayerArtwork(skin: PlayerSkin, isPlaying: Boolean, spin: SpinState, coverSeed: Int, modifier: Modifier = Modifier, coverUrl: String? = null) {
    when (skin) {
        PlayerSkin.VINYL -> VinylRecord(isPlaying, spin, coverSeed, modifier, coverUrl)
        PlayerSkin.COVER -> SquareCover(isPlaying, coverSeed, modifier, coverUrl)
        PlayerSkin.CASSETTE -> Cassette(spin, coverSeed, modifier)
        PlayerSkin.LYRICS -> Unit
    }
}

@Composable
fun VinylRecord(isPlaying: Boolean, spin: SpinState, coverSeed: Int, modifier: Modifier = Modifier, coverUrl: String? = null) {
    val style = Lanyin.style
    val cover = rememberCoverPainter(coverUrl, 480)
    val armAngle by animateFloatAsState(if (isPlaying) 0f else -26f, style.bouncy.spec(), label = "toneArm")
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val r = size.minDimension / 2f
            // The record's soft shadow / glow stays still; only the record turns.
            when (style.edge) {
                EdgeKind.Glow -> for ((w, a) in listOf(18f to 0.06f, 10f to 0.12f, 4f to 0.3f)) {
                    drawCircle(style.colors.accent.copy(alpha = a), r + w.dp.toPx() / 2, style = Stroke(w.dp.toPx()))
                }
                EdgeKind.Brutal -> drawCircle(style.colors.outline, r, center + Offset(5.dp.toPx(), 5.dp.toPx()))
                EdgeKind.Hairline -> Unit
                else -> drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), center, r * 1.12f), r * 1.12f)
            }
            rotate(spin.angle) { drawRecord(style, r, coverSeed, cover) }
        }
        Canvas(Modifier.fillMaxSize()) { drawToneArm(style, armAngle) }
    }
}

private fun DrawScope.drawRecord(style: LanyinStyle, r: Float, coverSeed: Int, cover: Painter? = null) {
    val c = style.colors
    drawCircle(c.vinyl, r)
    if (style.hardEdge) drawCircle(c.outline, r, style = Stroke(style.strokeWidth.toPx()))
    if (style.edge == EdgeKind.Hairline && style.dark) drawCircle(c.content.copy(alpha = 0.28f), r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
    val groove = if (style.edge == EdgeKind.Glow) c.accent else Color.White
    for (i in 1..16) {
        drawCircle(
            groove.copy(alpha = if (i % 4 == 0) 0.09f else 0.035f),
            radius = r * (0.46f + i * 0.032f), style = Stroke(1.dp.toPx()),
        )
    }
    // Sheen: a sweep of light that turns with the record so motion is visible.
    drawCircle(
        Brush.sweepGradient(
            0f to Color.Transparent, 0.08f to Color.White.copy(alpha = 0.10f), 0.16f to Color.Transparent,
            0.5f to Color.Transparent, 0.58f to Color.White.copy(alpha = 0.07f), 0.66f to Color.Transparent,
            center = center,
        ),
        r,
    )
    // Label: the cover art, clipped to a circle.
    val labelR = r * 0.4f
    val label = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, labelR)) }
    clipPath(label) {
        translate(center.x - labelR, center.y - labelR) {
            if (cover != null) with(cover) { draw(Size(labelR * 2, labelR * 2)) } else withLabelSize(labelR * 2) { drawCoverArt(style, coverSeed) }
        }
    }
    drawCircle(Color.Black.copy(alpha = 0.2f), labelR, style = Stroke(2.dp.toPx()))
    drawCircle(c.vinyl, r * 0.035f)
}

/** Runs [block] with the draw size temporarily set to a square of [side] px. */
private inline fun DrawScope.withLabelSize(side: Float, block: DrawScope.() -> Unit) {
    val previous = drawContext.size
    drawContext.size = Size(side, side)
    block()
    drawContext.size = previous
}

private fun DrawScope.drawToneArm(style: LanyinStyle, angle: Float) {
    val c = style.colors
    val metal = when (style.edge) {
        EdgeKind.Glow -> c.accent
        EdgeKind.Hairline, EdgeKind.Brutal, EdgeKind.Glass -> c.content
        else -> lerp(c.content, c.bg, 0.15f)
    }
    val pivot = Offset(size.width * 0.9f, size.height * 0.07f)
    rotate(degrees = angle, pivot = pivot) {
        val elbow = Offset(size.width * 0.86f, size.height * 0.5f)
        val head = Offset(size.width * 0.7f, size.height * 0.66f)
        val w = 4.dp.toPx()
        if (style.edge == EdgeKind.Glow) {
            drawLine(metal.copy(alpha = 0.25f), pivot, elbow, w * 3, StrokeCap.Round)
            drawLine(metal.copy(alpha = 0.25f), elbow, head, w * 3, StrokeCap.Round)
        }
        drawLine(metal, pivot, elbow, w, StrokeCap.Round)
        drawLine(metal, elbow, head, w, StrokeCap.Round)
        rotate(degrees = 35f, pivot = head) {
            drawRoundRect(metal, Offset(head.x - 6.dp.toPx(), head.y - 4.dp.toPx()), Size(12.dp.toPx(), 20.dp.toPx()), CornerRadius(3.dp.toPx()))
        }
        drawCircle(metal, 10.dp.toPx(), pivot)
        drawCircle(c.bg, 4.dp.toPx(), pivot)
    }
}

@Composable
fun SquareCover(isPlaying: Boolean, coverSeed: Int, modifier: Modifier = Modifier, coverUrl: String? = null) {
    val style = Lanyin.style
    val c = style.colors
    // Settles back when paused, springs forward on play.
    val scale by animateFloatAsState(if (isPlaying) 1f else 0.86f, style.bouncy.spec(), label = "coverScale")
    if (style.edge == EdgeKind.Film) {
        FilmCover(isPlaying, coverSeed, scale, modifier, coverUrl)
        return
    }
    // 律: the cover rounds into a squircle while playing and pinches into a cookie when paused.
    val pinch by animateFloatAsState(if (isPlaying) 1f else 0f, style.bouncy.spec(), label = "coverShape")
    val shape = if (style.edge == EdgeKind.Tonal) {
        androidx.compose.runtime.remember(pinch) { io.lanyin.player.ui.design.morphShape(io.lanyin.player.ui.design.Cookie, io.lanyin.player.ui.design.Squircle, pinch) }
    } else {
        style.artShape
    }
    val frame = when (style.edge) {
        EdgeKind.Brutal -> Modifier.retroShadow(shape, c.outline, 6.dp).clip(shape).border(style.strokeWidth, c.outline, shape)
        EdgeKind.Glow -> Modifier.neonGlow(shape, c.accent).clip(shape).border(1.dp, c.accent, shape)
        EdgeKind.Hairline -> Modifier.clip(shape).border(1.dp, c.outline, shape)
        EdgeKind.Tonal -> Modifier.clip(shape)
        else -> Modifier.shadow(28.dp, shape, ambientColor = c.accent, spotColor = Color.Black).clip(shape)
    }
    CoverImage(
        coverUrl,
        coverSeed,
        360.dp,
        modifier
            .aspectRatio(1f)
            .padding(18.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(frame),
    )
}

/**
 * 影's cover: a frame of 35mm film with sprocket holes and an edge marking, the orange date
 * stamp of a point-and-shoot in the picture's corner. While playing the frame weaves a hair in
 * the gate, as projected film does.
 */
@Composable
private fun FilmCover(isPlaying: Boolean, coverSeed: Int, scale: Float, modifier: Modifier, coverUrl: String?) {
    val clock = rememberFrameClock(isPlaying)
    val stamp = androidx.compose.runtime.remember {
        val today = java.time.LocalDate.now()
        "'%02d %d %02d".format(today.year % 100, today.monthValue, today.dayOfMonth)
    }
    BoxWithConstraints(
        modifier
            .aspectRatio(1f)
            .padding(10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                if (isPlaying) {
                    val frame = (clock.floatValue * 24f).toInt()
                    translationX = (hash01(frame, 7) - 0.5f) * 1.2.dp.toPx()
                    translationY = (hash01(frame, 8) - 0.5f) * 1.6.dp.toPx()
                }
            }
            .drawBehind { drawFilmFrame(size.height * FILM_MARGIN, 12 + coverSeed % 24) },
    ) {
        CoverImage(
            coverUrl, coverSeed, 360.dp,
            Modifier
                .fillMaxSize()
                .padding(horizontal = maxWidth * 0.03f, vertical = maxHeight * FILM_MARGIN)
                .drawWithContent {
                    drawContent()
                    val h = size.height * 0.06f
                    val w = dateStampWidth(stamp, h)
                    drawDateStamp(stamp, Offset(size.width - w - size.width * 0.05f, size.height - h * 2.2f), h)
                },
        )
    }
}

/** How much of 影's film frame, above and below the picture, is sprocket margin. */
private const val FILM_MARGIN = 0.12f

@Composable
fun Cassette(spin: SpinState, coverSeed: Int, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Canvas(modifier.aspectRatio(1.58f).padding(16.dp)) {
        val w = size.width
        val h = size.height
        // 格's tape is cut square.
        val round = if (style.edge == EdgeKind.Brutal) 0f else 1f
        val corner = CornerRadius(h * 0.07f * round)
        if (style.hardEdge) drawRoundRect(c.outline, Offset(5.dp.toPx(), 5.dp.toPx()), size, corner)
        drawRoundRect(c.surfaceAlt, cornerRadius = corner)
        drawRoundRect(if (style.hardEdge) c.outline else c.content.copy(alpha = 0.3f), cornerRadius = corner, style = Stroke(style.strokeWidth.toPx()))
        // A real tape label: paper, two colour bands, a line to write the title on.
        val labelTopLeft = Offset(w * 0.07f, h * 0.08f)
        val labelSize = Size(w * 0.86f, h * 0.56f)
        val label = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(androidx.compose.ui.geometry.Rect(labelTopLeft, labelSize), CornerRadius(h * 0.04f * round)))
        }
        clipPath(label) {
            drawRect(c.surface, labelTopLeft, labelSize)
            drawRect(c.accent, Offset(labelTopLeft.x, labelTopLeft.y + labelSize.height * 0.1f), Size(labelSize.width, labelSize.height * 0.1f))
            drawRect(c.accentAlt, Offset(labelTopLeft.x, labelTopLeft.y + labelSize.height * 0.2f), Size(labelSize.width, labelSize.height * 0.07f))
        }
        drawLine(
            c.content.copy(alpha = 0.35f),
            Offset(labelTopLeft.x + labelSize.width * 0.12f, labelTopLeft.y + labelSize.height * 0.88f),
            Offset(labelTopLeft.x + labelSize.width * 0.88f, labelTopLeft.y + labelSize.height * 0.88f),
            1.5.dp.toPx(),
        )
        if (round == 0f) {
            drawRect(c.outline, labelTopLeft, labelSize, style = Stroke(2.dp.toPx()))
        } else {
            drawRoundRect(c.content.copy(alpha = 0.5f), labelTopLeft, labelSize, CornerRadius(h * 0.04f), style = Stroke(1.dp.toPx()))
        }
        // Tape window with two turning reels.
        val window = Size(w * 0.56f, h * 0.24f)
        val windowTopLeft = Offset((w - window.width) / 2f, h * 0.24f)
        drawRoundRect(Color(0xFF141414), windowTopLeft, window, CornerRadius(window.height / 2f * round))
        val reelR = window.height * 0.38f
        val reelY = windowTopLeft.y + window.height / 2f
        val inset = window.height / 2f + 4.dp.toPx()
        for (cx in listOf(windowTopLeft.x + inset, windowTopLeft.x + window.width - inset)) {
            val center = Offset(cx, reelY)
            drawCircle(Color(0xFFEDEDED), reelR, center)
            rotate(spin.angle * 6f, pivot = center) {
                for (k in 0 until 6) {
                    rotate(k * 60f, pivot = center) {
                        drawRect(Color(0xFF141414), Offset(center.x - reelR * 0.12f, center.y - reelR * 0.85f), Size(reelR * 0.24f, reelR * 0.35f))
                    }
                }
            }
        }
        drawRoundRect(c.content.copy(alpha = 0.1f), Offset(w * 0.2f, h * 0.74f), Size(w * 0.6f, h * 0.2f), CornerRadius(h * 0.03f * round))
        for (x in listOf(w * 0.3f, w * 0.7f)) drawCircle(c.content.copy(alpha = 0.35f), h * 0.025f, Offset(x, h * 0.84f))
    }
}

/** Mini player thumbnail: a turning record for the vinyl skin, a small cover otherwise. */
@Composable
fun MiniArtwork(skin: PlayerSkin, spin: SpinState, coverSeed: Int, modifier: Modifier = Modifier, coverUrl: String? = null) {
    val style = Lanyin.style
    if (skin == PlayerSkin.VINYL) {
        val cover = rememberCoverPainter(coverUrl, 160)
        Canvas(modifier) {
            rotate(spin.angle) {
                val r = size.minDimension / 2f
                drawCircle(style.colors.vinyl, r)
                val labelR = r * 0.62f
                clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, labelR)) }) {
                    translate(center.x - labelR, center.y - labelR) {
                        if (cover != null) with(cover) { draw(Size(labelR * 2, labelR * 2)) } else withLabelSize(labelR * 2) { drawCoverArt(style, coverSeed) }
                    }
                }
                drawCircle(style.colors.vinyl, r * 0.08f)
            }
        }
    } else {
        CoverImage(coverUrl, coverSeed, 48.dp, modifier.clip(style.artShape))
    }
}
