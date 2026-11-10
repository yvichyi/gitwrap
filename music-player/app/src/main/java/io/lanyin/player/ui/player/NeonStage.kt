package io.lanyin.player.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.design.LikeButton
import io.lanyin.player.ui.design.formatTime
import io.lanyin.player.ui.design.PixelCover
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.rememberFrameClock
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PixelFamily

/** How tall the head-up display's strip of glass is in portrait, under the top bar. */
internal val NeonHudHeight = 196.dp

/**
 * 霓's portrait stage: the line being sung projected on the windscreen (the backdrop draws the
 * glass), then the console's screen with what's playing: the picture, the song, a spectrum, and
 * what's next. Tap either for the full lyrics.
 */
@Composable
internal fun NeonPortraitStage(stage: StageContext, onShowLyrics: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HudLyrics(stage, Modifier.fillMaxWidth().height(NeonHudHeight).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onShowLyrics))
        Spacer(Modifier.height(18.dp))
        SwipeToSkip(stage.state, Modifier.fillMaxWidth().weight(1f).then(stage.dismissDrag)) {
            NowPlayingPanel(stage.state, Modifier.fillMaxSize(), onShowLyrics)
        }
    }
}

/** 霓 in landscape: the lyrics on the wide glass above, the console's panel below. */
@Composable
internal fun NeonWideStage(stage: StageContext) {
    Column(Modifier.fillMaxSize()) {
        HudLyrics(stage, Modifier.fillMaxWidth().weight(1f))
        SwipeToSkip(stage.state, Modifier.fillMaxWidth().height(108.dp).then(stage.dismissDrag)) {
            NowPlayingPanel(stage.state, Modifier.fillMaxSize(), onShowLyrics = null, wide = true)
        }
    }
}

/**
 * The head-up display: the line being sung thrown on the glass in white light with a teal halo,
 * the next line under it, on the darker band of glass a display projects onto.
 */
@Composable
private fun HudLyrics(stage: StageContext, modifier: Modifier) {
    val state = stage.state
    val now = state.currentLine?.text ?: emptyLyricsNote(state)
    val next = state.lyrics.getOrNull(state.currentLyricIndex + 1)?.text
    val halo = Shadow(Color(0xFF6FE0C8).copy(alpha = 0.7f), Offset.Zero, 12f)
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 30.dp)
                .drawBehind {
                    drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to Color.Transparent, 0.15f to Color(0xCC05060A), 0.85f to Color(0xCC05060A), 1f to Color.Transparent,
                    ))
                }
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.animation.AnimatedContent(now, label = "hudLine") { line ->
                Text(
                    line, style = TextStyle(fontFamily = PixelFamily, fontSize = fixedSp(22f * stage.lyricScale), lineHeight = fixedSp(28f * stage.lyricScale), shadow = halo),
                    color = Color(0xFFD8E4EE), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (next != null) Text(
                next, style = TextStyle(fontFamily = PixelFamily, fontSize = fixedSp(12f), shadow = halo),
                color = Color(0xFF9FB2C8), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}

/**
 * The console's now-playing screen: a status line (track number, format), the cover in pixels,
 * the song, album and year, the heart, a spectrum in amber bars, and the next three songs.
 */
@Composable
private fun NowPlayingPanel(state: PlayerState, modifier: Modifier, onShowLyrics: (() -> Unit)?, wide: Boolean = false) {
    // In landscape the top bar carries the shared title.
    val shared = !wide
    val c = Lanyin.colors
    val label = Lanyin.type.caption.copy(fontSize = fixedSp(11f))
    Column(modifier.themedSurface(onClick = onShowLyrics).clipToBounds().padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(c.accent))
            Text("  正在播放 · %02d/%02d".format(state.trackNumber, state.trackCount), style = label, color = c.content, modifier = Modifier.weight(1f))
            Text(state.format.ifEmpty { state.quality }, style = label, color = c.muted, maxLines = 1)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            PixelCover(state.coverUrl, state.coverSeed, Modifier.size(if (wide) 84.dp else 112.dp).border(3.dp, Color(0xFF05060A)), cells = 40)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                TrackSwitch(state, if (shared) Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true) else Modifier) { track ->
                    Text(track.title, style = Lanyin.type.display.copy(fontSize = fixedSp(21f), lineHeight = fixedSp(26f)), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TrackSwitch(state) { track ->
                    Text("${track.artist} · 《${track.album}》", style = label, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LikeButton(state.liked, onToggle = state::toggleLike)
                    Text(if (state.liked) "已喜欢" else "喜欢", style = label, color = if (state.liked) c.like else c.muted)
                }
                PixelSpectrum(state, Modifier.fillMaxWidth().height(if (wide) 22.dp else 30.dp))
            }
        }
        if (!wide) {
            Text("接下来 ▸▸", style = label, color = c.muted, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            for (k in 1..5) {
                val next = state.queue.getOrNull(state.index + k) ?: break
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d".format(state.index + k + 1), style = label, color = c.accent, modifier = Modifier.width(28.dp))
                    Text("${next.title}  ${next.artist}", style = label, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(formatTime(next.durationMs), style = label, color = c.muted)
                }
            }
        }
    }
}

/**
 * The spectrum as amber pixel bars, the top block of each bar brighter; it answers the music while
 * it plays and settles low when paused.
 */
@Composable
private fun PixelSpectrum(state: PlayerState, modifier: Modifier) {
    val c = Lanyin.colors
    val feed = state.audioFeed
    val clock = rememberFrameClock(state.isPlaying)
    val levels = remember { FloatArray(32) }
    Canvas(modifier) {
        val t = clock.floatValue
        val live = feed != null && state.isPlaying && feed.levels(levels)
        if (!live) demoSpectrum(t, levels)
        val px = 3.dp.toPx()
        val bars = (size.width / (2 * px)).toInt()
        val rows = (size.height / px).toInt()
        for (i in 0 until bars) {
            val v = levels[(i * levels.size / bars).coerceIn(0, levels.size - 1)].coerceIn(0f, 1f) * (if (state.isPlaying) 1f else 0.25f)
            val n = (v * rows).toInt().coerceAtLeast(1)
            for (r in 0 until n) {
                val color = if (r == n - 1) Color(0xFFFFC874) else if (i % 6 == 5) c.accentAlt else c.accent
                drawRect(color, Offset(i * 2 * px, size.height - (r + 1) * px), Size(px, px * 0.75f))
            }
        }
    }
}
