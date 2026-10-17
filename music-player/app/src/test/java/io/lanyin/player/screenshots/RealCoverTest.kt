package io.lanyin.player.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.core.model.Album
import io.lanyin.player.core.model.Artist
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.core.model.Song
import io.lanyin.player.core.model.UserProfile
import io.lanyin.player.data.account.AccountState
import io.lanyin.player.data.library.ChartPreview
import io.lanyin.player.data.library.Picks
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.UiSettings
import io.lanyin.player.ui.LanyinApp
import io.lanyin.player.ui.home.HomePreview
import io.lanyin.player.ui.player.DemoPlayerState
import io.lanyin.player.ui.player.demoQueue
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.PlayerSkin
import io.lanyin.player.ui.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every theme with real covers loaded (from [FakeCovers]): the home page, the player with the
 * record and with the square cover, and 我的. Images land in build/outputs/roborazzi/covers.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h850dp-port-xhdpi")
class RealCoverTest(private val theme: AppTheme, private val mode: ThemeMode) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> = AppTheme.entries.map { theme ->
            arrayOf<Any>(theme, if (theme.darkOnly) ThemeMode.DARK else ThemeMode.LIGHT)
        }

        private fun url(kind: String, i: Int) = "https://p1.music.126.net/test/$kind$i.jpg"

        private val songs = listOf("潮水退去以后", "夜航船", "海边的风", "落日磁带", "Midnight Ferry", "月光下的鲸").mapIndexed { i, name ->
            Song(100L + i, name, listOf(Artist(1, "澜音")), Album(i.toLong(), "专辑$i", url("album", i)), 200_000L)
        }

        private val playlists = listOf("深夜写代码", "雨天的歌", "通勤路上", "周末慢慢听", "城市民谣精选", "旧时光").mapIndexed { i, name ->
            Playlist(id = 901L + i, name = name, coverUrl = url("list", i), trackCount = 12 + i * 37, creatorId = 1, creatorName = "潮汐里的鲸")
        }
    }

    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    @Before
    fun covers() = FakeCovers.install(ApplicationProvider.getApplicationContext())

    private val name get() = theme.name.lowercase()

    private fun app(skin: PlayerSkin = PlayerSkin.Default, expanded: Boolean = false) {
        val repo = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Unconfined)) { File(tmp.root, "s.preferences_pb") },
        )
        val player = DemoPlayerState(demoQueue.mapIndexed { i, t -> t.copy(coverUrl = url("track", i)) }).apply {
            togglePlay()
            seekTo(14_000)
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode) {
                LanyinApp(
                    UiSettings(theme = theme, themeMode = mode, playerSkin = skin), repo, player = player,
                    accountOverride = AccountState.LoggedIn(UserProfile(1, "潮汐里的鲸", null, 11)),
                    homePreview = HomePreview(
                        recommended = playlists,
                        charts = listOf("飙升榜", "新歌榜", "原创榜", "热歌榜").mapIndexed { i, n -> ChartPreview(Playlist(800L + i, n, url("chart", i), 100), songs.drop(i).take(3)) },
                        picks = Picks("今日推荐", "按你的口味挑的", songs),
                    ),
                    startExpanded = expanded,
                )
            }
        }
        // Let the images arrive and fade in.
        repeat(6) {
            compose.mainClock.advanceTimeBy(500)
            Thread.sleep(30)
        }
    }

    private fun shot(page: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/covers/${name}_$page.png")

    @Test fun home() {
        app()
        shot("home")
    }

    @Test fun vinyl() {
        app(PlayerSkin.VINYL, expanded = true)
        shot("vinyl")
    }

    @Test fun cover() {
        app(PlayerSkin.COVER, expanded = true)
        shot("cover")
    }

    @Test fun playlist() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    io.lanyin.player.ui.design.Backdrop(androidx.compose.ui.Modifier.fillMaxSize())
                    io.lanyin.player.ui.music.SongListScreen(
                        "深夜写代码", "潮汐里的鲸", io.lanyin.player.ui.music.ListHeader.Cover(url("list", 0), 7),
                        io.lanyin.player.ui.music.Load.Ready(songs), playingId = 102, isPlaying = true,
                        onBack = {}, onPlay = { _, _ -> }, onRetry = {},
                    )
                }
            }
        }
        repeat(6) {
            compose.mainClock.advanceTimeBy(500)
            Thread.sleep(30)
        }
        shot("playlist")
    }
}
