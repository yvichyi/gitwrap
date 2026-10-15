package io.lanyin.player.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.lanyin.player.core.model.Song
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.data.account.AccountRepository
import io.lanyin.player.data.library.LibraryRepository
import io.lanyin.player.playback.DownloadRepository
import io.lanyin.player.ui.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What screens reach for beyond their own state. Absent in previews and screenshot tests. */
class AppServices(
    val api: NeteaseApi,
    val account: AccountRepository,
    val library: LibraryRepository,
    val player: PlayerState,
    val recent: io.lanyin.player.data.library.RecentRepository,
    val downloads: DownloadRepository,
    val notices: Notices,
)

val LocalAppServices = staticCompositionLocalOf<AppServices?> { null }

/** Short messages for the banner over the bottom bar: something done, or what went wrong. */
class Notices {
    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current.asStateFlow()

    fun show(text: String) {
        _current.value = text
    }

    fun dismiss() {
        _current.value = null
    }
}

/** Ways to other pages, for places that aren't handed callbacks: the player and its sheets. */
class AppNavigator(
    val back: () -> Unit,
    val openArtist: (Long, String) -> Unit,
    val openAlbum: (Long, String) -> Unit,
    val openComments: (Song) -> Unit,
)

val LocalNavigator = staticCompositionLocalOf<AppNavigator?> { null }
