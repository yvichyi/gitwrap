package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.lanyin.player.core.audio.pulseAfter
import io.lanyin.player.ui.design.GameScreen
import io.lanyin.player.ui.design.PlayPauseButton
import io.lanyin.player.ui.design.SeekBar
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LyricSize
import io.lanyin.player.ui.theme.PixelFamily
import io.lanyin.player.ui.theme.PlayerSkin

private val PadDark = Color(0xFF2D2C33)
private val PadRim = Color(0xFF17161B)
private val PadGlyph = Color(0xFFC9C6D1)

/**
 * 游 in landscape is the whole console held sideways. The LCD in the middle shows the artwork, the
 * lyrics and a block progress bar, with a little sprite dancing on the beat; the D-pad on the left
 * steps back and forward through the queue (up likes the song, down opens the queue); A on the
 * right plays and pauses, B changes the play mode; SELECT picks the quality and START takes the
 * stage full screen. The usual top bar folds away as on the other themes.
 */
@Composable
internal fun ConsoleLandscape(
    state: PlayerState,
    skin: PlayerSkin,
    spin: SpinState,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    dismissDrag: Modifier,
    onCollapse: () -> Unit,
    onRotate: () -> Unit,
    lyricScale: Float,
) {
    val entrance = rememberEntrance(Unit)
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val openSheet = io.lanyin.player.ui.music.LocalOpenSheet.current
    val pixel = TextStyle(fontFamily = PixelFamily)
    Column(Modifier.fillMaxSize().displayCutoutPadding().padding(horizontal = 18.dp, vertical = 6.dp)) {
        AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LandscapeTopBar(state, onCollapse, onRotate, modifier = dismissDrag.enter(entrance, 0))
        }
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(136.dp).fillMaxHeight().enter(entrance, 1),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                DPad(state, onQueue = { openSheet(io.lanyin.player.ui.music.Sheet.Queue) }, Modifier.size(112.dp))
                Text("◀▶ 切歌  ▲ 喜欢  ▼ 队列", style = pixel.copy(fontSize = fixedSp(10f)), color = Lanyin.colors.muted, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MenuPill("SELECT", "音质") { openSheet(io.lanyin.player.ui.music.Sheet.Quality) }
                    MenuPill("START", if (chromeVisible) "全屏" else "退出全屏", onToggleChrome)
                }
            }
            GameScreen(
                state.isPlaying,
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .enter(entrance, 1)
                    .pointerInput(Unit) { detectTapGestures { onToggleChrome() } },
            ) {
                Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    val label = Lanyin.style.type.label.copy(fontFamily = PixelFamily, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
                    Text(if (state.isPlaying) "▶ NOW PLAYING" else "Ⅱ PAUSE", style = label, color = Lanyin.colors.content, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    BeatSprite(state, Modifier.size(20.dp))
                    Spacer(Modifier.weight(1f))
                    Text((rememberBpm(state)?.let { "BPM $it  " } ?: "") + "%02d/%02d".format(state.trackNumber, state.trackCount), style = label, color = Lanyin.colors.content, maxLines = 1)
                }
                val stage = StageContext(state, skin, spin, chromeVisible, scrubMs, lyricScale, dismissDrag, entrance)
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (skin != PlayerSkin.LYRICS) {
                        Box(Modifier.weight(0.42f).fillMaxHeight()) {
                            StageArtwork(stage, Modifier.fillMaxSize().then(dismissDrag), heightFraction = 0.9f)
                            LcdOverlay(state, Modifier.matchParentSize())
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    StageLyricsColumn(stage, Modifier.weight(0.58f).fillMaxHeight(), baseSp = LyricSize.BASE_SP * 0.9f, centered = skin == PlayerSkin.LYRICS)
                }
                SeekBar(state::positionMs, state.durationMs, state.isPlaying, state::seekTo, Modifier.fillMaxWidth(), onScrub = { scrubMs = it })
            }
            Column(
                Modifier.width(136.dp).fillMaxHeight().enter(entrance, 1),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Box(Modifier.size(width = 124.dp, height = 104.dp)) {
                    BButton(state, Modifier.align(Alignment.BottomStart))
                    Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayPauseButton(state.isPlaying, state::togglePlay, 60.dp, feed = state.audioFeed)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    Text("B 模式", style = pixel.copy(fontSize = fixedSp(10f)), color = Lanyin.colors.muted)
                    Text("A 播放", style = pixel.copy(fontSize = fixedSp(10f)), color = Lanyin.colors.muted)
                }
                SpeakerSlots(state, Modifier.size(width = 76.dp, height = 44.dp))
            }
        }
    }
}

/** The D-pad: a dark cross standing on its rim, each arm a button that dips as it's pressed. */
@Composable
internal fun DPad(state: PlayerState, onQueue: () -> Unit, modifier: Modifier) {
    val sources = remember { List(4) { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState().value }
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val s = size.minDimension
            val arm = s / 3f
            val cross = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(arm, 0f, arm * 2, s, CornerRadius(4.dp.toPx())))
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, arm, s, arm * 2, CornerRadius(4.dp.toPx())))
            }
            translate(0f, 4.dp.toPx()) { drawPath(cross, PadRim) }
            drawPath(cross, PadDark)
            drawCircle(Color.Black.copy(alpha = 0.25f), arm * 0.32f, Offset(s / 2f, s / 2f))
            // Arrows, the pressed arm darkened as if pushed down.
            val arms = listOf(Offset(s / 2f, arm / 2f), Offset(s - arm / 2f, s / 2f), Offset(s / 2f, s - arm / 2f), Offset(arm / 2f, s / 2f))
            arms.forEachIndexed { i, at ->
                if (pressed[i]) drawRect(Color.Black.copy(alpha = 0.3f), at - Offset(arm / 2f, arm / 2f), Size(arm, arm))
                rotate(90f * i, at) { arrow(at, arm * 0.22f, PadGlyph) }
            }
        }
        val arm = 112.dp / 3
        PadArm(sources[0], "喜欢", Modifier.align(Alignment.TopCenter).size(arm), state::toggleLike)
        PadArm(sources[1], "下一首", Modifier.align(Alignment.CenterEnd).size(arm), state::skipNext)
        PadArm(sources[2], "播放队列", Modifier.align(Alignment.BottomCenter).size(arm), onQueue)
        PadArm(sources[3], "上一首", Modifier.align(Alignment.CenterStart).size(arm), state::skipPrevious)
    }
}

@Composable
private fun PadArm(source: MutableInteractionSource, label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    )
}

/** An arrow pointing up at [at], [r] across. */
private fun DrawScope.arrow(at: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(at.x, at.y - r)
        lineTo(at.x + r, at.y + r * 0.6f)
        lineTo(at.x - r, at.y + r * 0.6f)
        close()
    }
    drawPath(path, color)
}

/** SELECT or START: a little slanted rubber pill with its job written under it. */
@Composable
internal fun MenuPill(name: String, job: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(interactionSource = source, indication = null, onClick = onClick).semantics { contentDescription = job }) {
        Canvas(Modifier.size(width = 38.dp, height = 14.dp).graphicsLayer { rotationZ = -22f; translationY = if (down) 2f * density else 0f }) {
            drawRoundRect(PadRim, Offset(0f, 2.dp.toPx()), size, CornerRadius(size.height / 2f))
            drawRoundRect(lerp(PadDark, Color.White, 0.18f), cornerRadius = CornerRadius(size.height / 2f))
        }
        Text(name, style = TextStyle(fontFamily = PixelFamily, fontSize = fixedSp(9f)), color = Lanyin.colors.accentAlt, modifier = Modifier.padding(top = 4.dp))
        Text(job, style = TextStyle(fontFamily = PixelFamily, fontSize = fixedSp(9f)), color = Lanyin.colors.muted, maxLines = 1)
    }
}

/** B: a smaller magenta button that steps through the play modes, showing which it's on. */
@Composable
internal fun BButton(state: PlayerState, modifier: Modifier) {
    val c = Lanyin.colors
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    Box(
        modifier
            .size(46.dp)
            .semantics { contentDescription = "播放模式：${state.playMode.label}" }
            .clickable(interactionSource = source, indication = null, onClick = state::cyclePlayMode),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize().offset(y = if (down) 3.dp else 0.dp)) {
            val r = size.minDimension / 2f
            if (!down) drawCircle(lerp(c.accent, Color.Black, 0.35f), r, center + Offset(0f, 3.dp.toPx()))
            drawCircle(c.accent, r)
            drawCircle(Color.White.copy(alpha = 0.16f), r * 0.76f, center - Offset(r * 0.1f, r * 0.18f))
            drawCircle(c.accent, r * 0.72f, center - Offset(r * 0.04f, r * 0.08f))
        }
        Text(
            when (state.playMode) {
                PlayMode.REPEAT_ALL -> "B"
                PlayMode.REPEAT_ONE -> "B¹"
                PlayMode.SHUFFLE -> "B?"
            },
            style = TextStyle(fontFamily = PixelFamily, fontSize = fixedSp(14f)),
            color = Color.White,
            modifier = Modifier.offset(y = if (down) 3.dp else 0.dp),
        )
    }
}

/** A little pixel figure on the LCD that changes pose on each beat. */
@Composable
internal fun BeatSprite(state: PlayerState, modifier: Modifier) {
    val clock = rememberFrameClock(state.isPlaying)
    val ink = Lanyin.colors.content
    val poses = remember {
        listOf(
            listOf("00111100", "00111100", "00011000", "01111110", "10111101", "00100100", "01000010", "11000011"),
            listOf("00111100", "00111100", "10011001", "01111110", "00111100", "00100100", "00100100", "01100110"),
        )
    }
    Canvas(modifier) {
        clock.floatValue
        // One pose, then the other on each beat; standing still when paused.
        val beat = state.isPlaying && pulseAfter(state.audioFeed.sinceBeat(), state.audioFeed.beatPeriod(), 0.25f) > 0f
        val rows = poses[if (beat) 1 else 0]
        val px = size.minDimension / 8f
        val lift = if (beat) -px else 0f
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch -> if (ch == '1') drawRect(ink, Offset(x * px, y * px + lift), Size(px + 0.5f, px + 0.5f)) }
        }
    }
}

/** The speaker's slanted slots, a little brighter as the bass pushes. */
@Composable
internal fun SpeakerSlots(state: PlayerState, modifier: Modifier) {
    val clock = rememberFrameClock(state.isPlaying)
    val levels = remember { FloatArray(32) }
    val eased = remember { floatArrayOf(0f) }
    Canvas(modifier) {
        clock.floatValue
        var bass = 0f
        if (state.isPlaying && state.audioFeed.levels(levels)) for (i in 0 until 6) bass += levels[i] / 6f
        eased[0] += (bass - eased[0]) * 0.4f
        val n = 6
        val gap = size.width / (n + 1)
        for (k in 0 until n) {
            val x = gap * (k + 1)
            drawLine(PadRim.copy(alpha = 0.55f + 0.35f * eased[0]), Offset(x - size.height * 0.25f, size.height), Offset(x + size.height * 0.25f, 0f), 4.dp.toPx(), StrokeCap.Round)
        }
    }
}
