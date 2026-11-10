package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.design.Cassette
import io.lanyin.player.ui.design.Hifi
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.MechCounter
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.VfdLevels
import io.lanyin.player.ui.design.drawKeycap
import io.lanyin.player.ui.design.drawLed
import io.lanyin.player.ui.design.drawPlayPauseGlyph
import io.lanyin.player.ui.design.engraved
import io.lanyin.player.ui.design.formatTime
import io.lanyin.player.ui.design.keyColors
import io.lanyin.player.ui.design.keyTravel
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PixelFamily
import io.lanyin.player.ui.theme.SpaceMonoFamily

/**
 * 机's portrait stage: the cassette deck. The tape behind the smoked door, lit by a warm lamp,
 * its reels winding as the song plays (swipe it to change song, tap for the lyrics); the eject
 * key (the queue), the side and the mechanical counter; and the fluorescent display with the song,
 * the level meters, the line being sung and the time.
 */
@Composable
internal fun DeckPortraitStage(stage: StageContext, onShowLyrics: () -> Unit) {
    val state = stage.state
    Column(Modifier.fillMaxSize()) {
        CassetteWell(stage, Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp).enter(stage.entrance, 1), onShowLyrics)
        CounterRow(state, Modifier.padding(top = 10.dp).enter(stage.entrance, 2))
        // The display takes what the door doesn't need: a few more lines of the song on a tall phone.
        DeckDisplay(stage, Modifier.padding(top = 10.dp).fillMaxWidth().weight(0.62f).heightIn(min = 176.dp).enter(stage.entrance, 2), onShowLyrics, shared = true, wide = true)
    }
}

/** 机 on its side: the cassette in its well on the left, the display with the lyrics on the right. */
@Composable
internal fun DeckWideStage(stage: StageContext) {
    Row(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        Column(Modifier.weight(0.44f).fillMaxHeight().enter(stage.entrance, 1)) {
            CassetteWell(stage, Modifier.weight(1f).fillMaxWidth(), onShowLyrics = null)
            CounterRow(stage.state, Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.width(16.dp))
        DeckDisplay(stage, Modifier.weight(0.56f).fillMaxHeight().enter(stage.entrance, 2), onShowLyrics = null, shared = false, wide = true)
    }
}

/**
 * The cassette well: a smoked door with a screw in each corner, a warm lamp glowing behind it from
 * the top left, and the cassette fitted inside at its own proportions.
 */
@Composable
private fun CassetteWell(stage: StageContext, modifier: Modifier, onShowLyrics: (() -> Unit)?) {
    val state = stage.state
    BoxWithConstraints(
        modifier
            .drawBehind { drawWell() }
            .then(if (onShowLyrics != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onShowLyrics) else Modifier)
            .then(stage.dismissDrag),
        contentAlignment = Alignment.Center,
    ) {
        val w = minOf(maxWidth * 0.9f, (maxHeight - 24.dp) * (100f / 64f))
        SwipeToSkip(state, Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Cassette(
                    state.title, state.artist,
                    { (state.positionMs.toFloat() / state.durationMs).let { if (it.isFinite()) it else 0f } },
                    state.isPlaying,
                    Modifier.size(w, w * 0.64f).sharedPlayer("artwork", zIndex = 1f).semantics { contentDescription = "磁带：${state.title}" },
                )
            }
        }
        Text("A ▮", style = engraved(7f), color = Hifi.Lamp.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp))
    }
}

/** The door: its bezel set into the plate, smoked glass lit warm from the top left, four screws. */
private fun DrawScope.drawWell() {
    val one = 1.dp.toPx()
    val r = CornerRadius(4.dp.toPx())
    drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(-one, -one), Size(size.width + 2 * one, size.height + one), r)
    drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(-one, size.height - one), Size(size.width + 2 * one, 2 * one), r)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF1B1613), Color(0xFF120E0C))), cornerRadius = r)
    drawRect(Brush.radialGradient(listOf(Color(0xFFFFB45A).copy(alpha = 0.28f), Color(0xFFFFB45A).copy(alpha = 0.06f), Color.Transparent), Offset(size.width * 0.3f, size.height * 0.15f), size.maxDimension * 0.75f))
    // The window's frame inside the door, and the door's screws.
    val inset = 8.dp.toPx()
    drawRoundRect(Color.White.copy(alpha = 0.07f), Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius(6.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(one))
    for (x in floatArrayOf(inset * 2.2f, size.width - inset * 2.2f)) for (y in floatArrayOf(inset * 2.2f, size.height - inset * 2.2f)) {
        drawCircle(Color.Black.copy(alpha = 0.6f), 3.dp.toPx(), Offset(x, y))
        drawCircle(Color(0xFF3A332E), 2.2.dp.toPx(), Offset(x, y))
    }
    // The glass: a diagonal sheen.
    drawRect(Brush.linearGradient(0.25f to Color.Transparent, 0.4f to Color.White.copy(alpha = 0.05f), 0.5f to Color.Transparent, start = Offset.Zero, end = Offset(size.width, size.height)))
}

/** Under the door: the eject key (the queue), the side and the tape's length, and the counter. */
@Composable
private fun CounterRow(state: PlayerState, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val openSheet = io.lanyin.player.ui.music.LocalOpenSheet.current
    // The counter turns by the second; reading the position itself would recompose the row every frame.
    val seconds by remember(state) { derivedStateOf { (state.positionMs / 1000).toInt() } }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(46.dp, 26.dp)
                .pressable(label = "播放队列") { openSheet(io.lanyin.player.ui.music.Sheet.Queue) }
                .drawBehind { drawKeycap(RoundedCornerShape(3.dp), style.keyColors()) },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(14.dp, 11.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width / 2f, 0f); lineTo(size.width, size.height * 0.62f); lineTo(0f, size.height * 0.62f); close()
                }
                drawPath(path, c.content)
                drawRect(c.content, Offset(0f, size.height * 0.8f), Size(size.width, size.height * 0.2f))
            }
        }
        Text("EJECT", style = engraved(7f), color = c.muted, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        val minutes = ((state.durationMs + 59_999) / 60_000).coerceIn(1, 99)
        Text("SIDE A · $minutes MIN", style = engraved(7f), color = c.muted, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text("COUNTER", style = engraved(7f), color = c.muted, modifier = Modifier.padding(end = 8.dp))
        MechCounter(seconds)
    }
}

/** The display's letters: the pixel face in phosphor, glowing. */
@Composable
private fun vfd(size: Float, color: Color = Hifi.Vfd) = TextStyle(
    fontFamily = PixelFamily, fontSize = fixedSp(size), lineHeight = fixedSp(size * 1.25f), color = color,
    shadow = Shadow(color.copy(alpha = 0.6f), Offset.Zero, 9f),
)

/**
 * The deck's fluorescent display, behind black glass: the song and its number in the queue, the
 * singer and album, the L and R level meters, the line being sung large with the next one under
 * it, and along the foot the transport, tape type and noise reduction, and the time.
 */
@Composable
private fun DeckDisplay(stage: StageContext, modifier: Modifier, onShowLyrics: (() -> Unit)?, shared: Boolean, wide: Boolean = false) {
    val state = stage.state
    val seconds by remember(state) { derivedStateOf { (state.positionMs / 1000).toInt() } }
    val small = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixedSp(7.5f), fontWeight = FontWeight.Bold)
    Column(
        modifier
            .drawBehind { drawVfdGlass() }
            .clipToBounds()
            .then(if (onShowLyrics != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onShowLyrics) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TrackSwitch(state, Modifier.weight(1f).then(if (shared) Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true) else Modifier)) { track ->
                Text(track.title, style = vfd(16f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("TRACK %02d/%02d".format(state.trackNumber, state.trackCount), style = small.copy(color = Hifi.Amber), maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
        TrackSwitch(state, Modifier.padding(top = 3.dp)) { track ->
            Text("${track.artist} · ${track.album}", style = vfd(10.5f, Hifi.Vfd.copy(alpha = 0.65f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        VfdLevels(state.audioFeed, state.isPlaying, Modifier.padding(top = 6.dp).fillMaxWidth().height(36.dp))
        val index = stage.scrubMs?.let { lyricIndexAt(state.lyrics, it) } ?: state.currentLyricIndex
        val now = state.lyrics.getOrNull(index)?.text ?: emptyLyricsNote(state)
        val next = state.lyrics.getOrNull(index + 1)?.text
        val previous = state.lyrics.getOrNull(index - 1)?.text
        Column(Modifier.padding(top = 6.dp).then(if (wide) Modifier.weight(1f) else Modifier).clipToBounds(), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            if (wide && previous != null) Text(previous, style = vfd(11f, Hifi.Vfd.copy(alpha = 0.3f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            AnimatedContent(
                now,
                transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) togetherWith fadeOut(tween(120)) },
                label = "deckLine",
            ) { line ->
                Text(line, style = vfd((if (onShowLyrics == null) 26f else 21f) * stage.lyricScale), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(next ?: " ", style = vfd(11f, Hifi.Vfd.copy(alpha = 0.38f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.isPlaying) "PLAY ▶" else "PAUSE ❚❚", style = small.copy(color = Hifi.Vfd))
            Text("  CrO2", style = small.copy(color = Hifi.Vfd))
            Text("  NR B", style = small.copy(color = Hifi.Vfd))
            Text("  REPEAT", style = small.copy(color = Hifi.Vfd.copy(alpha = if (state.playMode == PlayMode.REPEAT_ONE) 1f else 0.14f)))
            Spacer(Modifier.weight(1f))
            Text("${formatTime(seconds * 1000L)} / ${formatTime(state.durationMs)}", style = small.copy(color = Hifi.Vfd))
        }
    }
}

/** The display's glass: black, its bezel, the fine mesh in front of the phosphor. */
private fun DrawScope.drawVfdGlass() {
    val one = 1.dp.toPx()
    val r = CornerRadius(3.dp.toPx())
    drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(-one, -one), Size(size.width + 2 * one, size.height + one), r)
    drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(-one, size.height - one), Size(size.width + 2 * one, 2 * one), r)
    drawRoundRect(Hifi.Glass, cornerRadius = r)
    drawRect(Brush.radialGradient(listOf(Hifi.Vfd.copy(alpha = 0.05f), Color.Transparent), Offset(size.width * 0.4f, size.height * 0.6f), size.maxDimension * 0.6f))
}

/**
 * 机's transport: piano keys in a row. Play mode (where a deck has REC), rewind for the previous
 * song, play, fast forward for the next, stop (back to the start, paused) and pause. Play and
 * pause latch down; a light over each tells what's engaged. [compact] drops the printed names,
 * for the landscape bar.
 */
@Composable
internal fun DeckKeys(state: PlayerState, modifier: Modifier, compact: Boolean = false) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        val modeIcon = when (state.playMode) {
            PlayMode.REPEAT_ALL -> LineIcon.Repeat
            PlayMode.REPEAT_ONE -> LineIcon.RepeatOne
            PlayMode.SHUFFLE -> LineIcon.Shuffle
        }
        PianoKey("MODE", state.playMode.label, down = false, led = null, compact = compact, onClick = state::cyclePlayMode, modifier = Modifier.weight(1f)) {
            ThemedIcon(modeIcon, null, tint = c.content, size = 18.dp)
        }
        PianoKey("REW", "上一首", down = false, led = null, compact = compact, onClick = { state.skipPrevious() }, modifier = Modifier.weight(1f)) {
            ThemedIcon(LineIcon.Previous, null, tint = c.content, size = 18.dp)
        }
        PianoKey("PLAY", "播放", down = state.isPlaying, led = Color(0xFF63E36B), compact = compact, onClick = { if (!state.isPlaying) state.togglePlay() }, modifier = Modifier.weight(1f)) {
            Canvas(Modifier.size(14.dp)) { drawPlayPauseGlyph(0f, c.content) }
        }
        PianoKey("F.FWD", "下一首", down = false, led = null, compact = compact, onClick = state::skipNext, modifier = Modifier.weight(1f)) {
            ThemedIcon(LineIcon.Next, null, tint = c.content, size = 18.dp)
        }
        PianoKey("STOP", "停止", down = false, led = null, compact = compact, onClick = {
            if (state.isPlaying) state.togglePlay()
            state.seekTo(0)
        }, modifier = Modifier.weight(1f)) {
            Canvas(Modifier.size(11.dp)) { drawRect(c.content) }
        }
        PianoKey("PAUSE", "暂停", down = !state.isPlaying && state.positionMs > 0, led = Hifi.Amber, compact = compact, onClick = { if (state.isPlaying) state.togglePlay() }, modifier = Modifier.weight(1f)) {
            Canvas(Modifier.size(14.dp)) { drawPlayPauseGlyph(1f, c.content) }
        }
    }
}

/**
 * One piano key: a tall aluminium key, its light over it, the name engraved under it. A key that's
 * [down] stays sunk; the glyph sinks with it.
 */
@Composable
private fun PianoKey(name: String, label: String, down: Boolean, led: Color?, compact: Boolean, onClick: () -> Unit, modifier: Modifier, glyph: @Composable () -> Unit) {
    val style = Lanyin.style
    val sink by animateFloatAsState(if (down) 1f else 0f, tween(90), label = "pianoKey")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (!compact) Canvas(Modifier.size(8.dp)) { drawLed(center, led ?: Hifi.Vfd, on = led != null && down, r = 1.8.dp.toPx()) }
        Box(
            Modifier
                .padding(top = if (compact) 0.dp else 3.dp)
                .fillMaxWidth()
                .then(if (compact) Modifier.fillMaxHeight() else Modifier.height(56.dp))
                .pressable(label = label, onClick = onClick)
                .drawBehind { drawKeycap(RoundedCornerShape(3.dp), style.keyColors(), press = sink) },
            contentAlignment = if (compact) Alignment.Center else Alignment.TopCenter,
        ) {
            Box(Modifier.offset(y = keyTravel * sink).padding(top = if (compact) 0.dp else 14.dp)) { glyph() }
        }
        if (!compact) Text(name, style = engraved(6.5f), color = style.colors.muted, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
    }
}
