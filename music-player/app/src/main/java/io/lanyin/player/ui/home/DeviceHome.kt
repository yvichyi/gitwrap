package io.lanyin.player.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.components.pagePadding
import io.lanyin.player.ui.design.AnalogVu
import io.lanyin.player.ui.design.Avatar
import io.lanyin.player.ui.design.CompactDisc
import io.lanyin.player.ui.design.Hifi
import io.lanyin.player.ui.design.HifiKnob
import io.lanyin.player.ui.design.HifiUnit
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.SevenSeg
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.TuningDial
import io.lanyin.player.ui.design.drawGlassStrip
import io.lanyin.player.ui.design.drawKeycap
import io.lanyin.player.ui.design.drawKnobScale
import io.lanyin.player.ui.design.drawLed
import io.lanyin.player.ui.design.drawToggle
import io.lanyin.player.ui.design.drawWalnut
import io.lanyin.player.ui.design.engraved
import io.lanyin.player.ui.design.formatTime
import io.lanyin.player.ui.design.keyColors
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.rememberWalnut
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LongCangFamily
import io.lanyin.player.ui.theme.PixelFamily
import io.lanyin.player.ui.theme.SpaceMonoFamily
import io.lanyin.player.data.account.AccountState

/**
 * 机: the home page is a hi-fi stack in a walnut cabinet. The tuner on top, its fluorescent
 * display on 私人 FM and its six preset keys the ways in; the five-disc changer holding the
 * recommended playlists, a disc each; the amplifier with its pair of lamp-lit meters swinging to
 * the music (its knobs open 音效); the charts in the programme memory; today's picks written on a
 * cassette's J-card.
 */
@Composable
internal fun DeviceHome(model: HomeModel) {
    val style = Lanyin.style
    val wood = rememberWalnut()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val padding = pagePadding(extraTop = 8.dp)
    val side = 12.dp
    Box(Modifier.fillMaxSize()) {
        // The cabinet, from under the status bar down.
        Canvas(Modifier.fillMaxSize().padding(top = top)) { drawWalnut(wood, style.dark) }
        io.lanyin.player.ui.design.RevealLazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { Tuner(model, Modifier.padding(horizontal = side)) }
            item { Changer(model, Modifier.padding(start = side, end = side, top = 8.dp)) }
            item { Amplifier(model, Modifier.padding(start = side, end = side, top = 8.dp)) }
            item { ProgramMemory(model, Modifier.padding(start = side, end = side, top = 8.dp)) }
            model.picks?.let { picks ->
                item { JCardHead(picks.title, picks.songs.size, Modifier.padding(start = side + 8.dp, end = side + 8.dp, top = 14.dp)) }
                items(picks.songs.size) { i ->
                    JCardRow(i, picks.songs[i], picks.songs[i].id == model.player.playingSongId, last = i == picks.songs.size - 1, onClick = { model.playPick(i) }, modifier = Modifier.padding(horizontal = side + 8.dp))
                }
            }
        }
    }
}

private val mono: TextStyle get() = TextStyle(fontFamily = SpaceMonoFamily)

/** The fluorescent display's own letters: the pixel face in the phosphor's colour, with its glow. */
@Composable
private fun vfd(size: Float, color: Color = Hifi.Vfd) = TextStyle(
    fontFamily = PixelFamily, fontSize = fixedSp(size), lineHeight = fixedSp(size * 1.25f), color = color,
    shadow = Shadow(color.copy(alpha = 0.6f), Offset.Zero, 8f),
)

/** A window of black glass set into a plate, with its bezel; the phosphor glows behind it. */
private fun Modifier.vfdWindow(): Modifier = this
    .drawBehind {
        val one = 1.dp.toPx()
        drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(-one, -one), Size(size.width + 2 * one, size.height + one), CornerRadius(3.dp.toPx()))
        drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(-one, size.height - one), Size(size.width + 2 * one, 2 * one), CornerRadius(3.dp.toPx()))
    }
    .clip(RoundedCornerShape(2.dp))
    .background(Hifi.Glass)
    .drawWithContent {
        drawContent()
        // The fine mesh in front of the phosphor, then the glass's shadow and sheen.
        var x = 0f
        while (x < size.width) {
            drawRect(Color.Black.copy(alpha = 0.16f), Offset(x, 0f), Size(0.6f * density, size.height))
            x += 2.dp.toPx()
        }
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), endY = 10.dp.toPx()))
        drawRect(Brush.linearGradient(0.3f to Color.Transparent, 0.45f to Color.White.copy(alpha = 0.05f), 0.6f to Color.Transparent, start = Offset.Zero, end = Offset(size.width, size.height)))
    }

/**
 * The tuner: its display tuned to 107.7 on 私人 FM (a tap starts it), the lamp-lit dial with the
 * pointer, six preset keys (today's picks, FM, liked, charts, recent, downloads) with their
 * lights, and the tuning knob.
 */
@Composable
private fun Tuner(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    HifiUnit(
        "ST-18 · FM STEREO TUNER",
        modifier.fillMaxWidth(),
        trailing = { PanelKeys(model.account, model.openSettings, model.openAccount) },
    ) {
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(92.dp).vfdWindow().pressable(label = "私人 FM", onClick = model.playFm).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Column(Modifier.align(Alignment.TopStart)) {
                Text("FM", style = vfd(10f))
                Text("AM", style = vfd(10f, Hifi.Vfd.copy(alpha = 0.12f)))
            }
            Row(Modifier.align(Alignment.CenterStart).padding(start = 24.dp), verticalAlignment = Alignment.Bottom) {
                SevenSeg("107.7", 44.dp)
                Text("MHz", style = vfd(9f), modifier = Modifier.padding(start = 2.dp))
            }
            Column(Modifier.align(Alignment.TopEnd).width(118.dp)) {
                Text("私人 FM", style = vfd(14f), maxLines = 1)
                Text(model.fmNote, style = vfd(11f, Hifi.Vfd.copy(alpha = 0.75f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                for ((label, on) in listOf("STEREO" to true, "TUNED" to true, "MONO" to false, "AUTO" to false)) {
                    Text(label, style = mono.copy(fontSize = fixedSp(7f), fontWeight = FontWeight.Bold, color = if (on) Hifi.Vfd else Hifi.Vfd.copy(alpha = 0.12f)), modifier = Modifier.padding(end = 8.dp))
                }
                Text("P2", style = mono.copy(fontSize = fixedSp(7f), fontWeight = FontWeight.Bold, color = Hifi.Amber))
                Spacer(Modifier.weight(1f))
                Text("SIG ", style = mono.copy(fontSize = fixedSp(7f), color = Hifi.VfdDim))
                Canvas(Modifier.size(22.dp, 9.dp)) {
                    for (k in 0 until 5) {
                        val bh = size.height * (0.3f + 0.7f * k / 4f)
                        drawRect(Hifi.Vfd.copy(alpha = if (k < 4) 1f else 0.15f), Offset(k * size.width / 5f, size.height - bh), Size(size.width / 5f * 0.65f, bh))
                    }
                }
            }
        }
        TuningDial(107.7f, Modifier.padding(top = 10.dp).fillMaxWidth().height(24.dp))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    model.doors.forEachIndexed { i, door ->
                        PresetKey(i + 1, door.label, lit = i == 1 && model.player.isPlaying, onClick = door.onClick, modifier = Modifier.weight(1f))
                    }
                }
                Text("PRESET · MEMORY", style = engraved(6.5f), color = c.muted, modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(62.dp)) { drawKnobScale(c.content, size.minDimension / 2f - 1.dp.toPx(), count = 9) }
                    HifiKnob(50.dp, 38f, Modifier.pressable(label = "私人 FM", onClick = model.playFm))
                }
                Text("TUNING", style = engraved(6.5f), color = c.muted)
            }
        }
        if (!model.signedIn) {
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().vfdWindow().pressable(label = "登录", onClick = model.login).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("NO SIGNAL", style = mono.copy(fontSize = fixedSp(9f), fontWeight = FontWeight.Bold, color = Hifi.Amber))
                Text("  登录网易云，接收你的电台", style = vfd(11f), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("▶", style = vfd(11f))
            }
        }
    }
}

/** The tuner's own keys in its corner: a round menu key for settings and the listener's badge. */
@Composable
private fun PanelKeys(account: AccountState, openSettings: () -> Unit, openAccount: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawLed(center, Hifi.Red, on = true, r = 2.dp.toPx()) }
        Text(" POWER", style = engraved(6.5f), color = c.muted)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(28.dp).pressable(label = "设置", onClick = openSettings).drawBehind { drawKeycap(CircleShape, style.keyColors()) }, contentAlignment = Alignment.Center) {
            ThemedIcon(LineIcon.Sliders, null, tint = c.content, size = 14.dp)
        }
        Spacer(Modifier.width(6.dp))
        val profile = (account as? AccountState.LoggedIn)?.profile
        Box(Modifier.size(28.dp).pressable(label = if (profile != null) "我的账号" else "登录", onClick = openAccount).drawBehind { drawKeycap(CircleShape, style.keyColors()) }, contentAlignment = Alignment.Center) {
            if (profile != null) Avatar(profile.avatarUrl, profile.nickname, 22.dp, shape = CircleShape)
            else ThemedIcon(LineIcon.User, null, tint = c.content, size = 14.dp)
        }
    }
}

/** A preset key: its light above, the number on the cap, the station it holds printed beneath. */
@Composable
private fun PresetKey(number: Int, label: String, lit: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(6.dp)) { drawLed(center, Hifi.Vfd, on = lit, r = 1.6.dp.toPx()) }
        Box(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(30.dp)
                .pressable(label = label, onClick = onClick)
                .drawBehind { drawKeycap(RoundedCornerShape(3.dp), style.keyColors()) },
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = mono.copy(fontSize = fixedSp(11f), fontWeight = FontWeight.Bold), color = c.content)
        }
        Text(label.replace(" ", ""), style = TextStyle(fontSize = fixedSp(9.5f), fontWeight = FontWeight.Medium), color = c.content, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * The five-disc changer: behind its smoked window the recommended playlists as discs, each
 * printed with its cover (a tap opens it); beneath, the display reading the disc in turn, and a
 * key for each.
 */
@Composable
private fun Changer(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val lists = model.recommended.slots(5)
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(lists.size) {
        while (true) {
            kotlinx.coroutines.delay(3600)
            shown = (shown + 1) % lists.size.coerceAtLeast(1)
        }
    }
    val open = { i: Int -> lists.getOrNull(i)?.let(model.openPlaylist) ?: Unit }
    HifiUnit(
        "CD-5 · 5 DISC CAROUSEL",
        modifier.fillMaxWidth(),
        trailing = { Text("推荐歌单 · ${model.recommendedNote}", style = engraved(7f).copy(letterSpacing = 0.06.em), color = c.muted, maxLines = 1) },
    ) {
        Row(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .drawBehind { drawSmokedWindow() }
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            lists.forEachIndexed { i, p ->
                Disc(p, i, Modifier.weight(1f).pressable(label = p?.name ?: "推荐歌单", onClick = { open(i) }))
            }
        }
        Row(Modifier.padding(top = 4.dp).fillMaxWidth()) {
            lists.forEachIndexed { i, p ->
                Text(
                    "${i + 1} · ${p?.name ?: "· · ·"}", style = TextStyle(fontSize = fixedSp(9f)), color = if (i == shown) c.content else c.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val p = lists.getOrNull(shown)
            Row(Modifier.weight(1f).height(38.dp).vfdWindow().pressable(label = p?.name ?: "推荐歌单", onClick = { open(shown) }).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("DISC", style = mono.copy(fontSize = fixedSp(7f), fontWeight = FontWeight.Bold, color = Hifi.Vfd))
                    Text("%02d TRK".format(p?.trackCount?.coerceAtMost(99) ?: 0), style = mono.copy(fontSize = fixedSp(7f), color = Hifi.VfdDim))
                }
                SevenSeg("${shown + 1}", 24.dp, modifier = Modifier.padding(horizontal = 6.dp))
                Text(p?.name ?: "读取中…", style = vfd(12f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in lists.indices) {
                    SmallKey("${i + 1}", Modifier.size(22.dp, 18.dp)) { open(i) }
                }
            }
        }
    }
}

/** Smoked acrylic in front of the carousel: a warm dark glass with the light catching its top. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmokedWindow() {
    val one = 1.dp.toPx()
    drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(-one, -one), Size(size.width + 2 * one, size.height + one), CornerRadius(3.dp.toPx()))
    drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(-one, size.height - one), Size(size.width + 2 * one, 2 * one), CornerRadius(3.dp.toPx()))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF1C1A19), Color(0xFF2A2624), Color(0xFF151413))), cornerRadius = CornerRadius(2.dp.toPx()))
}

/** A disc in the carousel: the playlist's cover printed on it. */
@Composable
private fun Disc(p: Playlist?, slot: Int, modifier: Modifier) {
    CompactDisc(modifier.padding(2.dp)) { ShelfCover(p, slot, Modifier.fillMaxSize(), size = 72.dp) }
}

/** A small rectangular key with a label on the cap. */
@Composable
private fun SmallKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    val style = Lanyin.style
    Box(modifier.pressable(label = label, onClick = onClick).drawBehind { drawKeycap(RoundedCornerShape(2.dp), style.keyColors()) }, contentAlignment = Alignment.Center) {
        Text(label, style = mono.copy(fontSize = fixedSp(8f), fontWeight = FontWeight.Bold), color = style.colors.content)
    }
}

/**
 * The amplifier: two lamp-lit meters, left and right (the low and high end of what's playing);
 * the input selector, bass and treble, loudness and mute, the headphone socket and the big
 * volume knob. The tone knobs and the volume open 音效.
 */
@Composable
private fun Amplifier(model: HomeModel, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val player = model.player
    val openSheet = io.lanyin.player.ui.music.LocalOpenSheet.current
    val effects = { openSheet(io.lanyin.player.ui.music.Sheet.Effects) }
    HifiUnit(
        "A-18 · INTEGRATED AMPLIFIER",
        modifier.fillMaxWidth(),
        trailing = { Text("CLASS A · 2×50W", style = engraved(7f), color = c.muted, maxLines = 1) },
    ) {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalogVu(player.audioFeed, player.isPlaying, "LEFT · CH 1", Modifier.weight(1f).aspectRatio(1.75f), bands = 0 until 12)
            AnalogVu(player.audioFeed, player.isPlaying, "RIGHT · CH 2", Modifier.weight(1f).aspectRatio(1.75f), bands = 8 until 30)
        }
        Row(Modifier.padding(top = 14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // The input selector: TUNER, CD or TAPE, at TAPE while something's on the deck.
            val input = if (player.isEmpty) 0 else 2
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((i, name) in listOf("TUNER", "CD", "TAPE").withIndex()) {
                        Text(name, style = engraved(6f), color = if (i == input) c.content else c.muted)
                    }
                }
                Box(Modifier.padding(top = 2.dp), contentAlignment = Alignment.Center) {
                    HifiKnob(34.dp, (input - 1) * 40f)
                }
                Text("INPUT", style = engraved(6.5f), color = c.muted, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.weight(1f))
            for ((name, angle) in listOf("BASS" to -20f, "TREBLE" to 25f)) {
                Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    HifiKnob(26.dp, angle, Modifier.pressable(label = "音效", onClick = effects))
                    Text(name, style = engraved(6.5f), color = c.muted, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            for ((name, on) in listOf("LOUD" to true, "MUTE" to false)) {
                Column(Modifier.padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (on) "ON" else "OFF", style = engraved(5.5f), color = c.muted)
                    Canvas(Modifier.size(14.dp, 22.dp)) { drawToggle(on) }
                    Text(name, style = engraved(6.5f), color = c.muted, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(70.dp)) { drawKnobScale(c.content, size.minDimension / 2f - 1.dp.toPx(), count = 11, lit = 7) }
                    HifiKnob(58.dp, 52f, Modifier.pressable(label = "音效", onClick = effects))
                }
                Text("VOLUME", style = engraved(6.5f), color = c.muted)
            }
        }
    }
}

/**
 * The charts in the programme memory: four channels on a display, each with the song at its top,
 * a tap to open.
 */
@Composable
private fun ProgramMemory(model: HomeModel, modifier: Modifier) {
    val c = Lanyin.colors
    val charts = model.charts.slots(4)
    HifiUnit(
        "PM-4 · PROGRAM MEMORY",
        modifier.fillMaxWidth(),
        trailing = {
            Text(
                "排行榜 ▸", style = engraved(7.5f).copy(letterSpacing = 0.04.em), color = c.content,
                modifier = Modifier.pressable(label = "全部排行榜", onClick = model.openCharts).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        },
    ) {
        Column(Modifier.padding(top = 8.dp).fillMaxWidth().vfdWindow().padding(horizontal = 10.dp, vertical = 4.dp)) {
            charts.forEachIndexed { i, chart ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .pressable(onClick = { if (chart != null) model.openPlaylist(chart.playlist) else model.openCharts() })
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("P%02d".format(i + 1), style = mono.copy(fontSize = fixedSp(10f), fontWeight = FontWeight.Bold, color = Hifi.Amber), modifier = Modifier.width(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chart.nameOr(i), style = vfd(13f), maxLines = 1)
                        val top: Song? = chart?.top?.firstOrNull()
                        Text(
                            if (top != null) "▸ ${top.name} — ${top.artistLine}" else "▸ 调频中…",
                            style = vfd(10.5f, Hifi.Vfd.copy(alpha = 0.7f)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(chart?.playlist?.updateNote ?: "", style = vfd(9f, Hifi.VfdDim), maxLines = 1)
                }
            }
        }
    }
}

/** The paper the J-card is printed on: cream by day, dimmed a little at night so it doesn't glare. */
@Composable
private fun cardPaper(): Color = if (Lanyin.style.dark) Color(0xFFD8D0BF) else Hifi.Label

/** The top of the J-card: the red band with the side and the tape, the list's title by hand. */
@Composable
private fun JCardHead(title: String, count: Int, modifier: Modifier) {
    val paper = cardPaper()
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(2.dp.toPx(), 2.dp.toPx()), size)
                drawRect(paper)
            }
            .padding(top = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().background(Hifi.LabelBand).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("A", style = TextStyle(fontSize = fixedSp(14f), fontWeight = FontWeight.Black), color = Color.White)
            Text("  SIDE A · $count TRACKS", style = mono.copy(fontSize = fixedSp(9f), fontWeight = FontWeight.Bold), color = Color.White, modifier = Modifier.weight(1f))
            Text("CrO2 · 60", style = mono.copy(fontSize = fixedSp(9f), fontWeight = FontWeight.Bold), color = Color.White)
        }
        Text(title, style = TextStyle(fontFamily = LongCangFamily, fontSize = fixedSp(26f)), color = Hifi.LabelInk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp))
    }
}

/**
 * A track on the J-card: its number, the song and singer, the length, on a ruled line; the one
 * playing marked in red. The last row closes the card.
 */
@Composable
private fun JCardRow(index: Int, song: Song, playing: Boolean, last: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val paper = cardPaper()
    val ink = Hifi.LabelInk
    val red = Color(0xFFB8321C)
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(2.dp.toPx(), if (last) 2.dp.toPx() else 0f), size)
                drawRect(paper)
                drawLine(Color(0xFF6A8CB8).copy(alpha = 0.35f), Offset(12.dp.toPx(), size.height - 0.5.dp.toPx()), Offset(size.width - 12.dp.toPx(), size.height - 0.5.dp.toPx()), 1f * density)
            }
            .pressable(onClick = onClick)
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = if (last) 16.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (playing) "▶" else "%02d".format(index + 1), style = mono.copy(fontSize = fixedSp(11f), fontWeight = FontWeight.Bold), color = if (playing) red else ink.copy(alpha = 0.6f), modifier = Modifier.width(30.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = TextStyle(fontSize = fixedSp(14f), fontWeight = FontWeight.Medium), color = if (playing) red else ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistLine, style = TextStyle(fontSize = fixedSp(11f)), color = ink.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(song.durationMs), style = mono.copy(fontSize = fixedSp(10f)), color = ink.copy(alpha = 0.62f), modifier = Modifier.padding(start = 8.dp))
    }
}
