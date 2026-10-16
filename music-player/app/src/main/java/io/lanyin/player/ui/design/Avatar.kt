package io.lanyin.player.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import io.lanyin.player.ui.theme.Lanyin

/**
 * A NetEase image sized for where it is shown (NetEase scales on its side with `?param=WxH`).
 * Draws nothing until loaded, so put a placeholder behind it.
 */
@Composable
fun RemoteImage(url: String?, size: Dp, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (url.isNullOrEmpty()) return
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(64, 1080)
    AsyncImage(model = sized(url, px), contentDescription = null, contentScale = contentScale, modifier = modifier)
}

internal fun sized(url: String, px: Int): String = if ("music.126.net" in url && "param=" !in url) {
    url + (if ('?' in url) "&" else "?") + "param=${px}y$px"
} else {
    url
}

/** The first character of the name on the theme's surface, under the photo once it loads. */
@Composable
fun Avatar(url: String?, name: String, size: Dp, modifier: Modifier = Modifier, shape: Shape = Lanyin.style.roundShape) {
    val c = Lanyin.colors
    Box(modifier.size(size).clip(shape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
        Text(
            initialOf(name),
            color = c.muted,
            fontWeight = FontWeight.Medium,
            fontSize = with(LocalDensity.current) { (size * 0.4f).toSp() },
        )
        RemoteImage(url, size, Modifier.fillMaxSize())
    }
}

/** A small 「黑胶VIP」 tag beside a name. */
@Composable
fun VipBadge(vipType: Int, modifier: Modifier = Modifier) {
    if (vipType == 0) return
    val style = Lanyin.style
    val c = style.colors
    Text(
        when (vipType) {
            10 -> "音乐包"
            11 -> "黑胶VIP"
            else -> "VIP"
        },
        style = style.type.label.copy(fontWeight = FontWeight.SemiBold),
        color = c.accent,
        maxLines = 1,
        modifier = modifier
            .clip(style.chipShape)
            .border(1.dp, c.accent.copy(alpha = 0.6f), style.chipShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** A cover: the theme's generated sleeve, with the real one on top once it loads. */
@Composable
fun CoverImage(url: String?, seed: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier) {
        CoverArt(seed, Modifier.matchParentSize())
        RemoteImage(url, size, Modifier.matchParentSize())
    }
}

/**
 * The real cover as a painter for canvas drawing (the record label), or null until it has
 * loaded, so callers can draw the generated sleeve meanwhile.
 */
@Composable
fun rememberCoverPainter(url: String?, sizePx: Int): Painter? {
    if (url.isNullOrEmpty()) return null
    val context = LocalPlatformContext.current
    val request = remember(url, sizePx) { ImageRequest.Builder(context).data(sized(url, sizePx)).size(sizePx).build() }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsState()
    return painter.takeIf { state is AsyncImagePainter.State.Success }
}

/** The first character of [name] whole (an emoji is two chars, and half of one shows as �), or "?". */
fun initialOf(name: String): String = name.trim().let { if (it.isEmpty()) "?" else it.substring(0, it.offsetByCodePoints(0, 1)) }
