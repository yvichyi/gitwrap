package io.lanyin.player.ui.desktop

import android.content.Context
import android.os.Looper
import android.view.WindowManager
import androidx.compose.runtime.snapshots.Snapshot
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.lanyin.player.core.model.LyricLine
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.ui.player.DemoPlayerState
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings

/** When 桌面歌词 shows and goes, how it locks, and what line it shows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DesktopLyricsWindowTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val started = mutableListOf<DesktopLyricsWindow>()

    @After
    fun tearDown() {
        started.forEach { it.stop() }
        scope.cancel()
    }

    private fun window(player: DemoPlayerState, repo: SettingsRepository, visible: MutableStateFlow<Boolean>) =
        DesktopLyricsWindow(context, scope, player, repo, visible).also { started += it; it.start() }

    private fun settings() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Unconfined)) { File(tmp.root, "settings.preferences_pb") },
    )

    private fun views(): List<android.view.View> =
        org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowWindowManagerImpl>(context.getSystemService(WindowManager::class.java)).views

    /** Lets snapshot changes, the main looper and the settings file catch up until [done]. */
    private fun until(done: () -> Boolean): Boolean {
        repeat(200) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            if (done()) return true
            Thread.sleep(10)
        }
        return false
    }

    @Test
    fun `it shows while music plays out of the app's sight, and goes when the app returns or the music has been paused a while`() {
        ShadowSettings.setCanDrawOverlays(true)
        val repo = settings()
        runBlocking { repo.setDesktopLyrics(true) }
        val player = DemoPlayerState()
        val visible = MutableStateFlow(true)
        window(player, repo, visible)
        player.togglePlay()
        assertFalse("not over the app itself", until { views().isNotEmpty() })

        visible.value = false
        assertTrue("shows once the app is out of sight", until { views().size == 1 })

        visible.value = true
        assertTrue("goes when the app is back", until { views().isEmpty() })

        visible.value = false
        assertTrue(until { views().size == 1 })
        player.togglePlay()
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        assertEquals("a short pause keeps it", 1, views().size)
        shadowOf(Looper.getMainLooper()).idleFor(DesktopLyricsWindow.PAUSE_GRACE_MS, TimeUnit.MILLISECONDS)
        assertTrue("a longer one takes it away", until { views().isEmpty() })
    }

    @Test
    fun `without the permission, or switched off, there is no window`() {
        ShadowSettings.setCanDrawOverlays(false)
        val repo = settings()
        runBlocking { repo.setDesktopLyrics(true) }
        val player = DemoPlayerState()
        window(player, repo, MutableStateFlow(false))
        player.togglePlay()
        assertFalse(until { views().isNotEmpty() })

        ShadowSettings.setCanDrawOverlays(true)
        runBlocking { repo.setDesktopLyrics(false) }
        assertFalse(until { views().isNotEmpty() })
    }

    @Test
    fun `locked, touches go through it`() {
        ShadowSettings.setCanDrawOverlays(true)
        val repo = settings()
        runBlocking { repo.setDesktopLyrics(true) }
        val player = DemoPlayerState()
        window(player, repo, MutableStateFlow(false))
        player.togglePlay()
        assertTrue(until { views().size == 1 })
        val params = { views().single().layoutParams as WindowManager.LayoutParams }
        assertEquals(0, params().flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        runBlocking { repo.setDesktopLyricsLocked(true) }
        assertTrue(until { params().flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0 })
        // Android 12 lets touches through only a window no more opaque than this.
        assertTrue(params().alpha <= 0.8f)
    }

    @Test
    fun `the line shown is the one sung with its translation or the next, and the title between verses`() {
        val lines = listOf(
            LyricLine("", null, 0, 1_000),
            LyricLine("把今晚调成横屏", "Turn tonight sideways", 1_000, 4_000),
            LyricLine("让歌词亮起", null, 4_000, 8_000),
            LyricLine("  ", null, 8_000, 9_000),
            LyricLine("最后一句", null, 9_000, 12_000),
        )
        assertEquals(DesktopLine("横屏", "澜音", timed = false), desktopLine(lines, 0, "横屏", "澜音"))
        assertEquals(DesktopLine("把今晚调成横屏", "Turn tonight sideways", timed = true), desktopLine(lines, 1, "横屏", "澜音"))
        assertEquals("the next line with words, past a blank one", DesktopLine("让歌词亮起", "最后一句", timed = true), desktopLine(lines, 2, "横屏", "澜音"))
        assertNull(desktopLine(lines, 4, "横屏", "澜音").second)
        assertEquals(DesktopLine("横屏", null, timed = false), desktopLine(emptyList(), 0, "横屏", ""))
    }
}
