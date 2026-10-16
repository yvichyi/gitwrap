package io.lanyin.player.ui.home

import io.lanyin.player.core.model.Playlist
import io.lanyin.player.core.model.Song
import io.lanyin.player.data.account.AccountState
import io.lanyin.player.data.library.ChartPreview
import io.lanyin.player.data.library.Picks
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.player.PlayerState
import java.time.LocalDateTime

/**
 * What the home page shows and what each part of it does, worked out once so that every theme's
 * layout only has to arrange it. Lists are empty (and [picks] null) until they have loaded; the
 * layouts hold their place with the theme's own artwork meanwhile.
 */
class HomeModel(
    val now: LocalDateTime,
    val account: AccountState,
    val player: PlayerState,
    val recommended: List<Playlist>,
    val charts: List<ChartPreview>,
    val picks: Picks?,
    val openSettings: () -> Unit,
    /** The account page once signed in, the login page before. */
    val openAccount: () -> Unit,
    val login: () -> Unit,
    val openPlayer: () -> Unit,
    val openDaily: () -> Unit,
    /** Plays today's picks from the top (or opens them while they're still loading). */
    val playDaily: () -> Unit,
    val playFm: () -> Unit,
    val openLiked: () -> Unit,
    val openCharts: () -> Unit,
    val openRecent: () -> Unit,
    val openDownloads: () -> Unit,
    val openPlaylist: (Playlist) -> Unit,
) {
    val signedIn: Boolean get() = account is AccountState.LoggedIn
    val profile get() = (account as? AccountState.LoggedIn)?.profile
    val pickSongs: List<Song> get() = picks?.songs.orEmpty()

    fun playPick(index: Int) {
        picks?.let { player.play(it.songs, index, it.title) }
    }

    val dailyNote: String get() = if (signedIn) "每天 6:00 更新" else "登录后生成"
    val fmNote: String get() = if (signedIn) "越听越懂你" else "登录后开启"
    val recommendedNote: String get() = if (signedIn) "为你挑的" else "大家在听"

    /** The ways in, in the order the page offers them: a name, a short code for themes that print one, an icon and a note. */
    val doors: List<Door>
        get() = listOf(
            Door("每日推荐", "DAILY", LineIcon.Note, dailyNote, openDaily),
            Door("私人 FM", "FM", LineIcon.Radio, fmNote, playFm),
            Door("我喜欢", "LIKED", LineIcon.Heart, if (signedIn) "红心歌曲" else "登录后同步", openLiked),
            Door("排行榜", "CHARTS", LineIcon.Chart, "飙升 · 新歌 · 热歌", openCharts),
            Door("最近播放", "RECENT", LineIcon.History, "本机和云端", openRecent),
            Door("下载", "SAVED", LineIcon.Download, "离线也能听", openDownloads),
        )
}

/** One way into the music from the home page. */
class Door(val label: String, val code: String, val icon: LineIcon, val note: String, val onClick: () -> Unit)
