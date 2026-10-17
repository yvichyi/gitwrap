package io.lanyin.player.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.lanyin.player.ui.design.TextLink
import io.lanyin.player.ui.design.ThemedButton
import io.lanyin.player.ui.theme.Lanyin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The share sheet for a line of lyric: the card as it will be sent, then 「分享」 (the system's
 * share sheet) and 「保存到相册」 (Pictures/澜音). [onDone] closes the sheet; [notify] reports how
 * it went.
 */
@Composable
fun ShareLyricSheet(lyric: CardLyric, onDone: () -> Unit, notify: (String) -> Unit) {
    val style = Lanyin.style
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }

    fun withCard(action: suspend (Bitmap) -> String?) {
        if (busy) return
        busy = true
        scope.launch {
            val message = runCatching { action(layer.toImageBitmap().asAndroidBitmap()) }.getOrElse { "没能生成图片：${it.message ?: "未知错误"}" }
            busy = false
            if (message != null) notify(message)
            onDone()
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text("分享这句歌词", style = style.type.title, color = style.colors.content)
        Text("卡片的样子跟着当前主题", style = style.type.caption, color = style.colors.muted, modifier = Modifier.padding(top = 2.dp))
        CardPreview(lyric, layer, Modifier.padding(top = 14.dp))
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            TextLink("保存到相册", { withCard { saveToPictures(context, it, lyric) } })
            ThemedButton("分享", { withCard { share(context, it, lyric); null } }, enabled = !busy)
        }
    }
}

/** The card at its own size, shown scaled to the sheet's width, and recorded into [layer] for the picture. */
@Composable
private fun CardPreview(lyric: CardLyric, layer: GraphicsLayer, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(CardWidth / CardHeight).clipToBounds(), contentAlignment = Alignment.Center) {
        val scale = maxWidth / CardWidth
        Box(
            Modifier
                .requiredSize(CardWidth, CardHeight)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            LyricCard(
                lyric,
                Modifier
                    .requiredSize(CardWidth, CardHeight)
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            )
        }
    }
}

/** The card as a PNG in the cache's shared folder, the only place the provider hands out. */
private suspend fun writeShared(context: Context, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    // One card at a time: the last one shared is replaced.
    dir.listFiles()?.forEach { it.delete() }
    File(dir, "lanyin-lyric-${System.currentTimeMillis()}.png").also { file ->
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private suspend fun share(context: Context, bitmap: Bitmap, lyric: CardLyric) {
    val file = writeShared(context, bitmap)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "「${lyric.line}」——《${lyric.title}》${lyric.artist}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "分享歌词").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Into Pictures/澜音 through MediaStore: no permission needed on Android 10 and later. */
private suspend fun saveToPictures(context: Context, bitmap: Bitmap, lyric: CardLyric): String = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "澜音-${lyric.title.take(20)}-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/澜音")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext "没能保存到相册"
    try {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("打不开相册")
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        "已保存到相册「Pictures/澜音」"
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        "没能保存到相册：${e.message ?: "未知错误"}"
    }
}
