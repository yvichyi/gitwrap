package io.lanyin.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.LocalBottomChromePadding
import io.lanyin.player.ui.design.hash01

/** Vertical padding for a scrolling page: below the status bar, above the floating chrome. */
@Composable
fun pagePadding(withBottomChrome: Boolean = true, extraTop: Dp = 12.dp): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val chrome = if (withBottomChrome) LocalBottomChromePadding.current else 0.dp
    return PaddingValues(top = top + extraTop, bottom = bottom + chrome + 20.dp)
}

/** Decorative QR-like glyph (three finder squares and scattered modules). Not scannable. */
@Composable
fun QrGlyph(color: Color, modifier: Modifier = Modifier, seed: Int = 1) {
    Canvas(modifier) {
        val n = 21
        val cell = size.minDimension / n
        fun finder(cx: Int, cy: Int) {
            val o = Offset(cx * cell, cy * cell)
            drawRoundRect(color, o + Offset(cell / 2, cell / 2), Size(cell * 6, cell * 6), CornerRadius(cell * 1.4f), style = Stroke(cell))
            drawRoundRect(color, o + Offset(cell * 2, cell * 2), Size(cell * 3, cell * 3), CornerRadius(cell * 0.8f))
        }
        for (y in 0 until n) for (x in 0 until n) {
            val inFinder = (x < 8 && y < 8) || (x > n - 9 && y < 8) || (x < 8 && y > n - 9)
            if (!inFinder && hash01(x * 31 + y + seed * 977, 5) > 0.52f) {
                drawRoundRect(color, Offset(x * cell + cell * 0.1f, y * cell + cell * 0.1f), Size(cell * 0.8f, cell * 0.8f), CornerRadius(cell * 0.3f))
            }
        }
        finder(0, 0)
        finder(n - 7, 0)
        finder(0, n - 7)
    }
}
