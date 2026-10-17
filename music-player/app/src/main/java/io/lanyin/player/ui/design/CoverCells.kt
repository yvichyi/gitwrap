package io.lanyin.player.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import io.lanyin.player.ui.theme.Lanyin

/** The cover at [url] sampled down to [n]×[n] luminances in 0..1, row by row; see [rememberCoverPixels]. */
@Composable
fun rememberCoverLuminance(url: String?, seed: Int, n: Int): FloatArray {
    val pixels = rememberCoverPixels(url, seed, n)
    return remember(pixels) { FloatArray(n * n) { i -> luminance(pixels[i]) } }
}

/**
 * The cover at [url] sampled down to [n]×[n] ARGB pixels, row by row, for themes that redraw a
 * cover in their own medium: 游 in pixels, 报 in halftone. Until the cover has loaded, and for
 * songs without one, it samples the theme's generated sleeve for [seed] instead.
 */
@Composable
fun rememberCoverPixels(url: String?, seed: Int, n: Int): IntArray {
    val style = Lanyin.style
    val density = LocalDensity.current
    var loaded: AsyncImagePainter? = null
    if (!url.isNullOrEmpty()) {
        val context = LocalPlatformContext.current
        // A software bitmap, so it can be drawn into the sampling canvas below.
        val request = remember(url, n) { ImageRequest.Builder(context).data(sized(url, n * 4)).size(n * 4).allowHardware(false).build() }
        val painter = rememberAsyncImagePainter(request)
        if (painter.state.collectAsState().value is AsyncImagePainter.State.Success) loaded = painter
    }
    val painter = loaded
    return remember(url, painter != null, seed, n, style.theme, style.dark) {
        val bitmap = ImageBitmap(n, n)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(n.toFloat(), n.toFloat())) {
            if (painter != null) with(painter) { draw(size) } else drawCoverArt(style, seed)
        }
        IntArray(n * n).also { bitmap.readPixels(it) }
    }
}

/** Perceived lightness of an ARGB pixel in 0..1 (Rec. 709 weights, ignoring gamma). */
internal fun luminance(argb: Int): Float {
    val r = (argb shr 16 and 0xff) / 255f
    val g = (argb shr 8 and 0xff) / 255f
    val b = (argb and 0xff) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/** The pixel of [cells] (n×n ARGB, row by row) nearest a point in 0..1 on each axis. */
internal fun IntArray.nearestCell(n: Int, u: Float, v: Float): Int =
    this[(v * n).toInt().coerceIn(0, n - 1) * n + (u * n).toInt().coerceIn(0, n - 1)]

/** [cells] (n×n, row by row) at a point in 0..1 on each axis, bilinearly. */
internal fun FloatArray.sampleCells(n: Int, u: Float, v: Float): Float {
    val x = (u * n - 0.5f).coerceIn(0f, n - 1f)
    val y = (v * n - 0.5f).coerceIn(0f, n - 1f)
    val x0 = x.toInt().coerceAtMost(n - 2).coerceAtLeast(0)
    val y0 = y.toInt().coerceAtMost(n - 2).coerceAtLeast(0)
    val fx = x - x0
    val fy = y - y0
    val a = this[y0 * n + x0] + (this[y0 * n + x0 + 1] - this[y0 * n + x0]) * fx
    val b = this[(y0 + 1) * n + x0] + (this[(y0 + 1) * n + x0 + 1] - this[(y0 + 1) * n + x0]) * fx
    return a + (b - a) * fy
}
