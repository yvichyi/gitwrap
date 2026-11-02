package io.lanyin.player.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.lanyin.player.data.settings.UiSettings
import io.lanyin.player.diagnostics.Diag
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.TextLink
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A settings group: on a surface, or between hairlines in 墨. */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val style = Lanyin.style
    val hairline = style.edge == EdgeKind.Hairline
    Column(modifier.then(if (hairline) Modifier else Modifier.themedSurface(tone = SurfaceTone.Flat).padding(horizontal = 16.dp))) {
        if (hairline) Hairline()
        content()
        if (hairline) Hairline()
    }
}

/** A line of settings that does something when tapped: title, a note under it, and [action] at the end. */
@Composable
internal fun ActionLine(title: String, note: String, action: String?, onClick: () -> Unit) {
    val style = Lanyin.style
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = style.type.body, color = style.colors.content)
            Text(note, style = style.type.caption, color = style.colors.muted)
        }
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            Text(action, style = style.type.caption, color = style.colors.accent)
        }
    }
}

/**
 * 后台播放: whether the system may stop 澜音 in the background to save battery, and the way to
 * let it run. Many phones (MIUI, ColorOS, OriginOS, HarmonyOS…) also keep their own list of apps
 * allowed to stay in the background; the note says where to find it on this phone.
 */
@Composable
internal fun BackgroundPlayback() {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(isExempt(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { exempt = isExempt(context) }
    ActionLine(
        "后台播放",
        if (exempt) "已允许澜音在后台运行，不受省电限制。${romHint()}" else "系统可能为了省电在后台停掉播放。点这里允许澜音在后台运行。${romHint()}",
        if (exempt) "系统设置" else "去允许",
    ) {
        val intents = buildList {
            if (!exempt) add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)))
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
        }
        // The first page this phone has.
        intents.firstOrNull { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
    }
}

private fun isExempt(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** Where this phone's own list of background apps lives, by maker. */
private fun romHint(): String = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi", "poco" -> "小米还要在「应用信息 → 省电策略」选「无限制」，并打开「自启动」。"
    "oppo", "realme", "oneplus" -> "OPPO / 一加还要在「应用信息 → 耗电管理」里允许后台运行。"
    "vivo", "iqoo" -> "vivo 还要在「i 管家 → 后台高耗电」里允许澜音。"
    "huawei", "honor" -> "华为 / 荣耀还要在「应用启动管理」里把澜音改成手动管理并全部打开。"
    "meizu" -> "魅族还要在「手机管家 → 后台管理」里允许澜音后台运行。"
    "samsung" -> "三星还要在「电池 → 后台使用限制」里把澜音设为「不受限」。"
    else -> ""
}

/**
 * 诊断: what this phone has recorded of stutters, errors and crashes, and a report of it to
 * share. It stays on the phone until shared; the report leaves out accounts, cookies and songs.
 */
@Composable
internal fun DiagnosticsLines(settings: UiSettings) {
    val context = LocalContext.current
    val diagnostics = Diag.current
    val scope = rememberCoroutineScope()
    var refreshed by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshed++ }
    val summary = remember(refreshed) { diagnostics?.summary() }
    val note = summary?.let {
        val share = if (it.frames > 0) "%.1f%%".format(it.slow * 100.0 / it.frames) else "0%"
        "记录了 ${it.frames} 帧，掉帧 $share，严重卡顿 ${it.stalls} 次；出错 ${it.errors} 次，闪退 ${it.crashes} 次"
    } ?: "还没有记录"
    ActionLine("导出诊断报告", "$note。报告只在你分享时离开手机，不含账号、Cookie 或听过的歌", "分享") {
        val d = diagnostics ?: return@ActionLine
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                d.flush()
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                File(dir, "lanyin-diagnostics.txt").apply { writeText(d.report(about(context, settings))) }
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "澜音诊断报告")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(send, "分享诊断报告").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("清空后从现在重新记录", style = Lanyin.style.type.caption, color = Lanyin.colors.muted, modifier = Modifier.weight(1f))
        TextLink("清空", {
            diagnostics?.clear()
            refreshed++
        })
    }
}

/** The app and the phone, for the head of a report. */
private fun about(context: Context, s: UiSettings): List<String> {
    val exempt = isExempt(context)
    return listOf(
        "版本 ${appVersion(context)}",
        "手机 ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）· 系统 ${Build.DISPLAY}",
        "主题 ${s.theme.label} · ${s.themeMode.id} · 播放页 ${s.playerSkin.label} · 减少动效 ${if (s.reduceMotion) "开" else "关"} · 桌面歌词 ${if (s.desktopLyrics) "开" else "关"} · 后台不受省电限制 ${if (exempt) "是" else "否"}",
    )
}

/** 「0.17.0-m16」 and its build number. */
internal fun appVersion(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    "${info.versionName}（$code）"
}.getOrDefault("未知")
