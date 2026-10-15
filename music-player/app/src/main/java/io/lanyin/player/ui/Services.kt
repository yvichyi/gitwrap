package io.lanyin.player.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.data.account.AccountRepository

/** What screens reach for beyond their own state. Absent in previews and screenshot tests. */
class AppServices(val api: NeteaseApi, val account: AccountRepository)

val LocalAppServices = staticCompositionLocalOf<AppServices?> { null }
