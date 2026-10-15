package io.lanyin.player.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.data.account.AccountRepository
import io.lanyin.player.data.library.LibraryRepository
import io.lanyin.player.ui.player.PlayerState

/** What screens reach for beyond their own state. Absent in previews and screenshot tests. */
class AppServices(
    val api: NeteaseApi,
    val account: AccountRepository,
    val library: LibraryRepository,
    val player: PlayerState,
    val recent: io.lanyin.player.data.library.RecentRepository,
)

val LocalAppServices = staticCompositionLocalOf<AppServices?> { null }
