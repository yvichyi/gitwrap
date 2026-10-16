package io.lanyin.player.data.library

import io.lanyin.player.core.model.CloudPage
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.core.model.RankedSong
import io.lanyin.player.core.model.RecentPlay
import io.lanyin.player.core.model.Song
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.core.netease.NeteaseException
import io.lanyin.player.core.netease.PlaylistDetail
import io.lanyin.player.data.account.AccountRepository
import io.lanyin.player.data.account.AccountState
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A chart and its top songs. */
data class ChartPreview(val playlist: Playlist, val top: List<Song>)

/** Songs offered on the home page, with a heading saying where they come from. */
data class Picks(val title: String, val note: String, val songs: List<Song>)

/**
 * The listener's music: liked songs, their playlists, today's picks. Follows the account, so a
 * login fills it in and a logout clears it. Lists are fetched when first asked for and kept;
 * changes made here (a song added, a playlist renamed) update what is kept and bump [revision].
 */
class LibraryRepository(private val api: NeteaseApi, account: AccountRepository, private val scope: CoroutineScope) {

    private val _likedIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedIds: StateFlow<Set<Long>> = _likedIds.asStateFlow()

    /** Null until loaded (or when signed out). */
    private val _playlists = MutableStateFlow<List<Playlist>?>(null)
    val playlists: StateFlow<List<Playlist>?> = _playlists.asStateFlow()

    /** Goes up whenever a playlist changes here, so open pages read it again. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

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

    /** The last sync didn't get through (offline, say), even after a few tries; [refresh] tries again. */
    private val _syncFailed = MutableStateFlow(false)
    val syncFailed: StateFlow<Boolean> = _syncFailed.asStateFlow()

    private var syncJob: Job? = null

    fun refresh() {
        val uid = userId ?: return
        _syncFailed.value = false
        syncJob?.cancel()
        syncJob = scope.launch {
            // A few tries, further apart each time, before saying it didn't work.
            for (attempt in 0 until SYNC_TRIES) {
                val liked = runCatching { api.likedIds(uid) }.onSuccess { _likedIds.value = it }
                val lists = runCatching { api.userPlaylists(uid) }.onSuccess { _playlists.value = it }
                if (liked.isSuccess && lists.isSuccess || userId != uid) return@launch
                if (attempt < SYNC_TRIES - 1) delay(2_000L shl attempt)
            }
            _syncFailed.value = true
        }
    }

    val signedIn: Boolean get() = userId != null

    /** The user's own 「我喜欢的音乐」 playlist. */
    fun likedPlaylist(): Playlist? = _playlists.value?.firstOrNull { it.specialType == 5 }

    fun created(): List<Playlist> = _playlists.value.orEmpty().filter { it.creatorId == userId && it.specialType != 5 }

    fun collected(): List<Playlist> = _playlists.value.orEmpty().filter { it.creatorId != userId }

    /** One the listener made (and can rename, delete, add songs to); 「我喜欢的音乐」 aside. */
    fun isOwn(p: Playlist): Boolean = userId != null && p.creatorId == userId && p.specialType != 5

    /** Someone else's playlist, which a signed-in listener can collect. */
    fun canCollect(p: Playlist): Boolean = userId != null && p.creatorId != userId && p.specialType != 5

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

    private var chartPreviews: Pair<Long, List<ChartPreview>>? = null

    /** The first few charts with their top three songs, for the home page; kept ten minutes. */
    suspend fun chartPreviews(count: Int = 4): List<ChartPreview> {
        chartPreviews?.takeIf { System.currentTimeMillis() - it.first < 10 * 60_000 }?.let { return it.second }
        val previews = coroutineScope {
            api.charts().take(count).map { chart -> async { ChartPreview(chart, runCatching { api.playlistHead(chart.id, 3).songs }.getOrDefault(emptyList())) } }.awaitAll()
        }
        chartPreviews = System.currentTimeMillis() to previews
        return previews
    }

    /** A handful of songs for the home page: today's picks when signed in, otherwise what is hot. */
    suspend fun picks(count: Int = 6): Picks =
        if (userId != null) Picks("今日推荐", "按你的口味挑的", dailySongs().take(count))
        else Picks("热歌榜", "大家都在听", api.playlistHead(HOT_CHART_ID, count).songs)

    suspend fun playlist(id: Long, fresh: Boolean = false): PlaylistDetail {
        if (!fresh) details[id]?.let { return it }
        return api.playlist(id).also {
            details[id] = it
            while (details.size > 12) details.remove(details.keys.first())
        }
    }

    suspend fun search(keywords: String): List<Song> = api.search(keywords, limit = 50)

    suspend fun personalFm(): List<Song> = api.personalFm()

    suspend fun artist(id: Long) = api.artist(id)

    suspend fun album(id: Long) = api.album(id)

    suspend fun searchArtists(keywords: String) = api.searchArtists(keywords)

    suspend fun searchAlbums(keywords: String) = api.searchAlbums(keywords)

    suspend fun searchPlaylists(keywords: String) = api.searchPlaylists(keywords)

    // —— Cloud drive ——

    suspend fun cloud(offset: Int): CloudPage = api.cloud(offset)

    /** A song landed in the cloud drive from elsewhere (an upload). */
    fun cloudChanged() = changed(reload = false)

    suspend fun deleteFromCloud(songId: Long): String? = attempt("从云盘删除") {
        api.deleteFromCloud(songId)
        changed(reload = false)
    }

    // —— Playlists of one's own ——

    /** Makes a playlist and, if given, puts [songs] in it. Returns it, or the error to show. */
    suspend fun createPlaylist(name: String, private: Boolean, songs: List<Song> = emptyList()): Result<Playlist> {
        if (userId == null) return Result.failure(IllegalStateException("登录后才能新建歌单"))
        return try {
            val p = api.createPlaylist(name.trim(), private)
            if (songs.isNotEmpty()) api.addToPlaylist(p.id, songs.map { it.id })
            changed()
            Result.success(p.copy(trackCount = songs.size))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(failure("新建歌单", e)))
        }
    }

    suspend fun renamePlaylist(id: Long, name: String): String? = attempt("改名") {
        api.renamePlaylist(id, name.trim())
        details[id]?.let { details[id] = PlaylistDetail(it.playlist.copy(name = name.trim()), it.songs) }
        _playlists.update { all -> all?.map { if (it.id == id) it.copy(name = name.trim()) else it } }
        changed(reload = false)
    }

    suspend fun deletePlaylist(id: Long): String? = attempt("删除歌单") {
        api.deletePlaylist(id)
        details.remove(id)
        _playlists.update { all -> all?.filter { it.id != id } }
        changed(reload = false)
    }

    suspend fun addToPlaylist(playlist: Playlist, songs: List<Song>): String? = attempt("添加到「${playlist.name}」") {
        api.addToPlaylist(playlist.id, songs.map { it.id })
        details.remove(playlist.id)
        changed()
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long): String? = attempt("从歌单删除") {
        api.removeFromPlaylist(playlistId, listOf(songId))
        details[playlistId]?.let { d ->
            details[playlistId] = PlaylistDetail(d.playlist.copy(trackCount = (d.playlist.trackCount - 1).coerceAtLeast(0)), d.songs.filter { it.id != songId })
        }
        changed()
    }

    /** Collects someone else's playlist, or lets it go. */
    suspend fun setSubscribed(playlist: Playlist, subscribe: Boolean): String? = attempt(if (subscribe) "收藏歌单" else "取消收藏") {
        api.subscribePlaylist(playlist.id, subscribe)
        details[playlist.id]?.let { details[playlist.id] = PlaylistDetail(it.playlist.copy(subscribed = subscribe), it.songs) }
        changed()
    }

    // —— Play records ——

    suspend fun recentPlays(): List<RecentPlay> = api.recentPlays()

    suspend fun ranking(week: Boolean): List<RankedSong> {
        val uid = userId ?: throw NeteaseException(301, "登录后才能看听歌排行")
        return api.listeningRanking(uid, week)
    }

    companion object {
        /** NetEase's 热歌榜. */
        const val HOT_CHART_ID = 3778678L

        /** How many times a sync is tried (2, 4 and 8 seconds apart) before giving up. */
        private const val SYNC_TRIES = 4
    }

    private fun changed(reload: Boolean = true) {
        _revision.update { it + 1 }
        if (reload) refresh()
    }

    /** Runs [block]; null when it worked, else what to tell the listener. */
    private suspend fun attempt(what: String, block: suspend () -> Unit): String? {
        if (userId == null) return "登录后才能$what"
        return try {
            block()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(what, e)
        }
    }

    private fun failure(what: String, e: Exception): String = when {
        e is NeteaseException && e.code == 301 -> "登录已过期，重新登录后才能$what"
        e is NeteaseException -> "没能$what：${e.message ?: "网易云返回 ${e.code}"}"
        else -> "没能$what：网络不好"
    }
}
