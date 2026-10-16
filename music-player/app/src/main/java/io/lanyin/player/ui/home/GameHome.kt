package io.lanyin.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.DitheredCover
import io.lanyin.player.ui.design.PixelHearts
import io.lanyin.player.ui.design.SectionTitle
import io.lanyin.player.ui.design.gameWindow
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.GameLcd
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PixelFamily
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.delay

private val Gold = Color(0xFFFFD34E)
private val Silver = Color(0xFFC9D2E3)
private val Bronze = Color(0xFFE59A62)
private val HeartPink = Color(0xFFFF6B9A)

/**
 * 游: the home page as a game. A status line with lives and level; the title screen on the green
 * LCD, PRESS START blinking; the main menu in a role-playing game's window, two columns with a ▶
 * on the choice; the recommended playlists as an inventory of item slots; the first chart as a
 * high-score table; the picks as a quest log. Before signing in there's no save file to load.
 */
@Composable
internal fun GameHome(model: HomeModel) {
    val style = Lanyin.style
    val m = style.margin
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { Hud(model, Modifier.padding(horizontal = m)) }
        item { TitleScreen(model, Modifier.padding(start = m, end = m, top = 16.dp)) }
        if (!model.signedIn) item { NoSaveData(model.login, Modifier.padding(start = m, end = m, top = 16.dp)) }
        item { MainMenu(model, Modifier.padding(start = m, end = m, top = 16.dp)) }
        item {
            Column(Modifier.padding(start = m, end = m, top = 30.dp)) {
                SectionTitle("道具", trailing = model.recommendedNote)
                Inventory(model, Modifier.padding(top = 12.dp))
            }
        }
        item {
            Column(Modifier.padding(start = m, end = m, top = 30.dp)) {
                SectionTitle("排行榜", trailing = "全部 ▶")
                HighScores(model, Modifier.padding(top = 12.dp))
            }
        }
        model.picks?.let { picks ->
            item { SectionTitle("任务 · ${picks.title}", Modifier.padding(start = m, end = m, top = 30.dp), trailing = picks.note) }
            items(picks.songs.size) { i ->
                Quest(picks.songs[i], i, picks.songs[i].id == model.player.playingSongId, { model.playPick(i) }, Modifier.padding(horizontal = m))
            }
        }
    }
}

private val pixel: TextStyle get() = TextStyle(fontFamily = PixelFamily)

/** The greeting, the actions, and the player's status: 1P, three hearts, the day as a level. */
@Composable
private fun Hud(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val now = model.now
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(now.hour), style = style.type.display)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("1P", style = pixel.copy(fontSize = fixedSp(13f)), color = c.content)
                Spacer(Modifier.width(6.dp))
                PixelHearts(3, c.accent, Modifier.size(width = 40.dp, height = 11.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "LV.%02d  %02d/%02d %s".format(now.dayOfMonth, now.monthValue, now.dayOfMonth, now.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase()),
                    style = pixel.copy(fontSize = fixedSp(13f)),
                    color = c.muted,
                    maxLines = 1,
                )
            }
        }
        HeaderActions(model.account, model.openSettings, model.openAccount)
    }
}

/** A blink at a game's pace, for PRESS START and the ▼ of a waiting window. */
@Composable
private fun rememberBlink(): Boolean {
    // Mostly on: the eye should catch the words, not chase them.
    val on by produceState(true) {
        while (true) {
            delay(if (value) 820 else 380)
            value = !value
        }
    }
    return on
}

/** The title screen, on the green LCD inside its dark bezel. */
@Composable
private fun TitleScreen(model: HomeModel, modifier: Modifier) {
    val blink = rememberBlink()
    val player = model.player
    val ink = GameLcd[3]
    Box(
        modifier
            .fillMaxWidth()
            .height(176.dp)
            .themedSurface(onClick = if (player.isEmpty) model.playDaily else model.openPlayer)
            .background(GameLcd[0])
            .drawBehind {
                // The LCD's pixel grid.
                val pitch = 3.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(GameLcd[1].copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 1f)
                    x += pitch
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(GameLcd[1].copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1f)
                    y += pitch
                }
            },
    ) {
        Column(Modifier.align(Alignment.Center).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("澜音", style = pixel.copy(fontSize = fixedSp(46f), lineHeight = fixedSp(50f), letterSpacing = 0.1.em), color = ink)
            Text("LANYIN  MUSIC  QUEST", style = pixel.copy(fontSize = fixedSp(12f), letterSpacing = 0.12.em), color = GameLcd[2])
            Text(
                "▶ PRESS START",
                style = pixel.copy(fontSize = fixedSp(15f)),
                color = ink.copy(alpha = if (blink) 1f else 0f),
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        // A note bobbing in the corner, as title screens have a sprite.
        Canvas(Modifier.align(Alignment.TopStart).padding(14.dp).size(22.dp)) { drawPixelNote(ink) }
        Canvas(Modifier.align(Alignment.TopEnd).padding(14.dp).size(22.dp)) { drawPixelNote(GameLcd[2]) }
        Text(
            if (player.isEmpty) "© 2026 LANYIN" else "NOW · ${player.title}",
            style = pixel.copy(fontSize = fixedSp(11f)),
            color = GameLcd[2],
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** A quaver drawn in pixels. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixelNote(color: Color) {
    val rows = listOf("00011100", "00011110", "00010011", "00010001", "00010000", "01110000", "11110000", "01100000")
    val px = size.minDimension / 8f
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch -> if (ch == '1') drawRect(color, Offset(x * px, y * px), Size(px + 0.5f, px + 0.5f)) }
    }
}

/** Before signing in: no save to load. */
@Composable
private fun NoSaveData(onLogin: () -> Unit, modifier: Modifier) {
    val c = Lanyin.colors
    val blink = rememberBlink()
    Column(
        modifier
            .fillMaxWidth()
            .gameWindow(c.accentAlt, c.onAccent)
            .pressable(onClick = onLogin)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text("NO SAVE DATA", style = pixel.copy(fontSize = fixedSp(13f), letterSpacing = 0.1.em), color = Gold)
        Text("还没有存档。扫码登录网易云，读取你的歌单和红心。", style = pixel.copy(fontSize = fixedSp(15f), lineHeight = fixedSp(22f)), color = c.onAccent, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("▶ 读取存档", style = pixel.copy(fontSize = fixedSp(14f)), color = c.onAccent, modifier = Modifier.weight(1f))
            Text("▼", style = pixel.copy(fontSize = fixedSp(13f)), color = c.onAccent.copy(alpha = if (blink) 1f else 0f))
        }
    }
}

/** The main menu: the ways in, two columns of three, with a ▶ on the choice. */
@Composable
private fun MainMenu(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val doors = model.doors
    Column(
        modifier
            .fillMaxWidth()
            .gameWindow(c.accentAlt, c.onAccent)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Text("MENU", style = pixel.copy(fontSize = fixedSp(12f), letterSpacing = 0.14.em), color = c.onAccent.copy(alpha = 0.7f), modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
        for (row in 0 until 3) {
            Row {
                for (col in 0 until 2) {
                    val i = row * 2 + col
                    MenuChoice(doors[i], first = i == 0, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MenuChoice(door: Door, first: Boolean, modifier: Modifier) {
    val c = Lanyin.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = door.onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▶", style = pixel.copy(fontSize = fixedSp(14f)), color = c.onAccent.copy(alpha = if (pressed || first) 1f else 0f), modifier = Modifier.width(18.dp))
        Column {
            Text(door.label, style = pixel.copy(fontSize = fixedSp(17f)), color = c.onAccent, maxLines = 1)
            Text(door.note, style = pixel.copy(fontSize = fixedSp(11f)), color = c.onAccent.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The recommended playlists as an inventory: item slots on the LCD, each with its count. */
@Composable
private fun Inventory(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val lists = model.recommended.slots(6)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in 0 until (lists.size + 2) / 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (k in 0..2) {
                    val slot = row * 3 + k
                    if (slot >= lists.size) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val p = lists[slot]
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).themedSurface(tone = SurfaceTone.Flat, onClick = { if (p != null) model.openPlaylist(p) })) {
                            DitheredCover(p?.coverUrl, if (p != null) (p.id % 97).toInt() else 10 + slot, Modifier.fillMaxSize().padding(5.dp), n = 32)
                            if (p != null) {
                                Text(
                                    "×${p.trackCount}",
                                    style = pixel.copy(fontSize = fixedSp(11f)),
                                    color = Color.White,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(GameLcd[3]).padding(horizontal = 3.dp),
                                )
                            }
                        }
                        Text(
                            p?.name ?: "？？？",
                            style = pixel.copy(fontSize = fixedSp(12f), lineHeight = fixedSp(16f)),
                            color = c.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The first chart as an arcade's high-score table; the other charts as choices under it. */
@Composable
private fun HighScores(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val charts = model.charts.slots(4)
    val first = charts.firstOrNull()
    val blink = rememberBlink()
    Column(
        modifier
            .fillMaxWidth()
            .gameWindow(Color(0xFF15142A), c.onAccent)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HIGH SCORE", style = pixel.copy(fontSize = fixedSp(14f), letterSpacing = 0.12.em), color = Gold.copy(alpha = if (blink) 1f else 0.75f), modifier = Modifier.weight(1f))
            Text(first.nameOr(0), style = pixel.copy(fontSize = fixedSp(13f)), color = c.onAccent.copy(alpha = 0.75f))
        }
        val top = first?.top.orEmpty()
        listOf("1ST" to Gold, "2ND" to Silver, "3RD" to Bronze).forEachIndexed { i, (rank, color) ->
            val song: Song? = top.getOrNull(i)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).pressable(onClick = { if (first != null) model.openPlaylist(first.playlist) else model.openCharts() }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(rank, style = pixel.copy(fontSize = fixedSp(15f)), color = color, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(song?.name ?: "- - - - -", style = pixel.copy(fontSize = fixedSp(15f)), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (song != null) Text(song.artistLine, style = pixel.copy(fontSize = fixedSp(11f)), color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            charts.drop(1).forEachIndexed { k, chart ->
                Text(
                    "▶ ${chart.nameOr(k + 1)}",
                    style = pixel.copy(fontSize = fixedSp(13f)),
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    modifier = Modifier.pressable(onClick = { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() }).padding(vertical = 6.dp),
                )
            }
        }
    }
}

/** A song in the quest log: its cover in a slot, the name, and a mark on the one being played. */
@Composable
private fun Quest(song: Song, index: Int, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).themedSurface(tone = SurfaceTone.Flat)) {
            DitheredCover(song.album.coverUrl, (song.id % 97).toInt(), Modifier.fillMaxSize().padding(3.dp), n = 24)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = style.type.body, color = if (playing) c.accent else c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistLine, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (playing) "♪" else if (index == 0) "!" else "", style = pixel.copy(fontSize = fixedSp(16f)), color = c.accent, modifier = Modifier.padding(start = 8.dp, end = 4.dp))
    }
}
