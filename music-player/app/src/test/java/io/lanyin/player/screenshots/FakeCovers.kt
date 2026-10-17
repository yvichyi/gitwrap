package io.lanyin.player.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult

/**
 * Stands in for the network: every image asked for comes back at once as a picture made from its
 * address (bold diagonal bands and a disc), so screenshots show what real covers look like in
 * each theme instead of the generated sleeves.
 */
object FakeCovers {
    fun install(context: Context) {
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components { add(Covers) }.build(),
        )
    }

    private object Covers : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
            SuccessResult(cover(chain.request.data.toString()).asImage(), chain.request, DataSource.MEMORY)
    }

    private val palettes = listOf(
        intArrayOf(0xFFE94F37.toInt(), 0xFF393E41.toInt(), 0xFFF6F7EB.toInt()),
        intArrayOf(0xFF2E86AB.toInt(), 0xFFF6C85F.toInt(), 0xFF1B1B3A.toInt()),
        intArrayOf(0xFF6A994E.toInt(), 0xFFF2E8CF.toInt(), 0xFFBC4749.toInt()),
        intArrayOf(0xFF7B2CBF.toInt(), 0xFFFF9E00.toInt(), 0xFF10002B.toInt()),
        intArrayOf(0xFF00A6A6.toInt(), 0xFFEFCA08.toInt(), 0xFFF08700.toInt()),
    )

    fun cover(key: String, size: Int = 400): Bitmap {
        val p = palettes[Math.floorMod(key.hashCode(), palettes.size)]
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), p[0], p[2], Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        paint.color = p[1]
        for (k in -2..6 step 2) {
            val x = k * size / 5f
            canvas.drawPath(android.graphics.Path().apply {
                moveTo(x, 0f); lineTo(x + size / 10f, 0f); lineTo(x + size / 10f + size * 0.6f, size.toFloat()); lineTo(x + size * 0.6f, size.toFloat()); close()
            }, paint)
        }
        paint.color = p[2]
        canvas.drawCircle(size * 0.68f, size * 0.36f, size * 0.17f, paint)
        return bitmap
    }
}
