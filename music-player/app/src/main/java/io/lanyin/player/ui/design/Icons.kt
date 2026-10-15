package io.lanyin.player.ui.design

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.Lanyin

/**
 * One line-icon family drawn on a 24-unit grid, so every icon shares the theme's stroke weight
 * (hairline in 墨, chunky in 暮) instead of mixing in filled stock icons.
 */
enum class LineIcon(internal val strokes: String, internal val fills: String = "") {
    Home("M4 10.5L12 4l8 6.5V20h-5.5v-6h-5v6H4z"),
    Search("M17.5 11a6.5 6.5 0 1 1-13 0a6.5 6.5 0 1 1 13 0zM16 16l4 4"),
    Library("M5 5v14M9.5 5v14M13.5 5.6l5 13.6"),
    Note("M9.5 17V5.5l9-2V15M9.5 17a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0zM18.5 15a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z"),
    Sliders("M4 8h3M11 8h9M4 16h9M17 16h3M11 8a2 2 0 1 1-4 0a2 2 0 1 1 4 0zM17 16a2 2 0 1 1-4 0a2 2 0 1 1 4 0z"),
    User("M15.5 8a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0zM5 20c0-3.6 3.1-6 7-6s7 2.4 7 6"),
    Radio("M8.5 15.5a5 5 0 0 1 0-7M15.5 8.5a5 5 0 0 1 0 7M5.6 18.4a9 9 0 0 1 0-12.8M18.4 5.6a9 9 0 0 1 0 12.8", fills = "M13.4 12a1.4 1.4 0 1 1-2.8 0a1.4 1.4 0 1 1 2.8 0z"),
    Heart("M12 20s-8-5-8-10.5C4 7 6 5 8.3 5c1.7 0 3 1 3.7 2.2C12.7 6 14 5 15.7 5C18 5 20 7 20 9.5C20 15 12 20 12 20z"),
    HeartFilled("", fills = "M12 20s-8-5-8-10.5C4 7 6 5 8.3 5c1.7 0 3 1 3.7 2.2C12.7 6 14 5 15.7 5C18 5 20 7 20 9.5C20 15 12 20 12 20z"),
    Chart("M5 20v-6M10 20V5M15 20v-9M20 20v-4"),
    ChevronDown("M6 9l6 6l6-6"),
    Back("M15 5l-7 7l7 7"),
    Forward("M9 5l7 7l-7 7"),
    More("", fills = "M7.4 12a1.4 1.4 0 1 1-2.8 0a1.4 1.4 0 1 1 2.8 0zM13.4 12a1.4 1.4 0 1 1-2.8 0a1.4 1.4 0 1 1 2.8 0zM19.4 12a1.4 1.4 0 1 1-2.8 0a1.4 1.4 0 1 1 2.8 0z"),
    Rotate("M9 7h6v10H9zM3.5 10A9 9 0 0 1 10 3.5M7 3l3 .5l-.5 3M20.5 14A9 9 0 0 1 14 20.5M17 21l-3-.5l.5-3"),
    Lyrics("M4 6h10M4 11h10M4 16h6M18.5 15V5l2.5 1.2M18.5 15a2 2 0 1 1-4 0a2 2 0 1 1 4 0z"),
    Queue("M4 7h12M4 12h12M4 17h8M17 14.5l4 2.5l-4 2.5z"),
    Repeat("M5 11V9.5A3.5 3.5 0 0 1 8.5 6H19M16 3l3 3l-3 3M19 13v1.5a3.5 3.5 0 0 1-3.5 3.5H5M8 21l-3-3l3-3"),
    RepeatOne("M5 11V9.5A3.5 3.5 0 0 1 8.5 6H19M16 3l3 3l-3 3M19 13v1.5a3.5 3.5 0 0 1-3.5 3.5H5M8 21l-3-3l3-3M11.5 10.5l1-.5v4"),
    Shuffle("M4 7h3c4 0 6 10 10 10h3M4 17h3c1.6 0 2.8-1.2 3.8-3M13.2 10c1-1.8 2.2-3 3.8-3h3M17 4l3 3l-3 3M17 14l3 3l-3 3"),
    Previous("M6.5 5v14M18 5.5L9 12l9 6.5z"),
    Next("M17.5 5v14M6 5.5L15 12l-9 6.5z"),
    Lock("M7.5 11V8a4.5 4.5 0 0 1 9 0v3M5.5 11h13v9h-13z"),
    History("M4 12a8 8 0 1 0 2.3-5.7M4 4v4.5h4.5M12 8v4l3 2"),
    Download("M12 4v11M7.5 10.5L12 15l4.5-4.5M5 20h14"),
    Cloud("M7 18h10a4 4 0 0 0 .4-8A5.5 5.5 0 0 0 6.6 9.6A4.2 4.2 0 0 0 7 18z"),
    Check("M5 12.5l4.5 4.5L19 7"),
    Plus("M12 5v14M5 12h14"),
    Comment("M5 5h14a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-8l-4.5 3.5V16H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM8.5 9.5h7M8.5 12.5h4"),
    Trash("M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1-13M10.5 11v5.5M13.5 11v5.5"),
    Edit("M14.5 5.5l4 4L9 19H5v-4zM12.5 7.5l4 4"),
    Close("M6.5 6.5l11 11M17.5 6.5l-11 11"),
    Send("M20 4L3.5 10.5l7 3l3 7zM10.5 13.5L20 4"),
}

private val cache = HashMap<Pair<LineIcon, Float>, ImageVector>()

fun LineIcon.vector(stroke: Float): ImageVector = cache.getOrPut(this to stroke) {
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            if (strokes.isNotEmpty()) {
                addPath(
                    pathData = addPathNodes(strokes),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            if (fills.isNotEmpty()) addPath(pathData = addPathNodes(fills), fill = SolidColor(Color.Black))
        }
        .build()
}

/** A [LineIcon] at the active theme's stroke weight. */
@Composable
fun ThemedIcon(
    icon: LineIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Icon(icon.vector(Lanyin.style.iconStroke), contentDescription, modifier.size(size), tint = tint)
}
