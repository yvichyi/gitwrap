package io.lanyin.player.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.UiSettings
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.Backdrop
import io.lanyin.player.ui.design.ChoiceRow
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.ThemedSwitch
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.player.PlayerArtwork
import io.lanyin.player.ui.player.rememberSpinState
import io.lanyin.player.ui.home.greeting
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.LyricSize
import io.lanyin.player.ui.theme.PlayerSkin
import io.lanyin.player.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import io.lanyin.player.data.account.AccountState
import io.lanyin.player.ui.design.Avatar
import io.lanyin.player.ui.design.VipBadge
import io.lanyin.player.ui.design.TextLink
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun SettingsScreen(
    settings: UiSettings,
    repository: SettingsRepository,
    onBack: () -> Unit,
    account: AccountState = AccountState.LoggedOut,
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    /** Stands in for the phone's storage, for previews and screenshots. */
    storagePreview: io.lanyin.player.playback.StorageUsage? = null,
) {
    val scope = rememberCoroutineScope()
    val style = Lanyin.style
    val c = style.colors
    val m = style.margin
    CompositionLocalProvider(LocalContentColor provides c.content) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding(withBottomChrome = false, extraTop = 4.dp)) {
            item {
                Row(Modifier.padding(horizontal = m - 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(LineIcon.Back, "返回", onBack)
                    Text("外观与播放", style = style.type.title)
                }
            }

            item { SectionTitle("主题", Modifier.padding(start = m, end = m, top = 24.dp), trailing = settings.theme.tagline, index = 1) }
            item {
                // Open on the theme in use, with its neighbour peeking in before it.
                val themes = androidx.compose.foundation.lazy.rememberLazyListState(
                    initialFirstVisibleItemIndex = (settings.theme.ordinal - 1).coerceAtLeast(0),
                )
                LazyRow(state = themes, contentPadding = PaddingValues(start = m, end = m, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(AppTheme.entries) { theme ->
                        ThemeCard(theme, settings.themeMode, selected = theme == settings.theme) {
                            scope.launch { repository.setTheme(theme) }
                        }
                    }
                }
            }

            item { SectionTitle("深浅模式", Modifier.padding(start = m, end = m, top = 36.dp), index = 2) }
            item {
                Column(Modifier.padding(start = m, end = m, top = 14.dp)) {
                    ChoiceRow(ThemeMode.entries.map { it.label }, settings.themeMode.label, { label ->
                        val mode = ThemeMode.entries.first { it.label == label }
                        scope.launch { repository.setThemeMode(mode) }
                    })
                    if (settings.theme.darkOnly || settings.theme.lightOnly) {
                        Text(
                            if (settings.theme.darkOnly) "「${settings.theme.label}」只在夜里亮，始终使用深色。" else "「${settings.theme.label}」是印在纸上的，始终使用浅色。",
                            style = style.type.caption, color = c.muted, modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }

            item { SectionTitle("播放页样式", Modifier.padding(start = m, end = m, top = 36.dp), index = 3) }
            item {
                val spin = rememberSpinState(running = true)
                LazyRow(contentPadding = PaddingValues(start = m, end = m, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(PlayerSkin.entries) { skin -> SkinCard(skin, skin == settings.playerSkin, spin) { scope.launch { repository.setPlayerSkin(skin) } } }
                }
            }
            item {
                Column(Modifier.padding(start = m, end = m, top = 14.dp).then(if (style.edge == EdgeKind.Hairline) Modifier else Modifier.themedSurface(tone = SurfaceTone.Flat).padding(horizontal = 16.dp))) {
                    if (style.edge == EdgeKind.Hairline) Hairline()
                    ToggleRow("跟随封面取色", "播放页的强调色取自正在放的封面，换歌时慢慢过渡", settings.followCover) {
                        scope.launch { repository.setFollowCover(it) }
                    }
                    if (style.edge == EdgeKind.Hairline) Hairline()
                }
            }

            item { SectionTitle("播放", Modifier.padding(start = m, end = m, top = 36.dp), index = 4) }
            item {
                Column(Modifier.padding(start = m, end = m, top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("歌词大小", style = style.type.body, modifier = Modifier.weight(1f))
                        ChoiceRow(LyricSize.entries.map { it.label }, settings.lyricSize.label, { label ->
                            val size = LyricSize.entries.first { it.label == label }
                            scope.launch { repository.setLyricSize(size) }
                        })
                    }
                    // What the player will show at this size.
                    val preview = androidx.compose.material3.MaterialTheme.typography.headlineSmall.copy(
                        // Same size as the player: the in-app setting, not the system font scale.
                        fontSize = (settings.lyricSize.sp / androidx.compose.ui.platform.LocalDensity.current.fontScale).sp,
                        lineHeight = (settings.lyricSize.sp * 1.36f / androidx.compose.ui.platform.LocalDensity.current.fontScale).sp,
                        fontFamily = if (style.lyric.serif) style.display else null,
                        fontWeight = if (style.lyric.serif) FontWeight.Medium else FontWeight.Bold,
                    )
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        if (style.edge == EdgeKind.Brutal) {
                            // 格's highlighter block, as on the player.
                            io.lanyin.player.ui.player.SweepText(
                                "让歌词一行一行亮起", preview, baseColor = c.content, sweepColor = c.content, progress = { 1f }, glow = null,
                                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                                block = io.lanyin.player.ui.theme.GridYellow, blockEdge = c.outline,
                            )
                        } else {
                            Text("让歌词一行一行亮起", style = preview, color = if (style.edge == EdgeKind.Hairline) c.content else c.accent)
                        }
                        Text("唱片在掌心慢慢转动", style = preview, color = c.content.copy(alpha = style.lyric.inactiveAlpha))
                    }
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("在线音质", style = style.type.body)
                            Text("没有这个音质的歌会自动降一档；超清母带需要 SVIP", style = style.type.caption, color = c.muted)
                        }
                    }
                    ChoiceRow(
                        io.lanyin.player.core.model.Quality.entries.reversed().map { it.label },
                        settings.quality.label,
                        { label -> scope.launch { repository.setQuality(io.lanyin.player.core.model.Quality.entries.first { it.label == label }) } },
                        Modifier.padding(top = 10.dp, bottom = 16.dp).horizontalScroll(rememberScrollState()),
                    )
                    val openSheet = io.lanyin.player.ui.music.LocalOpenSheet.current
                    Row(
                        Modifier.fillMaxWidth().pressable { openSheet(io.lanyin.player.ui.music.Sheet.Effects) }.padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("音效", style = style.type.body)
                            Text("45 个预设和十段均衡器，播放页上也能调", style = style.type.caption, color = c.muted)
                        }
                        Text(settings.effects.preset.label, style = style.type.caption, color = if (settings.effects.enabled) c.accent else c.muted)
                        ThemedIcon(LineIcon.Forward, null, tint = c.muted, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            item {
                Column(Modifier.padding(start = m, end = m, top = 6.dp).then(if (style.edge == EdgeKind.Hairline) Modifier else Modifier.padding(top = 8.dp).themedSurface(tone = SurfaceTone.Flat).padding(horizontal = 16.dp))) {
                    if (style.edge == EdgeKind.Hairline) Hairline()
                    ToggleRow("打开播放页时自动横屏", "也可以随时点播放页右上角的旋转按钮", settings.autoLandscape) {
                        scope.launch { repository.setAutoLandscape(it) }
                    }
                    Hairline()
                    ToggleRow("横屏时保持屏幕常亮", "把手机立在桌上，当歌词屏用", settings.keepScreenOnInLandscape) {
                        scope.launch { repository.setKeepScreenOnInLandscape(it) }
                    }
                    Hairline()
                    ToggleRow("只在 Wi-Fi 下下载", "用流量时，下载会等连上 Wi-Fi 再开始", settings.downloadOnWifiOnly) {
                        scope.launch { repository.setDownloadOnWifiOnly(it) }
                    }
                    Hairline()
                    ToggleRow("同步播放记录", "把在澜音听的歌记到网易云账号里，「最近播放」和「听歌排行」都会有", settings.syncPlays) {
                        scope.launch { repository.setSyncPlays(it) }
                    }
                    Hairline()
                    ToggleRow("状态栏歌词", "魅族 Flyme 直接显示；澎湃、ColorOS 等装了 Lyric Getter 或 SuperLyric 模块也能显示", settings.statusBarLyrics) {
                        scope.launch { repository.setStatusBarLyrics(it) }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 36 && settings.statusBarLyrics) {
                        Hairline()
                        LyricCapsuleRow(settings.lyricCapsule) { scope.launch { repository.setLyricCapsule(it) } }
                    }
                    if (style.edge == EdgeKind.Hairline) Hairline()
                }
            }
            item { SectionTitle("存储", Modifier.padding(start = m, end = m, top = 36.dp), index = 5) }
            item {
                StorageSection(
                    settings.cacheLimit, { limit -> scope.launch { repository.setCacheLimit(limit) } },
                    Modifier.padding(start = m, end = m, top = 14.dp), preview = storagePreview,
                )
            }
            item { SectionTitle("账号", Modifier.padding(start = m, end = m, top = 36.dp), index = 6) }
            item { AccountRow(account, onLogin, onLogout, Modifier.padding(start = m, end = m, top = 14.dp)) }
            item {
                Text("澜音 0.7 · 预览版", style = style.type.caption, color = c.muted, modifier = Modifier.fillMaxWidth().padding(m))
            }
        }
    }
}

/** Who is signed in, and the way out; signing out asks twice. */
@Composable
private fun AccountRow(account: AccountState, onLogin: () -> Unit, onLogout: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    var confirming by remember(account) { mutableStateOf(false) }
    Column(modifier.then(if (hairline) Modifier else Modifier.themedSurface(tone = SurfaceTone.Flat).padding(horizontal = 16.dp))) {
        if (hairline) Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            val profile = (account as? AccountState.LoggedIn)?.profile
            if (profile != null) {
                Avatar(profile.avatarUrl, profile.nickname, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.nickname, style = style.type.body, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("网易云音乐", style = style.type.caption, color = c.muted, maxLines = 1)
                }
                VipBadge(profile.vipType, Modifier.padding(horizontal = 10.dp))
                TextLink(if (confirming) "再点一次退出" else "退出登录", { if (confirming) onLogout() else confirming = true }, color = if (confirming) c.accent else c.muted)
            } else {
                Column(Modifier.weight(1f)) {
                    Text(if (account == AccountState.Loading) "正在连接…" else "未登录", style = style.type.body, maxLines = 1)
                    Text("登录网易云音乐后同步歌单和红心", style = style.type.caption, color = c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                if (account == AccountState.LoggedOut) TextLink("扫码登录", onLogin)
            }
        }
        if (hairline) Hairline()
    }
}

/** A miniature of the app rendered in [theme], so each theme shows its own shapes and backdrop. */
@Composable
private fun ThemeCard(theme: AppTheme, mode: ThemeMode, selected: Boolean, onClick: () -> Unit) {
    val outer = Lanyin.style
    val frame = if (outer.edge == EdgeKind.Hairline) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(22.dp)
    val inner = if (outer.edge == EdgeKind.Hairline) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(18.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(128.dp, 196.dp)
                .border(if (selected) 1.5.dp else 1.dp, if (selected) outer.colors.content else outer.colors.outline, frame)
                .padding(5.dp)
                .clip(inner)
                .pressable(onClick = onClick),
        ) {
            // A miniature: drawn at a fixed text size so a large system font can't burst it.
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale = 1f),
            ) {
                LanyinTheme(theme = theme, mode = mode, applySystemBars = false) { ThemeThumbnail() }
            }
        }
        Text(
            theme.label,
            style = outer.type.body.copy(fontFamily = outer.display, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) outer.colors.content else outer.colors.muted,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun ThemeThumbnail() {
    val style = Lanyin.style
    val c = style.colors
    Box(Modifier.fillMaxSize()) {
        Backdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Text(greeting(java.time.LocalTime.now().hour), style = style.type.display.copy(fontSize = 16.sp, lineHeight = 20.sp), color = c.content)
            Spacer(Modifier.height(12.dp))
            CoverArt(3, Modifier.size(54.dp).clip(style.artShape))
            Spacer(Modifier.height(10.dp))
            Text(
                "潮水退去以后",
                style = style.type.caption.copy(fontFamily = if (style.lyric.serif) style.display else null, fontWeight = FontWeight.Bold),
                color = if (style.edge == EdgeKind.Hairline) c.content else c.accent,
            )
            Text(
                "沙上留着谁的脚印",
                style = style.type.caption.copy(fontSize = 11.sp, fontFamily = if (style.lyric.serif) style.display else null),
                color = c.content.copy(alpha = 0.5f),
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .then(if (style.edge == EdgeKind.Hairline) Modifier.border(1.dp, c.outline) else Modifier.themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(16.dp).clip(CircleShape).border(3.dp, c.vinyl, CircleShape))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).height(2.dp).background(c.content.copy(alpha = 0.2f)))
                Spacer(Modifier.width(6.dp))
                Canvas(Modifier.size(9.dp)) { drawPlayPauseGlyph(0f, c.content) }
            }
        }
    }
}

@Composable
private fun SkinCard(skin: PlayerSkin, selected: Boolean, spin: io.lanyin.player.ui.player.SpinState, onClick: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    Column(
        Modifier
            .width(132.dp)
            .then(
                if (hairline) {
                    Modifier.border(if (selected) 1.5.dp else 1.dp, if (selected) c.content else c.outline).pressable(onClick = onClick)
                } else {
                    Modifier.themedSurface(tone = if (selected) SurfaceTone.Card else SurfaceTone.Flat, onClick = onClick)
                        .then(if (selected && !style.hardEdge) Modifier.border(2.dp, c.accent, style.cardShape) else Modifier)
                },
            )
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(108.dp), contentAlignment = Alignment.Center) {
            if (skin == PlayerSkin.LYRICS) {
                ThemedIcon(LineIcon.Lyrics, null, size = 44.dp)
            } else {
                PlayerArtwork(skin, isPlaying = selected, spin = spin, coverSeed = 3, modifier = Modifier.height(104.dp))
            }
        }
        Text(skin.label, style = style.type.body.copy(fontWeight = FontWeight.Medium), modifier = Modifier.padding(top = 6.dp))
        Text(skin.description, style = style.type.caption.copy(fontSize = 11.sp), color = c.muted, minLines = 2, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/**
 * Android 16's Live Update capsule for the lyrics. The system can turn these off per app, so
 * when it has, say so and offer the way to its setting.
 */
@Composable
private fun LyricCapsuleRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val allowed = androidx.compose.runtime.remember { io.lanyin.player.playback.LyricCapsule(context).allowed() }
    ToggleRow("状态栏胶囊", if (allowed) "Android 16 的实时活动：播放时状态栏里有一枚显示当前歌词的胶囊" else "系统关掉了澜音的实时活动，点这里打开后才能显示", checked) {
        if (!allowed) {
            runCatching {
                context.startActivity(
                    android.content.Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        onChange(it)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val style = Lanyin.style
    Row(
        Modifier.fillMaxWidth().pressable { onChange(!checked) }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = style.type.body)
            Text(subtitle, style = style.type.caption, color = style.colors.muted)
        }
        Spacer(Modifier.width(12.dp))
        ThemedSwitch(checked, onChange)
    }
}
