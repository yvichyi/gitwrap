package io.lanyin.player.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import io.lanyin.player.ui.theme.EdgeKind
import io.lanyin.player.ui.theme.Lanyin
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import io.lanyin.player.ui.theme.SpaceMonoFamily
import io.lanyin.player.ui.theme.BackdropKind

/** A text field in the theme's hand: an underline in 墨, a field in the chip shape elsewhere. */
@Composable
fun ThemedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: LineIcon? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    /** On a sheet or card rather than the page: a tinted well, since a surface wouldn't show. */
    onSurface: Boolean = false,
) {
    val style = Lanyin.style
    val c = style.colors
    val hairline = style.edge == EdgeKind.Hairline
    // On 机's screen the type is the screen's.
    val ink = if (style.backdrop == BackdropKind.Device) style.screenStyle().colors.content else c.content
    val hint = if (style.backdrop == BackdropKind.Device) style.screenStyle().colors.muted else c.muted
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(
                    when {
                        hairline -> Modifier
                        // 机: typed into a little screen set into the case.
                        style.backdrop == BackdropKind.Device -> Modifier.background(style.screenStyle().colors.bg, RoundedCornerShape(10.dp)).padding(horizontal = 18.dp)
                        // 游: a game's input window.
                        style.backdrop == BackdropKind.Game -> Modifier.gameWindow(c.surface, c.content).padding(horizontal = 18.dp)
                        // 报: written on a ruled line, no box.
                        style.backdrop == BackdropKind.Paper -> Modifier.drawBehind {
                            drawRect(c.content, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 1.5.dp.toPx()))
                        }
                        onSurface && style.edge != EdgeKind.Brutal ->
                            Modifier.clip(style.chipShape).background(c.content.copy(alpha = if (style.dark) 0.1f else 0.06f)).padding(horizontal = 18.dp)
                        else -> Modifier.themedSurface(shape = style.chipShape, tone = SurfaceTone.Flat).padding(horizontal = 18.dp)
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                ThemedIcon(leading, null, tint = hint, size = 22.dp)
                Spacer(Modifier.width(12.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = style.type.body.copy(color = ink, fontFamily = if (style.backdrop == BackdropKind.Device) SpaceMonoFamily else style.type.body.fontFamily),
                cursorBrush = SolidColor(ink),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(placeholder, style = style.type.body, color = hint, maxLines = 1)
                        field()
                    }
                },
            )
        }
        if (hairline) Hairline()
    }
}
