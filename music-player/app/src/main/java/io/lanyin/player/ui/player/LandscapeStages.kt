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
import androidx.compose.ui.draw.drawBehind
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
import io.lanyin.player.ui.design.Flavor
import io.lanyin.player.ui.design.drawGelBead
import io.lanyin.player.ui.design.drawGloss
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

/** 机 on its side: the cassette deck, its tape on the left and its display on the right. */
@Composable
internal fun DeviceStage(stage: StageContext) {
    DeckWideStage(stage)
}

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

/** 酸's window: a sheet of black flyer stock with the paper's faint edge. */
internal fun Modifier.mediaWindow(): Modifier = this
    .background(io.lanyin.player.ui.design.Acid.Black)
    .drawBehind { drawGloss(0.3f) }
    .border(1.dp, io.lanyin.player.ui.design.Acid.Paper.copy(alpha = 0.16f))

/** The window's head: a strip of lime with its [title] and three asterisks, as a flyer heads a listing. */
@Composable
internal fun MediaTitleBar(title: String = "LANYIN SOUND SYSTEM") {
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(io.lanyin.player.ui.design.Acid.Lime)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixedSp(11f), letterSpacing = 0.04.em), color = io.lanyin.player.ui.design.Acid.Black, maxLines = 1, modifier = Modifier.weight(1f))
        Text("✱ ✱ ✱", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixedSp(11f)), color = io.lanyin.player.ui.design.Acid.Black)
    }
}

/** The LCD strip scrolling the title, the stream, the track and the tempo in lime. */
@Composable
internal fun MediaLcd(state: PlayerState, modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    Marquee(
        listOfNotNull(if (state.isPlaying) null else "❚❚ PAUSED", "${state.title} — ${state.artist}", "${state.quality.ifEmpty { "STREAM" }} ${state.format}", "TRACK ${state.trackNumber}/${state.trackCount}", rememberBpm(state)?.let { "$it BPM" }),
        modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp)),
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
                    (if (i % 2 == 0) Flavor.Bondi else Color.White).copy(alpha = 0.2f + 0.5f * eased[2]),
                    center + Offset(cos(a) * inner, sin(a) * inner), center + Offset(cos(a) * outer, sin(a) * outer),
                    2.dp.toPx(), StrokeCap.Round,
                )
            }
        }
        // Three star rings: bass outermost, swelling on the beat.
        val rings = listOf(Triple(0, 16, Flavor.Grape), Triple(1, 12, Flavor.Strawberry), Triple(2, 20, Flavor.Bondi))
        for ((k, points, color) in rings) {
            val radius = r * (0.7f + 0.12f * eased[k] + 0.08f * pulse) * (1f - k * 0.06f)
            val turn = t * (0.3f + 0.2f * k) * (if (k % 2 == 0) 1f else -1f)
            drawPath(starPath(center, radius, radius * 0.9f, points, turn), color.copy(alpha = 0.35f + 0.4f * eased[k]), style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** A graphic equaliser's sixteen bars, Bondi blue rising through tangerine to strawberry, with falling peak caps. */
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
                val f = k / cells.toFloat()
                val color = if (k < lit) (if (f < 0.55f) Flavor.Bondi else if (f < 0.8f) Flavor.Tangerine else Flavor.Strawberry) else Color.White.copy(alpha = 0.06f)
                drawRect(color, Offset(i * (w + gap), y), Size(w, cell))
            }
            val py = size.height - peaks[i] * size.height
            drawRect(Color.White.copy(alpha = 0.8f), Offset(i * (w + gap), py.coerceAtMost(size.height - 2f)), Size(w, 1.5.dp.toPx()))
        }
    }
}

