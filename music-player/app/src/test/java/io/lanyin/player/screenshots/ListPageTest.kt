package io.lanyin.player.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.core.model.Album
import io.lanyin.player.core.model.Artist
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.design.Backdrop
import io.lanyin.player.ui.music.ListHeader
import io.lanyin.player.ui.music.Load
import io.lanyin.player.ui.music.SongListScreen
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A song list's page (a playlist, and today's picks) in each theme, whose head is drawn in the
 * theme's own medium. Images land in build/outputs/roborazzi/lists.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h850dp-port-xhdpi")
class ListPageTest(private val theme: AppTheme, private val mode: ThemeMode) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> = AppTheme.entries.map { theme ->
            arrayOf<Any>(theme, if (theme.darkOnly) ThemeMode.DARK else ThemeMode.LIGHT)
        }
    }

    @get:Rule val compose = createComposeRule()

    private val songs = listOf(
        "潮水退去以后" to "澜音", "夜航船" to "北岸乐队", "海边的风" to "林间 / 小舟", "落日磁带" to "澜音乐队",
        "Midnight Ferry" to "Harbor Lights", "月光下的鲸" to "潮汐里的鲸", "雨停以后" to "北岸乐队", "慢慢来" to "林间",
    ).mapIndexed { i, (name, artist) ->
        Song(
            id = 100L + i, name = name, artists = artist.split(" / ").mapIndexed { j, a -> Artist(j.toLong(), a) },
            album = Album(i.toLong(), "夜航", null), durationMs = 200_000L + i * 7_000, fee = if (i % 4 == 1) 1 else 8,
        )
    }

    private fun shot(name: String, title: String, subtitle: String?, header: ListHeader) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode) {
                // On the page's backdrop, as in the app.
                Box(Modifier.fillMaxSize()) {
                    Backdrop(Modifier.fillMaxSize())
                    SongListScreen(
                        title, subtitle, header, Load.Ready(songs), playingId = 102, isPlaying = true,
                        onBack = {}, onPlay = { _, _ -> }, onRetry = {}, likedIds = setOf(100, 105),
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lists/${theme.name.lowercase()}_$name.png")
    }

    @Test fun playlist() = shot("playlist", "深夜写代码", "潮汐里的鲸 · 写给夜里还亮着的屏幕", ListHeader.Cover(null, 7))

    @Test fun daily() = shot("daily", "每日推荐", "根据你的口味生成，每天 6:00 更新", ListHeader.Day(16, "10月 · 周五"))
}
