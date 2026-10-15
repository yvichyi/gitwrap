package io.lanyin.player.core.model

import kotlinx.serialization.Serializable

/** What the app knows about music, independent of where it came from. */
data class Artist(val id: Long, val name: String)

data class Album(val id: Long, val name: String, val coverUrl: String?)

data class Song(
    val id: Long,
    val name: String,
    val artists: List<Artist>,
    val album: Album,
    val durationMs: Long,
    /** NetEase's fee class: 0/8 free, 1 VIP, 4 bought separately. */
    val fee: Int = 0,
    /** A subtitle such as 「电视剧《……》插曲」. */
    val alias: String? = null,
) {
    val artistLine: String get() = artists.joinToString(" / ") { it.name }.ifEmpty { "未知歌手" }
}

data class Playlist(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    val creatorId: Long = 0,
    val creatorName: String? = null,
    /** 5 marks the user's own 「我喜欢的音乐」. */
    val specialType: Int = 0,
    val subscribed: Boolean = false,
    val description: String? = null,
)

@Serializable
data class UserProfile(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    /** 0 none; anything else is some kind of VIP (11 is 黑胶VIP). */
    val vipType: Int,
) {
    val isVip: Boolean get() = vipType != 0
}

/** Raw lyric documents; parsed into timed lines by the lyrics package. */
data class LyricsSource(
    val lrc: String?,
    val translation: String?,
    /** Word-timed lyrics (YRC), when NetEase has them. */
    val wordTimed: String?,
    val instrumental: Boolean,
)

/** Audio quality levels, best first, as NetEase names them. */
enum class Quality(val level: String, val label: String) {
    MASTER("jymaster", "超清母带"),
    HIRES("hires", "Hi-Res"),
    LOSSLESS("lossless", "无损"),
    EXHIGH("exhigh", "极高"),
    STANDARD("standard", "标准");

    companion object {
        fun fromLevel(level: String?): Quality? = entries.firstOrNull { it.level == level }
    }
}

/** A playable address for a song at some quality, or why there isn't one. */
data class Stream(
    val songId: Long,
    val url: String?,
    val quality: Quality?,
    val bitrate: Int,
    val format: String?,
    val sizeBytes: Long,
    val fee: Int,
    /** Only a preview clip is available (not entitled to the full song). */
    val trialOnly: Boolean,
    val code: Int,
)
