package io.lanyin.player.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.core.model.Album
import io.lanyin.player.core.model.Artist
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.core.model.Song
import io.lanyin.player.data.account.AccountState
import io.lanyin.player.core.model.UserProfile
import io.lanyin.player.data.library.ChartPreview
import io.lanyin.player.data.library.Picks
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.UiSettings
import io.lanyin.player.ui.LanyinApp
import io.lanyin.player.ui.home.HomePreview
import io.lanyin.player.ui.player.DemoPlayerState
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Each theme's home page from top to bottom, on a tall screen, signed in with its shelves loaded
 * and signed out, for reviewing the layouts. Images land in build/outputs/roborazzi/layout.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h2300dp-port-xhdpi")
class HomeLayoutTest(private val theme: AppTheme, private val mode: ThemeMode) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> = AppTheme.entries.flatMap { theme ->
            val modes = when {
                theme.darkOnly -> listOf(ThemeMode.DARK)
                theme.lightOnly -> listOf(ThemeMode.LIGHT)
                else -> listOf(ThemeMode.LIGHT, ThemeMode.DARK)
            }
            modes.map { arrayOf<Any>(theme, it) }
        }

        private val songs = listOf(
            Triple("潮水退去以后", "澜音", "潮汐"), Triple("夜航船", "北岸乐队", "夜航"), Triple("海边的风", "林间 / 小舟", "夏至"),
            Triple("落日磁带", "澜音乐队", "黄昏"), Triple("Midnight Ferry", "Harbor Lights", "Night Lines"), Triple("月光下的鲸", "潮汐里的鲸", "深海"),
            Triple("雨停以后", "北岸乐队", "夜航"), Triple("慢慢来", "林间", "夏至"), Triple("一首名字特别长、用来看看排版会不会被挤坏的歌", "很多很多位合作歌手", "排版测试"),
        ).mapIndexed { i, (name, artist, album) ->
            Song(
                id = 100L + i, name = name,
                artists = artist.split(" / ").mapIndexed { j, a -> Artist(j.toLong(), a) },
                album = Album(i.toLong(), album, null), durationMs = 200_000L + i * 7_000,
            )
        }

        private val playlists = listOf("深夜写代码", "雨天的歌", "通勤路上", "周末慢慢听", "收藏：城市民谣精选", "一个名字很长很长的歌单，看看会不会挤").mapIndexed { i, name ->
            Playlist(id = 901L + i, name = name, coverUrl = null, trackCount = 12 + i * 37, creatorId = 1, creatorName = "潮汐里的鲸")
        }

        private fun preview(signedIn: Boolean) = HomePreview(
            recommended = playlists,
            charts = listOf("飙升榜" to "刚刚更新", "新歌榜" to "刚刚更新", "原创榜" to "每周四更新", "热歌榜" to "每周四更新").mapIndexed { i, (name, note) ->
                ChartPreview(Playlist(800L + i, name, null, 100, updateNote = note), songs.drop(i).take(3))
            },
            picks = if (signedIn) Picks("今日推荐", "按你的口味挑的", songs.take(6)) else Picks("热歌榜", "大家都在听", songs.take(6)),
        )
    }

    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private fun shot(signedIn: Boolean) {
        val repo = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Unconfined)) { File(tmp.root, "s.preferences_pb") },
        )
        val player = DemoPlayerState().apply {
            togglePlay()
            seekTo(14_000)
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode) {
                LanyinApp(
                    UiSettings(theme = theme, themeMode = mode), repo, player = player,
                    accountOverride = if (signedIn) AccountState.LoggedIn(UserProfile(1, "潮汐里的鲸", null, 11)) else null,
                    homePreview = preview(signedIn),
                )
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/layout/${theme.name.lowercase()}_${mode.id}_${if (signedIn) "in" else "out"}.png")
    }

    @Test fun signedIn() = shot(signedIn = true)
    @Test fun signedOut() = shot(signedIn = false)

    /** 我的, signed in, with a shelf of playlists. */
    @Test fun library() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode) {
                androidx.compose.foundation.layout.Box {
                    io.lanyin.player.ui.design.Backdrop(androidx.compose.ui.Modifier.matchParentSize())
                    io.lanyin.player.ui.library.LibraryScreen(
                        onLogin = {},
                        account = AccountState.LoggedIn(UserProfile(1, "潮汐里的鲸", null, 11)),
                        previewPlaylists = listOf(Playlist(900, "我喜欢的音乐", null, 214, creatorId = 1, specialType = 5)) + playlists,
                        previewDownloads = 23,
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/layout/${theme.name.lowercase()}_${mode.id}_lib.png")
    }
}
