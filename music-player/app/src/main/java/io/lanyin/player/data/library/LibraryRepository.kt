package io.lanyin.player.data.library

import io.lanyin.player.core.model.Playlist
import io.lanyin.player.core.model.Song
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.core.netease.PlaylistDetail
import io.lanyin.player.data.account.AccountRepository
import io.lanyin.player.data.account.AccountState
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The listener's music: liked songs, their playlists, today's picks. Follows the account, so a
 * login fills it in and a logout clears it. Lists are fetched when first asked for and kept.
 */
class LibraryRepository(private val api: NeteaseApi, account: AccountRepository, private val scope: CoroutineScope) {

    private val _likedIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedIds: StateFlow<Set<Long>> = _likedIds.asStateFlow()

    /** Null until loaded (or when signed out). */
    private val _playlists = MutableStateFlow<List<Playlist>?>(null)
    val playlists: StateFlow<List<Playlist>?> = _playlists.asStateFlow()

    private var userId: Long? = null
    private var daily: Pair<LocalDate, List<Song>>? = null
    private val details = LinkedHashMap<Long, PlaylistDetail>()

    init {
        scope.launch {
            account.state
                .map { (it as? AccountState.LoggedIn)?.profile?.userId }
                .distinctUntilChanged()
                .collect { uid ->
                    userId = uid
                    daily = null
                    details.clear()
                    _likedIds.value = emptySet()
                    _playlists.value = null
                    if (uid != null) refresh()
                }
        }
    }

    fun refresh() {
        val uid = userId ?: return
        scope.launch { runCatching { _likedIds.value = api.likedIds(uid) } }
        scope.launch { runCatching { _playlists.value = api.userPlaylists(uid) } }
    }

    /** The user's own 「我喜欢的音乐」 playlist. */
    fun likedPlaylist(): Playlist? = _playlists.value?.firstOrNull { it.specialType == 5 }

    fun created(): List<Playlist> = _playlists.value.orEmpty().filter { it.creatorId == userId && it.specialType != 5 }

    fun collected(): List<Playlist> = _playlists.value.orEmpty().filter { it.creatorId != userId }

    /** Likes or unlikes at once, and puts it back if NetEase says no. Returns an error to show, or null. */
    suspend fun toggleLike(songId: Long): String? {
        if (userId == null) return "登录后才能收藏到「我喜欢的音乐」"
        val like = songId !in _likedIds.value
        _likedIds.update { if (like) it + songId else it - songId }
        return runCatching { api.setLiked(songId, like) }.fold(
            onSuccess = {
                likedPlaylist()?.let { details.remove(it.id) }
                null
            },
            onFailure = {
                _likedIds.update { if (like) it - songId else it + songId }
                "没能${if (like) "收藏" else "取消收藏"}：${it.message ?: "网络不好"}"
            },
        )
    }

    suspend fun dailySongs(): List<Song> {
        val today = LocalDate.now()
        daily?.takeIf { it.first == today }?.let { return it.second }
        return api.dailySongs().also { daily = today to it }
    }

    /** For you when signed in; popular ones otherwise. */
    suspend fun recommendedPlaylists(): List<Playlist> =
        if (userId != null) runCatching { api.recommendedPlaylists() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: api.personalizedPlaylists()
        else api.personalizedPlaylists()

    suspend fun charts(): List<Playlist> = api.charts()

    suspend fun playlist(id: Long, fresh: Boolean = false): PlaylistDetail {
        if (!fresh) details[id]?.let { return it }
        return api.playlist(id).also {
            details[id] = it
            while (details.size > 12) details.remove(details.keys.first())
        }
    }

    suspend fun search(keywords: String): List<Song> = api.search(keywords, limit = 50)

    suspend fun personalFm(): List<Song> = api.personalFm()

    val signedIn: Boolean get() = userId != null
}
