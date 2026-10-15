package io.lanyin.player.core.netease

import io.lanyin.player.core.model.Album
import io.lanyin.player.core.model.AlbumDetail
import io.lanyin.player.core.model.AlbumHit
import io.lanyin.player.core.model.ArtistDetail
import io.lanyin.player.core.model.ArtistHit
import io.lanyin.player.core.model.Artist
import io.lanyin.player.core.model.CloudPage
import io.lanyin.player.core.model.CloudSong
import io.lanyin.player.core.model.Comment
import io.lanyin.player.core.model.CommentPage
import io.lanyin.player.core.model.CommentSort
import io.lanyin.player.core.model.CommentUser
import io.lanyin.player.core.model.LyricsSource
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.core.model.Quality
import io.lanyin.player.core.model.QuotedComment
import io.lanyin.player.core.model.RankedSong
import io.lanyin.player.core.model.RecentPlay
import io.lanyin.player.core.model.Song
import io.lanyin.player.core.model.Stream
import io.lanyin.player.core.model.UserProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

/** Where a QR login stands. */
sealed interface QrStatus {
    data object Waiting : QrStatus
    /** Scanned; waiting for the person to confirm on their phone. */
    data class Scanned(val nickname: String?, val avatarUrl: String?) : QrStatus
    data object Confirmed : QrStatus
    data object Expired : QrStatus
    data class Failed(val code: Int, val message: String?) : QrStatus
}

class PlaylistDetail(val playlist: Playlist, val songs: List<Song>)

/**
 * The NetEase calls the app uses, typed. Endpoints and the protocol each one goes over follow
 * NeteaseCloudMusicApi's modules (one file per endpoint under `module/`).
 */
class NeteaseApi(private val client: NeteaseClient) {

    val session get() = client.session

    suspend fun currentSession() = client.currentSession()

    suspend fun saveProfile(profile: UserProfile) {
        client.updateSession { it.copy(profile = profile) }
    }

    // —— Login ——

    /**
     * Registers the anonymous account used before login, once per install. NetEase turns some
     * device ids away for good (about one in three random ones, the reference client too), so
     * a refused id is replaced and the registration tried again.
     */
    suspend fun ensureAnonymous() {
        repeat(5) {
            val s = client.currentSession()
            if (s.loggedIn || s.musicA != null) return
            val r = client.weapi("/api/register/anonimous", buildJsonObject { put("username", NeteaseCrypto.anonymousUsername(s.deviceId)) })
            if (r.code == 200) {
                client.updateSession { it.withCookies(r.setCookies) }
                return
            }
            if (r.code != 400) throw NeteaseException(r.code, r.message ?: "匿名账号注册失败")
            client.updateSession { it.copy(deviceId = NeteaseCrypto.newDeviceId()) }
        }
        throw NeteaseException(400, "匿名账号注册失败")
    }

    suspend fun qrKey(): String {
        val r = client.eapi("/api/login/qrcode/unikey", buildJsonObject { put("type", 3) }).requireOk()
        return r.decode<QrKeyDto>().unikey ?: throw NeteaseException(r.code, "没有拿到二维码")
    }

    fun qrUrl(key: String): String = "https://music.163.com/login?codekey=$key"

    suspend fun qrCheck(key: String): QrStatus {
        val r = client.eapi("/api/login/qrcode/client/login", buildJsonObject { put("key", key); put("type", 3) })
        return when (r.code) {
            801 -> QrStatus.Waiting
            802 -> r.decode<QrScanDto>().let { QrStatus.Scanned(it.nickname, it.avatarUrl?.https()) }
            803 -> {
                if (r.setCookies["MUSIC_U"].isNullOrEmpty()) return QrStatus.Failed(803, "登录成功，但没有拿到登录凭证")
                client.updateSession { it.withCookies(r.setCookies) }
                QrStatus.Confirmed
            }
            800 -> QrStatus.Expired
            else -> QrStatus.Failed(r.code, r.message)
        }
    }

    /** Signs in with a `MUSIC_U` value copied from elsewhere. Returns the profile, or null if it isn't valid. */
    suspend fun loginWithCookie(musicU: String): UserProfile? {
        val previous = client.currentSession()
        client.updateSession { it.loggedOut().withCookies(mapOf("MUSIC_U" to musicU.trim())) }
        val profile = runCatching { account() }.getOrNull()
        if (profile == null) client.updateSession { previous }
        return profile
    }

    /** The signed-in profile, or null when not signed in (or the login has expired). */
    suspend fun account(): UserProfile? {
        val r = client.weapi("/api/w/nuser/account/get").requireOk()
        val dto = r.decode<AccountDto>()
        val p = dto.profile ?: return null
        return UserProfile(p.userId, p.nickname.orEmpty(), p.avatarUrl?.https(), maxOf(p.vipType, dto.account?.vipType ?: 0))
    }

    suspend fun logout() {
        runCatching { client.eapi("/api/logout") }
        client.updateSession { it.loggedOut() }
    }

    // —— Library ——

    suspend fun userPlaylists(uid: Long, limit: Int = 100): List<Playlist> {
        val r = client.weapi("/api/user/playlist", buildJsonObject { put("uid", uid); put("limit", limit); put("offset", 0); put("includeVideo", true) }).requireOk()
        return r.decode<UserPlaylistsDto>().playlist.map { it.toPlaylist() }
    }

    suspend fun likedIds(uid: Long): Set<Long> =
        client.eapi("/api/song/like/get", buildJsonObject { put("uid", uid) }).requireOk().decode<LikeListDto>().ids.toSet()

    suspend fun setLiked(songId: Long, like: Boolean) {
        client.weapi("/api/radio/like", buildJsonObject { put("alg", "itembased"); put("trackId", songId); put("like", like); put("time", "3") }).requireOk()
    }

    /** A playlist with all of its songs; tracks the detail call leaves out are fetched in batches. */
    suspend fun playlist(id: Long): PlaylistDetail {
        val r = client.eapi("/api/v6/playlist/detail", buildJsonObject { put("id", id); put("n", 100000); put("s", 8) }).requireOk()
        val dto = r.decode<PlaylistDetailDto>().playlist ?: throw NeteaseException(404, "歌单不存在")
        val known = dto.tracks.map { it.toSong() }
        val knownIds = known.mapTo(HashSet()) { it.id }
        val missing = dto.trackIds.map { it.id }.filter { it !in knownIds }
        val rest = missing.chunked(500).flatMap { songs(it) }
        val byId = (known + rest).associateBy { it.id }
        val ordered = if (dto.trackIds.isEmpty()) known else dto.trackIds.mapNotNull { byId[it.id] }
        return PlaylistDetail(dto.toPlaylist(), ordered)
    }

    /** A playlist and only its first [count] songs: enough for a preview. */
    suspend fun playlistHead(id: Long, count: Int): PlaylistDetail {
        val r = client.eapi("/api/v6/playlist/detail", buildJsonObject { put("id", id); put("n", count); put("s", 0) }).requireOk()
        val dto = r.decode<PlaylistDetailDto>().playlist ?: throw NeteaseException(404, "歌单不存在")
        return PlaylistDetail(dto.toPlaylist(), dto.tracks.take(count).map { it.toSong() })
    }

    suspend fun songs(ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val c = ids.joinToString(",", "[", "]") { "{\"id\":$it}" }
        return client.weapi("/api/v3/song/detail", buildJsonObject { put("c", c) }).requireOk().decode<SongsDto>().songs.map { it.toSong() }
    }

    // —— Discovery ——

    suspend fun dailySongs(): List<Song> =
        client.weapi("/api/v3/discovery/recommend/songs").requireOk().decode<DailyDto>().data?.dailySongs.orEmpty().map { it.toSong() }

    suspend fun recommendedPlaylists(): List<Playlist> =
        client.weapi("/api/v1/discovery/recommend/resource").requireOk().decode<RecommendResourceDto>().recommend.map { it.toPlaylist() }

    suspend fun personalFm(): List<Song> =
        client.weapi("/api/v1/radio/get").requireOk().decode<FmDto>().data.map { it.toSong() }

    /** The official charts; public, no login needed. */
    suspend fun charts(): List<Playlist> =
        client.eapi("/api/toplist").requireOk().decode<ToplistDto>().list.map { it.toPlaylist() }

    /** Popular playlists picked by NetEase; works before login too. */
    suspend fun personalizedPlaylists(limit: Int = 12): List<Playlist> =
        client.weapi("/api/personalized/playlist", buildJsonObject { put("limit", limit); put("total", true); put("n", 1000) })
            .requireOk().decode<PersonalizedDto>().result.map { it.toPlaylist() }

    /** Tells FM not to play this song again. */
    suspend fun fmTrash(songId: Long) {
        client.weapi("/api/radio/trash/add", buildJsonObject { put("songId", songId); put("alg", "RT"); put("time", 25) }).requireOk()
    }

    suspend fun search(keywords: String, limit: Int = 30, offset: Int = 0): List<Song> {
        val r = client.eapi(
            "/api/cloudsearch/pc",
            buildJsonObject { put("s", keywords); put("type", 1); put("limit", limit); put("offset", offset); put("total", true) },
        ).requireOk()
        return r.decode<SearchDto>().result?.songs.orEmpty().map { it.toSong() }
    }

    suspend fun artist(id: Long): ArtistDetail {
        val dto = client.weapi("/api/v1/artist/$id").requireOk().decode<ArtistPageDto>()
        val a = dto.artist ?: throw NeteaseException(404, "找不到这位歌手")
        return ArtistDetail(
            id = a.id, name = a.name.orEmpty(), pictureUrl = (a.picUrl ?: a.img1v1Url)?.https(),
            brief = a.briefDesc?.takeIf { it.isNotBlank() }, songCount = a.musicSize, albumCount = a.albumSize,
            hotSongs = dto.hotSongs.map { it.toSong() },
        )
    }

    suspend fun album(id: Long): AlbumDetail {
        val dto = client.weapi("/api/v1/album/$id").requireOk().decode<AlbumDetailDto>()
        val a = dto.album ?: throw NeteaseException(404, "找不到这张专辑")
        val cover = a.picUrl?.https()
        return AlbumDetail(
            id = a.id, name = a.name.orEmpty(), coverUrl = cover,
            artistName = (a.artists?.joinToString(" / ") { it.name.orEmpty() } ?: a.artist?.name).orEmpty(),
            publishTime = a.publishTime, description = a.description?.takeIf { it.isNotBlank() },
            // Album tracks often leave the cover out; they all share the album's.
            songs = dto.songs.map { it.toSong().let { s -> if (s.album.coverUrl == null) s.copy(album = s.album.copy(coverUrl = cover)) else s } },
        )
    }

    suspend fun searchArtists(keywords: String, limit: Int = 30): List<ArtistHit> =
        cloudsearch(keywords, 100, limit).decode<SearchDto>().result?.artists.orEmpty()
            .map { ArtistHit(it.id, it.name.orEmpty(), (it.picUrl ?: it.img1v1Url)?.https(), it.albumSize) }

    suspend fun searchAlbums(keywords: String, limit: Int = 30): List<AlbumHit> =
        cloudsearch(keywords, 10, limit).decode<SearchDto>().result?.albums.orEmpty()
            .map { AlbumHit(it.id, it.name.orEmpty(), it.picUrl?.https(), it.artist?.name.orEmpty(), it.size) }

    suspend fun searchPlaylists(keywords: String, limit: Int = 30): List<Playlist> =
        cloudsearch(keywords, 1000, limit).decode<SearchDto>().result?.playlists.orEmpty().map { it.toPlaylist() }

    private suspend fun cloudsearch(keywords: String, type: Int, limit: Int): NeteaseResponse =
        client.eapi(
            "/api/cloudsearch/pc",
            buildJsonObject { put("s", keywords); put("type", type); put("limit", limit); put("offset", 0); put("total", true) },
        ).requireOk()

    // —— Cloud drive ——

    suspend fun cloud(offset: Int = 0, limit: Int = 200): CloudPage {
        val dto = client.weapi("/api/v1/cloud/get", buildJsonObject { put("limit", limit); put("offset", offset) }).requireOk().decode<CloudDto>()
        return CloudPage(dto.data.map { it.toCloudSong() }, dto.count, dto.size, dto.maxSize, dto.hasMore)
    }

    suspend fun deleteFromCloud(songId: Long) {
        client.weapi("/api/cloud/del", buildJsonObject { put("songIds", JsonArray(listOf(JsonPrimitive(songId)))) }).requireOk()
    }

    // —— Comments ——

    /** One page of a song's comments; [cursor] comes from the previous page (null for the first). */
    suspend fun comments(songId: Long, sort: CommentSort, pageNo: Int = 1, cursor: String? = null, pageSize: Int = 20): CommentPage {
        val r = client.eapi(
            "/api/v2/resource/comments",
            buildJsonObject {
                put("threadId", "$SONG_THREAD$songId")
                put("pageNo", pageNo)
                put("showInner", true)
                put("pageSize", pageSize)
                put(
                    "cursor",
                    when (sort) {
                        CommentSort.RECOMMENDED -> ((pageNo - 1) * pageSize).toString()
                        CommentSort.HOT -> "normalHot#${(pageNo - 1) * pageSize}"
                        CommentSort.NEWEST -> cursor ?: "0"
                    },
                )
                put("sortType", sort.code)
            },
        ).requireOk()
        val data = r.decode<CommentsDto>().data ?: return CommentPage(emptyList(), 0, false, null)
        return CommentPage(data.comments.map { it.toComment() }, data.totalCount, data.hasMore, data.cursor)
    }

    suspend fun likeComment(songId: Long, commentId: Long, like: Boolean) {
        client.weapi(
            "/api/v1/comment/${if (like) "like" else "unlike"}",
            buildJsonObject { put("threadId", "$SONG_THREAD$songId"); put("commentId", commentId) },
        ).requireOk()
    }

    /** Posts a comment, or a reply to [replyTo]. Returns it as NetEase stored it, when it says. */
    suspend fun postComment(songId: Long, content: String, replyTo: Long? = null): Comment? {
        val r = client.weapi(
            "/api/resource/comments/${if (replyTo == null) "add" else "reply"}",
            buildJsonObject {
                put("threadId", "$SONG_THREAD$songId")
                put("content", content)
                replyTo?.let { put("commentId", it) }
            },
        ).requireOk()
        return r.decode<PostedCommentDto>().comment?.takeIf { it.commentId != 0L }?.toComment()
    }

    // —— Playlists of one's own ——

    /** Creates a playlist; [private] ones only the owner sees. Returns the new playlist. */
    suspend fun createPlaylist(name: String, private: Boolean = false): Playlist {
        val r = client.weapi(
            "/api/playlist/create",
            buildJsonObject { put("name", name); put("privacy", if (private) "10" else "0"); put("type", "NORMAL") },
        ).requireOk()
        val dto = r.decode<CreatedPlaylistDto>()
        return dto.playlist?.toPlaylist()?.takeIf { it.id != 0L } ?: Playlist(dto.id, name, null, 0)
    }

    suspend fun deletePlaylist(id: Long) {
        client.weapi("/api/playlist/remove", buildJsonObject { put("ids", "[$id]") }).requireOk()
    }

    suspend fun renamePlaylist(id: Long, name: String) {
        client.eapi("/api/playlist/update/name", buildJsonObject { put("id", id); put("name", name) }).requireOk()
    }

    suspend fun addToPlaylist(playlistId: Long, songIds: List<Long>) = manipulateTracks("add", playlistId, songIds)

    suspend fun removeFromPlaylist(playlistId: Long, songIds: List<Long>) = manipulateTracks("del", playlistId, songIds)

    private suspend fun manipulateTracks(op: String, playlistId: Long, songIds: List<Long>) {
        fun body(ids: List<Long>) = buildJsonObject {
            put("op", op)
            put("pid", playlistId)
            put("trackIds", ids.joinToString(",", "[", "]") { "\"$it\"" })
            put("imme", "true")
        }
        val r = client.eapi("/api/playlist/manipulate/tracks", body(songIds))
        // NetEase sometimes answers 512 to this; the reference client sends the ids twice then.
        val final = if (r.code == 512) client.eapi("/api/playlist/manipulate/tracks", body(songIds + songIds)) else r
        if (final.code == 502) throw NeteaseException(502, final.message ?: "歌单里已经有这首歌了")
        final.requireOk()
    }

    /** Collects someone else's playlist into one's library, or lets it go. */
    suspend fun subscribePlaylist(id: Long, subscribe: Boolean) {
        val data = buildJsonObject {
            put("id", id)
            if (subscribe) put("checkToken", NeteaseClient.CHECK_TOKEN)
        }
        client.eapi("/api/playlist/${if (subscribe) "subscribe" else "unsubscribe"}", data, checkToken = true).requireOk()
    }

    // —— Play records ——

    /**
     * Tells NetEase a song was listened to for [seconds], so it shows in the account's history and
     * 听歌排行. [end] is `playend` when it played through, `ui` when skipped.
     */
    suspend fun scrobble(songId: Long, seconds: Long, sourceId: Long = 0, end: String = "playend") {
        val log = buildJsonArray {
            add(
                buildJsonObject {
                    put("action", "play")
                    put(
                        "json",
                        buildJsonObject {
                            put("download", 0)
                            put("end", end)
                            put("id", songId)
                            put("sourceId", if (sourceId != 0L) sourceId.toString() else "")
                            put("time", seconds)
                            put("type", "song")
                            put("wifi", 0)
                            put("source", "list")
                            put("mainsite", 1)
                            put("content", "")
                        },
                    )
                },
            )
        }
        client.weapi("/api/feedback/weblog", buildJsonObject { put("logs", log.toString()) }).requireOk()
    }

    /** What the account played lately, on any device, newest first. */
    suspend fun recentPlays(limit: Int = 100): List<RecentPlay> =
        client.weapi("/api/play-record/song/list", buildJsonObject { put("limit", limit) }).requireOk()
            .decode<RecentPlaysDto>().data?.list.orEmpty()
            .mapNotNull { item -> item.data?.toSong()?.takeIf { it.id != 0L }?.let { RecentPlay(it, item.playTime) } }

    /** 听歌排行: the last week's or all-time most played. Fails with -2 when the account hides it. */
    suspend fun listeningRanking(uid: Long, week: Boolean): List<RankedSong> {
        val r = client.weapi("/api/v1/play/record", buildJsonObject { put("uid", uid); put("type", if (week) 1 else 0) })
        if (r.code == -2) throw NeteaseException(-2, "这个账号没有公开听歌排行")
        val dto = r.requireOk().decode<RankingDto>()
        return (if (week) dto.weekData else dto.allData).orEmpty().mapNotNull { row ->
            row.song?.toSong()?.let { RankedSong(it, row.playCount, row.score) }
        }
    }

    // —— Playback ——

    suspend fun lyrics(songId: Long): LyricsSource {
        val r = client.eapi(
            "/api/song/lyric/v1",
            buildJsonObject {
                put("id", songId); put("cp", false)
                listOf("tv", "lv", "rv", "kv", "yv", "ytv", "yrv").forEach { put(it, 0) }
            },
        ).requireOk()
        val dto = r.decode<LyricDto>()
        return LyricsSource(
            lrc = dto.lrc?.lyric?.takeIf { it.isNotBlank() },
            translation = dto.tlyric?.lyric?.takeIf { it.isNotBlank() },
            wordTimed = dto.yrc?.lyric?.takeIf { it.isNotBlank() },
            instrumental = dto.pureMusic == true || dto.nolyric == true,
        )
    }

    /** The address to play [songId] at [quality]; NetEase may hand back a lower one. */
    suspend fun stream(songId: Long, quality: Quality): Stream {
        val r = client.eapi(
            "/api/song/enhance/player/url/v1",
            buildJsonObject { put("ids", "[$songId]"); put("level", quality.level); put("encodeType", "flac") },
        ).requireOk()
        val d = r.decode<UrlDto>().data.firstOrNull() ?: return Stream(songId, null, null, 0, null, 0, 0, false, 404)
        return Stream(
            songId = songId,
            // As given: some audio CDN nodes only answer plain http (cleartext is allowed for them).
            url = d.url,
            quality = Quality.fromLevel(d.level),
            bitrate = d.br,
            format = d.type?.lowercase(),
            sizeBytes = d.size,
            fee = d.fee,
            trialOnly = d.freeTrialInfo != null,
            code = d.code,
        )
    }

    private fun NeteaseResponse.requireOk(): NeteaseResponse = if (code == 200) this else throw NeteaseException(code, message)

    private inline fun <reified T> NeteaseResponse.decode(): T = NeteaseClient.json.decodeFromJsonElement(body)

    private companion object {
        /** Comment threads on songs are named `R_SO_4_{id}`. */
        const val SONG_THREAD = "R_SO_4_"
    }
}

// —— Wire formats ——

/** NetEase hands out plain-http image and audio links; its CDNs serve the same over https. */
internal fun String.https(): String {
    if (!startsWith("http://")) return this
    val host = removePrefix("http://").substringBefore('/').substringBefore(':')
    return if (host.endsWith(".126.net") || host.endsWith(".163.com") || host.endsWith(".127.net")) "https://" + removePrefix("http://") else this
}

@Serializable internal class QrKeyDto(val unikey: String? = null)
@Serializable internal class QrScanDto(val nickname: String? = null, val avatarUrl: String? = null)

@Serializable internal class AccountDto(val account: AccountInfoDto? = null, val profile: ProfileDto? = null)
@Serializable internal class AccountInfoDto(val id: Long = 0, val vipType: Int = 0)
@Serializable internal class ProfileDto(val userId: Long = 0, val nickname: String? = null, val avatarUrl: String? = null, val vipType: Int = 0)

@Serializable internal class ArtistDto(val id: Long = 0, val name: String? = null)
@Serializable internal class AlbumDto(val id: Long = 0, val name: String? = null, val picUrl: String? = null)

/** Songs come in two shapes: the current one (`ar`, `al`, `dt`) and the old one (`artists`, `album`, `duration`). */
@Serializable
internal class SongDto(
    val id: Long = 0,
    val name: String? = null,
    val ar: List<ArtistDto>? = null,
    val artists: List<ArtistDto>? = null,
    val al: AlbumDto? = null,
    val album: AlbumDto? = null,
    val dt: Long? = null,
    val duration: Long? = null,
    val fee: Int = 0,
    val alia: List<String>? = null,
    val alias: List<String>? = null,
    val tns: List<String>? = null,
) {
    fun toSong(): Song {
        val a = al ?: album
        return Song(
            id = id,
            name = name.orEmpty(),
            artists = (ar ?: artists).orEmpty().map { Artist(it.id, it.name.orEmpty()) },
            album = Album(a?.id ?: 0, a?.name.orEmpty(), a?.picUrl?.https()),
            durationMs = dt ?: duration ?: 0,
            fee = fee,
            alias = (tns ?: alia ?: alias)?.firstOrNull()?.takeIf { it.isNotBlank() },
        )
    }
}

@Serializable internal class SongsDto(val songs: List<SongDto> = emptyList())
@Serializable internal class CreatorDto(val userId: Long = 0, val nickname: String? = null)
@Serializable internal class TrackIdDto(val id: Long = 0)

@Serializable
internal class PlaylistDto(
    val id: Long = 0,
    val name: String? = null,
    val coverImgUrl: String? = null,
    val picUrl: String? = null,
    val trackCount: Int = 0,
    val creator: CreatorDto? = null,
    val specialType: Int = 0,
    val subscribed: Boolean = false,
    val description: String? = null,
    val copywriter: String? = null,
    val tracks: List<SongDto> = emptyList(),
    val trackIds: List<TrackIdDto> = emptyList(),
    val updateFrequency: String? = null,
) {
    fun toPlaylist() = Playlist(
        id = id,
        name = name.orEmpty(),
        coverUrl = (coverImgUrl ?: picUrl)?.https(),
        trackCount = trackCount,
        creatorId = creator?.userId ?: 0,
        creatorName = creator?.nickname,
        specialType = specialType,
        subscribed = subscribed,
        description = (description ?: copywriter)?.takeIf { it.isNotBlank() },
        updateNote = updateFrequency?.takeIf { it.isNotBlank() },
    )
}

@Serializable internal class ToplistDto(val list: List<PlaylistDto> = emptyList())
@Serializable internal class PersonalizedDto(val result: List<PlaylistDto> = emptyList())
@Serializable internal class UserPlaylistsDto(val playlist: List<PlaylistDto> = emptyList())
@Serializable internal class PlaylistDetailDto(val playlist: PlaylistDto? = null)
@Serializable internal class LikeListDto(val ids: List<Long> = emptyList())
@Serializable internal class DailyDto(val data: DailyDataDto? = null)
@Serializable internal class DailyDataDto(val dailySongs: List<SongDto> = emptyList())
@Serializable internal class RecommendResourceDto(val recommend: List<PlaylistDto> = emptyList())
@Serializable internal class FmDto(val data: List<SongDto> = emptyList())
@Serializable internal class SearchDto(val result: SearchResultDto? = null)
@Serializable
internal class SearchResultDto(
    val songs: List<SongDto> = emptyList(),
    val artists: List<ArtistInfoDto> = emptyList(),
    val albums: List<AlbumInfoDto> = emptyList(),
    val playlists: List<PlaylistDto> = emptyList(),
)

@Serializable
internal class ArtistInfoDto(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null,
    val img1v1Url: String? = null,
    val briefDesc: String? = null,
    val musicSize: Int = 0,
    val albumSize: Int = 0,
)

@Serializable internal class ArtistPageDto(val artist: ArtistInfoDto? = null, val hotSongs: List<SongDto> = emptyList())

@Serializable
internal class AlbumInfoDto(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null,
    val artist: ArtistDto? = null,
    val artists: List<ArtistDto>? = null,
    val publishTime: Long = 0,
    val description: String? = null,
    val size: Int = 0,
)

@Serializable internal class AlbumDetailDto(val album: AlbumInfoDto? = null, val songs: List<SongDto> = emptyList())
@Serializable internal class LyricTextDto(val lyric: String? = null)

@Serializable
internal class LyricDto(
    val lrc: LyricTextDto? = null,
    val tlyric: LyricTextDto? = null,
    val yrc: LyricTextDto? = null,
    val pureMusic: Boolean? = null,
    val nolyric: Boolean? = null,
)

@Serializable
internal class CloudDto(
    val data: List<CloudItemDto> = emptyList(),
    val count: Int = 0,
    // Byte counts; NetEase sends these as strings.
    val size: Long = 0,
    val maxSize: Long = 0,
    val hasMore: Boolean = false,
)

@Serializable
internal class CloudItemDto(
    val simpleSong: SongDto? = null,
    val songId: Long = 0,
    val songName: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val bitrate: Int = 0,
    val addTime: Long = 0,
) {
    /** Uploads NetEase couldn't match keep their own names; fall back to them where the song record is blank. */
    fun toCloudSong(): CloudSong {
        val base = simpleSong?.toSong()
        val song = Song(
            id = base?.id?.takeIf { it != 0L } ?: songId,
            name = base?.name?.ifEmpty { null } ?: songName?.ifEmpty { null } ?: fileName?.substringBeforeLast('.').orEmpty(),
            artists = base?.artists?.filter { it.name.isNotBlank() }?.ifEmpty { null }
                ?: listOfNotNull(artist?.takeIf { it.isNotBlank() }?.let { Artist(0, it) }),
            album = base?.album?.takeIf { it.name.isNotEmpty() } ?: Album(0, album.orEmpty(), base?.album?.coverUrl),
            durationMs = base?.durationMs ?: 0,
            // The listener's own upload: no VIP mark, whatever the catalogue says.
            fee = 0,
            alias = base?.alias,
        )
        return CloudSong(song, fileName.orEmpty(), fileSize, bitrate, addTime)
    }
}

@Serializable internal class CommentsDto(val data: CommentsDataDto? = null)

@Serializable
internal class CommentsDataDto(
    val comments: List<CommentDto> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val cursor: String? = null,
)

@Serializable internal class CommentUserDto(val userId: Long = 0, val nickname: String? = null, val avatarUrl: String? = null)
@Serializable internal class BeRepliedDto(val user: CommentUserDto? = null, val content: String? = null)
@Serializable internal class IpLocationDto(val location: String? = null)

@Serializable
internal class CommentDto(
    val commentId: Long = 0,
    val user: CommentUserDto? = null,
    val content: String? = null,
    val time: Long = 0,
    val likedCount: Int = 0,
    val liked: Boolean = false,
    val beReplied: List<BeRepliedDto>? = null,
    val ipLocation: IpLocationDto? = null,
) {
    fun toComment() = Comment(
        id = commentId,
        user = CommentUser(user?.userId ?: 0, user?.nickname.orEmpty(), user?.avatarUrl?.https()),
        content = content.orEmpty(),
        timeMs = time,
        likedCount = likedCount,
        liked = liked,
        replyTo = beReplied?.firstOrNull()?.let { QuotedComment(it.user?.nickname.orEmpty(), it.content.orEmpty()) },
        location = ipLocation?.location?.takeIf { it.isNotBlank() },
    )
}

@Serializable internal class PostedCommentDto(val comment: CommentDto? = null)
@Serializable internal class CreatedPlaylistDto(val id: Long = 0, val playlist: PlaylistDto? = null)

@Serializable internal class RecentPlaysDto(val data: RecentPlaysDataDto? = null)
@Serializable internal class RecentPlaysDataDto(val list: List<RecentPlayDto> = emptyList())
@Serializable internal class RecentPlayDto(val playTime: Long = 0, val data: SongDto? = null)

@Serializable internal class RankingDto(val weekData: List<RankedSongDto>? = null, val allData: List<RankedSongDto>? = null)
@Serializable internal class RankedSongDto(val playCount: Int = 0, val score: Int = 0, val song: SongDto? = null)

@Serializable internal class UrlDto(val data: List<UrlItemDto> = emptyList())

@Serializable
internal class UrlItemDto(
    val id: Long = 0,
    val url: String? = null,
    val br: Int = 0,
    val size: Long = 0,
    val code: Int = 0,
    val type: String? = null,
    val level: String? = null,
    val fee: Int = 0,
    val freeTrialInfo: JsonObject? = null,
)
