package io.lanyin.player.ui.player

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import io.lanyin.player.ui.design.Marquee
import io.lanyin.player.ui.design.Sticker
import io.lanyin.player.ui.design.retroShadow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.lanyin.player.ui.design.Backdrop
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.IconAction
import io.lanyin.player.ui.design.LikeButton
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.LocalOrientationController
import io.lanyin.player.ui.design.PlayPauseButton
import io.lanyin.player.ui.design.SeekBar
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.sharedPlayer
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.design.DeviceScreen
import io.lanyin.player.ui.design.VuMeter
import io.lanyin.player.ui.design.keycap
import io.lanyin.player.ui.design.padKey
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.ButtonKind
import io.lanyin.player.ui.theme.DockKind
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.LyricSize
import io.lanyin.player.ui.theme.PlayerSkin
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    state: PlayerState,
    skin: PlayerSkin,
    spin: SpinState,
    keepScreenOnInLandscape: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    lyricScale: Float = 1f,
    /** Open landscape already full screen, as after a tap on the stage; for previews and screenshots. */
    startFullScreen: Boolean = false,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val style = Lanyin.style
    val c = style.colors
    val orientation = LocalOrientationController.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // 0 = resting, 1 = fully pulled away; driven by drag-down and predictive back.
    var dismiss by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler { progress ->
        try {
            progress.collect { dismiss = it.progress * 0.5f }
            onCollapse()
        } catch (e: CancellationException) {
            scope.launch { animate(dismiss, 0f, animationSpec = style.motion.spec()) { v, _ -> dismiss = v } }
            throw e
        }
    }
    val dismissDrag = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = {
                if (dismiss > 0.22f) onCollapse()
                else scope.launch { animate(dismiss, 0f, animationSpec = style.motion.spec()) { v, _ -> dismiss = v } }
            },
        ) { change, dy ->
            change.consume()
            dismiss = (dismiss + dy / (size.height * 1.2f)).coerceIn(0f, 1f)
        }
    }

    // Landscape is a stage: hide system bars and keep the screen on.
    DisposableEffect(landscape) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (landscape && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(landscape, keepScreenOnInLandscape) {
        view.keepScreenOn = landscape && keepScreenOnInLandscape
        onDispose { view.keepScreenOn = false }
    }
    // Full screen only when asked for, with a tap on the empty stage: left alone, the bars stay,
    // since several themes' stages look their best framed by them.
    var chromeVisible by remember { mutableStateOf(!startFullScreen) }
    LaunchedEffect(landscape) { if (!landscape) chromeVisible = true }

    val onRotate = { if (landscape) orientation.lockPortrait() else orientation.lockLandscape() }

    CompositionLocalProvider(LocalContentColor provides c.content) {
        Box(
            modifier
                .fillMaxSize()
                .sharedPlayer("player-container", container = true)
                .graphicsLayer {
                    val p = dismiss
                    val s = 1f - 0.1f * p
                    scaleX = s
                    scaleY = s
                    translationY = p * 96.dp.toPx()
                    shape = RoundedCornerShape(36.dp * p)
                    clip = p > 0f
                }
                .background(c.bg),
        ) {
            Backdrop(
                Modifier.matchParentSize(), live = true, playing = state.isPlaying, trackNumber = state.trackNumber, feed = state.audioFeed,
                progress = { state.positionMs.toFloat() / state.durationMs },
            )
            if (landscape) {
                LandscapePlayer(
                    state, skin, spin, chromeVisible, onToggleChrome = { chromeVisible = !chromeVisible },
                    dismissDrag, onCollapse, onRotate, lyricScale,
                )
            } else {
                PortraitPlayer(state, skin, spin, dismissDrag, onCollapse, onRotate, lyricScale)
            }
        }
    }
}

/**
 * Top to bottom: where the music is playing from, the theme's own stage (the artwork with the
 * lyrics as that theme sets them: a scroll, a poster, a neon sign, a front page…), title and
 * artist, the progress line with its times beneath, transport, and a quiet row of secondary
 * actions. Everything rises into place in turn when the player opens; the artwork settles back
 * when paused and throws a ripple on every beat while playing. Swipe the artwork to change track;
 * tap it (or the lyrics) for the full lyrics. Dragging the progress line scrubs the lyrics along
 * with it. 游 is the whole console instead.
 */
@Composable
private fun PortraitPlayer(
    state: PlayerState,
    skin: PlayerSkin,
    spin: SpinState,
    dismissDrag: Modifier,
    onCollapse: () -> Unit,
    onRotate: () -> Unit,
    lyricScale: Float,
) {
    val style = Lanyin.style
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val lyricsOnly = skin == PlayerSkin.LYRICS || showLyrics
    val entrance = rememberEntrance(Unit)
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    // The bars always show in portrait, so the stages leave out their own footers.
    val stage = StageContext(state, skin, spin, chromeVisible = false, scrubMs, lyricScale, dismissDrag, entrance)
    val toggleLyrics = if (skin == PlayerSkin.LYRICS) null else ({ showLyrics = !showLyrics })
    if (style.backdrop == BackdropKind.Game) {
        ConsolePortrait(stage, lyricsOnly, toggleLyrics, onCollapse, onRotate, onScrub = { scrubMs = it })
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        PlayerTopBar(state, onCollapse, modifier = dismissDrag.enter(entrance, 0))
        AnimatedContent(
            targetState = lyricsOnly,
            transitionSpec = { (fadeIn(tween(260)) + scaleIn(initialScale = 0.96f)) togetherWith (fadeOut(tween(160)) + scaleOut(targetScale = 1.02f)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "artOrLyrics",
        ) { lyrics ->
            if (lyrics) {
                val full = @Composable { m: Modifier ->
                    StateLyrics(state, lyricTextStyle(LyricSize.BASE_SP, lyricScale), centered = style.lyric.centered, anchorFraction = 0.4f, scrubMs = scrubMs, modifier = m)
                }
                // 机 keeps its lyrics on the screen set into its case.
                if (style.backdrop == BackdropKind.Device) {
                    DeviceScreen(Modifier.fillMaxSize().padding(top = 2.dp)) {
                        ScreenHeader(state)
                        full(Modifier.weight(1f).fillMaxWidth())
                    }
                } else {
                    full(Modifier.fillMaxSize())
                }
            } else {
                PortraitStage(stage, onShowLyrics = { showLyrics = true })
            }
        }
        TrackTitle(state, Modifier.padding(top = 14.dp).enter(entrance, 3))
        SeekBar(
            state::positionMs, state.durationMs, state.isPlaying, state::seekTo,
            Modifier.padding(top = 10.dp).enter(entrance, 4),
            timesBelow = true, onScrub = { scrubMs = it },
        )
        TransportControls(state, playSize = 72.dp, Modifier.fillMaxWidth().padding(top = 4.dp).enter(entrance, 5))
        UtilityRow(
            state,
            onToggleLyrics = toggleLyrics,
            lyricsShown = lyricsOnly,
            onRotate = onRotate,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp).enter(entrance, 6),
        )
    }
}

/** A square for records and covers, the tape's own proportions for the cassette, fitted to the box. */
internal fun artSize(skin: PlayerSkin, maxWidth: Dp, maxHeight: Dp): Modifier =
    if (skin == PlayerSkin.CASSETTE) {
        Modifier.width(minOf(maxWidth, maxHeight * CASSETTE_RATIO))
    } else {
        Modifier.size(minOf(maxWidth, maxHeight))
    }

private const val CASSETTE_RATIO = 1.58f

/** The status line along the top of 机's screen: track counter, the VU meter, transport state. */
@Composable
internal fun ScreenHeader(state: PlayerState, meter: Boolean = true) {
    val style = Lanyin.style
    val c = style.colors
    val label = style.type.label.copy(fontFamily = style.numeric, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp)
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("TRK %02d/%02d".format(state.trackNumber, state.trackCount), style = label, color = c.muted, maxLines = 1, modifier = Modifier.weight(1f))
        if (meter) VuMeter(state.audioFeed, state.isPlaying, Modifier.size(64.dp, 32.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(if (state.isPlaying) c.accent else c.muted.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(if (state.isPlaying) "PLAY" else "PAUSE", style = label, color = if (state.isPlaying) c.content else c.muted, maxLines = 1)
        }
    }
}

/**
 * Landscape is a stage between a top bar and a control bar. The top bar carries the collapse
 * button with the title beside it, as a window title; the stage is the theme's own. Tap the
 * empty stage for full screen (and again to come back): the bars fold away and a quiet status
 * line with the title and the time takes their place.
 */
@Composable
private fun LandscapePlayer(
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
    // 游 turns the whole screen into the console, its buttons doing the transport.
    if (Lanyin.style.backdrop == BackdropKind.Game) {
        ConsoleLandscape(state, skin, spin, chromeVisible, onToggleChrome, dismissDrag, onCollapse, onRotate, lyricScale)
        return
    }
    val entrance = rememberEntrance(Unit)
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .displayCutoutPadding()
            .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LandscapeTopBar(state, onCollapse, onRotate, modifier = dismissDrag.enter(entrance, 0))
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { onToggleChrome() } },
        ) {
            if (skin == PlayerSkin.LYRICS) {
                StageLyrics(
                    state, chromeVisible,
                    textStyle = lyricTextStyle(LyricSize.BASE_SP * 1.2f, lyricScale),
                    centered = true,
                    scrubMs = scrubMs,
                    modifier = Modifier.fillMaxSize().enter(entrance, 1),
                )
            } else {
                val stage = StageContext(state, skin, spin, chromeVisible, scrubMs, lyricScale, dismissDrag, entrance)
                when (Lanyin.style.backdrop) {
                    BackdropKind.Device -> DeviceStage(stage)
                    BackdropKind.Film -> CinemaStage(stage)
                    BackdropKind.Dot -> GlyphStage(stage)
                    BackdropKind.Acid -> MediaPlayerStage(stage)
                    BackdropKind.Paper -> BroadsheetStage(stage)
                    BackdropKind.Ink -> ScrollStage(stage)
                    BackdropKind.Grid -> PosterStage(stage)
                    BackdropKind.Dusk -> PostcardStage(stage)
                    BackdropKind.Neon -> SignStage(stage)
                    BackdropKind.Game -> Unit
                }
            }
        }
        AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LandscapeControlBar(state, onScrub = { scrubMs = it }, Modifier.padding(top = 8.dp).enter(entrance, 2))
        }
        AnimatedVisibility(!chromeVisible, enter = fadeIn(tween(600)), exit = fadeOut(tween(150))) {
            // 墨 writes the title in its inscription instead.
            ImmersiveStatus(state, showTitle = !(Lanyin.style.edge == EdgeKind.Hairline && skin != PlayerSkin.LYRICS))
        }
    }
}

/**
 * Lyrics with a footer of where the track is from and what plays next. The anchor is worked out
 * from the measured footer so the active line stays at the stage's vertical centre, level with
 * the artwork, while the footer folds in and out.
 */
@Composable
internal fun StageLyrics(
    state: PlayerState,
    chromeVisible: Boolean,
    textStyle: TextStyle,
    centered: Boolean,
    modifier: Modifier = Modifier,
    scrubMs: Long? = null,
) {
    var footerPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier) {
        val total = constraints.maxHeight
        val lyricsPx = (total - footerPx).coerceAtLeast(1)
        val anchor = (total / 2f / lyricsPx).coerceIn(0.25f, 0.75f)
        Column(Modifier.fillMaxSize()) {
            StateLyrics(state, textStyle, centered, anchor, scrubMs = scrubMs, modifier = Modifier.weight(1f).fillMaxWidth())
            AnimatedVisibility(
                chromeVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.onSizeChanged { footerPx = it.height },
            ) {
                StageFooter(state, centered)
            }
        }
    }
}

/** The track's lyrics, or a quiet note for an instrumental. */
@Composable
internal fun StateLyrics(
    state: PlayerState,
    textStyle: TextStyle,
    centered: Boolean,
    anchorFraction: Float,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    showTranslation: Boolean = true,
    /** Where the progress line is being dragged: the lyrics follow it before the seek lands. */
    scrubMs: Long? = null,
) {
    if (state.lyrics.isEmpty() && state.lyricsLoading) {
        // Not an instrumental, just not here yet: hold the space quietly.
        Box(modifier, contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
            Text("歌词加载中…", style = Lanyin.style.type.caption, color = Lanyin.colors.muted)
        }
    } else if (state.lyrics.isEmpty()) {
        Instrumental(textStyle, centered, modifier, state.lyricsGap, state::retryLyrics)
    } else {
        val index = scrubMs?.let { lyricIndexAt(state.lyrics, it) } ?: state.currentLyricIndex
        val scrubbing = scrubMs != null
        LyricsView(
            state.lyrics, index, { if (scrubbing) 0f else state.lineProgress() }, state::seekTo,
            textStyle = textStyle, modifier = modifier, centered = centered, anchorFraction = anchorFraction,
            interactive = interactive, showTranslation = showTranslation,
        )
    }
}

@Composable
internal fun Instrumental(
    textStyle: TextStyle,
    centered: Boolean,
    modifier: Modifier = Modifier,
    gap: LyricsGap = LyricsGap.Instrumental,
    onRetry: () -> Unit = {},
) {
    val style = Lanyin.style
    val c = style.colors
    val (headline, note) = when (gap) {
        LyricsGap.Instrumental -> "纯音乐，请欣赏" to "这首歌没有歌词"
        LyricsGap.Missing -> "暂无歌词" to "网易云还没有这首歌的歌词"
        LyricsGap.Failed -> "歌词没能加载" to "点一下重试"
    }
    Box(
        modifier.then(if (gap == LyricsGap.Failed) Modifier.pressable(onClick = onRetry) else Modifier),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
    ) {
        Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
            Text(
                headline,
                style = textStyle.copy(
                    fontFamily = if (style.lyric.serif) style.display else textStyle.fontFamily,
                    fontWeight = if (style.lyric.serif) FontWeight.Medium else FontWeight.Bold,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                ),
                color = c.content.copy(alpha = 0.7f),
            )
            Text(note, style = style.type.caption, color = c.muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Where the track is playing from, and what comes next; the source gives way first when space is short. */
@Composable
internal fun StageFooter(state: PlayerState, centered: Boolean) {
    val style = Lanyin.style
    val c = style.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f, fill = false).padding(end = if (centered) 28.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            ThemedIcon(LineIcon.Note, null, tint = c.muted, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                buildAnnotatedString {
                    append(state.playlistName)
                    withStyle(SpanStyle(fontFamily = style.numeric)) { append("  ${state.trackNumber} / ${state.trackCount}") }
                },
                style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("下一首", style = style.type.label, color = c.muted, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(
                state.next.title, style = style.type.caption, color = c.content.copy(alpha = 0.72f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}

/** What is playing and the time, in place of the bars while the stage is full screen. */
@Composable
internal fun ImmersiveStatus(state: PlayerState, showTitle: Boolean = true) {
    val style = Lanyin.style
    val c = style.colors
    val now by rememberMinuteClock()
    if (style.edge == EdgeKind.Brutal) {
        // 格 runs the news along a ribbon, the progress as a solid bar beneath it.
        val clock = "%02d:%02d".format(now.hour, now.minute)
        Column(Modifier.border(style.strokeWidth, c.outline)) {
            Marquee(listOf("${state.title} — ${state.artist}", "${state.playlistName} ${state.trackNumber}/${state.trackCount}", clock, "下一首 ${state.next.title}"))
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val f = (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                drawRect(c.surface)
                drawRect(c.accent, size = androidx.compose.ui.geometry.Size(size.width * f, size.height))
            }
        }
        return
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showTitle) {
                    buildAnnotatedString {
                        append(state.title)
                        withStyle(SpanStyle(color = c.muted)) { append("  ·  ${state.artist}") }
                    }
                } else {
                    buildAnnotatedString { withStyle(SpanStyle(color = c.muted)) { append("${state.playlistName}  ${state.trackNumber} / ${state.trackCount}") } }
                },
                style = style.type.caption,
                color = c.content.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "%02d:%02d".format(now.hour, now.minute),
                style = style.type.caption.copy(fontFamily = style.numeric, fontWeight = FontWeight.Medium),
                color = c.muted,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val f = (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
            drawRect(c.content.copy(alpha = 0.12f))
            drawRect(c.accent, size = androidx.compose.ui.geometry.Size(size.width * f, size.height))
        }
    }
}

/** The current time, updated on the minute. */
@Composable
internal fun rememberMinuteClock(): State<LocalTime> = produceState(LocalTime.now()) {
    while (true) {
        val now = LocalTime.now()
        value = now
        delay(((60 - now.second) * 1000L - now.nano / 1_000_000).coerceAtLeast(200L))
    }
}

/** Collapse, the title beside it like a window title, and the track's tags and actions. */
@Composable
internal fun LandscapeTopBar(state: PlayerState, onCollapse: () -> Unit, onRotate: () -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val openMore = io.lanyin.player.ui.music.LocalOpenSheet.current
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(LineIcon.ChevronDown, "收起", onCollapse, iconSize = 30.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            TrackSwitch(state, Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true)) { track ->
                Text(track.title, style = style.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TrackSwitch(state) { track ->
                Text("${track.artist} · ${track.album}", style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(16.dp))
        QualityTags(state)
        CommentButton(state, c.content)
        IconAction(LineIcon.Equalizer, "音效", { openMore(io.lanyin.player.ui.music.Sheet.Effects) })
        LikeButton(state.liked, onToggle = state::toggleLike)
        IconAction(LineIcon.Rotate, "切换到竖屏", onRotate)
        IconAction(LineIcon.More, "更多", { openMore(io.lanyin.player.ui.music.Sheet.TrackActions(state.track.toSong())) })
    }
}

/** 格 slaps a turning sticker with the quality and format over the artwork's corner. */
@Composable
internal fun QualitySticker(state: PlayerState, modifier: Modifier = Modifier) {
    if (Lanyin.style.edge != EdgeKind.Brutal) return
    TrackSwitch(state, modifier.offset((-6).dp, (-6).dp)) { track ->
        Sticker(listOf(state.quality, state.format.substringBefore(" ·"), "★"))
    }
}

/**
 * The quality tag, with the stream format when there's room: a chip in most themes, a quiet
 * tracked label in 墨. Tapping it opens the quality picker.
 */
@Composable
internal fun QualityTags(state: PlayerState, chipFirst: Boolean = false, showFormat: Boolean = true) {
    val style = Lanyin.style
    val openSheet = io.lanyin.player.ui.music.LocalOpenSheet.current
    // Unknown until the stream has been resolved; still a way in to choosing one.
    val quality = state.quality.ifEmpty { "音质" }
    val format = state.format.takeIf { showFormat && it.isNotEmpty() }
    Row(
        Modifier.pressable { openSheet(io.lanyin.player.ui.music.Sheet.Quality) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (style.quietSections) {
            Text(listOfNotNull(quality, format).joinToString(" · "), style = style.type.label, color = style.colors.muted, maxLines = 1)
            ThemedIcon(LineIcon.CaretDown, "选择音质", tint = style.colors.muted, size = 14.dp)
        } else if (chipFirst) {
            QualityChip(quality, caret = true)
            if (format != null) {
                Spacer(Modifier.width(8.dp))
                Text(format, style = style.type.caption, color = style.colors.muted, maxLines = 1)
            }
        } else {
            if (format != null) {
                Text(format, style = style.type.caption, color = style.colors.muted, maxLines = 1)
                Spacer(Modifier.width(8.dp))
            }
            QualityChip(quality, caret = true)
        }
    }
}

@Composable
internal fun QualityChip(text: String, caret: Boolean = false) {
    val style = Lanyin.style
    val c = style.colors
    val brutal = style.edge == EdgeKind.Brutal
    val ink = if (brutal) c.content else c.accent
    Row(
        Modifier
            .then(
                if (brutal) {
                    // 格: a yellow label boxed in ink.
                    Modifier.background(GridYellow).border(2.dp, c.outline)
                } else {
                    Modifier.clip(style.chipShape).border(1.dp, c.accent.copy(alpha = 0.6f), style.chipShape)
                },
            )
            .padding(start = if (brutal) 8.dp else 10.dp, end = if (caret) 5.dp else if (brutal) 8.dp else 10.dp, top = if (brutal) 2.dp else 3.dp, bottom = if (brutal) 2.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = style.type.caption.copy(fontWeight = if (brutal) FontWeight.Black else FontWeight.SemiBold), color = ink, maxLines = 1)
        if (caret) ThemedIcon(LineIcon.CaretDown, "选择音质", tint = ink, size = 14.dp, modifier = Modifier.padding(start = 1.dp))
    }
}

@Composable
internal fun LandscapeControlBar(state: PlayerState, onScrub: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val docked = style.dock == DockKind.Docked
    Column(modifier) {
        if (docked) Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                // 机's keys sit in a panel set into the case rather than on a key of their own.
                .then(if (docked) Modifier else Modifier.themedSurface(shape = style.chipShape, tone = if (style.edge == EdgeKind.Panel) SurfaceTone.Card else SurfaceTone.Chrome))
                .padding(start = if (docked) 0.dp else 18.dp, end = if (docked) 0.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeekBar(state::positionMs, state.durationMs, state.isPlaying, state::seekTo, Modifier.weight(1f).padding(end = 12.dp), onScrub = onScrub)
            TransportControls(state, playSize = 46.dp, Modifier.width(264.dp))
        }
    }
}

/**
 * Lyric face at [baseSp] scaled by the user's 歌词大小, line height 1.36×. It deliberately ignores
 * the system font scale: many phones ship with a large default font, which made lyrics far bigger
 * than intended, and 歌词大小 already lets anyone who wants them bigger say so.
 */
@Composable
internal fun lyricTextStyle(baseSp: Float, scale: Float): TextStyle {
    // Through dp, which undoes the system's font scaling whether it is linear or not.
    val size = (baseSp * scale * Lanyin.style.lyric.sizeScale).dp
    return with(androidx.compose.ui.platform.LocalDensity.current) {
        MaterialTheme.typography.headlineSmall.copy(fontSize = size.toSp(), lineHeight = (size * 1.36f).toSp())
    }
}

/** Collapse, where the music is playing from (centred, like a page title), and more. */
@Composable
internal fun PlayerTopBar(state: PlayerState, onCollapse: () -> Unit, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Box(modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        IconAction(LineIcon.ChevronDown, "收起", onCollapse, Modifier.align(Alignment.CenterStart), iconSize = 30.dp)
        if (style.edge == EdgeKind.Brutal) {
            // 格 runs a ticker where the others put a page title.
            Marquee(
                listOf("正在播放", state.playlistName, "%02d / %02d".format(state.trackNumber, state.trackCount)),
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 52.dp)
                    .retroShadow(androidx.compose.ui.graphics.RectangleShape, c.outline, 3.dp)
                    .border(style.strokeWidth, c.outline),
                height = 30.dp,
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (style.backdrop) {
                    // 点: the red dot of a recording light beside the label.
                    BackdropKind.Dot -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(if (state.isPlaying) c.accentAlt else c.muted, androidx.compose.foundation.shape.CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("NOW PLAYING", style = style.type.label.copy(fontFamily = style.numeric, fontWeight = FontWeight.Bold, fontSize = 13.sp), color = c.content, maxLines = 1)
                    }
                    // 机: the maker's name and model, as printed on the case.
                    BackdropKind.Device -> Text("LANYIN · L-01", style = style.type.label.copy(fontFamily = style.numeric, fontWeight = FontWeight.Bold), color = c.content, maxLines = 1)
                    // 报: the masthead.
                    BackdropKind.Paper -> Text("澜音日报", style = style.type.title.copy(fontFamily = style.display, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 0.16.em), color = c.content, maxLines = 1)
                    // 游: the console's name, in the navy of its lettering.
                    BackdropKind.Game -> Text("LANYIN POCKET", style = style.type.label.copy(fontSize = 13.sp, letterSpacing = 1.sp), color = c.accentAlt, maxLines = 1)
                    // 酸: a track number between stars.
                    BackdropKind.Acid -> Text("★ TRACK %02d ★".format(state.trackNumber), style = style.type.label.copy(fontFamily = style.numeric, fontWeight = FontWeight.Bold), color = c.accent, maxLines = 1)
                    else -> Text("正在播放", style = style.type.label, color = c.muted, maxLines = 1)
                }
                Text(
                    if (style.backdrop == BackdropKind.Paper) "第 %02d 版 · %s".format(state.trackNumber, state.playlistName) else state.playlistName,
                    style = style.type.caption.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        val openMore = io.lanyin.player.ui.music.LocalOpenSheet.current
        IconAction(LineIcon.More, "更多", { openMore(io.lanyin.player.ui.music.Sheet.TrackActions(state.track.toSong())) }, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
internal fun TrackTitle(state: PlayerState, modifier: Modifier = Modifier) {
    val c = Lanyin.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            TrackSwitch(state, Modifier.sharedPlayer("title", zIndex = 2f, scaleContent = true)) { track ->
                // 酸's title is polished chrome, like its page titles.
                if (Lanyin.style.edge == EdgeKind.Chrome) {
                    Text(track.title, style = MaterialTheme.typography.headlineSmall.copy(brush = io.lanyin.player.ui.design.ChromeBrush), maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TrackSwitch(state, Modifier.padding(top = 2.dp)) { track ->
                Text(
                    "${track.artist} · ${track.album}", style = MaterialTheme.typography.bodyMedium, color = c.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        LikeButton(state.liked, onToggle = state::toggleLike)
    }
}

/**
 * Per-track text that slides out the way you skipped while the next slides in after it. The
 * wrapper keeps its place (and any shared-element key), only the content changes.
 */
@Composable
internal fun TrackSwitch(state: PlayerState, modifier: Modifier = Modifier, content: @Composable (Track) -> Unit) {
    AnimatedContent(
        targetState = state.track,
        modifier = modifier,
        transitionSpec = {
            val dir = if (state.skipDirection < 0) -1 else 1
            (slideInHorizontally(tween(340)) { it / 5 * dir } + fadeIn(tween(300, delayMillis = 60))) togetherWith
                (slideOutHorizontally(tween(220)) { -it / 5 * dir } + fadeOut(tween(160))) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.CenterStart,
        label = "trackText",
    ) { track -> content(track) }
}

/** The comment bubble, with NetEase's way of counting over its shoulder: 「999+」「1w+」「10w+」. */
@Composable
internal fun CommentButton(state: PlayerState, tint: androidx.compose.ui.graphics.Color) {
    val style = Lanyin.style
    val nav = io.lanyin.player.ui.LocalNavigator.current
    LaunchedEffect(state.track.id) { state.loadCommentCount() }
    Box(Modifier.size(48.dp).pressable { nav?.openComments?.invoke(state.track.toSong()) }, contentAlignment = Alignment.Center) {
        ThemedIcon(LineIcon.Comment, "评论", tint = tint, size = 24.dp)
        state.commentCount?.takeIf { it > 0 }?.let { n ->
            Text(
                commentBadge(n),
                style = style.type.label.copy(fontSize = 9.sp, fontFamily = style.numeric, fontWeight = FontWeight.Medium),
                color = tint,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(Alignment.TopStart).offset(x = 31.dp, y = 7.dp),
            )
        }
    }
}

internal fun commentBadge(n: Int): String = when {
    n >= 1_000_000 -> "100w+"
    n >= 100_000 -> "10w+"
    n >= 10_000 -> "1w+"
    n >= 1_000 -> "999+"
    else -> n.toString()
}

/** The quieter actions under the transport: quality on the left, comments, lyrics and rotation on the right. */
@Composable
internal fun UtilityRow(
    state: PlayerState,
    onToggleLyrics: (() -> Unit)?,
    lyricsShown: Boolean,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Lanyin.colors
    val openSheet = io.lanyin.player.ui.music.LocalOpenSheet.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        QualityTags(state, chipFirst = true, showFormat = false)
        Spacer(Modifier.weight(1f))
        CommentButton(state, c.muted)
        IconAction(LineIcon.Equalizer, "音效", { openSheet(io.lanyin.player.ui.music.Sheet.Effects) }, tint = c.muted)
        if (onToggleLyrics != null) {
            IconAction(LineIcon.Lyrics, if (lyricsShown) "显示封面" else "显示歌词", onToggleLyrics, tint = if (lyricsShown) c.accent else c.muted)
        }
        IconAction(LineIcon.Rotate, "切换到横屏", onRotate, tint = c.muted)
    }
}

/** Transport; skip buttons give a little push in their direction, the mode icon pops as it changes. */
@Composable
internal fun TransportControls(state: PlayerState, playSize: Dp, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    // 机's transport is a row of keys, 游's the console's dark D-pad keys.
    val keys = style.button == ButtonKind.Key || style.button == ButtonKind.Pad
    val cap = when (style.button) {
        ButtonKind.Key -> Modifier.padding(2.dp).keycap(RoundedCornerShape(14.dp), style)
        ButtonKind.Pad -> Modifier.padding(3.dp).padKey(RoundedCornerShape(8.dp))
        else -> Modifier
    }
    val skipIcon = if (keys) 26.dp else 34.dp
    val side = if (style.button == ButtonKind.Pad) io.lanyin.player.ui.design.GamePadGlyph else c.muted
    val skip = if (style.button == ButtonKind.Pad) io.lanyin.player.ui.design.GamePadGlyph else c.content
    val scope = rememberCoroutineScope()
    val back = remember { Animatable(0f) }
    val forward = remember { Animatable(0f) }
    fun push(a: Animatable<Float, *>, dir: Float) {
        scope.launch {
            a.animateTo(dir, tween(80))
            a.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            state.playMode, label = "playMode", modifier = cap,
            transitionSpec = { (fadeIn(tween(200)) + scaleIn(initialScale = 0.5f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.5f)) },
        ) { mode ->
            IconAction(
                when (mode) {
                    PlayMode.REPEAT_ALL -> LineIcon.Repeat
                    PlayMode.REPEAT_ONE -> LineIcon.RepeatOne
                    PlayMode.SHUFFLE -> LineIcon.Shuffle
                },
                mode.label, state::cyclePlayMode, tint = side,
            )
        }
        IconAction(
            LineIcon.Previous, "上一首", { state.skipPrevious(); push(back, -1f) },
            Modifier.graphicsLayer { translationX = back.value * 7.dp.toPx() }.then(cap), tint = skip, iconSize = skipIcon,
        )
        PlayPauseButton(state.isPlaying, state::togglePlay, playSize, feed = state.audioFeed)
        IconAction(
            LineIcon.Next, "下一首", { state.skipNext(); push(forward, 1f) },
            Modifier.graphicsLayer { translationX = forward.value * 7.dp.toPx() }.then(cap), tint = skip, iconSize = skipIcon,
        )
        val openSheet = io.lanyin.player.ui.music.LocalOpenSheet.current
        IconAction(LineIcon.Queue, "播放队列", { openSheet(io.lanyin.player.ui.music.Sheet.Queue) }, cap, tint = side)
    }
}
