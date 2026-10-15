package io.lanyin.player.ui.login

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.core.netease.NeteaseException
import io.lanyin.player.core.netease.QrStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A QR code's modules, row by row, without the quiet zone. */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]

    companion object {
        fun encode(text: String): QrMatrix {
            val code = Encoder.encode(text, ErrorCorrectionLevel.M, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
            val m = code.matrix
            return QrMatrix(m.width, BooleanArray(m.width * m.height) { i -> m.get(i % m.width, i / m.width).toInt() == 1 })
        }
    }
}

/** What the login screen shows. */
sealed interface QrPhase {
    data object Loading : QrPhase
    data class Showing(val url: String, val matrix: QrMatrix, val scanned: QrStatus.Scanned? = null) : QrPhase
    data class Error(val message: String, val expired: Boolean = false) : QrPhase
    data object Success : QrPhase
}

/**
 * Runs one QR login: gets a code, shows it, and asks NetEase every two seconds whether it has
 * been scanned and confirmed. An expired code is replaced on its own a few times, then it waits
 * for a tap so a forgotten screen doesn't poll forever.
 */
class QrLoginController(private val api: NeteaseApi, private val onConfirmed: suspend () -> Unit) {
    var phase by mutableStateOf<QrPhase>(QrPhase.Loading)
        private set

    suspend fun run() {
        var codes = 0
        while (true) {
            phase = QrPhase.Loading
            val key = try {
                api.qrKey()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                phase = QrPhase.Error(describe(e))
                return
            }
            codes++
            val url = api.qrUrl(key)
            phase = QrPhase.Showing(url, withContext(Dispatchers.Default) { QrMatrix.encode(url) })
            var failures = 0
            while (true) {
                delay(POLL_MS)
                val status = try {
                    api.qrCheck(key).also { failures = 0 }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (++failures >= 5) {
                        phase = QrPhase.Error(describe(e))
                        return
                    }
                    continue
                }
                when (status) {
                    QrStatus.Waiting -> Unit
                    is QrStatus.Scanned -> (phase as? QrPhase.Showing)?.let { phase = it.copy(scanned = status) }
                    QrStatus.Confirmed -> {
                        phase = QrPhase.Success
                        onConfirmed()
                        return
                    }
                    QrStatus.Expired -> {
                        if (codes >= MAX_CODES) {
                            phase = QrPhase.Error("二维码过期了", expired = true)
                            return
                        }
                        break
                    }
                    is QrStatus.Failed -> {
                        phase = QrPhase.Error(status.message?.takeIf { it.isNotBlank() } ?: "登录没有成功（${status.code}）")
                        return
                    }
                }
            }
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is NeteaseException -> e.message ?: "网易云返回 ${e.code}"
        is IOException -> "连不上网易云，检查一下网络"
        else -> "出了点问题：${e.message ?: e::class.simpleName}"
    }

    companion object {
        const val POLL_MS = 2_000L
        const val MAX_CODES = 4
    }
}

internal const val NETEASE_PACKAGE = "com.netease.cloudmusic"

/**
 * Draws the code black on white with a wide quiet zone and a caption, and adds it to the
 * gallery under Pictures/澜音, where NetEase's 「扫一扫 → 相册」 can read it. Returns its Uri.
 */
suspend fun saveQrToGallery(context: Context, matrix: QrMatrix): Uri? = withContext(Dispatchers.IO) {
    val module = 24
    val quiet = 4
    val side = (matrix.size + quiet * 2) * module
    val captionHeight = 120
    val bitmap = Bitmap.createBitmap(side, side + captionHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val ink = Paint().apply { color = android.graphics.Color.BLACK }
    for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
        if (matrix[x, y]) {
            val l = ((x + quiet) * module).toFloat()
            val t = ((y + quiet) * module).toFloat()
            canvas.drawRect(l, t, l + module, t + module, ink)
        }
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF555555.toInt()
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("澜音 · 用网易云「扫一扫 → 相册」识别", side / 2f, side + 40f, text)

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "lanyin-login-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/澜音")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
    try {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("no stream")
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        uri
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        null
    } finally {
        bitmap.recycle()
    }
}

/** Removes a saved code once it has done its job; it can't be used again anyway. */
suspend fun deleteSavedQr(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
    runCatching { context.contentResolver.delete(uri, null, null) }
}

/** Opens NetEase Cloud Music. False if it isn't installed. */
fun openNetease(context: Context): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(NETEASE_PACKAGE) ?: return false
    return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

/**
 * Hands the login link straight to NetEase Cloud Music, which may offer to confirm it without
 * scanning anything. Not every version handles the link; false if nothing took it.
 */
fun openLoginLinkInNetease(context: Context, url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(NETEASE_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
