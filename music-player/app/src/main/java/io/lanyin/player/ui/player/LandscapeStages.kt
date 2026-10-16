package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedContent
import io.lanyin.player.ui.design.pressable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.design.ChromeBrush
import io.lanyin.player.ui.design.DeviceScreen
import io.lanyin.player.ui.design.DoubleRule
import io.lanyin.player.ui.design.Marquee
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.VuMeter
import io.lanyin.player.ui.design.screenStyle
import io.lanyin.player.ui.theme.LocalLanyinStyle
import androidx.compose.runtime.CompositionLocalProvider
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.design.starPath
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.ArchivoBlackFamily
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LyricSize
import io.lanyin.player.ui.theme.PlayerSkin
import io.lanyin.player.ui.theme.SpaceMonoFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What a theme's stage works with, in landscape or portrait. */
internal class StageContext(
    val state: PlayerState,
    val skin: PlayerSkin,
    val spin: SpinState,
    val chromeVisible: Boolean,
    val scrubMs: Long?,
    val lyricScale: Float,
    val dismissDrag: Modifier,
    val entrance: State<Float>,
)

/** Type that is part of a drawing, held at its size whatever the system font scale. */
@Composable
internal fun fixedSp(size: Float): TextUnit = with(LocalDensity.current) { size.dp.toSp() }

/**
 * The artwork fitted to its box with room for the spectrum halo round it (when [halo]), swiped
 * to change track, 格's sticker on its corner. A record or tape settles back a little when
 * paused. [onClick] is for a tap on it (the full lyrics, in portrait); with [ripples] it throws
 * a ripple on each beat.
 */
@Composable
internal fun StageArtwork(
    stage: StageContext,
    modifier: Modifier,
    halo: Boolean = true,
    heightFraction: Float = 0.82f,
    onClick: (() -> Unit)? = null,
    ripples: Boolean = false,
) {
    val state = stage.state
    val settle by animateFloatAsState(
        if (state.isPlaying || stage.skin == PlayerSkin.COVER) 1f else 0.95f,
        Lanyin.style.bouncy.spec(), label = "artSettle",
    )
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val room = if (halo) 20.dp else 4.dp
        Box(
            artSize(stage.skin, maxWidth - room * 2, maxHeight * heightFraction)
                .then(if (halo) Modifier.spectrumHalo(stage.skin, state.isPlaying, state.audioFeed) else Modifier)
                .then(if (ripples) Modifier.beatRipples(stage.skin, state.isPlaying, state.audioFeed) else Modifier)
                .artworkBackdrop(stage.skin)
                .graphicsLayer {
                    scaleX = settle
                    scaleY = settle
                },
            contentAlignment = Alignment.Center,
        ) {
            // The halo stays put while the record is swiped away and the next slides in.
            SwipeToSkip(state) {
                PlayerArtwork(
                    stage.skin, state.isPlaying, stage.spin, state.coverSeed,
                    Modifier
                        .fillMaxWidth()
                        .sharedPlayer("artwork", zIndex = 1f)
                        .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier),
                    state.coverUrl,
                )
            }
            QualitySticker(state, Modifier.align(Alignment.TopStart))
        }
    }
}

/**
 * The tempo the analyser has locked on to, in beats a minute, checked once a second; null while
 * paused or before the music has settled on one.
 */
@Composable
internal fun rememberBpm(state: PlayerState): Int? {
    val bpm by produceState<Int?>(null, state.isPlaying) {
        while (true) {
            val period = state.audioFeed.beatPeriod()
            value = if (state.isPlaying && period > 0f) kotlin.math.round(60f / period).toInt() else null
            kotlinx.coroutines.delay(1_000)
        }
    }
    return bpm
}

/** The lyrics column of a stage, in the stage's type at [baseSp]. */
@Composable
internal fun StageLyricsColumn(stage: StageContext, modifier: Modifier, baseSp: Float = LyricSize.BASE_SP, centered: Boolean = false) {
    StageLyrics(
        stage.state, stage.chromeVisible,
        textStyle = lyricTextStyle(baseSp, stage.lyricScale),
        centered = centered,
        scrubMs = stage.scrubMs,
        modifier = modifier,
    )
}

// 机 -------------------------------------------------------------------------------------------

/**
 * 机: the machine's face on its side. The screen holds the artwork and the lyrics under its
 * status line; beside it, on the case, a recessed panel with a pair of VU meters (the low end and
 * the high), a tape counter rolling over the seconds, and a knob turned to how far through the
 * song it is, next to the speaker grille.
 */
@Composable
internal fun DeviceStage(stage: StageContext) {
    val state = stage.state
    Row(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        DeviceScreen(Modifier.weight(0.7f).fillMaxHeight().enter(stage.entrance, 1)) {
            ScreenHeader(state, meter = false)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                StageArtwork(stage, Modifier.weight(0.42f).fillMaxHeight().then(stage.dismissDrag), heightFraction = 0.92f)
                Spacer(Modifier.width(16.dp))
                StageLyricsColumn(stage, Modifier.weight(0.58f).fillMaxHeight(), baseSp = LyricSize.BASE_SP * 0.92f)
            }
        }
        Spacer(Modifier.width(16.dp))
        DeckPanel(state, Modifier.weight(0.3f).fillMaxHeight().enter(stage.entrance, 2))
    }
}

@Composable
private fun DeckPanel(state: PlayerState, modifier: Modifier) {
    val c = Lanyin.colors
    val style = Lanyin.style
    val screen = remember(style) { style.screenStyle() }
    val mono = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em)
    // The counter turns by the second; reading the position itself would recompose the deck every frame.
    val seconds by remember(state) { derivedStateOf { (state.positionMs / 1000).toInt() } }
    Column(modifier.themedSurface().padding(12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, bands) in listOf("LOW" to (0 until 10), "HIGH" to (14 until 30))) {
                Box(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(8.dp)).background(screen.colors.bg), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalLanyinStyle provides screen) {
                        VuMeter(state.audioFeed, state.isPlaying, Modifier.padding(horizontal = 6.dp, vertical = 4.dp).fillMaxSize(), bands = bands, label = name)
                    }
                }
            }
        }
        Column {
            Row {
                Text("COUNTER", style = mono.copy(fontSize = fixedSp(8f)), color = c.muted, modifier = Modifier.weight(1f))
                Text(rememberBpm(state)?.let { "BPM $it" } ?: "BPM --", style = mono.copy(fontSize = fixedSp(8f)), color = c.accent)
            }
            TapeCounter(seconds, Modifier.padding(top = 4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PositionKnob(state, Modifier.size(64.dp))
            Spacer(Modifier.width(10.dp))
            PulsingGrille(state, Modifier.weight(1f).height(56.dp))
        }
    }
}

/** A mechanical counter: four digits of minutes and seconds, each rolling up as it changes. */
@Composable
internal fun TapeCounter(seconds: Int, modifier: Modifier) {
    val digits = listOf(seconds / 600 % 10, seconds / 60 % 10, seconds % 60 / 10, seconds % 10)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        digits.forEachIndexed { i, d ->
            if (i == 2) Text(":", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixedSp(16f)), color = Lanyin.colors.muted)
            Box(Modifier.width(20.dp).height(28.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    d,
                    transitionSpec = { slideInVertically(tween(260)) { it } togetherWith slideOutVertically(tween(260)) { -it } },
                    label = "counterDigit",
                ) { value ->
                    Text("$value", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixedSp(16f)), color = Color(0xFFF1EFE6))
                }
                // The drum's highlight and shadow.
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.45f)))))
            }
        }
    }
}

/** A knob with a scale round it, its pointer turned as far as the song has got. */
@Composable
internal fun PositionKnob(state: PlayerState, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val clock = rememberFrameClock(state.isPlaying)
    Canvas(modifier) {
        clock.floatValue
        val f = (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        // The scale: eleven ticks over 270°, the part played in orange.
        for (k in 0..10) {
            val a = (135f + 27f * k) / 180f * PI.toFloat()
            val lit = k / 10f <= f
            drawLine(
                if (lit) c.accent else c.content.copy(alpha = 0.35f),
                Offset(center.x + cos(a) * r * 0.86f, center.y + sin(a) * r * 0.86f),
                Offset(center.x + cos(a) * r, center.y + sin(a) * r),
                1.5.dp.toPx(),
            )
        }
        val body = r * 0.7f
        drawCircle(Color.Black.copy(alpha = 0.25f), body, center + Offset(0f, 2.dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(lerp(c.surface, Color.White, 0.5f), c.surface, lerp(c.surface, Color.Black, 0.15f)), center - Offset(body * 0.3f, body * 0.3f), body * 1.6f), body, center)
        drawCircle(c.outline, body, center, style = Stroke(1.dp.toPx()))
        val a = (135f + 270f * f) / 180f * PI.toFloat()
        drawLine(c.accent, center + Offset(cos(a) * body * 0.25f, sin(a) * body * 0.25f), center + Offset(cos(a) * body * 0.85f, sin(a) * body * 0.85f), 2.5.dp.toPx(), StrokeCap.Round)
    }
}

/** The speaker grille, its holes darkening with the bass as the cone pushes. */
@Composable
internal fun PulsingGrille(state: PlayerState, modifier: Modifier) {
    val style = Lanyin.style
    val clock = rememberFrameClock(state.isPlaying)
    val levels = remember { FloatArray(32) }
    val smooth = remember { floatArrayOf(0f) }
    Canvas(modifier) {
        clock.floatValue
        var bass = 0f
        if (state.isPlaying && state.audioFeed.levels(levels)) for (i in 0 until 6) bass += levels[i] / 6f
        smooth[0] += (bass - smooth[0]) * 0.4f
        val cols = 7
        val rows = 5
        val pitch = minOf(size.width / cols, size.height / rows)
        val left = (size.width - pitch * cols) / 2f
        val top = (size.height - pitch * rows) / 2f
        for (y in 0 until rows) for (x in 0 until cols) {
            val center = Offset(left + pitch * (x + 0.5f), top + pitch * (y + 0.5f))
            // Holes nearer the middle move more, as a cone does.
            val d = kotlin.math.hypot(x - (cols - 1) / 2f, y - (rows - 1) / 2f) / 4f
            val push = smooth[0] * (1f - d).coerceAtLeast(0f)
            val r = pitch * (0.24f + 0.08f * push)
            drawCircle(Color.White.copy(alpha = if (style.dark) 0.06f else 0.7f), r, center + Offset(0f, density * 0.8f))
            drawCircle(style.colors.content.copy(alpha = 0.55f + 0.3f * push), r, center)
        }
    }
}

// 报 -------------------------------------------------------------------------------------------

/**
 * 报: the stage as a page of the paper. A running head with the paper's name, the edition and
 * the date over a double rule; a photograph (the artwork) with its caption in the first column;
 * the song's title as the headline across the rest, a byline, and the lyrics set as the story,
 * the line being sung marked in red; a ticker of what's coming along the foot.
 */
@Composable
internal fun BroadsheetStage(stage: StageContext) {
    val style = Lanyin.style
    val c = style.colors
    val state = stage.state
    Column(Modifier.fillMaxSize()) {
        DoubleRule(Modifier.padding(bottom = 10.dp))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(0.36f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    StageArtwork(stage, Modifier.fillMaxSize().then(stage.dismissDrag), heightFraction = 0.95f)
                    PaperPauseStamp(state, Modifier.align(Alignment.Center))
                }
                Text(
                    "图：《${state.title}》　${state.artist}",
                    style = style.type.caption.copy(fontStyle = FontStyle.Italic, fontSize = 11.sp),
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.padding(horizontal = 16.dp).width(1.dp).fillMaxHeight().background(c.outline))
            Column(Modifier.weight(0.64f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val bpm = rememberBpm(state)
                    Text("澜音日报 · 号外 · 第 %d 版".format(state.trackNumber) + (bpm?.let { " · 节拍每分 $it" } ?: ""), style = style.type.caption.copy(fontWeight = FontWeight.Bold), color = c.accent, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    val now by rememberMinuteClock()
                    Text("%02d:%02d 付印".format(now.hour, now.minute), style = style.type.caption.copy(fontStyle = FontStyle.Italic), color = c.muted, maxLines = 1)
                }
                TrackSwitch(state) { track ->
                    Text(track.title, style = style.type.display.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("本报讯　${state.artist}${if (state.track.album.isNotEmpty()) " · 《${state.track.album}》" else ""}", style = style.type.caption.copy(fontStyle = FontStyle.Italic), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(0.75.dp).background(c.content))
                StageLyricsColumn(stage, Modifier.weight(1f).fillMaxWidth().enter(stage.entrance, 2), baseSp = LyricSize.BASE_SP * 0.9f)
            }
        }
        PaperTicker(state, Modifier.padding(top = 6.dp))
    }
}

/** The paper's ticker along the foot of the page: what's next, where it's from. */
@Composable
internal fun PaperTicker(state: PlayerState, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Marquee(
        listOf("快讯", "下一首《${state.next.title}》", "${state.playlistName} 第 ${state.trackNumber} / ${state.trackCount} 首", "澜音日报 · 号外"),
        modifier,
        height = 20.dp,
        speed = 22.dp,
        fill = c.content,
        ink = c.bg,
        marks = c.accent to c.bg,
        font = style.display,
    )
}

// 酸 -------------------------------------------------------------------------------------------

/**
 * 酸: the stage as a media player's skin from the turn of the millennium. A chrome window with
 * its title bar and buttons; the disc spinning in front of a visualiser of rings and rays that
 * swell and turn with the music; an LCD strip scrolling the title in lime; the lyrics; and a
 * graphic equaliser's bars dancing along the foot.
 */
@Composable
internal fun MediaPlayerStage(stage: StageContext) {
    val state = stage.state
    Column(
        Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
            .enter(stage.entrance, 1)
            .mediaWindow(),
    ) {
        MediaTitleBar()
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Box(Modifier.weight(0.42f).fillMaxHeight()) {
                Visualizer(state, Modifier.matchParentSize())
                StageArtwork(stage, Modifier.fillMaxSize().then(stage.dismissDrag), halo = false, heightFraction = 0.78f)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(0.58f).fillMaxHeight()) {
                MediaLcd(state)
                StageLyricsColumn(stage, Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp), baseSp = LyricSize.BASE_SP * 0.92f)
                GraphicEq(state, Modifier.fillMaxWidth().height(30.dp).padding(top = 6.dp))
            }
        }
    }
}

/** 酸's window: rounded, near-black glass in a chrome frame. */
internal fun Modifier.mediaWindow(): Modifier {
    val shape = RoundedCornerShape(22.dp)
    return clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF1C1C22), Color(0xFF07070A))))
        .border(2.dp, ChromeBrush, shape)
}

/** The chrome window's title bar: traffic-light buttons, the player's name, the window controls. */
@Composable
internal fun MediaTitleBar() {
    val c = Lanyin.colors
    Row(
        Modifier.fillMaxWidth().height(26.dp).background(Brush.verticalGradient(listOf(Color(0xFF3A3A44), Color(0xFF121216)))).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (dot in listOf(c.accentAlt, c.accent, Color.White)) {
            Box(Modifier.padding(end = 6.dp).size(9.dp).clip(CircleShape).background(dot))
        }
        Spacer(Modifier.weight(1f))
        Text("LANYIN MEDIA PLAYER 2000", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(10f), brush = ChromeBrush, letterSpacing = 0.08.em), maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text("_ □ ×", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixedSp(11f)), color = Color.White.copy(alpha = 0.7f))
    }
}

/** The LCD strip scrolling the title, the stream, the track and the tempo in lime. */
@Composable
internal fun MediaLcd(state: PlayerState, modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    Marquee(
        listOfNotNull(if (state.isPlaying) null else "❚❚ PAUSED", "${state.title} — ${state.artist}", "${state.quality.ifEmpty { "STREAM" }} ${state.format}", "TRACK ${state.trackNumber}/${state.trackCount}", rememberBpm(state)?.let { "$it BPM" }),
        modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, c.accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
        height = 26.dp,
        fill = Color.Black,
        ink = c.accent,
        marks = c.accentAlt to c.accent,
        font = SpaceMonoFamily,
    )
}

/** Rings of stars and rays behind the disc, swelling with the bass, mids and treble and turning with the beat. */
@Composable
internal fun Visualizer(state: PlayerState, modifier: Modifier) {
    val c = Lanyin.colors
    val clock = rememberFrameClock(state.isPlaying)
    val levels = remember { FloatArray(32) }
    val eased = remember { FloatArray(3) }
    Canvas(modifier) {
        val t = clock.floatValue
        val live = state.isPlaying && state.audioFeed.levels(levels)
        val bands = listOf(0 until 6, 6 until 16, 16 until 32)
        for (k in 0..2) {
            var v = 0f
            if (live) for (i in bands[k]) v += levels[i] / bands[k].count()
            eased[k] += (v - eased[k]) * 0.35f
        }
        val pulse = if (state.isPlaying) io.lanyin.player.core.audio.pulseAfter(state.audioFeed.sinceBeat(), state.audioFeed.beatPeriod(), 0.4f) else 0f
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        // Rays turning slowly, longer with the treble.
        val rays = 36
        rotate(t * 12f, center) {
            for (i in 0 until rays) {
                val a = 2f * PI.toFloat() * i / rays
                val inner = r * 0.62f
                val outer = r * (0.78f + 0.3f * eased[2] * (0.6f + 0.4f * sin(i * 1.7f + t * 3f)))
                drawLine(
                    (if (i % 2 == 0) c.accent else c.accentAlt).copy(alpha = 0.25f + 0.5f * eased[2]),
                    center + Offset(cos(a) * inner, sin(a) * inner), center + Offset(cos(a) * outer, sin(a) * outer),
                    2.dp.toPx(), StrokeCap.Round,
                )
            }
        }
        // Three star rings: bass outermost, swelling on the beat.
        val rings = listOf(Triple(0, 16, c.accent), Triple(1, 12, c.accentAlt), Triple(2, 20, Color(0xFF00E5FF)))
        for ((k, points, color) in rings) {
            val radius = r * (0.7f + 0.12f * eased[k] + 0.08f * pulse) * (1f - k * 0.06f)
            val turn = t * (0.3f + 0.2f * k) * (if (k % 2 == 0) 1f else -1f)
            drawPath(starPath(center, radius, radius * 0.9f, points, turn), color.copy(alpha = 0.35f + 0.4f * eased[k]), style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** A graphic equaliser's sixteen bars, lime rising to pink, with falling peak caps. */
@Composable
internal fun GraphicEq(state: PlayerState, modifier: Modifier) {
    val c = Lanyin.colors
    val clock = rememberFrameClock(state.isPlaying)
    val levels = remember { FloatArray(32) }
    val peaks = remember { FloatArray(16) }
    Canvas(modifier) {
        clock.floatValue
        val live = state.isPlaying && state.audioFeed.levels(levels)
        val n = 16
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val cell = 3.dp.toPx()
        for (i in 0 until n) {
            val v = if (live) (levels[i * 2] + levels[i * 2 + 1]) / 2f else 0f
            peaks[i] = maxOf(v, peaks[i] - 0.015f)
            val cells = (size.height / (cell + 1.dp.toPx())).toInt()
            val lit = (v * cells).toInt()
            for (k in 0 until cells) {
                val y = size.height - (k + 1) * (cell + 1.dp.toPx())
                val color = if (k < lit) lerp(c.accent, c.accentAlt, k / cells.toFloat()) else Color.White.copy(alpha = 0.06f)
                drawRect(color, Offset(i * (w + gap), y), Size(w, cell))
            }
            val py = size.height - peaks[i] * size.height
            drawRect(Color.White.copy(alpha = 0.8f), Offset(i * (w + gap), py.coerceAtMost(size.height - 2f)), Size(w, 1.5.dp.toPx()))
        }
    }
}

