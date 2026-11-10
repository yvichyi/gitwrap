package io.lanyin.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.core.model.Song
import io.lanyin.player.data.account.AccountState
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.BitPx
import io.lanyin.player.ui.design.DitheredCover
import io.lanyin.player.ui.design.GameHud
import io.lanyin.player.ui.design.GameWindow
import io.lanyin.player.ui.design.Sprites
import io.lanyin.player.ui.design.TinyText
import io.lanyin.player.ui.design.WorldMap
import io.lanyin.player.ui.design.drawPixelButton
import io.lanyin.player.ui.design.drawSprite
import io.lanyin.player.ui.design.formatTime
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.spriteSize
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PixelFamily

/**
 * 游: the home page is a game's world map. Along the top the HUD (the world is the month, the
 * coins today's picks); the island with six places on it, each a level and a way in (castle:
 * today's picks, radio tower: 私人 FM, the house with the heart: 我喜欢, the flag on the
 * mountain: the charts, the clock tower: recent, the cave: downloads); the cat sits at the one
 * chosen and the dialogue box below says what it is, with START to go. Then the items (the same
 * ways in, one tap each), the save files (the recommended playlists), the high scores (charts)
 * and today's quest (the picks).
 */
@Composable
internal fun GameHome(model: HomeModel) {
    val m = 12.dp
    var level by rememberSaveable { mutableIntStateOf(0) }
    val doors = model.doors
    io.lanyin.player.ui.design.RevealLazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding(extraTop = 6.dp)) {
        item { Hud(model, Modifier.padding(horizontal = m)) }
        item {
            GameWindow(Modifier.padding(start = m, end = m, top = 8.dp).fillMaxWidth(), padding = PaddingValues(BitPx * 4)) {
                WorldMap(level, doors.map { "${it.label}，第 1-${doors.indexOf(it) + 1} 关" }, onSelect = { level = it }, modifier = Modifier.fillMaxWidth().aspectRatio(1.06f))
            }
        }
        item { LevelDialog(model, level, Modifier.padding(start = m, end = m, top = 8.dp)) }
        item { Items(model, Modifier.padding(start = m, end = m, top = 8.dp)) }
        if (!model.signedIn) item { NoSave(model.login, Modifier.padding(start = m, end = m, top = 8.dp)) }
        item { SaveFiles(model, Modifier.padding(start = m, end = m, top = 8.dp)) }
        item { HighScores(model, Modifier.padding(start = m, end = m, top = 8.dp)) }
        model.picks?.let { picks ->
            if (picks.songs.isNotEmpty()) item { Quest(model, picks.title, picks.songs, Modifier.padding(start = m, end = m, top = 8.dp)) }
        }
    }
}

private val pixel: TextStyle get() = TextStyle(fontFamily = PixelFamily)

/**
 * The HUD: WORLD and the month, three hearts, today's coins and the day; at its end OPT for the
 * settings and 1P for the account (LOGIN before there is one).
 */
@Composable
private fun Hud(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val coins = model.pickSongs.size
    Row(modifier.fillMaxWidth().height(22.dp).background(c.content), verticalAlignment = Alignment.CenterVertically) {
        GameHud("WORLD ${model.now.monthValue}", 3, "x%02d DAY%02d".format(coins, model.now.dayOfMonth), Modifier.weight(1f).height(22.dp))
        HudKey("OPT", "设置", model.openSettings)
        HudKey(if (model.account is AccountState.LoggedIn) "1P" else "LOGIN", if (model.signedIn) "我的账号" else "登录", model.openAccount)
    }
}

/** A key in the HUD: its name knocked out of a paper box. */
@Composable
private fun HudKey(name: String, label: String, onClick: () -> Unit) {
    val c = Lanyin.colors
    Box(
        Modifier
            .padding(end = 4.dp)
            .height(16.dp)
            .background(c.bg)
            .pressable(label = label, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) { TinyText(name, c.content) }
}

/**
 * The dialogue box for the chosen level: its number on the tab, the name and what's in it, and
 * START. Today's picks give their count and running time.
 */
@Composable
private fun LevelDialog(model: HomeModel, level: Int, modifier: Modifier) {
    val c = Lanyin.colors
    val door = model.doors[level]
    val songs = model.pickSongs
    val detail = when (level) {
        0 -> if (songs.isEmpty()) model.dailyNote else "${songs.size} 首 · 今晚的关卡"
        else -> door.note
    }
    val small = when (level) {
        0 -> if (songs.isEmpty()) "LEVEL 1-1" else "TRK %02d . %s".format(songs.size, hms(songs.sumOf { it.durationMs }))
        else -> "LEVEL 1-${level + 1}"
    }
    GameWindow(modifier.fillMaxWidth(), title = "1-${level + 1}") {
        Text(door.label, style = pixel.copy(fontSize = fixedSp(20f), lineHeight = fixedSp(26f)), color = c.content, maxLines = 1)
        Text(detail, style = pixel.copy(fontSize = fixedSp(14f), lineHeight = fixedSp(20f)), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TinyText(small, c.content, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .background(c.content)
                    .pressable(label = "开始：${door.label}", onClick = door.onClick)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("开始", style = pixel.copy(fontSize = fixedSp(15f)), color = c.bg)
                Canvas(Modifier.padding(start = 6.dp).size(8.dp, 10.dp)) { drawSprite(Sprites.Play, 0f, 0f, BitPx.toPx(), c.bg) }
            }
        }
    }
}

/** h:mm:ss for a running time. */
private fun hms(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
}

/** The item bar: the five other ways in, an icon and a name each; FM first, held. */
@Composable
private fun Items(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val icons = listOf(Sprites.Radio, Sprites.Heart, Sprites.Chart, Sprites.History, Sprites.Download)
    val names = listOf("FM", "喜欢", "排行", "最近", "下载")
    GameWindow(modifier.fillMaxWidth(), title = "ITEMS", padding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in 0 until 5) {
                val door = model.doors[k + 1]
                val on = k == 0
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 60.dp)
                        .drawBehind {
                            drawRect(c.content)
                            drawRect(if (on) c.content else c.bg, androidx.compose.ui.geometry.Offset(BitPx.toPx(), BitPx.toPx()), androidx.compose.ui.geometry.Size(size.width - 2 * BitPx.toPx(), size.height - 2 * BitPx.toPx()))
                        }
                        .pressable(label = door.label, onClick = door.onClick)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val icon = icons[k]
                    val (w, h) = spriteSize(icon)
                    Canvas(Modifier.size(BitPx * (w * 2), BitPx * (h * 2))) { drawSprite(icon, 0f, 0f, BitPx.toPx() * 2, if (on) c.bg else c.content) }
                    Text(names[k], style = pixel.copy(fontSize = fixedSp(13f)), color = if (on) c.bg else c.content, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** No account yet: the game has no save data, and a key to start one. */
@Composable
private fun NoSave(onLogin: () -> Unit, modifier: Modifier) {
    val c = Lanyin.colors
    GameWindow(modifier.fillMaxWidth().pressable(label = "登录", onClick = onLogin), title = "SYSTEM") {
        Text("NO SAVE DATA", style = pixel.copy(fontSize = fixedSp(15f)), color = c.content)
        Text("登录网易云，开始你的存档 ▶", style = pixel.copy(fontSize = fixedSp(13f)), color = c.content, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The save files: the recommended playlists, each with its cover in one bit, its number and how many tracks. */
@Composable
private fun SaveFiles(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val lists = model.recommended.slots(5)
    GameWindow(modifier.fillMaxWidth(), title = "SAVE", padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        lists.forEachIndexed { i, p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(label = p?.name ?: "推荐歌单") { if (p != null) model.openPlaylist(p) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).background(c.content).padding(BitPx)) {
                    DitheredCover(p?.coverUrl, p?.let { (it.id % 97).toInt() } ?: (10 + i), Modifier.fillMaxSize(), n = 18)
                }
                TinyText("FILE ${i + 1}", c.content, Modifier.padding(start = 10.dp))
                Text(p?.name ?: "- - -", style = pixel.copy(fontSize = fixedSp(14f)), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp, end = 8.dp))
                TinyText("%02d TRK".format((p?.trackCount ?: 0).coerceAtMost(99)), c.content)
            }
        }
    }
}

/** The charts as a high-score table: 1ST to 4TH, the chart and the song at its top. */
@Composable
private fun HighScores(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val charts = model.charts.slots(4)
    val ranks = listOf("1ST", "2ND", "3RD", "4TH")
    GameWindow(modifier.fillMaxWidth(), title = "HIGH SCORE") {
        charts.forEachIndexed { i, chart ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(label = chart.nameOr(i)) { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() }
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TinyText(ranks[i], c.content, Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(chart.nameOr(i), style = pixel.copy(fontSize = fixedSp(14f)), color = c.content, maxLines = 1)
                    val top: Song? = chart?.top?.firstOrNull()
                    Text(if (top != null) "${top.name} — ${top.artistLine}" else "读取中…", style = pixel.copy(fontSize = fixedSp(12f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Today's picks as the day's quest: each song a step, the one playing marked with the cursor. */
@Composable
private fun Quest(model: HomeModel, title: String, songs: List<Song>, modifier: Modifier) {
    val c = Lanyin.colors
    GameWindow(modifier.fillMaxWidth(), title = "QUEST") {
        Text(title, style = pixel.copy(fontSize = fixedSp(16f)), color = c.content, modifier = Modifier.padding(bottom = 4.dp))
        songs.forEachIndexed { i, song ->
            val playing = song.id == model.player.playingSongId
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (playing) Modifier.background(c.content) else Modifier)
                    .pressable(label = song.name) { model.playPick(i) }
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ink = if (playing) c.bg else c.content
                if (playing) {
                    Canvas(Modifier.width(26.dp).height(10.dp)) { drawSprite(Sprites.Play, 0f, 0f, BitPx.toPx(), ink) }
                } else {
                    TinyText("%02d".format(i + 1), ink, Modifier.width(26.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(song.name, style = pixel.copy(fontSize = fixedSp(14f)), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artistLine, style = pixel.copy(fontSize = fixedSp(11f)), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TinyText(formatTime(song.durationMs), ink, Modifier.padding(start = 8.dp))
            }
        }
    }
}
