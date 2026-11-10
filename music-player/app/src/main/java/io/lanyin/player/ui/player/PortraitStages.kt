package io.lanyin.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.ui.design.DeviceScreen
import io.lanyin.player.ui.design.DoubleRule
import io.lanyin.player.ui.design.GameScreen
import io.lanyin.player.ui.design.PlayPauseButton
import io.lanyin.player.ui.design.SeekBar
import io.lanyin.player.ui.design.VuMeter
import io.lanyin.player.ui.design.screenStyle
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.music.LocalOpenSheet
import io.lanyin.player.ui.music.Sheet
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LocalLanyinStyle
import io.lanyin.player.ui.theme.LyricSize
import io.lanyin.player.ui.theme.PixelFamily
import io.lanyin.player.ui.theme.SpaceMonoFamily

/**
 * The portrait player's stage, each theme's own: the parts its landscape stage is made of,
 * stacked for a tall screen, above the title, progress and transport. A tap on the artwork or
 * on the lyrics opens the full lyrics. (游 is the whole console instead: see [ConsolePortrait].)
 */
@Composable
internal fun PortraitStage(stage: StageContext, onShowLyrics: () -> Unit) {
    when (Lanyin.style.backdrop) {
        BackdropKind.Ink -> InkPortrait(stage, onShowLyrics)
        BackdropKind.Dot -> DotPortrait(stage, onShowLyrics)
        BackdropKind.Device -> DevicePortrait(stage, onShowLyrics)
        BackdropKind.Grid -> GridPortrait(stage, onShowLyrics)
        BackdropKind.Paper -> PaperPortrait(stage, onShowLyrics)
        BackdropKind.Acid -> AcidPortrait(stage, onShowLyrics)
        BackdropKind.Dusk -> DuskPortrait(stage, onShowLyrics)
        BackdropKind.Neon -> NeonPortrait(stage, onShowLyrics)
        BackdropKind.Game -> Unit
    }
}

/** A tap anywhere on it opens the full lyrics; no press shade, the lyrics answer instead. */
@Composable
private fun Modifier.opensLyrics(onShowLyrics: () -> Unit): Modifier =
    clickable(remember { MutableInteractionSource() }, indication = null, onClick = onShowLyrics)

/** A short window onto the lyrics: the line being sung with its neighbours. */
@Composable
private fun LyricsWindow(stage: StageContext, onShowLyrics: () -> Unit, modifier: Modifier, baseSp: Float = 16f) {
    StateLyrics(
        stage.state,
        textStyle = lyricTextStyle(baseSp, stage.lyricScale),
        centered = true,
        anchorFraction = 0.5f,
        interactive = false,
        showTranslation = false,
        scrubMs = stage.scrubMs,
        modifier = modifier.clipToBounds().opensLyrics(onShowLyrics),
    )
}

/** 墨: the inkstone turning above a scroll of the lyrics written down in columns, the title inscribed beside them. */
@Composable
private fun InkPortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        InkstoneStage(stage, Modifier.weight(1f).fillMaxWidth().enter(stage.entrance, 1), onClick = onShowLyrics)
        ScrollSheet(
            stage,
            Modifier.fillMaxWidth().height(206.dp).padding(top = 10.dp).enter(stage.entrance, 2),
            lyricsModifier = Modifier.opensLyrics(onShowLyrics),
        )
    }
}

/** 格: the poster's head over the record, and the line being sung set big beneath it. */
@Composable
private fun GridPortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.enter(stage.entrance, 1)) { PosterHead(stage) }
        StageArtwork(stage, Modifier.weight(1f).fillMaxWidth().then(stage.dismissDrag), heightFraction = 0.94f, onClick = onShowLyrics, ripples = true)
        PosterLine(stage, Modifier.fillMaxWidth().height(186.dp).enter(stage.entrance, 2).opensLyrics(onShowLyrics))
    }
}

/** 暮: the song as a print taped to the mat, its sun setting over the song, and the slip with its name. */
@Composable
private fun DuskPortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    RisoPortraitStage(stage, onShowLyrics)
}

/** 霓: ON AIR and the tempo up top, the record, and the neon sign lighting the words beneath. */
@Composable
private fun NeonPortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    NeonPortraitStage(stage, onShowLyrics)
}

/** 机: the cassette deck: the tape behind its door, the counter, the fluorescent display. */
@Composable
private fun DevicePortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    DeckPortraitStage(stage, onShowLyrics)
}

/** 点: the record inside its Glyph lights, and the line being sung on the dot-matrix display. */
@Composable
private fun DotPortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            GlyphLights(stage.state, Modifier.matchParentSize())
            StageArtwork(stage, Modifier.fillMaxSize().then(stage.dismissDrag), heightFraction = 0.72f, onClick = onShowLyrics)
            GlyphTempo(stage.state, Modifier.align(Alignment.BottomStart).padding(4.dp))
        }
        DotMatrixLyrics(stage, Modifier.fillMaxWidth().height(184.dp).padding(top = 8.dp).enter(stage.entrance, 2).opensLyrics(onShowLyrics))
    }
}

/** 酸: the song's own poster, pasted on the wall. */
@Composable
private fun AcidPortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    PosterPortraitStage(stage, onShowLyrics)
}

/**
 * 报: the front page. The line being sung is the lead story's headline (its translation the
 * standfirst), changing as the song goes; the photograph (the artwork) across the page with its
 * caption; the lines to come set beneath in two columns as the rest of the story; the ticker
 * along the foot.
 */
@Composable
private fun PaperPortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    val state = stage.state
    val lines = state.lyrics
    val index = stage.scrubMs?.let { lyricIndexAt(lines, it) } ?: state.currentLyricIndex
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.enter(stage.entrance, 1), verticalAlignment = Alignment.Bottom) {
            val bpm = rememberBpm(state)
            Text(
                "澜音日报 · 第 %d 版".format(state.trackNumber) + (bpm?.let { " · 节拍每分 $it" } ?: ""),
                style = style.type.caption.copy(fontWeight = FontWeight.Bold), color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 10.dp),
            )
            val now by rememberMinuteClock()
            Text("%02d:%02d 付印".format(now.hour, now.minute), style = style.type.caption.copy(fontStyle = FontStyle.Italic), color = c.muted, maxLines = 1)
        }
        DoubleRule(Modifier.padding(top = 4.dp, bottom = 8.dp))
        val headline = lyricTextStyle(LyricSize.BASE_SP * 1.3f, stage.lyricScale).let {
            it.copy(fontFamily = style.display, fontWeight = FontWeight.Black, lineHeight = it.fontSize * 1.22f)
        }
        AnimatedContent(
            index,
            transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(160)) },
            contentAlignment = Alignment.TopStart,
            modifier = Modifier.fillMaxWidth().height(92.dp).enter(stage.entrance, 2).opensLyrics(onShowLyrics),
            label = "headline",
        ) { i ->
            val line = lines.getOrNull(i)
            Column {
                Text(
                    line?.text?.takeIf { it.isNotBlank() } ?: if (lines.isEmpty()) emptyLyricsNote(state) else "（间奏）",
                    style = headline, color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                line?.translation?.let {
                    Text(it, style = style.type.caption.copy(fontStyle = FontStyle.Italic), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(
            "本报讯　${state.artist}${if (state.track.album.isNotEmpty()) " · 《${state.track.album}》" else ""}",
            style = style.type.caption.copy(fontStyle = FontStyle.Italic), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(0.75.dp).background(c.content))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            StageArtwork(stage, Modifier.fillMaxSize().then(stage.dismissDrag), heightFraction = 0.96f, onClick = onShowLyrics)
            PaperPauseStamp(state, Modifier.align(Alignment.Center))
        }
        Text(
            "图：《${state.title}》　${state.artist}",
            style = style.type.caption.copy(fontStyle = FontStyle.Italic, fontSize = fixedSp(11f)), color = c.muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        // The story continues: the lines still to come, in two columns under a rule.
        Box(Modifier.padding(top = 6.dp, bottom = 6.dp).fillMaxWidth().height(0.75.dp).background(c.content))
        Row(Modifier.fillMaxWidth().height(62.dp).clipToBounds().opensLyrics(onShowLyrics)) {
            for (col in 0..1) {
                if (col == 1) Box(Modifier.padding(horizontal = 10.dp).width(1.dp).fillMaxHeight().background(c.outline))
                Column(Modifier.weight(1f)) {
                    if (col == 0) Text("下文", style = style.type.label.copy(fontWeight = FontWeight.Bold), color = c.accent)
                    for (k in (if (col == 0) 1..2 else 3..5)) {
                        val text = lines.getOrNull(index + k)?.text?.takeIf { it.isNotBlank() } ?: continue
                        Text(
                            text,
                            style = style.type.body.copy(fontFamily = style.display, fontSize = fixedSp(13f), lineHeight = fixedSp(18f)),
                            color = c.content.copy(alpha = (0.85f - k * 0.08f).coerceAtLeast(0.35f)),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        PaperTicker(state, Modifier.padding(top = 6.dp))
    }
}

