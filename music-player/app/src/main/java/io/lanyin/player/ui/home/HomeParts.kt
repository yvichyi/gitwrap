package io.lanyin.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.data.library.ChartPreview
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.CoverImage
import io.lanyin.player.ui.theme.Lanyin

/** A playlist's cover, or (while the shelf loads) one of the theme's own sleeves in its place. */
@Composable
internal fun ShelfCover(playlist: Playlist?, slot: Int, modifier: Modifier, size: Dp = 160.dp) {
    // The generated sleeves draw freely; keep them inside their frame.
    if (playlist != null) CoverImage(playlist.coverUrl, (playlist.id % 97).toInt(), size, modifier.clipToBounds())
    else CoverArt(10 + slot, modifier.clipToBounds())
}

/**
 * Up to [n] shelf entries; while the shelf is still empty (loading, or offline), [n] nulls, for
 * the layout to fill with placeholders.
 */
internal fun <T> List<T>.slots(n: Int): List<T?> = if (isEmpty()) List(n) { null } else take(n)

/** Names for the chart slots while the charts load. */
internal val ChartNames = listOf("飙升榜", "新歌榜", "原创榜", "热歌榜")

/** A chart's name, or its placeholder's. */
internal fun ChartPreview?.nameOr(slot: Int): String = this?.playlist?.name ?: ChartNames[slot % ChartNames.size]

/** A horizontal rule [thickness] thick across the width. */
@Composable
internal fun Rule(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = Lanyin.colors.content) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

/** A vertical rule down the height of a row with intrinsic height. */
@Composable
internal fun VRule(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = Lanyin.colors.outline) {
    Box(modifier.width(thickness).fillMaxHeight().background(color))
}

/** Dots from here to the next thing in the row, as in a table of contents. */
@Composable
internal fun DotLeader(modifier: Modifier = Modifier, color: Color = Lanyin.colors.content.copy(alpha = 0.3f), square: Boolean = false, pitch: Dp = 5.dp) {
    Canvas(modifier.height(4.dp)) {
        val step = pitch.toPx()
        val r = 0.8.dp.toPx()
        var x = r
        while (x < size.width - r) {
            if (square) drawRect(color, Offset(x - r, size.height / 2f - r), Size(r * 2, r * 2))
            else drawCircle(color, r, Offset(x, size.height / 2f))
            x += step
        }
    }
}

/**
 * Type that is part of a drawing, held at its size whatever the system font scale. Set through
 * dp: dividing by the font scale doesn't undo Android 14's nonlinear scaling, which grows large
 * type less than small.
 */
@Composable
internal fun fixedSp(size: Float): TextUnit = with(LocalDensity.current) { size.dp.toSp() }

/**
 * Type on a fixed-size tile, allowed to grow with the system font size only so far ([maxScale]),
 * so a name still fits its tile on a phone set to large text.
 */
@Composable
internal fun cappedSp(size: Float, maxScale: Float = 1.15f): TextUnit = with(LocalDensity.current) {
    // As large as the system would set it, up to the cap, compared in dp so either scaling holds.
    minOf(size.sp.toDp(), (size * maxScale).dp).toSp()
}

/** 14 → 「十四」 as a column count, or the digits for anything past ninety-nine. */
internal fun chineseIndex(n: Int): String = if (n in 0..99) chineseNumber(n) else "$n"
