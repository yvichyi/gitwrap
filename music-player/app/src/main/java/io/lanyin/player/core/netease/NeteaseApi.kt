package io.lanyin.player.core.netease

import io.lanyin.player.core.model.Album
import io.lanyin.player.core.model.Artist
import io.lanyin.player.core.model.LyricsSource
import io.lanyin.player.core.model.Playlist
import io.lanyin.player.core.model.Quality
import io.lanyin.player.core.model.Song
import io.lanyin.player.core.model.Stream
import io.lanyin.player.core.model.UserProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
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

    /** Registers the anonymous account used before login, once per install. Best effort. */
    suspend fun ensureAnonymous() {
        val s = client.currentSession()
        if (s.loggedIn || s.musicA != null) return
        val r = client.weapi("/api/register/anonimous", buildJsonObject { put("username", NeteaseCrypto.anonymousUsername(s.deviceId)) })
        if (r.code == 200) client.updateSession { it.withCookies(r.setCookies) } else throw NeteaseException(r.code, r.message ?: "匿名账号注册失败")
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

    suspend fun search(keywords: String, limit: Int = 30, offset: Int = 0): List<Song> {
        val r = client.eapi(
            "/api/cloudsearch/pc",
            buildJsonObject { put("s", keywords); put("type", 1); put("limit", limit); put("offset", offset); put("total", true) },
        ).requireOk()
        return r.decode<SearchDto>().result?.songs.orEmpty().map { it.toSong() }
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
            url = d.url?.https(),
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
}

// —— Wire formats ——

internal fun String.https(): String = if (startsWith("http://")) "https://" + removePrefix("http://") else this

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
    )
}

@Serializable internal class UserPlaylistsDto(val playlist: List<PlaylistDto> = emptyList())
@Serializable internal class PlaylistDetailDto(val playlist: PlaylistDto? = null)
@Serializable internal class LikeListDto(val ids: List<Long> = emptyList())
@Serializable internal class DailyDto(val data: DailyDataDto? = null)
@Serializable internal class DailyDataDto(val dailySongs: List<SongDto> = emptyList())
@Serializable internal class RecommendResourceDto(val recommend: List<PlaylistDto> = emptyList())
@Serializable internal class FmDto(val data: List<SongDto> = emptyList())
@Serializable internal class SearchDto(val result: SearchResultDto? = null)
@Serializable internal class SearchResultDto(val songs: List<SongDto> = emptyList())
@Serializable internal class LyricTextDto(val lyric: String? = null)

@Serializable
internal class LyricDto(
    val lrc: LyricTextDto? = null,
    val tlyric: LyricTextDto? = null,
    val yrc: LyricTextDto? = null,
    val pureMusic: Boolean? = null,
    val nolyric: Boolean? = null,
)

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
