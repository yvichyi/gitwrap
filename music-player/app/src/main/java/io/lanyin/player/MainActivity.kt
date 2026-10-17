package io.lanyin.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.lanyin.player.ui.LanyinApp
import io.lanyin.player.ui.LocalAppServices
import io.lanyin.player.ui.LocalThemeReveal
import io.lanyin.player.ui.ThemeReveal
import io.lanyin.player.ui.ThemeRevealHost
import io.lanyin.player.ui.design.ActivityOrientationController
import io.lanyin.player.ui.design.LocalHapticsOn
import io.lanyin.player.ui.design.LocalOrientationController
import io.lanyin.player.ui.design.LocalReduceMotion
import io.lanyin.player.ui.design.systemAnimationsOff
import io.lanyin.player.ui.theme.LanyinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LanyinApplication).container
        setContent {
            // Null until DataStore's first read so the wrong theme never flashes.
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val orientation = remember { ActivityOrientationController(this) }
            val reveal = remember { ThemeReveal() }
            settings?.let { s ->
                CompositionLocalProvider(
                    LocalOrientationController provides orientation,
                    LocalAppServices provides container.services,
                    LocalReduceMotion provides (s.reduceMotion || systemAnimationsOff()),
                    LocalHapticsOn provides s.haptics,
                    LocalThemeReveal provides reveal,
                ) {
                    ThemeRevealHost(reveal, key = s.theme to s.themeMode) {
                        LanyinTheme(theme = s.theme, mode = s.themeMode) {
                            LanyinApp(settings = s, settingsRepository = container.settings)
                        }
                    }
                }
            }
        }
    }
}
