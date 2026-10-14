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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import io.lanyin.player.ui.design.CoverArt
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemedChip
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.player.PlayerArtwork
import io.lanyin.player.ui.player.rememberSpinState
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.PlayerSkin
import io.lanyin.player.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: UiSettings, repository: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val c = Lanyin.colors
    CompositionLocalProvider(LocalContentColor provides c.content) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = pagePadding(withBottomChrome = false, extraTop = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                    Text("外观与播放", style = MaterialTheme.typography.headlineSmall)
                }
            }

            item { SectionTitle("主题", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), trailing = settings.theme.tagline) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(AppTheme.entries) { theme ->
                        ThemeCard(theme, settings.themeMode, selected = theme == settings.theme) {
                            scope.launch { repository.setTheme(theme) }
                        }
                    }
                }
            }

            item { SectionTitle("深浅模式", Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            ThemedChip(mode.label, selected = mode == settings.themeMode, onClick = { scope.launch { repository.setThemeMode(mode) } })
                        }
                    }
                    if (settings.theme.darkOnly) {
                        Text(
                            "「${settings.theme.label}」只在夜里亮，始终使用深色。",
                            style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            item { SectionTitle("播放页样式", Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            item {
                val spin = rememberSpinState(running = true)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(PlayerSkin.entries) { skin ->
                        val selected = skin == settings.playerSkin
                        Column(
                            Modifier
                                .width(132.dp)
                                .themedSurface(onClick = { scope.launch { repository.setPlayerSkin(skin) } })
                                .then(if (selected) Modifier.border(2.dp, c.accent, Lanyin.style.cardShape) else Modifier)
                                .padding(10.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().height(108.dp), contentAlignment = Alignment.Center) {
                                if (skin == PlayerSkin.LYRICS) {
                                    Icon(Icons.Filled.Lyrics, null, tint = c.accent, modifier = Modifier.size(48.dp))
                                } else {
                                    PlayerArtwork(skin, isPlaying = selected, spin = spin, coverSeed = 3, modifier = Modifier.height(104.dp))
                                }
                            }
                            Text(skin.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 6.dp))
                            Text(skin.description, style = MaterialTheme.typography.labelSmall, color = c.muted, minLines = 2, maxLines = 2)
                        }
                    }
                }
            }

            item { SectionTitle("播放", Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            item {
                ToggleRow("打开播放页时自动横屏", "也可以随时点播放页右上角的旋转按钮", settings.autoLandscape) {
                    scope.launch { repository.setAutoLandscape(it) }
                }
            }
            item {
                ToggleRow("横屏时保持屏幕常亮", "把手机立在桌上当歌词屏用", settings.keepScreenOnInLandscape) {
                    scope.launch { repository.setKeepScreenOnInLandscape(it) }
                }
            }
            item {
                Text(
                    "澜音 0.1 · 界面预览版",
                    style = MaterialTheme.typography.labelSmall, color = c.muted,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                )
            }
        }
    }
}

/** A miniature of the app rendered in [theme], so each theme shows its own shapes and backdrop. */
@Composable
private fun ThemeCard(theme: AppTheme, mode: ThemeMode, selected: Boolean, onClick: () -> Unit) {
    val outer = Lanyin.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(128.dp, 196.dp)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) outer.accent else outer.outline, RoundedCornerShape(22.dp))
                .padding(5.dp)
                .clip(RoundedCornerShape(18.dp))
                .pressable(onClick = onClick),
        ) {
            LanyinTheme(theme = theme, mode = mode, applySystemBars = false) { ThemeThumbnail() }
        }
        Text(
            theme.label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = if (selected) outer.content else outer.muted,
            modifier = Modifier.padding(top = 8.dp),
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
            Text("晚上好", style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = c.content)
            Spacer(Modifier.height(10.dp))
            CoverArt(3, Modifier.size(54.dp).clip(style.artShape))
            Spacer(Modifier.height(10.dp))
            Text("潮水退去以后", style = MaterialTheme.typography.labelMedium.copy(fontFamily = if (style.lyric.serif) style.display else null, fontWeight = FontWeight.Bold), color = c.accent)
            Text("沙上留着谁的脚印", style = MaterialTheme.typography.labelSmall.copy(fontFamily = if (style.lyric.serif) style.display else null), color = c.content.copy(alpha = 0.5f))
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().height(30.dp).themedSurface(shape = style.chipShape, tone = SurfaceTone.Chrome).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(16.dp).clip(CircleShape).border(3.dp, c.vinyl, CircleShape))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(c.content.copy(alpha = 0.2f)))
                Spacer(Modifier.width(6.dp))
                Canvas(Modifier.size(9.dp)) { drawPlayPauseGlyph(0f, c.content) }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Lanyin.colors
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .themedSurface(onClick = { onChange(!checked) })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
        )
    }
}
