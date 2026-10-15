package io.lanyin.player.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.ThemedButton
import io.lanyin.player.ui.design.ThemedTextField
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.Lanyin
import kotlinx.coroutines.launch

/**
 * The fallback: paste the `MUSIC_U` cookie from a browser that is signed in to music.163.com.
 * [onSubmit] checks it with NetEase and returns whether it worked.
 */
@Composable
fun CookieLoginDialog(onDismiss: () -> Unit, onSubmit: suspend (String) -> Boolean, onDone: () -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = { if (!checking) onDismiss() }) {
        CompositionLocalProvider(LocalContentColor provides c.content) {
            Column(Modifier.fillMaxWidth().themedSurface().padding(22.dp)) {
                Text("用 Cookie 登录", style = style.type.title)
                Text(
                    "在电脑浏览器里登录 music.163.com，打开开发者工具 → Application → Cookies，复制 MUSIC_U 的值粘贴到这里。" +
                        "它相当于你的登录密码：只会加密保存在这台手机上，只发给网易云。",
                    style = style.type.caption, color = c.muted,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
                ThemedTextField(
                    value = value,
                    onValueChange = {
                        value = it.removePrefix("MUSIC_U=").trim().trimEnd(';')
                        error = null
                    },
                    placeholder = "MUSIC_U 的值",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                )
                Text(
                    error ?: if (checking) "正在向网易云确认…" else " ",
                    style = style.type.caption,
                    color = if (error != null) c.accent else c.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.size(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "取消",
                        style = style.type.body,
                        color = c.muted,
                        modifier = Modifier.pressable { if (!checking) onDismiss() }.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                    Spacer(Modifier.size(16.dp))
                    ThemedButton(
                        "登录",
                        onClick = {
                            checking = true
                            scope.launch {
                                val ok = runCatching { onSubmit(value) }.getOrDefault(false)
                                checking = false
                                if (ok) onDone() else error = "网易云不认这个值，可能复制错了或已经过期"
                            }
                        },
                        enabled = value.length > 20 && !checking,
                    )
                }
            }
        }
    }
}
