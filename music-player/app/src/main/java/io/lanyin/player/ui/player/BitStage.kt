package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.design.BitPx
import io.lanyin.player.ui.design.GameHud
import io.lanyin.player.ui.design.GameScreen
import io.lanyin.player.ui.design.GameWindow
import io.lanyin.player.ui.design.SeekBar
import io.lanyin.player.ui.design.Sprites
import io.lanyin.player.ui.design.TinyText
import io.lanyin.player.ui.design.bayerAt
import io.lanyin.player.ui.design.drawPixelButton
import io.lanyin.player.ui.design.drawPixels
import io.lanyin.player.ui.design.drawSprite
import io.lanyin.player.ui.design.drawTiny
import io.lanyin.player.ui.design.formatTime
import io.lanyin.player.ui.design.inked
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.rememberCoverLuminance
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.design.spriteSize
import io.lanyin.player.ui.music.LocalOpenSheet
import io.lanyin.player.ui.music.Sheet
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LyricSize
import io.lanyin.player.ui.theme.PixelFamily
import io.lanyin.player.ui.theme.PlayerSkin
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

private val pixel: TextStyle get() = TextStyle(fontFamily = PixelFamily)

/**
 * 游 upright: a side-scrolling level. The HUD (stage = track, hearts = whether it's liked, a
 * tap on them likes it; the score and the time left), the level itself (the cat runs to the flag
 * as the song plays, the moon the cover in one bit; tap for the lyrics, swipe to change song),
 * the progress as a line of the level map, the line being sung in a dialogue box, the song's
 * name on a black bar and the keys.
 */
@Composable
internal fun BitPortrait(
    stage: StageContext,
    lyricsOnly: Boolean,
    onToggleLyrics: (() -> Unit)?,
    onCollapse: () -> Unit,
    onRotate: () -> Unit,
    onScrub: (Long?) -> Unit,
) {
    val state = stage.state
    val entrance = stage.entrance
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp),
    ) {
        PlayerTopBar(state, onCollapse, modifier = stage.dismissDrag.enter(entrance, 0))
        StageHud(state, Modifier.fillMaxWidth().height(22.dp).enter(entrance, 1))
        if (lyricsOnly) {
            // With the lyrics up, a tap anywhere on the window but a line brings the level back.
            GameScreen(state.isPlaying, Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp).enter(entrance, 1).backToCover(onToggleLyrics)) {
                StateLyrics(
                    state, lyricTextStyle(LyricSize.BASE_SP * 0.95f, stage.lyricScale).copy(fontFamily = PixelFamily), centered = false, anchorFraction = 0.4f,
                    scrubMs = stage.scrubMs, modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        } else {
            io.lanyin.player.ui.design.GameBox(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp).enter(entrance, 1)) {
                val tap = if (onToggleLyrics != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToggleLyrics) else Modifier
                SwipeToSkip(state, Modifier.fillMaxSize().then(tap).then(stage.dismissDrag)) {
                    SideScroller(state, Modifier.fillMaxSize().clipToBounds().sharedPlayer("artwork", zIndex = 1f))
                }
                // PAUSE blinking while stopped, and the stage card as each song begins.
                LcdOverlay(state, Modifier.matchParentSize())
            }
        }
        SeekBar(state::positionMs, state.durationMs, state.isPlaying, state::seekTo, Modifier.fillMaxWidth().padding(top = 4.dp), onScrub = onScrub)
        if (!lyricsOnly) DialogLyrics(stage, Modifier.fillMaxWidth().height(78.dp).enter(entrance, 2), onToggleLyrics)
        TitleBar(state, Modifier.fillMaxWidth().padding(top = 6.dp).enter(entrance, 3))
        BitKeys(state, Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp).enter(entrance, 4))
        UtilityRow(state, onToggleLyrics = onToggleLyrics, lyricsShown = lyricsOnly, onRotate = onRotate, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp).enter(entrance, 5))
    }
}

/**
 * 游 on its side: the HUD, then the level wide across the screen with the progress under it,
 * and along the foot the dialogue box and the keys. A tap on the level takes it full screen.
 */
@Composable
internal fun BitLandscape(
    state: PlayerState,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    dismissDrag: Modifier,
    onCollapse: () -> Unit,
    onRotate: () -> Unit,
    lyricScale: Float,
    skin: PlayerSkin,
    spin: SpinState,
) {
    val entrance = rememberEntrance(Unit)
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val stage = StageContext(state, skin, spin, chromeVisible, scrubMs, lyricScale, dismissDrag, entrance)
    Column(Modifier.fillMaxSize().displayCutoutPadding().padding(horizontal = 18.dp, vertical = 6.dp)) {
        AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LandscapeTopBar(state, onCollapse, onRotate, modifier = dismissDrag.enter(entrance, 0))
        }
        StageHud(state, Modifier.fillMaxWidth().height(22.dp))
        io.lanyin.player.ui.design.GameBox(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp).enter(entrance, 1)) {
            SwipeToSkip(state, Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onToggleChrome() } }) {
                SideScroller(state, Modifier.fillMaxSize().clipToBounds().sharedPlayer("artwork", zIndex = 1f))
            }
            LcdOverlay(state, Modifier.matchParentSize())
        }
        SeekBar(state::positionMs, state.durationMs, state.isPlaying, state::seekTo, Modifier.fillMaxWidth(), onScrub = { scrubMs = it })
        AnimatedVisibility(chromeVisible) {
            Row(Modifier.fillMaxWidth().height(70.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DialogLyrics(stage, Modifier.weight(1f).fillMaxHeight(), onToggle = null)
                Spacer(Modifier.width(10.dp))
                BitKeys(state, Modifier.width(250.dp).height(52.dp), compact = true)
            }
        }
    }
}

/**
 * The HUD over the level: the stage (track of the queue), three hearts (full when the song is
 * liked; a tap on them likes it), the score (a hundred for every line sung) and the time left.
 */
@Composable
private fun StageHud(state: PlayerState, modifier: Modifier) {
    // By the second: the time and the score don't need every frame.
    val seconds by remember(state) { derivedStateOf { (state.positionMs / 1000).toInt() } }
    val lines by remember(state) { derivedStateOf { state.currentLyricIndex + 1 } }
    val left = ((state.durationMs / 1000).toInt() - seconds).coerceAtLeast(0)
    Box(modifier) {
        GameHud(
            "%d-%02d".format(state.trackNumber, state.trackCount), 3, "TIME %03d".format(left.coerceAtMost(999)), Modifier.fillMaxSize(),
            middle = "SCORE %06d".format(lines.coerceAtLeast(0) * 100), filled = if (state.liked) 3 else 0,
        )
        Box(Modifier.padding(start = 40.dp).width(60.dp).fillMaxHeight().pressable(label = if (state.liked) "取消喜欢" else "喜欢", onClick = state::toggleLike))
    }
}

/**
 * The dialogue box: the line being sung typed out a letter at a time as a game prints its text,
 * the next line under it, and the blinking ▼ once the line is all there.
 */
@Composable
private fun DialogLyrics(stage: StageContext, modifier: Modifier, onToggle: (() -> Unit)?) {
    val c = Lanyin.colors
    val state = stage.state
    val index = stage.scrubMs?.let { lyricIndexAt(state.lyrics, it) } ?: state.currentLyricIndex
    val line = state.lyrics.getOrNull(index)
    val text = line?.text ?: emptyLyricsNote(state)
    val next = state.lyrics.getOrNull(index + 1)?.text
    // Letters shown, and the blink: from the position, a few steps a second.
    val step by remember(state, line) { derivedStateOf { if (line == null) -1 else ((state.positionMs - line.startMs) / 45).toInt() } }
    val shown = if (step < 0) text.length else step.coerceIn(0, text.length)
    Box(modifier.then(if (onToggle != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToggle) else Modifier)) {
        GameWindow(Modifier.fillMaxSize(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text.take(shown), style = pixel.copy(fontSize = fixedSp(17f * stage.lyricScale), lineHeight = fixedSp(22f * stage.lyricScale)), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (next != null) Text(next, style = pixel.copy(fontSize = fixedSp(13f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (shown >= text.length && (step / 11) % 2 == 0) {
            Canvas(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 10.dp).size(10.dp, 6.dp)) { drawSprite(Sprites.Pointer, 0f, 0f, BitPx.toPx(), c.content) }
        }
    }
}

/** The song's name on a black bar, with the stage number and its length in the tiny face. */
@Composable
private fun TitleBar(state: PlayerState, modifier: Modifier) {
    val c = Lanyin.colors
    Row(modifier.background(c.content).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TrackSwitch(state, Modifier.weight(1f).sharedPlayer("title", zIndex = 2f, scaleContent = true)) { track ->
            Text("${track.title}  ${track.artist}", style = pixel.copy(fontSize = fixedSp(16f)), color = c.bg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TinyText("%d-%02d . %s".format(state.trackNumber, state.trackCount, formatTime(state.durationMs)), c.bg, Modifier.padding(start = 8.dp))
    }
}

/**
 * 游's keys: play mode, back, play / pause (the big one, in ink), forward and the queue. Each a
 * pixel key standing on its foot, sinking a pixel when pressed.
 */
@Composable
internal fun BitKeys(state: PlayerState, modifier: Modifier, compact: Boolean = false) {
    val openSheet = LocalOpenSheet.current
    val mode = when (state.playMode) {
        PlayMode.REPEAT_ALL -> Sprites.Repeat
        PlayMode.REPEAT_ONE -> Sprites.RepeatOne
        PlayMode.SHUFFLE -> Sprites.Shuffle
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!compact) PixelKey(mode, state.playMode.label, on = false, Modifier.weight(1f), state::cyclePlayMode)
        PixelKey(Sprites.Prev, "上一首", on = false, Modifier.weight(1f)) { state.skipPrevious() }
        PixelKey(if (state.isPlaying) Sprites.Pause else Sprites.Play, if (state.isPlaying) "暂停" else "播放", on = true, Modifier.weight(1.5f), state::togglePlay)
        PixelKey(Sprites.Next, "下一首", on = false, Modifier.weight(1f), state::skipNext)
        if (!compact) PixelKey(Sprites.List, "播放队列", on = false, Modifier.weight(1f)) { openSheet(Sheet.Queue) }
    }
}

/** A pixel key with [glyph] on it at twice the pixel; [on] in ink. */
@Composable
private fun PixelKey(glyph: List<String>, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Lanyin.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Canvas(
        modifier
            .fillMaxHeight()
            .clickable(source, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        val px = BitPx.toPx()
        val sink = if (pressed) px else 0f
        translate(0f, sink) {
            drawPixelButtonSized(c.content, c.bg, on, Size(size.width, size.height - sink), px)
            val (w, h) = spriteSize(glyph)
            val gp = px * 2
            drawSprite(glyph, ((size.width - w * gp) / 2f / px).toInt() * px, ((size.height - 2 * px - h * gp) / 2f / px).toInt() * px, gp, if (on) c.bg else c.content)
        }
    }
}

private fun DrawScope.drawPixelButtonSized(ink: Color, paper: Color, on: Boolean, area: Size, px: Float) {
    drawRect(ink, size = area)
    drawRect(if (on) ink else paper, Offset(px, px), Size(area.width - 2 * px, area.height - 4 * px))
    if (on) drawRect(paper, Offset(px, area.height - 3 * px), Size(area.width - 2 * px, px))
}

// ─── The level ───────────────────────────────────────────────────────────────

/** The parts of the level that don't move with the song, drawn once for its size. */
private class StageArt(val cols: Int, val rows: Int, ink: Int, paper: Int) {
    val ground = rows - max(16, (rows * 0.12f).toInt())
    /** The sky, dithered darker towards the hills, and its stars. */
    val sky: ImageBitmap
    /** The far hills and the near ones with their lamps, as tiles that repeat. */
    val far: ImageBitmap
    val near: ImageBitmap
    /** The brick floor, a tile two bricks wide. */
    val floor: ImageBitmap

    init {
        val clear = 0
        sky = bitmap(cols, ground) { x, y ->
            val t = y.toFloat() / ground
            if (bayerAt(x, y) < 0.04f + t * 0.24f) ink else paper
        }
        val farTop = ground - ((ground) * 0.3f).toInt()
        far = bitmap(FarTile, ground) { x, y ->
            val h = farTop + 12 * sin(x * 2 * PI.toFloat() * 2 / FarTile + 1) + 7 * sin(x * 2 * PI.toFloat() * 5 / FarTile)
            when {
                y == h.toInt() -> paper
                y > h -> ink
                else -> clear
            }
        }
        val nearTop = ground - (ground * 0.14f).toInt()
        near = bitmap(NearTile, ground) { x, y ->
            val h = (nearTop + 6 * sin(x * 2 * PI.toFloat() * 2 / NearTile + 3)).toInt()
            when {
                y == h -> paper
                y == h + 1 -> ink
                y > h -> if ((x + y) % 3 == 0) paper else ink
                else -> clear
            }
        }
        floor = bitmap(24, rows - ground) { x, y ->
            val row = y / 6
            val mortarY = y % 6 == 1
            val off = if (row % 2 == 0) 0 else 6
            val mortarX = (x + off) % 12 == 0
            if (y > 0 && (mortarY || mortarX)) paper else ink
        }
    }

    private inline fun bitmap(w: Int, h: Int, color: (Int, Int) -> Int): ImageBitmap {
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = color(x, y)
        return android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    companion object {
        const val FarTile = 280
        const val NearTile = 160
    }
}

/** The moon: the cover in one bit inside a disc of [r] pixels, an ink ring and a dithered halo. */
private fun moonBitmap(cells: FloatArray, n: Int, r: Int, ink: Int, paper: Int): ImageBitmap {
    val side = 2 * r + 11
    val c = side / 2
    val px = IntArray(side * side)
    for (y in 0 until side) for (x in 0 until side) {
        val dx = x - c
        val dy = y - c
        val d = hypot(dx.toFloat(), dy.toFloat())
        px[y * side + x] = when {
            d <= r -> {
                val u = ((dx + r) / (2f * r) * (n - 1)).toInt().coerceIn(0, n - 1)
                val v = ((dy + r) / (2f * r) * (n - 1)).toInt().coerceIn(0, n - 1)
                val l = (0.2f + cells[v * n + u] * 0.95f).coerceIn(0f, 1f)
                if (inked(l, x, y)) ink else paper
            }
            d <= r + 1.5f -> ink
            d <= r + 3f -> paper
            d <= r + 4.2f && (x + y) % 2 == 0 -> paper
            else -> 0
        }
    }
    return android.graphics.Bitmap.createBitmap(px, side, side, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * The level, drawn in one bit at [BitPx] a pixel. The sky darkening to the hills, the moon (the
 * cover), stars and two clouds; far hills, near hills with their lamps and the brick floor, each
 * scrolling at its own pace as the cat runs; a note block for each line of the song (struck,
 * and spent, as the cat passes under it at its time), notes in arcs between to collect, and the
 * flag at the end, reached as the song ends. Paused, the cat sits down.
 */
@Composable
internal fun SideScroller(state: PlayerState, modifier: Modifier) {
    val c = Lanyin.colors
    val ink = c.content
    val paper = c.bg
    val cells = rememberCoverLuminance(state.coverUrl, state.coverSeed, 40)
    // A twelfth of a second at a time: the level moves in steps, as a game's does.
    val step by remember(state) { derivedStateOf { state.positionMs / 83 } }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val px = with(density) { BitPx.toPx() }
        val cols = with(density) { (maxWidth.toPx() / px).toInt() }.coerceAtLeast(60)
        val rows = with(density) { (maxHeight.toPx() / px).toInt() }.coerceAtLeast(60)
        val art = remember(cols, rows, ink, paper) { StageArt(cols, rows, ink.toArgb(), paper.toArgb()) }
        val r = (minOf(cols, art.ground) * 0.17f).toInt().coerceAtLeast(8)
        val moon = remember(cells, r, ink, paper) { moonBitmap(cells, 40, r, ink.toArgb(), paper.toArgb()) }
        Canvas(Modifier.fillMaxSize()) {
            val pos = step * 83
            val dur = state.durationMs.coerceAtLeast(1)
            val f = (pos.toFloat() / dur).coerceIn(0f, 1f)
            val g = art.ground
            val level = max(cols * 3f, dur / 1000f * 8f)
            val catX = (cols * 0.2f).toInt()
            val cam = f * level - catX
            // Sky, stars, moon.
            drawPixels(art.sky, IntOffset.Zero, IntSize((cols * px).toInt(), (g * px).toInt()))
            val mcx = cols - r - (cols * 0.08f).toInt()
            val mcy = r + (rows * 0.1f).toInt() + 2
            for (k in 0 until 7) {
                val sx = ((hashF(k, 3) * (cols - 10)) + 4).toInt()
                val sy = ((hashF(k, 5) * g * 0.45f) + 4).toInt()
                if (hypot((sx - mcx).toFloat(), (sy - mcy).toFloat()) < r + 8) continue
                val twinkle = (step / 6 + k) % 5 != 0L
                pixel(sx, sy, paper, px)
                if (twinkle) for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) pixel(sx + dx, sy + dy, ink, px)
            }
            drawPixels(moon, IntOffset(((mcx - moon.width / 2) * px).toInt(), ((mcy - moon.height / 2) * px).toInt()), IntSize((moon.width * px).toInt(), (moon.height * px).toInt()))
            // Two clouds drifting slowly the other way.
            for ((k, base) in listOf(0.12f, 0.5f).withIndex()) {
                val span = cols + 30
                val cx = (((base * cols - cam * 0.05f - step * 0.15f) % span + span) % span).toInt() - 15
                val cy = (rows * (0.18f + 0.08f * k)).toInt()
                drawSprite(Sprites.CloudFill, cx * px, cy * px, px, paper)
                drawSprite(Sprites.Cloud, cx * px, (cy - 1) * px, px, ink)
            }
            // Hills and floor, each at its own pace.
            tiles(art.far, cam * 0.25f, px, cols)
            tiles(art.near, cam * 0.5f, px, cols)
            for (k in 0..((cols + 24) / 24)) {
                val x = k * 24 - (cam.toInt() % 24 + 24) % 24
                drawPixels(art.floor, IntOffset((x * px).toInt(), (g * px).toInt()), IntSize((24 * px).toInt(), ((rows - g) * px).toInt()))
            }
            // Lamps along the near hills, lit.
            run {
                val gap = 64
                var lx = -(((cam * 0.5f).toInt() % gap + gap) % gap) + 18
                while (lx < cols + 4) {
                    for (y in g - 22..g) pixel(lx, y, paper, px)
                    for (x in lx - 2..lx + 2) for (y in g - 24..g - 22) pixel(x, y, paper, px)
                    pixel(lx, g - 21, ink, px)
                    for (rr in listOf(4f, 7f)) for (a in 0 until 16) {
                        val t = a / 16f * 2 * PI.toFloat()
                        pixel((lx + rr * kotlin.math.cos(t)).toInt(), (g - 23 + rr * sin(t) * 0.7f).toInt(), paper, px)
                    }
                    lx += gap
                }
            }
            // A block for every line of the song; spent once its time has come.
            val lines = state.lyrics
            for (line in lines) {
                val wx = line.startMs.toFloat() / dur * level
                val sx = (wx - cam).toInt() + 6
                if (sx < -14 || sx > cols + 2) continue
                val spent = line.startMs <= pos
                val by = g - 48
                rectPx(sx, by, 11, 11, ink, px)
                if (spent) {
                    for ((dx, dy) in listOf(2 to 2, 8 to 2, 2 to 8, 8 to 8)) pixel(sx + dx, by + dy, paper, px)
                } else {
                    rectPx(sx + 1, by + 1, 9, 9, paper, px)
                    drawSprite(Sprites.Note, (sx + 3) * px, (by + 3) * px, px, ink)
                }
            }
            // Notes in arcs between, collected as the cat runs through them.
            run {
                val spacing = 11
                val first = ((cam + catX) / spacing).toInt() + 1
                var k = first
                while (true) {
                    val wx = k * spacing.toFloat()
                    val sx = (wx - cam).toInt()
                    if (sx > cols) break
                    if (wx < level) {
                        val arc = (k % 16)
                        if (arc < 6) {
                            val ny = g - 20 - (10 * sin(arc / 5f * PI.toFloat())).toInt()
                            rectPx(sx - 1, ny - 1, 7, 7, paper, px)
                            drawSprite(Sprites.Note, sx * px, ny * px, px, ink)
                        }
                    }
                    k++
                }
            }
            // The flag at the end of the level, and its pole.
            val flagX = (level - cam).toInt() + 14
            if (flagX < cols + 6) {
                for (y in g - 34 until g) pixel(flagX, y, paper, px)
                rectPx(flagX - 7, g - 34, 7, 6, paper, px)
                rectPx(flagX - 6, g - 33, 5, 4, ink, px)
            }
            // The cat: running while the song plays, a hop as each line starts, sitting when paused.
            val playing = state.isPlaying
            val since = lines.lastOrNull { it.startMs <= pos }?.let { pos - it.startMs } ?: Long.MAX_VALUE
            val hop = if (playing && since < 420) (sin(since / 420f * PI.toFloat()) * 9).toInt() else 0
            val sprite = when {
                !playing -> Sprites.CatSit
                (step / 2) % 2 == 0L -> Sprites.CatRun
                else -> Sprites.CatRun2
            }
            val (cw, ch) = spriteSize(sprite)
            val cy = g - ch - 1 - hop
            rectPx(catX - 1, cy - 1, cw + 2, ch + 2, paper, px)
            drawSprite(sprite, catX * px, cy * px, px, ink, paper)
            if (playing && hop == 0) for ((dx, dy) in listOf(-4 to -3, -7 to -5, -9 to -2)) pixel(catX + dx - (step % 3).toInt(), g + dy, paper, px)
            // What the last line scored, rising off its block.
            if (playing && since < 700) {
                val y = g - 58 - (since / 60).toInt()
                drawTiny("+100", (catX + 4) * px, y * px, px, ink)
            }
        }
    }
}

private fun DrawScope.pixel(x: Int, y: Int, color: Color, px: Float) = drawRect(color, Offset(x * px, y * px), Size(px, px))

private fun DrawScope.rectPx(x: Int, y: Int, w: Int, h: Int, color: Color, px: Float) = drawRect(color, Offset(x * px, y * px), Size(w * px, h * px))

/** A layer tile repeated across [cols] pixels, scrolled by [offset] pixels. */
private fun DrawScope.tiles(tile: ImageBitmap, offset: Float, px: Float, cols: Int) {
    val w = tile.width
    var x = -((offset.toInt() % w + w) % w)
    while (x < cols) {
        drawPixels(tile, IntOffset((x * px).toInt(), 0), IntSize((w * px).toInt(), (tile.height * px).toInt()))
        x += w
    }
}

private fun hashF(i: Int, salt: Int): Float = io.lanyin.player.ui.design.hash01(i, salt)
