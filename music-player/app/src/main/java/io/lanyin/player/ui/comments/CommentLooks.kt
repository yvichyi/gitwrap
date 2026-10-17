package io.lanyin.player.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.lanyin.player.core.model.Comment
import io.lanyin.player.ui.design.Flavor
import io.lanyin.player.ui.design.IndexTag
import io.lanyin.player.ui.design.candyPlastic
import io.lanyin.player.ui.design.gameWindow
import io.lanyin.player.ui.design.neonCore
import io.lanyin.player.ui.design.retroShadow
import io.lanyin.player.ui.music.relativeTime
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.SpaceMonoFamily

/** The space inside a comment's frame. */
internal fun commentInset(kind: BackdropKind): PaddingValues = when (kind) {
    BackdropKind.Ink -> PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp)
    BackdropKind.Film, BackdropKind.Paper -> PaddingValues(vertical = 14.dp)
    else -> PaddingValues(14.dp)
}

/** What a comment stands on, the [index]th on the page; see CommentRow. */
@Composable
internal fun Modifier.commentFrame(index: Int): Modifier {
    val style = Lanyin.style
    val c = style.colors
    return when (style.backdrop) {
        // A note in the margin: a fine rule down its left.
        BackdropKind.Ink -> drawBehind { drawRect(c.content.copy(alpha = 0.22f), Offset(0f, 12.dp.toPx()), Size(1.dp.toPx(), size.height - 24.dp.toPx())) }
        BackdropKind.Neon -> background(c.surface, RoundedCornerShape(14.dp)).drawBehind {
            drawRect(Color.White.copy(alpha = 0.05f), Offset(14.dp.toPx(), 0f), Size(size.width - 28.dp.toPx(), 1.dp.toPx()))
        }
        // A postcard, set down a little crooked.
        BackdropKind.Dusk -> graphicsLayer { rotationZ = if (index % 2 == 0) -0.4f else 0.4f }
            .shadow(3.dp, RoundedCornerShape(4.dp))
            .background(if (style.dark) Color(0xFF2A2F48) else Color(0xFFFFFCF6), RoundedCornerShape(4.dp))
        BackdropKind.Grid -> retroShadow(RectangleShape, c.outline, 4.dp).background(c.surface).border(2.dp, c.outline)
        BackdropKind.Film -> this
        BackdropKind.Dot -> background(c.surfaceAlt, RoundedCornerShape(18.dp))
        // A line in the device's log, on its recessed panel.
        BackdropKind.Device -> background(c.bgDeep, RoundedCornerShape(10.dp)).drawBehind {
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (style.dark) 0.3f else 0.1f), Color.Transparent), endY = 6.dp.toPx()))
        }
        // A letter: ruled off above.
        BackdropKind.Paper -> drawBehind { drawRect(c.content, size = Size(size.width, 1.dp.toPx())) }
        BackdropKind.Game -> gameWindow(c.surface, c.content)
        BackdropKind.Acid -> candyPlastic(Flavor.all[index % Flavor.all.size], RoundedCornerShape(22.dp), rim = false)
    }
}

/** Who wrote it and when, in the theme's way of signing. */
@Composable
internal fun CommentByline(comment: Comment, now: Long, index: Int, modifier: Modifier = Modifier, quiet: Color = Lanyin.colors.muted) {
    val style = Lanyin.style
    val c = style.colors
    val name = comment.user.nickname
    val whenWhere = listOfNotNull(relativeTime(comment.timeMs, now), comment.location).joinToString(" · ")
    val nameStyle = style.type.caption.copy(fontWeight = FontWeight.Medium)
    val mono = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = style.type.label.fontSize, letterSpacing = 0.04.em)
    Column(modifier) {
        when (style.backdrop) {
            BackdropKind.Neon -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 6.dp).size(6.dp).background(neonCore(c.accent), CircleShape))
                Text(name, style = nameStyle, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BackdropKind.Dusk -> Text("From: $name", style = nameStyle.copy(fontFamily = style.display, fontStyle = FontStyle.Italic), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BackdropKind.Grid -> Row(verticalAlignment = Alignment.CenterVertically) {
                IndexTag(index + 1, Modifier.padding(end = 8.dp))
                Text(name, style = nameStyle.copy(fontWeight = FontWeight.Black), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // A screenplay: the speaker's name in capitals over the line.
            BackdropKind.Film -> Text(name.uppercase(), style = mono.copy(letterSpacing = 0.12.em), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BackdropKind.Device -> Text("> $name", style = mono, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BackdropKind.Paper -> Text("来信｜$name", style = nameStyle.copy(fontFamily = style.display, fontWeight = FontWeight.Black), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BackdropKind.Game -> Text("$name：", style = nameStyle, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BackdropKind.Acid -> Text("✦ $name", style = nameStyle.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else -> Text(name, style = nameStyle, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            when (style.backdrop) {
                BackdropKind.Film -> "（$whenWhere）"
                BackdropKind.Device -> "[$whenWhere]"
                else -> whenWhere
            },
            style = if (style.backdrop == BackdropKind.Device) mono.copy(fontWeight = FontWeight.Normal) else style.type.label,
            color = quiet, maxLines = 1,
        )
    }
}
