package io.lanyin.player.core.model

import kotlinx.serialization.Serializable

/** What the app knows about music, independent of where it came from. */
@Serializable
data class Artist(val id: Long, val name: String)

@Serializable
data class Album(val id: Long, val name: String, val coverUrl: String?)

@Serializable
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
    /** How often a chart is renewed, as NetEase puts it (「刚刚更新」「每周五更新」). */
    val updateNote: String? = null,
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

/** One timed lyric line, with its translation when there is one. */
@androidx.compose.runtime.Immutable
data class LyricLine(
    val text: String,
    val translation: String?,
    val startMs: Long,
    val endMs: Long,
    /** Word timings (from YRC) when NetEase has them; [text] is the words joined. */
    val words: List<LyricWord>? = null,
) {
    /**
     * How far through the line's characters [positionMs] is, 0..1, following the words. A line
     * without word timings lights up in one quick stroke as it starts: when each word is sung is
     * unknown, and an even sweep to the next line's start runs behind the voice (the gap holds
     * breaths and instrumental bars too).
     */
    fun charProgress(positionMs: Long): Float {
        val ws = words ?: return ((positionMs - startMs).toFloat() / STROKE_MS.coerceAtMost(endMs - startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
        val total = text.length.coerceAtLeast(1)
        var sung = 0f
        for (w in ws) {
            when {
                positionMs >= w.startMs + w.durationMs -> sung += w.text.length
                positionMs > w.startMs -> {
                    sung += w.text.length * (positionMs - w.startMs).toFloat() / w.durationMs.coerceAtLeast(1)
                    break
                }
                else -> break
            }
        }
        return (sung / total).coerceIn(0f, 1f)
    }

    companion object {
        /** How long a line without word timings takes to light up. */
        const val STROKE_MS = 420L
    }
}

@androidx.compose.runtime.Immutable
data class LyricWord(val text: String, val startMs: Long, val durationMs: Long)

/** An artist's page: who they are and their best-known songs. */
data class ArtistDetail(
    val id: Long,
    val name: String,
    val pictureUrl: String?,
    val brief: String?,
    val songCount: Int,
    val albumCount: Int,
    val hotSongs: List<Song>,
)

/** An album and its tracks. */
data class AlbumDetail(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val artistName: String,
    /** Epoch millis; 0 when unknown. */
    val publishTime: Long,
    val description: String?,
    val songs: List<Song>,
)

/** A row in artist search results. */
data class ArtistHit(val id: Long, val name: String, val pictureUrl: String?, val albumCount: Int)

/** A row in album search results. */
data class AlbumHit(val id: Long, val name: String, val coverUrl: String?, val artistName: String, val size: Int)

/** Raw lyric documents; parsed into timed lines by the lyrics package. */
data class LyricsSource(
    val lrc: String?,
    val translation: String?,
    /** Word-timed lyrics (YRC), when NetEase has them. */
    val wordTimed: String?,
    val instrumental: Boolean,
)

/** A song in the listener's cloud drive, with the file they uploaded. */
data class CloudSong(val song: Song, val fileName: String, val sizeBytes: Long, val bitrate: Int, val addedAt: Long)

/** A page of the cloud drive, and how full it is. */
data class CloudPage(val songs: List<CloudSong>, val count: Int, val usedBytes: Long, val maxBytes: Long, val hasMore: Boolean)

/** How comments are ordered, with NetEase's code for each. */
enum class CommentSort(val code: Int, val label: String) { RECOMMENDED(99, "推荐"), HOT(2, "最热"), NEWEST(3, "最新") }

data class CommentUser(val id: Long, val nickname: String, val avatarUrl: String?)

/** One comment on a song. [replyTo] is the comment it answers, quoted. */
@androidx.compose.runtime.Immutable
data class Comment(
    val id: Long,
    val user: CommentUser,
    val content: String,
    val timeMs: Long,
    val likedCount: Int,
    val liked: Boolean,
    val replyTo: QuotedComment? = null,
    /** 「IP属地」, when NetEase gives it. */
    val location: String? = null,
)

data class QuotedComment(val nickname: String, val content: String)

/** A page of comments; [cursor] asks for the next one. */
data class CommentPage(val comments: List<Comment>, val total: Int, val hasMore: Boolean, val cursor: String?)

/** A song from the account's play history, across all its devices. */
data class RecentPlay(val song: Song, val playedAt: Long)

/** A row of 听歌排行: a song, how often it was played, and its score (100 for the top one). */
data class RankedSong(val song: Song, val playCount: Int, val score: Int)

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
