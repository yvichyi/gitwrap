package io.lanyin.player.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.UiSettings
import io.lanyin.player.ui.home.HomeScreen
import io.lanyin.player.ui.player.DemoPlayerState
import io.lanyin.player.ui.player.PlayerScreen
import io.lanyin.player.ui.settings.SettingsScreen
import io.lanyin.player.ui.theme.AppSkin
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.PlayerSkin
import io.lanyin.player.ui.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders key screens on the JVM so the UI can be reviewed without an emulator.
 * Record with `./gradlew recordRoborazziDebug`; images land in build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private fun capture(name: String, skin: AppSkin, mode: ThemeMode, content: @Composable () -> Unit) {
        // Spinning records never go idle, so drive the clock by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent { LanyinTheme(skin = skin, mode = mode, content = content) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun playingDemo() = DemoPlayerState().apply {
        togglePlay()
        seekTo(9_000)
    }

    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    @Test fun landscape_vinyl_lan_dark() =
        capture("landscape_vinyl_lan_dark", AppSkin.LAN, ThemeMode.DARK) { PlayerScreen(playingDemo(), PlayerSkin.VINYL) {} }

    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    @Test fun landscape_cover_ying_light() =
        capture("landscape_cover_ying_light", AppSkin.YING, ThemeMode.LIGHT) { PlayerScreen(playingDemo(), PlayerSkin.COVER) {} }

    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    @Test fun landscape_lyrics_mo_dark() =
        capture("landscape_lyrics_mo_dark", AppSkin.MO, ThemeMode.DARK) { PlayerScreen(playingDemo(), PlayerSkin.LYRICS) {} }

    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    @Test fun landscape_cassette_mu_light() =
        capture("landscape_cassette_mu_light", AppSkin.MU, ThemeMode.LIGHT) { PlayerScreen(playingDemo(), PlayerSkin.CASSETTE) {} }

    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    @Test fun landscape_vinyl_paused_zhu_light() =
        capture("landscape_vinyl_paused_zhu_light", AppSkin.ZHU, ThemeMode.LIGHT) {
            PlayerScreen(DemoPlayerState(), PlayerSkin.VINYL) {}
        }

    @Config(qualifiers = "w380dp-h800dp-port-xhdpi")
    @Test fun portrait_vinyl_lan_dark() =
        capture("portrait_vinyl_lan_dark", AppSkin.LAN, ThemeMode.DARK) { PlayerScreen(playingDemo(), PlayerSkin.VINYL) {} }

    @Config(qualifiers = "w380dp-h800dp-port-xhdpi")
    @Test fun portrait_home_lan_light() =
        capture("portrait_home_lan_light", AppSkin.LAN, ThemeMode.LIGHT) { HomeScreen(onOpenSettings = {}, onLogin = {}) }

    @Config(qualifiers = "w380dp-h800dp-port-xhdpi")
    @Test fun portrait_settings_lan_dark() {
        val repo = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Unconfined)) {
                File(tmp.root, "settings.preferences_pb")
            },
        )
        capture("portrait_settings_lan_dark", AppSkin.LAN, ThemeMode.DARK) {
            SettingsScreen(UiSettings(themeMode = ThemeMode.DARK), repo, onBack = {})
        }
    }
}
