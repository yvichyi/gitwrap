package io.lanyin.player.ui.login

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.components.QrGlyph
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.ThemedButton
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.Lanyin

@Composable
fun LoginScreen(onBack: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    CompositionLocalProvider(LocalContentColor provides c.content) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                Text("扫码登录", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.weight(0.4f))
            QrFrame(Modifier.align(Alignment.CenterHorizontally))
            Text(
                "用网易云音乐 App 扫一扫",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
            Spacer(Modifier.weight(0.3f))
            Step(1, "保存二维码到相册")
            Step(2, "打开网易云音乐 → 扫一扫 → 右上角「相册」")
            Step(3, "在网易云里点「确认登录」，这边会自动进入")
            Spacer(Modifier.weight(0.6f))
            ThemedButton("保存二维码到相册（下一版开放）", {}, Modifier.fillMaxWidth(), enabled = false)
            Text(
                "有第二台设备？直接用它扫屏幕上的二维码也可以。",
                style = MaterialTheme.typography.bodySmall, color = c.muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun QrFrame(modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val scan by rememberInfiniteTransition(label = "scan").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "scanLine",
    )
    Box(modifier.size(236.dp).themedSurface().padding(22.dp), contentAlignment = Alignment.Center) {
        // Placeholder modules, faded: the real code arrives with the NetEase client in M1.
        QrGlyph(c.content.copy(alpha = 0.16f), Modifier.fillMaxSize(), seed = 7)
        Canvas(Modifier.fillMaxSize()) {
            val len = size.width * 0.18f
            val w = 4.dp.toPx()
            for ((corner, dir) in listOf(
                Offset(0f, 0f) to Offset(1f, 1f), Offset(size.width, 0f) to Offset(-1f, 1f),
                Offset(0f, size.height) to Offset(1f, -1f), Offset(size.width, size.height) to Offset(-1f, -1f),
            )) {
                drawLine(c.accent, corner, corner + Offset(len * dir.x, 0f), w, StrokeCap.Round)
                drawLine(c.accent, corner, corner + Offset(0f, len * dir.y), w, StrokeCap.Round)
            }
            val y = size.height * scan
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, c.accent.copy(alpha = 0.35f)), startY = y - 36.dp.toPx(), endY = y),
                topLeft = Offset(0f, y - 36.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 36.dp.toPx()),
            )
            drawLine(c.accent, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
        }
        Text(
            "下一版开放",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = c.content,
            modifier = Modifier.clip(style.chipShape).background(c.bg.copy(alpha = 0.9f)).padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val c = Lanyin.colors
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = c.accent)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
