package io.lanyin.player.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.core.model.Album
import io.lanyin.player.core.model.Artist
import io.lanyin.player.core.model.Comment
import io.lanyin.player.core.model.CommentPage
import io.lanyin.player.core.model.CommentUser
import io.lanyin.player.core.model.QuotedComment
import io.lanyin.player.core.model.Song
import io.lanyin.player.ui.comments.CommentsController
import io.lanyin.player.ui.comments.CommentsPage
import io.lanyin.player.ui.design.Backdrop
import io.lanyin.player.ui.music.QueueSheet
import io.lanyin.player.ui.player.DemoPlayerState
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The comments page and the play queue in every theme. Images land in build/outputs/roborazzi/pages. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h850dp-port-xhdpi")
class PagesSweepTest(private val theme: AppTheme, private val mode: ThemeMode) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> = AppTheme.entries.map { theme ->
            arrayOf<Any>(theme, if (theme.darkOnly) ThemeMode.DARK else ThemeMode.LIGHT)
        }
    }

    @get:Rule val compose = createComposeRule()

    private val now = 1_760_000_000_000L
    private val song = Song(100, "潮水退去以后", listOf(Artist(1, "澜音")), Album(1, "潮汐", null), 200_000)

    private fun samplePage(): CommentPage {
        fun user(id: Long, name: String) = CommentUser(id, name, null)
        return CommentPage(
            listOf(
                Comment(1, user(1, "潮汐里的鲸"), "副歌一出来，整个人就安静下来了。像是有人把海风装进了耳机里。", now - 3 * 60_000, 32_145, true, location = "浙江"),
                Comment(2, user(2, "北岸"), "高三晚自习偷偷听的歌，现在下班路上还在听。", now - 5 * 3_600_000, 2_870, false, location = "广东"),
                Comment(3, user(3, "城南旧事"), "同，每次听都想起那年夏天。", now - 26 * 3_600_000, 96, false, replyTo = QuotedComment("北岸", "高三晚自习偷偷听的歌，现在下班路上还在听。"), location = "上海"),
                Comment(4, user(4, "Harbor Lights"), "Found this through a friend, the bridge at 2:40 is unreal.", now - 40L * 86_400_000, 12, false),
            ),
            total = 12_480, hasMore = true, cursor = null,
        )
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode) {
                Box(Modifier.fillMaxSize()) {
                    Backdrop(Modifier.fillMaxSize())
                    content()
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/pages/${theme.name.lowercase()}_$name.png")
    }

    @Test fun comments() = shot("comments") {
        val scope = rememberCoroutineScope()
        val controller = remember { CommentsController(100, null, scope, me = null).apply { preview(samplePage()) } }
        CommentsPage(song, onBack = {}, controller = controller, now = now)
    }

    @Test fun queue() = shot("queue") {
        val player = remember { DemoPlayerState().apply { togglePlay() } }
        androidx.compose.foundation.layout.Column { QueueSheet(player) }
    }
}
