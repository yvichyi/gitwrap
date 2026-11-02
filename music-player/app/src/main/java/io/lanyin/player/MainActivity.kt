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
    /** Times every frame for the diagnostics, on a thread of its own so measuring costs the UI nothing. */
    private val frameThread by lazy { android.os.HandlerThread("frame-metrics").apply { start() } }
    private val frames = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        // The first frame after the window appears includes setting it up; it isn't a stutter.
        if (metrics.getMetric(android.view.FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        val total = metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION) / 1_000_000f
        val budget = if (android.os.Build.VERSION.SDK_INT >= 31) {
            metrics.getMetric(android.view.FrameMetrics.DEADLINE) / 1_000_000f
        } else {
            refreshBudgetMs
        }
        (application as LanyinApplication).container.diagnostics.frame(total, budget.takeIf { it > 0f } ?: refreshBudgetMs)
    }

    @Volatile
    private var refreshBudgetMs = 16.7f

    override fun onResume() {
        super.onResume()
        @Suppress("DEPRECATION")
        val rate = (if (android.os.Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay)?.refreshRate ?: 60f
        refreshBudgetMs = 1000f / rate.coerceAtLeast(30f)
        window.addOnFrameMetricsAvailableListener(frames, android.os.Handler(frameThread.looper))
    }

    override fun onPause() {
        runCatching { window.removeOnFrameMetricsAvailableListener(frames) }
        super.onPause()
    }

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
                container.diagnostics.theme = s.theme.label + if (s.reduceMotion) "（减少动效）" else ""
                CompositionLocalProvider(
                    LocalOrientationController provides orientation,
                    LocalAppServices provides container.services,
                    LocalReduceMotion provides (s.reduceMotion || systemAnimationsOff()),
                    LocalHapticsOn provides s.haptics,
                    io.lanyin.player.ui.design.LocalCoverMedium provides s.coverMedium,
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
