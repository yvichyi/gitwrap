package io.lanyin.player.ui.desktop

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.lanyin.player.core.model.LyricLine
import io.lanyin.player.data.settings.DESKTOP_SIZES
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.UiSettings
import io.lanyin.player.ui.design.LocalHapticsOn
import io.lanyin.player.ui.design.LocalReduceMotion
import io.lanyin.player.ui.design.systemAnimationsOff
import io.lanyin.player.ui.player.PlayerState
import io.lanyin.player.ui.theme.LanyinTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 桌面歌词: while music plays and 澜音 is out of sight, the line being sung floats over other apps
 * in a window of its own (which needs 「显示在其他应用上层」). A tap shows its controls, a drag
 * moves it up or down; locked, touches go through it to what's underneath. It goes when the app
 * comes back, a few seconds after pausing, or when switched off.
 */
class DesktopLyricsWindow(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: PlayerState,
    private val settings: SettingsRepository,
    private val appVisible: StateFlow<Boolean>,
) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val current = MutableStateFlow(UiSettings())
    private var host: Host? = null
    private var writes: ObserverHandle? = null

    fun start() {
        // The player's state is snapshot state, and Compose only tells observers outside a
        // composition about changes once one of its windows exists; music started from a headset
        // with no screen opened would never bring the lyrics up. So tell them here too.
        val main = Handler(Looper.getMainLooper())
        val pending = AtomicBoolean(false)
        writes = Snapshot.registerGlobalWriteObserver {
            if (pending.compareAndSet(false, true)) {
                main.post {
                    pending.set(false)
                    Snapshot.sendApplyNotifications()
                }
            }
        }
        scope.launch {
            combine(settings.settings, appVisible, playingLately()) { s, visible, playing ->
                current.value = s
                s.desktopLyrics && playing && !visible && canDraw(context)
            }.distinctUntilChanged().collect { wanted -> if (wanted) show() else hide() }
        }
        // Moved, resized or locked while showing: the window follows.
        scope.launch {
            settings.settings.collect { s -> host?.let { place(it, s) } }
        }
    }

    /** Takes the window away and stops listening; the app itself never needs to, it lasts as long as the process. */
    fun stop() {
        writes?.dispose()
        writes = null
        hide()
    }

    /** Playing now, or paused for less than a few seconds (so a pause to answer something doesn't make it blink). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun playingLately(): Flow<Boolean> = snapshotFlow { player.isPlaying && !player.isEmpty }
        .transformLatest { playing ->
            if (playing) {
                emit(true)
            } else {
                delay(PAUSE_GRACE_MS)
                emit(false)
            }
        }

    private fun show() {
        if (host != null) return
        val h = Host(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "澜音 · 桌面歌词"
        }
        h.params = params
        h.frame.onDrag = { dy ->
            params.y = (params.y + dy.roundToInt()).coerceIn(0, maxY())
            runCatching { windows.updateViewLayout(h.frame, params) }
        }
        h.frame.onDragEnd = {
            val y = params.y.toFloat() / screenHeight().coerceAtLeast(1)
            scope.launch { settings.setDesktopLyricsY(y) }
        }
        h.compose.setContent { Content() }
        place(h, current.value, add = false)
        // The permission can go between checking and adding; then there's simply no window.
        if (runCatching { windows.addView(h.frame, params) }.isFailure) {
            h.destroy()
            return
        }
        host = h
    }

    private fun hide() {
        val h = host ?: return
        host = null
        runCatching { windows.removeView(h.frame) }
        h.destroy()
    }

    /** Puts the window where the settings say, touchable or not. */
    private fun place(h: Host, s: UiSettings, add: Boolean = true) {
        val p = h.params ?: return
        p.y = (s.desktopLyricsY * screenHeight()).roundToInt().coerceIn(0, maxY())
        if (s.desktopLyricsLocked) {
            p.flags = BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            // Since Android 12 a window only lets touches through to another app when it's no
            // more opaque than this; locked, it is drawn just under it.
            p.alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(InputManager::class.java)?.maximumObscuringOpacityForTouch ?: 0.8f
            } else {
                1f
            }
        } else {
            p.flags = BASE_FLAGS
            p.alpha = 1f
        }
        h.frame.draggable = !s.desktopLyricsLocked
        if (add) runCatching { windows.updateViewLayout(h.frame, p) }
    }

    private fun screenHeight(): Int = context.resources.displayMetrics.heightPixels

    private fun maxY(): Int = (screenHeight() * 0.9f).roundToInt()

    @Composable
    private fun Content() {
        val s by current.collectAsState()
        CompositionLocalProvider(
            LocalReduceMotion provides (s.reduceMotion || systemAnimationsOff()),
            LocalHapticsOn provides s.haptics,
        ) {
            LanyinTheme(theme = s.theme, mode = s.themeMode, applySystemBars = false) {
                DesktopLyricsPanel(
                    player, s,
                    onSize = { size -> scope.launch { settings.setDesktopLyricsSize(size) } },
                    onLock = {
                        scope.launch { settings.setDesktopLyricsLocked(true) }
                        Toast.makeText(context, "桌面歌词已锁定，可在快捷开关或设置里解锁", Toast.LENGTH_SHORT).show()
                    },
                    onClose = {
                        scope.launch { settings.setDesktopLyrics(false) }
                        Toast.makeText(context, "已关闭桌面歌词，可在设置里再打开", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    /** The view in the window and what its Compose content needs from an owner: a lifecycle and somewhere to save state. */
    private class Host(context: Context) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        var params: WindowManager.LayoutParams? = null
        val frame = DragFrame(context)
        val compose = ComposeView(context)

        init {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
            frame.setViewTreeLifecycleOwner(this)
            frame.setViewTreeSavedStateRegistryOwner(this)
            frame.setViewTreeViewModelStoreOwner(this)
            frame.addView(compose)
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            compose.disposeComposition()
            viewModelStore.clear()
        }
    }

    companion object {
        private const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        /** How long a pause lasts before the lyrics go. */
        const val PAUSE_GRACE_MS = 4_000L

        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}

/**
 * Takes a vertical drag for itself (measured on the screen, as the window moves under the finger)
 * and leaves taps to the Compose content inside.
 */
@SuppressLint("ViewConstructor")
internal class DragFrame(context: Context) : FrameLayout(context) {
    var onDrag: (Float) -> Unit = {}
    var onDragEnd: () -> Unit = {}
    var draggable = true
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!draggable) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.rawY
                lastY = ev.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> if (!dragging && abs(ev.rawY - downY) > slop) {
                dragging = true
                lastY = ev.rawY
            }
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (dragging) {
                onDrag(ev.rawY - lastY)
                lastY = ev.rawY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragEnd()
                dragging = false
            }
        }
        return dragging || super.onTouchEvent(ev)
    }
}

/** The line being sung for [DesktopLyric]: the title and artist when there are no lyrics to follow. */
internal fun desktopLine(lines: List<LyricLine>, index: Int, title: String, artist: String): DesktopLine {
    val line = lines.getOrNull(index)
    if (line == null || line.text.isBlank()) {
        // Between verses (or before the first line), the song's name, unswept.
        return DesktopLine(title, artist.takeIf { it.isNotBlank() }, timed = false)
    }
    val second = line.translation?.takeIf { it.isNotBlank() } ?: lines.drop(index + 1).firstOrNull { it.text.isNotBlank() }?.text
    return DesktopLine(line.text.trim(), second?.trim(), timed = true)
}

/**
 * The strip and, after a tap, its controls. Keeps [player]'s position moving (about 30 times a
 * second is plenty for a sweep) while it shows, as the app's own frame driver is stopped.
 */
@Composable
internal fun DesktopLyricsPanel(
    player: PlayerState,
    s: UiSettings,
    onSize: (Int) -> Unit,
    onLock: () -> Unit,
    onClose: () -> Unit,
    controlsShown: Boolean = false,
) {
    LaunchedEffect(player, player.isPlaying) {
        while (player.isPlaying && isActive) {
            withFrameMillis(player::onFrame)
            delay(25)
        }
    }
    // Each touch of the controls keeps them up a little longer.
    var shown by remember { mutableStateOf(controlsShown) }
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(shown, touched) {
        if (shown && !controlsShown) {
            delay(4_000)
            shown = false
        }
    }
    val index = player.currentLyricIndex
    val line = desktopLine(player.lyrics, index, player.title, player.artist)
    val source = player.lyrics.getOrNull(index)
    val reduced = LocalReduceMotion.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Each line keeps its own progress while it fades out under the next.
        Crossfade(line to source, animationSpec = tween(if (reduced) 0 else 220), label = "desktopLine") { (l, sungLine) ->
            DesktopLyric(
                l, progress = { sungLine?.charProgress(player.positionMs) ?: 1f }, size = s.desktopLyricsSize,
                modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectTapGestures {
                        shown = !shown
                        touched++
                    }
                },
            )
        }
        AnimatedVisibility(shown && !s.desktopLyricsLocked, enter = fadeIn(), exit = fadeOut()) {
            DesktopControls(
                playing = player.isPlaying,
                onPrevious = { touched++; player.skipPrevious() },
                onToggle = { touched++; player.togglePlay() },
                onNext = { touched++; player.skipNext() },
                onSmaller = { touched++; onSize((s.desktopLyricsSize - 1).coerceAtLeast(0)) },
                onLarger = { touched++; onSize((s.desktopLyricsSize + 1).coerceAtMost(DESKTOP_SIZES - 1)) },
                onLock = onLock,
                onClose = onClose,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
