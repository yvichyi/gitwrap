package io.lanyin.player.data.account

import io.lanyin.player.core.model.UserProfile
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.core.netease.NeteaseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether someone is signed in, as far as the app knows right now. */
sealed interface AccountState {
    /** The session hasn't been read yet. */
    data object Loading : AccountState
    data object LoggedOut : AccountState
    data class LoggedIn(val profile: UserProfile) : AccountState
}

/**
 * The account: shows the cached profile at once, then checks with NetEase in the background.
 * A login that NetEase no longer accepts is dropped; a network failure keeps the cached one.
 */
class AccountRepository(private val api: NeteaseApi, private val scope: CoroutineScope) {

    val state: StateFlow<AccountState> = api.session
        .map { s ->
            when {
                s == null -> AccountState.Loading
                s.loggedIn -> s.profile?.let { AccountState.LoggedIn(it) } ?: AccountState.Loading
                else -> AccountState.LoggedOut
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AccountState.Loading)

    /** Called once at start-up. */
    fun start() {
        scope.launch {
            runCatching {
                val session = api.currentSession()
                if (session.loggedIn) refresh() else api.ensureAnonymous()
            }
        }
    }

    /** Re-reads the profile; after a QR login this is what fills it in. */
    suspend fun refresh(): UserProfile? = try {
        val profile = api.account()
        if (profile == null) api.logout() else api.saveProfile(profile)
        profile
    } catch (e: NeteaseException) {
        if (e.code == 301) api.logout()
        null
    } catch (e: Exception) {
        // Offline or NetEase unreachable: keep whatever we had.
        api.currentSession().profile
    }

    /** Signs in with a pasted `MUSIC_U`. */
    suspend fun loginWithCookie(musicU: String): UserProfile? = api.loginWithCookie(musicU)?.also { api.saveProfile(it) }

    suspend fun logout() = api.logout()
}
