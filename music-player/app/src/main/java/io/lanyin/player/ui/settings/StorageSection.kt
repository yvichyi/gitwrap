package io.lanyin.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.lanyin.player.playback.CacheLimits
import io.lanyin.player.playback.StorageUsage
import io.lanyin.player.ui.LocalAppServices
import io.lanyin.player.ui.design.ChoiceRow
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.music.byteSize
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A cache limit as the chips show it: 「512 MB」「2 GB」. */
fun limitLabel(bytes: Long): String = if (bytes >= 1L shl 30) "${bytes shr 30} GB" else "${bytes shr 20} MB"

/**
 * What the app keeps on the phone, and ways to make room: the listening cache (with its limit),
 * images, and downloads. Sizes are read when shown and again after each change.
 */
@Composable
fun StorageSection(limit: Long, onLimit: (Long) -> Unit, modifier: Modifier = Modifier, preview: StorageUsage? = null) {
    val style = Lanyin.style
    val c = style.colors
    val services = LocalAppServices.current
    val storage = services?.storage
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf(preview) }
    var reads by remember { mutableIntStateOf(0) }
    val downloads = services?.downloads?.items?.collectAsState()?.value
    LaunchedEffect(reads, limit, downloads?.size) {
        if (storage != null) usage = runCatching { storage.usage() }.getOrNull() ?: usage
    }
    fun after(message: String, work: suspend () -> Unit) {
        scope.launch {
            work()
            services?.notices?.show(message)
            reads++
        }
    }
    val u = usage
    Column(modifier.then(if (style.edge == EdgeKind.Hairline) Modifier else Modifier.themedSurface(tone = SurfaceTone.Flat).padding(horizontal = 16.dp))) {
        if (style.edge == EdgeKind.Hairline) Hairline()
        StorageRow(
            "边听边存",
            if (u == null) "听过的歌留在手机里，再听不用流量" else "已用 ${byteSize(u.audioBytes)} / ${limitLabel(limit)}，满了先删最久没听的",
            action = "清空",
            enabled = (u?.audioBytes ?: 0) > 0,
            onAction = { after("已清空边听边存的歌") { storage?.clearAudio() } },
        )
        Gauge(if (u == null) 0f else u.audioBytes.toFloat() / limit, Modifier.padding(bottom = 12.dp))
        ChoiceRow(
            CacheLimits.map(::limitLabel), limitLabel(limit),
            { label -> CacheLimits.firstOrNull { limitLabel(it) == label }?.let(onLimit) },
            Modifier.padding(bottom = 14.dp).horizontalScroll(rememberScrollState()),
        )
        Hairline()
        StorageRow(
            "封面和头像",
            if (u == null) "看过的图片，下次打开更快" else "已用 ${byteSize(u.imageBytes)}",
            action = "清空",
            enabled = (u?.imageBytes ?: 0) > 0,
            onAction = { after("已清空图片缓存") { storage?.clearImages() } },
        )
        Hairline()
        var confirming by remember { mutableStateOf(false) }
        LaunchedEffect(confirming) {
            if (confirming) {
                delay(4_000)
                confirming = false
            }
        }
        StorageRow(
            "下载的歌",
            if (u == null) "离线能听的歌" else if (u.downloadCount == 0) "还没有下载" else "${u.downloadCount} 首 · ${byteSize(u.downloadBytes)}",
            action = if (confirming) "确认删除" else "全部删除",
            enabled = (u?.downloadCount ?: 0) > 0 || (u?.downloadBytes ?: 0) > 0,
            danger = true,
            emphasized = confirming,
            onAction = {
                if (!confirming) {
                    confirming = true
                } else {
                    confirming = false
                    storage?.removeAllDownloads()
                    services?.notices?.show("已删除全部下载")
                    reads++
                }
            },
        )
        if (style.edge == EdgeKind.Hairline) Hairline()
    }
}

@Composable
private fun StorageRow(
    title: String,
    detail: String,
    action: String,
    enabled: Boolean,
    onAction: () -> Unit,
    danger: Boolean = false,
    emphasized: Boolean = false,
) {
    val style = Lanyin.style
    val c = style.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = style.type.body)
            Text(detail, style = style.type.caption, color = c.muted)
        }
        Spacer(Modifier.width(12.dp))
        val tint: Color = when {
            !enabled -> c.muted.copy(alpha = 0.5f)
            danger -> c.like
            else -> c.accent
        }
        Text(
            action,
            style = style.type.caption.copy(fontWeight = FontWeight.Medium),
            color = if (emphasized) c.onAccent else tint,
            modifier = Modifier
                .clip(style.chipShape)
                .then(if (emphasized) Modifier.background(c.like) else Modifier)
                .pressable(enabled = enabled, onClick = onAction)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun Gauge(fraction: Float, modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.content.copy(alpha = 0.1f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(if (fraction > 0.92f) c.like else c.accent))
    }
}
