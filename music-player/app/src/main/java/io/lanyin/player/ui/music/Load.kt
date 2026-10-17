package io.lanyin.player.ui.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.lanyin.player.core.netease.NeteaseException
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Something fetched for a screen. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/** A fetch tied to [key], with a way to try again; [busy] while fetching, even over what's shown. */
class Loader<T>(val state: Load<T>, val retry: () -> Unit, val busy: Boolean = false)

/**
 * A fetch tied to [key], with a way to try again. A change of [refresh] fetches again too, but
 * keeps showing what was there meanwhile (and if the new fetch fails).
 */
@Composable
fun <T> rememberLoad(key: Any?, refresh: Any? = null, fetch: suspend () -> T): Loader<T> {
    var attempt by remember(key) { mutableIntStateOf(0) }
    var state by remember(key) { mutableStateOf<Load<T>>(Load.Loading) }
    var busy by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, attempt, refresh) {
        val shown = state
        if (shown !is Load.Ready) state = Load.Loading
        busy = true
        try {
            state = try {
                Load.Ready(fetch())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (shown is Load.Ready) shown else Load.Failed(describe(e))
            }
        } finally {
            busy = false
        }
    }
    return Loader(state, { attempt++ }, busy)
}

fun describe(e: Throwable): String = when {
    e is NeteaseException && e.code == 301 -> "需要先登录网易云音乐"
    e is NeteaseException -> e.message ?: "网易云返回 ${e.code}"
    e is IOException -> "连不上网易云，检查一下网络"
    else -> "出了点问题"
}
