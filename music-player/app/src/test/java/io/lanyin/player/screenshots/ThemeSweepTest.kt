package io.lanyin.player.screenshots

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.UiSettings
import io.lanyin.player.ui.LanyinApp
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
 * Every page of the real app shell in every theme and mode, so a layout slip in one theme can't
 * hide behind the themes that were reviewed by hand. Images land in build/outputs/roborazzi/sweep.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h850dp-port-xhdpi")
open class ThemeSweepTest(private val theme: AppTheme, private val mode: ThemeMode) {

    /** Output folder under build/outputs/roborazzi. */
    protected open val folder = "sweep"

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
    }

    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val tag get() = "${theme.name.lowercase()}_${mode.id}"

    private fun app(startExpanded: Boolean = false) {
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
                LanyinApp(UiSettings(theme = theme, themeMode = mode), repo, player = player, startExpanded = startExpanded)
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
    }

    private fun shot(page: String) {
        compose.mainClock.advanceTimeBy(1_200)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$folder/${tag}_$page.png")
    }

    private fun open(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.onAllNodes(matcher, useUnmergedTree = true)[0].performClick()
    }

    @Test fun home() {
        app()
        shot("1home")
    }

    @Test fun search() {
        app()
        open(hasText("搜索"))
        shot("2search")
    }

    @Test fun library() {
        app()
        open(hasText("我的"))
        shot("3library")
    }

    @Test fun settings() {
        app()
        open(hasContentDescription("设置"))
        shot("4settings")
    }

    @Test fun login() {
        app()
        open(hasText("我的"))
        compose.mainClock.advanceTimeBy(600)
        open(hasText("登录"))
        shot("5login")
    }

    @Test fun player() {
        app(startExpanded = true)
        shot("6player")
    }

    @Config(qualifiers = "w850dp-h392dp-land-xhdpi")
    @Test fun playerLandscape() {
        app(startExpanded = true)
        shot("7land")
    }
}

/**
 * The same sweep on a narrower phone with the system font size turned up, as many phones ship:
 * fixed-height boxes that fit at 1.0 clip their text here.
 */
@Config(sdk = [35], qualifiers = "w360dp-h780dp-port-xhdpi", fontScale = 1.3f)
class LargeTextSweepTest(theme: AppTheme, mode: ThemeMode) : ThemeSweepTest(theme, mode) {
    override val folder = "sweep-large"
}
