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
        BackdropKind.Film -> FilmPortrait(stage, onShowLyrics)
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

/** 墨: the record above a scroll of the lyrics written down in columns, the title inscribed beside them. */
@Composable
private fun InkPortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StageArtwork(stage, Modifier.weight(1f).fillMaxWidth().then(stage.dismissDrag), heightFraction = 0.94f, onClick = onShowLyrics, ripples = true)
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

/** 暮: the countdown to sunset, the polaroid hung below it, and the line on the horizon with its reflection. */
@Composable
private fun DuskPortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.enter(stage.entrance, 1)) { Sunset(stage.state) }
        Polaroid(stage, Modifier.weight(1f).fillMaxWidth(), onClick = onShowLyrics)
        HorizonLyrics(stage, Modifier.fillMaxWidth().height(176.dp).enter(stage.entrance, 2).opensLyrics(onShowLyrics))
    }
}

/** 霓: ON AIR and the tempo up top, the record, and the neon sign lighting the words beneath. */
@Composable
private fun NeonPortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.enter(stage.entrance, 1)) { SignHead(stage.state) }
        StageArtwork(stage, Modifier.weight(1f).fillMaxWidth().then(stage.dismissDrag), heightFraction = 0.94f, onClick = onShowLyrics, ripples = true)
        NeonSign(stage, Modifier.fillMaxWidth().height(184.dp).padding(top = 14.dp).enter(stage.entrance, 2).opensLyrics(onShowLyrics))
    }
}

/** 机: the screen with the artwork and a few lines, and the panel under it: VU meters, counter and knob. */
@Composable
private fun DevicePortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    val state = stage.state
    Column(Modifier.fillMaxSize()) {
        DeviceScreen(Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp).enter(stage.entrance, 1)) {
            ScreenHeader(state, meter = false)
            StageArtwork(stage, Modifier.weight(1f).fillMaxWidth().then(stage.dismissDrag), heightFraction = 0.96f, onClick = onShowLyrics, ripples = true)
            LyricsWindow(stage, onShowLyrics, Modifier.fillMaxWidth().height(96.dp))
        }
        DeckStrip(state, Modifier.fillMaxWidth().padding(top = 12.dp).height(78.dp).enter(stage.entrance, 2))
    }
}

/** 机's panel along the foot of the screen: the low and high VU meters, the tape counter and the position knob. */
@Composable
private fun DeckStrip(state: PlayerState, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val screen = remember(style) { style.screenStyle() }
    val mono = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em)
    // The counter turns by the second; reading the position itself would recompose the panel every frame.
    val seconds by remember(state) { derivedStateOf { (state.positionMs / 1000).toInt() } }
    Row(
        modifier.themedSurface().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((name, bands) in listOf("LOW" to (0 until 10), "HIGH" to (14 until 30))) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(screen.colors.bg), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalLanyinStyle provides screen) {
                    VuMeter(state.audioFeed, state.isPlaying, Modifier.padding(horizontal = 4.dp, vertical = 3.dp).fillMaxSize(), bands = bands, label = name)
                }
            }
        }
        Column {
            Text(rememberBpm(state)?.let { "BPM $it" } ?: "COUNTER", style = mono.copy(fontSize = fixedSp(8f)), color = c.accent, maxLines = 1)
            TapeCounter(seconds, Modifier.padding(top = 3.dp))
        }
        PositionKnob(state, Modifier.size(52.dp))
    }
}

/** 影: the film running down through the gate with the artwork in it, and the screen with the subtitle below. */
@Composable
private fun FilmPortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        FilmGate(stage, Modifier.weight(1f).fillMaxWidth(), onClick = onShowLyrics)
        CinemaScreen(stage, Modifier.fillMaxWidth().height(176.dp).padding(top = 12.dp).enter(stage.entrance, 2).opensLyrics(onShowLyrics))
    }
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

/** 酸: the media player's chrome window stood upright: the disc before its visualiser, the LCD, the lyrics, the equaliser. */
@Composable
private fun AcidPortrait(stage: StageContext, onShowLyrics: () -> Unit) {
    val state = stage.state
    Column(Modifier.fillMaxSize().padding(top = 4.dp).enter(stage.entrance, 1).mediaWindow()) {
        MediaTitleBar()
        Box(Modifier.weight(1f).fillMaxWidth().padding(10.dp)) {
            Visualizer(state, Modifier.matchParentSize())
            StageArtwork(stage, Modifier.fillMaxSize().then(stage.dismissDrag), halo = false, heightFraction = 0.74f, onClick = onShowLyrics)
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
            MediaLcd(state)
            LyricsWindow(stage, onShowLyrics, Modifier.fillMaxWidth().padding(top = 4.dp).height(96.dp))
            GraphicEq(state, Modifier.fillMaxWidth().padding(top = 6.dp).height(26.dp))
        }
    }
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

/**
 * 游 upright is the console as it was held. The LCD up top holds the artwork, the title and a few
 * lines (or all the lyrics), the progress beneath; below it the D-pad (left and right skip, up
 * likes the song, down opens the queue), A to play and pause, B for the play mode, SELECT for the
 * quality and START for the lyrics. The quieter actions keep their row along the bottom.
 */
@Composable
internal fun ConsolePortrait(
    stage: StageContext,
    lyricsOnly: Boolean,
    onToggleLyrics: (() -> Unit)?,
    onCollapse: () -> Unit,
    onRotate: () -> Unit,
    onScrub: (Long?) -> Unit,
) {
    val state = stage.state
    val entrance = stage.entrance
    val openSheet = LocalOpenSheet.current
    val pixel = TextStyle(fontFamily = PixelFamily)
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        PlayerTopBar(state, onCollapse, modifier = stage.dismissDrag.enter(entrance, 0))
        GameScreen(state.isPlaying, Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp).enter(entrance, 1)) {
            val label = Lanyin.style.type.label.copy(fontFamily = PixelFamily, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.isPlaying) "▶ NOW PLAYING" else "Ⅱ PAUSE", style = label, color = Lanyin.colors.content, maxLines = 1)
                Spacer(Modifier.weight(1f))
                BeatSprite(state, Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                Text((rememberBpm(state)?.let { "BPM $it  " } ?: "") + "%02d/%02d".format(state.trackNumber, state.trackCount), style = label, color = Lanyin.colors.content, maxLines = 1)
            }
            if (lyricsOnly) {
                StateLyrics(
                    state, lyricTextStyle(LyricSize.BASE_SP * 0.95f, stage.lyricScale), centered = true, anchorFraction = 0.4f,
                    scrubMs = stage.scrubMs, modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    StageArtwork(stage, Modifier.fillMaxSize().then(stage.dismissDrag), heightFraction = 0.96f, onClick = onToggleLyrics, ripples = true)
                    LcdOverlay(state, Modifier.matchParentSize())
                }
                TrackSwitch(state, Modifier.fillMaxWidth().padding(top = 4.dp)) { track ->
                    Text("${track.title} · ${track.artist}", style = pixel.copy(fontSize = fixedSp(14f)), color = Lanyin.colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LyricsWindow(stage, onToggleLyrics ?: {}, Modifier.fillMaxWidth().height(78.dp), baseSp = 14f)
            }
            SeekBar(state::positionMs, state.durationMs, state.isPlaying, state::seekTo, Modifier.fillMaxWidth(), onScrub = onScrub)
        }
        // The console's lower half: the D-pad and A and B, then along the bottom what the pad
        // does, SELECT and START, and the speaker.
        Box(Modifier.fillMaxWidth().height(170.dp).padding(top = 12.dp).enter(entrance, 2)) {
            DPad(state, onQueue = { openSheet(Sheet.Queue) }, Modifier.align(Alignment.TopStart).size(112.dp))
            Box(Modifier.align(Alignment.TopEnd).size(width = 124.dp, height = 104.dp)) {
                BButton(state, Modifier.align(Alignment.BottomStart))
                PlayPauseButton(state.isPlaying, state::togglePlay, 60.dp, Modifier.align(Alignment.TopEnd), feed = state.audioFeed)
            }
            Text(
                "◀▶ 切歌\n▲ 喜欢  ▼ 队列", style = pixel.copy(fontSize = fixedSp(10f), lineHeight = fixedSp(13f)), color = Lanyin.colors.muted,
                modifier = Modifier.align(Alignment.BottomStart),
            )
            Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MenuPill("SELECT", "音质") { openSheet(Sheet.Quality) }
                MenuPill("START", if (lyricsOnly) "封面" else "歌词", onToggleLyrics ?: {})
            }
            SpeakerSlots(state, Modifier.align(Alignment.BottomEnd).size(width = 64.dp, height = 26.dp))
        }
        UtilityRow(state, onToggleLyrics = null, lyricsShown = lyricsOnly, onRotate = onRotate, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp).enter(entrance, 3))
    }
}
