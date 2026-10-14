package io.lanyin.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lanyin.player.ui.design.ensoPath
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PlayerSkin

/** Characters set upright in vertical text; anything else (Latin words, digits) is turned sideways. */
internal fun isUpright(ch: Char): Boolean {
    val block = Character.UnicodeBlock.of(ch)
    return Character.UnicodeScript.of(ch.code) in setOf(
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL,
    ) || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
        block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
}

/**
 * Splits [text] into runs for vertical setting: each CJK character on its own, Latin words kept
 * together to be turned sideways. Longer than [maxRuns] and the end becomes a vertical ellipsis.
 */
internal fun verticalRuns(text: String, maxRuns: Int): List<Pair<String, Boolean>> {
    val runs = mutableListOf<Pair<String, Boolean>>()
    val word = StringBuilder()
    fun flush() {
        if (word.isNotBlank()) runs += word.toString().trim() to false
        word.clear()
    }
    for (ch in text) {
        if (isUpright(ch)) {
            flush()
            runs += ch.toString() to true
        } else if (ch == ' ' || ch == '/' || ch == '·') {
            flush()
        } else {
            word.append(ch)
        }
    }
    flush()
    return if (runs.size <= maxRuns) runs else runs.take(maxRuns - 1) + ("⋮" to true)
}

/** Lays out sideways: measured as a line, placed turned 90° clockwise. */
private fun Modifier.sideways(): Modifier = layout { measurable, _ ->
    val p = measurable.measure(Constraints())
    layout(p.height, p.width) {
        p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) { rotationZ = 90f }
    }
}

@Composable
private fun VerticalText(text: String, style: TextStyle, color: Color, maxRuns: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        verticalRuns(text, maxRuns).forEach { (run, upright) ->
            Text(run, style = style, color = color, modifier = if (upright) Modifier else Modifier.sideways().padding(vertical = 2.dp))
        }
    }
}

/**
 * 题款: in 墨's full-screen stage the title is written down the right edge like the inscription
 * on a hanging scroll, the artist in a smaller hand beside it, closed with a red seal.
 */
@Composable
fun Inscription(state: DemoPlayerState, modifier: Modifier = Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val titleStyle = TextStyle(fontFamily = style.display, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium)
    val artistStyle = TextStyle(fontFamily = style.display, fontSize = 13.sp, lineHeight = 17.sp)
    // Read right to left: the title column first, then the artist with the seal beneath it.
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(30.dp))
            VerticalText(state.artist, artistStyle, c.muted, maxRuns = 6)
            Spacer(Modifier.height(10.dp))
            Seal(state.artist.firstOrNull()?.toString() ?: "澜", 22.dp)
        }
        VerticalText(state.title, titleStyle, c.content.copy(alpha = 0.85f), maxRuns = 9)
    }
}

@Composable
private fun Seal(text: String, size: Dp) {
    val c = Lanyin.colors
    Box(Modifier.size(size).background(c.accentAlt), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontFamily = Lanyin.style.display, fontSize = 13.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold), color = c.bg)
    }
}

/**
 * Decoration that belongs to the artwork rather than the screen: 墨's ensō around the record, drawn
 * from the artwork's own bounds so it can never drift off it, whatever the screen's shape.
 */
@Composable
internal fun Modifier.artworkBackdrop(skin: PlayerSkin): Modifier {
    val style = Lanyin.style
    if (style.edge != EdgeKind.Hairline || skin != PlayerSkin.VINYL) return this
    val color = style.colors.content.copy(alpha = if (style.dark) 0.1f else 0.08f)
    return drawBehind {
        // Just outside the record's rim (the record sits 14dp inside these bounds).
        val r = size.minDimension / 2f + 6.dp.toPx()
        rotate(-70f, Offset(size.width / 2f, size.height / 2f)) {
            drawPath(ensoPath(Offset(size.width / 2f, size.height / 2f), r, r * 0.12f), color)
        }
    }
}
