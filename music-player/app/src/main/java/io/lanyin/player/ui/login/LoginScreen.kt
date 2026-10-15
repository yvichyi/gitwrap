package io.lanyin.player.ui.login

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.LocalAppServices
import io.lanyin.player.ui.components.QrGlyph
import io.lanyin.player.ui.design.Avatar
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.IndexTag
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.TextLink
import io.lanyin.player.ui.design.ThemedButton
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The login screen wired to NetEase: runs the QR login while it is open, saves the code to the
 * gallery, opens NetEase Cloud Music, and offers a pasted cookie as a fallback.
 */
@Composable
fun LoginRoute(onBack: () -> Unit, onLoggedIn: () -> Unit) {
    val services = LocalAppServices.current
    if (services == null) {
        // Previews and screenshots: show what a fresh code looks like.
        val url = "https://music.163.com/login?codekey=preview"
        LoginScreen(remember { QrPhase.Showing(url, QrMatrix.encode(url)) }, onBack = onBack)
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var cookieOpen by remember { mutableStateOf(false) }
    val controller = remember {
        QrLoginController(services.api) {
            services.account.refresh()
            saved?.let { deleteSavedQr(context, it.second) }
            delay(900)
            onLoggedIn()
        }
    }
    LaunchedEffect(attempt) { controller.run() }
    val phase = controller.phase
    val showing = phase as? QrPhase.Showing
    // A new code makes the saved picture useless: take it out of the gallery.
    LaunchedEffect(showing?.url) {
        val s = saved
        if (s != null && showing != null && s.first != showing.url) {
            deleteSavedQr(context, s.second)
            saved = null
            notice = "二维码换新了，需要的话再保存一次"
        }
    }

    LoginScreen(
        phase = phase,
        onBack = onBack,
        saved = saved != null && saved?.first == showing?.url,
        notice = notice,
        onSave = {
            showing?.let { p ->
                scope.launch {
                    val uri = saveQrToGallery(context, p.matrix)
                    if (uri != null) {
                        saved = p.url to uri
                        notice = "已存到相册「澜音」。打开网易云 → 扫一扫 → 右上角「相册」选它"
                    } else {
                        notice = "没能保存。可以截屏，再在网易云里从相册识别"
                    }
                }
            }
        },
        onOpenNetease = { if (!openNetease(context)) notice = "这台手机上没有找到网易云音乐" },
        onOpenLink = { showing?.let { if (!openLoginLinkInNetease(context, it.url)) notice = "网易云没有接这个链接，用相册的办法吧" } },
        onRetry = { attempt++ },
        onUseCookie = { cookieOpen = true },
    )
    if (cookieOpen) {
        CookieLoginDialog(
            onDismiss = { cookieOpen = false },
            onSubmit = { services.account.loginWithCookie(it) != null },
            onDone = {
                cookieOpen = false
                onLoggedIn()
            },
        )
    }
}

@Composable
fun LoginScreen(
    phase: QrPhase,
    onBack: () -> Unit,
    saved: Boolean = false,
    notice: String? = null,
    onSave: () -> Unit = {},
    onOpenNetease: () -> Unit = {},
    onOpenLink: () -> Unit = {},
    onRetry: () -> Unit = {},
    onUseCookie: () -> Unit = {},
) {
    val style = Lanyin.style
    val c = style.colors
    val showing = phase as? QrPhase.Showing
    CompositionLocalProvider(LocalContentColor provides c.content) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = style.margin, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(LineIcon.Back, "返回", onBack, Modifier.padding(end = 4.dp))
                Text("扫码登录", style = style.type.title)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                QrFrame(phase, onRetry, Modifier.align(Alignment.CenterHorizontally).padding(top = 28.dp))
                AnimatedContent(
                    targetState = headline(phase),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "loginHeadline",
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) { text ->
                    Text(text, style = style.type.title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.size(20.dp))
                Step(1, "保存二维码到相册")
                Step(2, "打开网易云音乐，扫一扫，选右上角「相册」")
                Step(3, "在网易云里确认登录，这边会自动进入")
            }
            if (notice != null) {
                Text(
                    notice,
                    style = style.type.caption,
                    color = c.content.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            if (saved) {
                ThemedButton("打开网易云音乐", onOpenNetease, Modifier.fillMaxWidth())
            } else {
                ThemedButton("保存二维码到相册", onSave, Modifier.fillMaxWidth(), enabled = showing != null && showing.scanned == null)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextLink("直接在网易云里打开", onOpenLink, color = if (showing != null) c.content else c.muted)
                TextLink("用 Cookie 登录", onUseCookie, color = c.muted)
            }
            Text(
                "也可以用另一台手机或平板直接扫屏幕",
                style = style.type.caption, color = c.muted, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

private fun headline(phase: QrPhase): String = when (phase) {
    QrPhase.Loading -> "正在准备二维码"
    is QrPhase.Showing -> phase.scanned?.let { "${it.nickname ?: "已扫码"}，在手机上点「确认登录」" } ?: "用网易云音乐扫一扫"
    is QrPhase.Error -> phase.message
    QrPhase.Success -> "登录成功"
}

@Composable
private fun QrFrame(phase: QrPhase, onRetry: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    val showing = phase as? QrPhase.Showing
    val live = showing != null && showing.scanned == null
    val scan by rememberInfiniteTransition(label = "scan").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse), label = "scanLine",
    )
    // The code itself dims once scanned or when something went wrong.
    val codeAlpha by animateFloatAsState(if (live) 1f else 0.12f, tween(400), label = "qrAlpha")
    val mark = if (hairline) c.content else c.accent
    Box(
        modifier
            .size(240.dp)
            .then(if (hairline) Modifier.border(1.dp, c.outline) else Modifier.themedSurface(tone = SurfaceTone.Flat))
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        // A white plate in every theme, dark modules on it: scanners need the contrast.
        Box(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(if (style.hardEdge || hairline) androidx.compose.ui.graphics.RectangleShape else androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = codeAlpha }) {
                if (showing != null) QrCode(showing.matrix, Modifier.fillMaxSize()) else QrGlyph(QR_INK, Modifier.fillMaxSize(), seed = 7)
            }
        }
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
            if (live) {
                // The scan line sweeps the margin side to side, never across the modules for long.
                val y = size.height * scan
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, mark.copy(alpha = 0.12f)), startY = y - 28.dp.toPx(), endY = y),
                    topLeft = Offset(0f, y - 28.dp.toPx()), size = Size(size.width, 28.dp.toPx()),
                )
                drawLine(mark.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
        }
        when (phase) {
            QrPhase.Loading -> Badge("正在获取…")
            is QrPhase.Showing -> phase.scanned?.let { scanned ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(scanned.avatarUrl, scanned.nickname.orEmpty(), 64.dp)
                    Spacer(Modifier.size(10.dp))
                    Badge("已扫码，等你确认")
                }
            }
            is QrPhase.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Badge(if (phase.expired) "已过期" else "没有成功")
                Spacer(Modifier.size(10.dp))
                TextLink("刷新二维码", onRetry, color = Color(0xFF141414))
            }
            QrPhase.Success -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).clip(style.roundShape).background(c.accent), contentAlignment = Alignment.Center) {
                    ThemedIcon(LineIcon.Check, null, tint = c.onAccent, size = 28.dp)
                }
            }
        }
    }
}

private val QR_INK = Color(0xFF141414)

/** The real code: square modules, finder patterns drawn as solid rings. */
@Composable
private fun QrCode(matrix: QrMatrix, modifier: Modifier) {
    Canvas(modifier) {
        val n = matrix.size
        val cell = size.minDimension / n
        val inset = (size.minDimension - cell * n) / 2f
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix[x, y]) {
                // Overlap neighbours by a hair so no seams show between modules.
                drawRect(QR_INK, Offset(inset + x * cell, inset + y * cell), Size(cell + 0.6f, cell + 0.6f))
            }
        }
        // Soften the three finder patterns' outer corners a touch; the pattern stays intact.
        for ((fx, fy) in listOf(0 to 0, n - 7 to 0, 0 to n - 7)) {
            val o = Offset(inset + fx * cell, inset + fy * cell)
            drawRoundRect(Color.White, o, Size(cell * 7, cell * 7), CornerRadius(0f))
            drawRoundRect(QR_INK, o, Size(cell * 7, cell * 7), CornerRadius(cell * 1.6f))
            drawRoundRect(Color.White, o + Offset(cell, cell), Size(cell * 5, cell * 5), CornerRadius(cell * 1.0f))
            drawRoundRect(QR_INK, o + Offset(cell * 2, cell * 2), Size(cell * 3, cell * 3), CornerRadius(cell * 0.7f))
        }
    }
}

@Composable
private fun Badge(text: String) {
    val style = Lanyin.style
    Text(
        text,
        style = style.type.label,
        color = QR_INK,
        modifier = Modifier
            .clip(style.chipShape)
            .background(Color.White)
            .border(1.dp, QR_INK.copy(alpha = 0.2f), style.chipShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Step(n: Int, text: String) {
    val style = Lanyin.style
    val c = style.colors
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (style.edge == EdgeKind.Brutal) {
            IndexTag(n)
        } else {
            Text(
                "$n",
                style = style.type.title.copy(fontFamily = style.display),
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = style.type.body)
    }
}
