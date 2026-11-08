package io.lanyin.player.screenshots

import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The launcher icon as launchers show it: the paper layer under the mark, cut to a circle or a
 * rounded square, at a few sizes, on a light and a dark wallpaper, and tinted as a themed icon.
 * The image lands in build/outputs/roborazzi/icon.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w420dp-h520dp-port-xxhdpi")
class LauncherIconTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `the icon is adaptive, with a themed layer`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val icon = context.getDrawable(R.mipmap.ic_launcher)
        assertTrue(icon is AdaptiveIconDrawable)
        assertNotNull("themed icons need a monochrome layer", (icon as AdaptiveIconDrawable).monochrome)
    }

    @Test
    fun sheet() {
        compose.setContent {
            Column(Modifier.fillMaxSize().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(180.dp, RoundedCornerShape(30))
                    Icon(180.dp, CircleShape)
                }
                Wall(Brush.linearGradient(listOf(Color(0xFFDFE8EE), Color(0xFFC9D6C3)))) {
                    Icon(64.dp, CircleShape); Icon(48.dp, CircleShape); Icon(64.dp, RoundedCornerShape(30)); Icon(48.dp, RoundedCornerShape(30))
                }
                Wall(Brush.linearGradient(listOf(Color(0xFF1D2433), Color(0xFF3A2F3F)))) {
                    Icon(64.dp, CircleShape); Icon(48.dp, CircleShape); Icon(64.dp, RoundedCornerShape(30)); Icon(48.dp, RoundedCornerShape(30))
                }
                Wall(Brush.linearGradient(listOf(Color(0xFF40513F), Color(0xFF2A3A46)))) {
                    Themed(64.dp, Color(0xFF1F2A22), Color(0xFFBFE9C8)); Themed(64.dp, Color(0xFFD9E7FF), Color(0xFF16325C))
                    Themed(48.dp, Color(0xFF1F2A22), Color(0xFFBFE9C8)); Themed(48.dp, Color(0xFFD9E7FF), Color(0xFF16325C))
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/icon/launcher.png")
    }

    @Composable
    private fun Wall(brush: Brush, content: @Composable () -> Unit) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(brush).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }

    /** Both layers at 108/72 of the shown size, cut to the launcher's mask. */
    @Composable
    private fun Icon(size: Dp, shape: Shape) {
        Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(size * 1.5f))
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(size * 1.5f))
        }
    }

    /** A themed icon: the monochrome layer tinted on the wallpaper's tone. */
    @Composable
    private fun Themed(size: Dp, bg: Color, fg: Color) {
        Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(size * 1.5f), colorFilter = ColorFilter.tint(fg))
        }
    }
}
