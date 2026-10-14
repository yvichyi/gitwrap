package io.lanyin.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.lanyin.player.ui.LanyinApp
import io.lanyin.player.ui.theme.LanyinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LanyinApplication).container
        setContent {
            // Null until DataStore's first read so the wrong skin never flashes.
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            settings?.let { s ->
                LanyinTheme(skin = s.appSkin, mode = s.themeMode) {
                    LanyinApp(settings = s, settingsRepository = container.settings)
                }
            }
        }
    }
}
