package io.lanyin.player.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.UiSettings
import io.lanyin.player.ui.LanyinApp
import io.lanyin.player.ui.login.LoginScreen
import io.lanyin.player.ui.player.DemoPlayerState
import io.lanyin.player.ui.player.PlayerScreen
import io.lanyin.player.ui.player.rememberSpinState
import io.lanyin.player.ui.theme.AppTheme
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
 * Renders the real app on the JVM so the UI can be reviewed without an emulator.
 * Record with `./gradlew recordRoborazziDebug`; images land in build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PORTRAIT)
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private fun repo() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Unconfined)) {
            File(tmp.root, "settings.preferences_pb")
        },
    )

    private fun capture(name: String, theme: AppTheme, mode: ThemeMode, settle: Long = 1_500, content: @Composable () -> Unit) {
        // Spinning records and drifting backdrops never go idle, so drive the clock by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode) {
                // Pages are transparent and sit on the app's backdrop, as in LanyinApp.
                androidx.compose.foundation.layout.Box {
                    io.lanyin.player.ui.design.Backdrop(androidx.compose.ui.Modifier.matchParentSize())
                    content()
                }
            }
        }
        compose.mainClock.advanceTimeBy(settle)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun playingAt(ms: Long) = DemoPlayerState().apply {
        togglePlay()
        seekTo(ms)
    }

    @Composable
    private fun Player(skin: PlayerSkin, state: DemoPlayerState = playingAt(14_000)) {
        PlayerScreen(state, skin, rememberSpinState(state.isPlaying), keepScreenOnInLandscape = true, onCollapse = {})
    }

    private fun app(theme: AppTheme, mode: ThemeMode, skin: PlayerSkin = PlayerSkin.VINYL) {
        val repo = repo()
        capture("home_${theme.name.lowercase()}_${mode.id}", theme, mode) {
            LanyinApp(UiSettings(theme = theme, themeMode = mode, playerSkin = skin), repo, player = playingAt(14_000))
        }
    }

    // 墨, the flagship, page by page in light and dark.
    @Test fun ink_home_light() = app(AppTheme.INK, ThemeMode.LIGHT)
    @Test fun ink_search_light() = capture("ink_search_light", AppTheme.INK, ThemeMode.LIGHT) { io.lanyin.player.ui.search.SearchScreen() }
    @Test fun ink_library_light() = capture("ink_library_light", AppTheme.INK, ThemeMode.LIGHT) { io.lanyin.player.ui.library.LibraryScreen(onLogin = {}) }
    @Test fun ink_library_dark() = capture("ink_library_dark", AppTheme.INK, ThemeMode.DARK) { io.lanyin.player.ui.library.LibraryScreen(onLogin = {}) }
    @Test fun ink_login_light() = capture("ink_login_light", AppTheme.INK, ThemeMode.LIGHT) { LoginScreen(onBack = {}) }
    @Test fun ink_settings_light() {
        val repo = repo()
        capture("ink_settings_light", AppTheme.INK, ThemeMode.LIGHT) {
            io.lanyin.player.ui.settings.SettingsScreen(UiSettings(theme = AppTheme.INK, themeMode = ThemeMode.LIGHT), repo, onBack = {})
        }
    }
    @Config(qualifiers = "w392dp-h1500dp-port-xhdpi")
    @Test fun ink_settings_full() {
        val repo = repo()
        capture("ink_settings_full", AppTheme.INK, ThemeMode.LIGHT) {
            io.lanyin.player.ui.settings.SettingsScreen(UiSettings(theme = AppTheme.INK, themeMode = ThemeMode.LIGHT), repo, onBack = {})
        }
    }
    @Test fun ink_port_vinyl_light() = capture("ink_port_vinyl_light", AppTheme.INK, ThemeMode.LIGHT) { Player(PlayerSkin.VINYL) }
    @Test fun ink_port_cover_dark() = capture("ink_port_cover_dark", AppTheme.INK, ThemeMode.DARK) { Player(PlayerSkin.COVER) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun ink_land_vinyl_dark() = capture("ink_land_vinyl_dark", AppTheme.INK, ThemeMode.DARK) { Player(PlayerSkin.VINYL) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun ink_land_lyrics_light() = capture("ink_land_lyrics_light", AppTheme.INK, ThemeMode.LIGHT) { Player(PlayerSkin.LYRICS) }

    @Test fun ink_transition_light() {
        val repo = repo()
        capture("ink_transition_0", AppTheme.INK, ThemeMode.LIGHT) {
            LanyinApp(UiSettings(theme = AppTheme.INK, themeMode = ThemeMode.LIGHT), repo, player = playingAt(14_000))
        }
        compose.onNodeWithTag("miniPlayer").performClick()
        compose.mainClock.advanceTimeBy(140)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/ink_transition_1.png")
    }

    // 暮, page by page.
    @Test fun dusk_home_dark() = app(AppTheme.DUSK, ThemeMode.DARK)
    @Test fun dusk_search_light() = capture("dusk_search_light", AppTheme.DUSK, ThemeMode.LIGHT) { io.lanyin.player.ui.search.SearchScreen() }
    @Test fun dusk_library_light() = capture("dusk_library_light", AppTheme.DUSK, ThemeMode.LIGHT) { io.lanyin.player.ui.library.LibraryScreen(onLogin = {}) }
    @Test fun dusk_login_dark() = capture("dusk_login_dark", AppTheme.DUSK, ThemeMode.DARK) { LoginScreen(onBack = {}) }
    @Test fun dusk_settings_light() {
        val repo = repo()
        capture("dusk_settings_light", AppTheme.DUSK, ThemeMode.LIGHT) {
            io.lanyin.player.ui.settings.SettingsScreen(UiSettings(theme = AppTheme.DUSK, themeMode = ThemeMode.LIGHT), repo, onBack = {})
        }
    }
    @Test fun dusk_port_cassette_light() = capture("dusk_port_cassette_light", AppTheme.DUSK, ThemeMode.LIGHT) { Player(PlayerSkin.CASSETTE) }
    @Test fun dusk_port_vinyl_dark() = capture("dusk_port_vinyl_dark", AppTheme.DUSK, ThemeMode.DARK) { Player(PlayerSkin.VINYL) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun dusk_land_cover_dark() = capture("dusk_land_cover_dark", AppTheme.DUSK, ThemeMode.DARK) { Player(PlayerSkin.COVER) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun ink_land_vinyl_idle() {
        // After five idle seconds the chrome folds away and the stage takes the room.
        capture("ink_land_vinyl_idle", AppTheme.INK, ThemeMode.LIGHT, settle = 6_500) { Player(PlayerSkin.VINYL) }
    }

    @Config(qualifiers = LANDSCAPE)
    @Test fun ink_land_vinyl_large_lyrics() = capture("ink_land_vinyl_large_lyrics", AppTheme.INK, ThemeMode.LIGHT) {
        PlayerScreen(playingAt(14_000), PlayerSkin.VINYL, rememberSpinState(true), keepScreenOnInLandscape = true, onCollapse = {}, lyricScale = io.lanyin.player.ui.theme.LyricSize.LARGE.scale)
    }

    // Home, inside the real shell, for every theme.
    @Test fun home_tide_light() = app(AppTheme.TIDE, ThemeMode.LIGHT)
    @Test fun home_tide_dark() = app(AppTheme.TIDE, ThemeMode.DARK)
    @Test fun home_ink_light() = app(AppTheme.INK, ThemeMode.LIGHT)
    @Test fun home_ink_dark() = app(AppTheme.INK, ThemeMode.DARK)
    @Test fun home_sakura_light() = app(AppTheme.SAKURA, ThemeMode.LIGHT, PlayerSkin.COVER)
    @Test fun home_neon() = app(AppTheme.NEON, ThemeMode.DARK)
    @Test fun home_dusk_light() = app(AppTheme.DUSK, ThemeMode.LIGHT, PlayerSkin.CASSETTE)

    // The landscape stage for every theme.
    @Config(qualifiers = LANDSCAPE)
    @Test fun land_tide_dark_vinyl() = capture("land_tide_dark_vinyl", AppTheme.TIDE, ThemeMode.DARK) { Player(PlayerSkin.VINYL) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun land_ink_light_vinyl() = capture("land_ink_light_vinyl", AppTheme.INK, ThemeMode.LIGHT) { Player(PlayerSkin.VINYL) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun land_sakura_light_cover() = capture("land_sakura_light_cover", AppTheme.SAKURA, ThemeMode.LIGHT) { Player(PlayerSkin.COVER) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun land_neon_lyrics() = capture("land_neon_lyrics", AppTheme.NEON, ThemeMode.DARK) { Player(PlayerSkin.LYRICS) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun land_dusk_light_cassette() = capture("land_dusk_light_cassette", AppTheme.DUSK, ThemeMode.LIGHT) { Player(PlayerSkin.CASSETTE) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun land_tide_light_paused() =
        capture("land_tide_light_paused", AppTheme.TIDE, ThemeMode.LIGHT) { Player(PlayerSkin.VINYL, DemoPlayerState()) }

    // Portrait player.
    @Test fun port_tide_dark_vinyl() = capture("port_tide_dark_vinyl", AppTheme.TIDE, ThemeMode.DARK) { Player(PlayerSkin.VINYL) }
    @Test fun port_neon_vinyl() = capture("port_neon_vinyl", AppTheme.NEON, ThemeMode.DARK) { Player(PlayerSkin.VINYL) }
    @Test fun port_dusk_dark_cover() = capture("port_dusk_dark_cover", AppTheme.DUSK, ThemeMode.DARK) { Player(PlayerSkin.COVER) }
    @Test fun port_ink_dark_lyrics() = capture("port_ink_dark_lyrics", AppTheme.INK, ThemeMode.DARK) { Player(PlayerSkin.LYRICS) }

    // Other pages.
    @Test fun login_sakura_dark() = capture("login_sakura_dark", AppTheme.SAKURA, ThemeMode.DARK) { LoginScreen(onBack = {}) }

    @Test fun settings_tide_light() {
        val repo = repo()
        capture("settings_tide_light", AppTheme.TIDE, ThemeMode.LIGHT) {
            io.lanyin.player.ui.settings.SettingsScreen(UiSettings(), repo, onBack = {})
        }
    }

    @Test fun search_dusk_dark() = capture("search_dusk_dark", AppTheme.DUSK, ThemeMode.DARK) {
        io.lanyin.player.ui.search.SearchScreen()
    }

    @Test fun library_neon() = capture("library_neon", AppTheme.NEON, ThemeMode.DARK) {
        io.lanyin.player.ui.library.LibraryScreen(onLogin = {})
    }

    // The mini player morphing into the full player, caught mid-flight.
    @Test fun transition_tide_dark() {
        val repo = repo()
        capture("transition_0_tide_dark", AppTheme.TIDE, ThemeMode.DARK) {
            LanyinApp(UiSettings(theme = AppTheme.TIDE, themeMode = ThemeMode.DARK), repo, player = playingAt(14_000))
        }
        compose.onNodeWithTag("miniPlayer").performClick()
        compose.mainClock.advanceTimeBy(110)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transition_1_tide_dark.png")
        compose.mainClock.advanceTimeBy(110)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transition_2_tide_dark.png")
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transition_3_tide_dark.png")
    }
}

private const val PORTRAIT = "w392dp-h850dp-port-xhdpi"
private const val LANDSCAPE = "w850dp-h392dp-land-xhdpi"
