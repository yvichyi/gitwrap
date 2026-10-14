package io.lanyin.player.ui.login

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.components.QrGlyph
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.ThemedButton
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.EdgeKind
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
                .padding(horizontal = style.margin, vertical = 8.dp),
        ) {
            Row(Modifier.padding(start = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(LineIcon.Back, "返回", onBack, Modifier.padding(end = 4.dp))
                Text("扫码登录", style = style.type.title)
            }
            Spacer(Modifier.weight(0.35f))
            QrFrame(Modifier.align(Alignment.CenterHorizontally))
            Text(
                "用网易云音乐扫一扫",
                style = style.type.title,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            )
            Spacer(Modifier.weight(0.25f))
            Step(1, "保存二维码到相册")
            Step(2, "打开网易云音乐，扫一扫，选右上角「相册」")
            Step(3, "在网易云里确认登录，这边会自动进入")
            Spacer(Modifier.weight(0.5f))
            ThemedButton("保存二维码到相册", {}, Modifier.fillMaxWidth(), enabled = false)
            Text(
                "下一版开放 · 也可以用第二台设备直接扫屏幕",
                style = style.type.caption, color = c.muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun QrFrame(modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    val scan by rememberInfiniteTransition(label = "scan").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse), label = "scanLine",
    )
    val mark = if (hairline) c.content else c.accent
    Box(
        modifier
            .size(232.dp)
            .then(if (hairline) Modifier.border(1.dp, c.outline) else Modifier.themedSurface(tone = io.lanyin.player.ui.design.SurfaceTone.Flat))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Placeholder modules, faded: the real code arrives with the NetEase client in M1.
        QrGlyph(c.content.copy(alpha = 0.12f), Modifier.fillMaxSize(), seed = 7)
        Canvas(Modifier.fillMaxSize()) {
            val len = size.width * 0.14f
            val w = (if (hairline) 1.5.dp else 3.dp).toPx()
            for ((corner, dir) in listOf(
                Offset(0f, 0f) to Offset(1f, 1f), Offset(size.width, 0f) to Offset(-1f, 1f),
                Offset(0f, size.height) to Offset(1f, -1f), Offset(size.width, size.height) to Offset(-1f, -1f),
            )) {
                drawLine(mark, corner, corner + Offset(len * dir.x, 0f), w, StrokeCap.Square)
                drawLine(mark, corner, corner + Offset(0f, len * dir.y), w, StrokeCap.Square)
            }
            val y = size.height * scan
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, mark.copy(alpha = 0.18f)), startY = y - 32.dp.toPx(), endY = y),
                topLeft = Offset(0f, y - 32.dp.toPx()), size = Size(size.width, 32.dp.toPx()),
            )
            drawLine(mark.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        Text(
            "下一版开放",
            style = style.type.label,
            color = c.content,
            modifier = Modifier.clip(style.chipShape).background(c.bg).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val style = Lanyin.style
    val c = style.colors
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$n",
            style = style.type.title.copy(fontFamily = style.display),
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = style.type.body)
    }
}
