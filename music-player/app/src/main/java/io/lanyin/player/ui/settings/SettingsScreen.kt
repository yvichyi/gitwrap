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

@Composable
fun SettingsScreen(settings: UiSettings, repository: SettingsRepository, onBack: () -> Unit) {
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

            item { SectionTitle("主题", Modifier.padding(start = m, end = m, top = 24.dp), trailing = settings.theme.tagline) }
            item {
                LazyRow(contentPadding = PaddingValues(start = m, end = m, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(AppTheme.entries) { theme ->
                        ThemeCard(theme, settings.themeMode, selected = theme == settings.theme) {
                            scope.launch { repository.setTheme(theme) }
                        }
                    }
                }
            }

            item { SectionTitle("深浅模式", Modifier.padding(start = m, end = m, top = 36.dp)) }
            item {
                Column(Modifier.padding(start = m, end = m, top = 14.dp)) {
                    ChoiceRow(ThemeMode.entries.map { it.label }, settings.themeMode.label, { label ->
                        val mode = ThemeMode.entries.first { it.label == label }
                        scope.launch { repository.setThemeMode(mode) }
                    })
                    if (settings.theme.darkOnly) {
                        Text(
                            "「${settings.theme.label}」只在夜里亮，始终使用深色。",
                            style = style.type.caption, color = c.muted, modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }

            item { SectionTitle("播放页样式", Modifier.padding(start = m, end = m, top = 36.dp)) }
            item {
                val spin = rememberSpinState(running = true)
                LazyRow(contentPadding = PaddingValues(start = m, end = m, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(PlayerSkin.entries) { skin -> SkinCard(skin, skin == settings.playerSkin, spin) { scope.launch { repository.setPlayerSkin(skin) } } }
                }
            }

            item { SectionTitle("播放", Modifier.padding(start = m, end = m, top = 36.dp)) }
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
                        Text("让歌词一行一行亮起", style = preview, color = if (style.edge == EdgeKind.Hairline) c.content else c.accent)
                        Text("唱片在掌心慢慢转动", style = preview, color = c.content.copy(alpha = style.lyric.inactiveAlpha))
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
                    if (style.edge == EdgeKind.Hairline) Hairline()
                }
            }
            item {
                Text("澜音 0.1 · 界面预览版", style = style.type.caption, color = c.muted, modifier = Modifier.fillMaxWidth().padding(m))
            }
        }
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
                        .then(if (selected && style.edge != EdgeKind.Retro) Modifier.border(2.dp, c.accent, style.cardShape) else Modifier)
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
