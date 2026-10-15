package io.lanyin.player.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import io.lanyin.player.R
import io.lanyin.player.core.model.LyricLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The lyrics of what is playing, for what shows them outside the app: the status bar. */
class NowPlayingLyrics {
    data class Lyrics(val songId: Long, val title: String, val artist: String, val lines: List<LyricLine>)

    private val _current = MutableStateFlow<Lyrics?>(null)
    val current: StateFlow<Lyrics?> = _current.asStateFlow()

    fun publish(lyrics: Lyrics?) {
        _current.value = lyrics
    }
}

/**
 * Which line is being sung at a position, and the piece of it a status-bar capsule has room for.
 * A capsule shows its whole text only when it is short (under seven characters), so a long line
 * is cut at its spaces and punctuation into pieces that follow the singing.
 */
object LyricTicker {
    /** Lyrics are a beat early in the status bar, which takes a moment to redraw. */
    const val LEAD_MS = 150L

    data class Shown(val line: String, val piece: String)

    fun at(lines: List<LyricLine>, positionMs: Long): Shown? {
        val at = positionMs + LEAD_MS
        val i = lines.indexOfLast { it.startMs <= at }
        if (i < 0) return null
        val line = lines[i]
        val text = line.text.trim()
        if (text.isEmpty()) return null
        val pieces = pieces(text)
        if (pieces.size == 1) return Shown(text, text)
        // How far through the line's characters the singing is: by word timings when known, else by time.
        val progress = if (line.words != null) {
            line.charProgress(at)
        } else {
            ((at - line.startMs).toFloat() / (line.endMs - line.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
        }
        val sung = (progress * pieces.sumOf { it.length }).toInt()
        var seen = 0
        for (p in pieces) {
            seen += p.length
            if (sung < seen) return Shown(text, p)
        }
        return Shown(text, pieces.last())
    }

    private fun isCjk(c: Char) = Character.UnicodeScript.of(c.code).let {
        it == Character.UnicodeScript.HAN || it == Character.UnicodeScript.HIRAGANA || it == Character.UnicodeScript.KATAKANA || it == Character.UnicodeScript.HANGUL
    }

    private val breaks = setOf(' ', '　', '，', '。', '、', '！', '？', '；', '：', ',', '.', '!', '?', ';', ':', '…', '·', '～', '~', '/')

    /** [text] in pieces of at most six characters (ten for Latin script), cut at natural breaks where possible. */
    fun pieces(text: String): List<String> {
        val max = if (text.any(::isCjk)) 6 else 10
        if (text.length <= max) return listOf(text)
        // Phrases between breaks, punctuation dropped.
        val phrases = mutableListOf<String>()
        val sb = StringBuilder()
        for (c in text) {
            if (c in breaks) {
                if (sb.isNotEmpty()) phrases += sb.toString()
                sb.clear()
            } else {
                sb.append(c)
            }
        }
        if (sb.isNotEmpty()) phrases += sb.toString()
        val out = mutableListOf<String>()
        val join = if (text.any(::isCjk)) "" else " "
        var current = ""
        for (phrase in phrases) {
            // A phrase too long by itself is cut evenly.
            val chunks = if (phrase.length <= max) listOf(phrase) else phrase.chunkedEvenly(max)
            for (chunk in chunks) {
                val joined = if (current.isEmpty()) chunk else current + join + chunk
                if (joined.length <= max) {
                    current = joined
                } else {
                    out += current
                    current = chunk
                }
            }
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    /** Pieces of at most [max] characters, as even as possible (「一二三四五六七八」→「一二三四」「五六七八」). */
    private fun String.chunkedEvenly(max: Int): List<String> {
        val n = (length + max - 1) / max
        val size = (length + n - 1) / n
        return chunked(size)
    }
}

/** Somewhere the current lyric is shown outside the app. */
interface LyricSink {
    /** Shows [shown] for the song [title] by [artist]; null clears it. */
    fun show(shown: LyricTicker.Shown?, title: String, artist: String)
}

/**
 * Feeds the status bar while music plays: works out the line being sung a few times a second
 * and tells each [LyricSink] when what it shows changes; clears them when playback pauses or the
 * song has no lyrics.
 */
class StatusLyrics(
    private val scope: CoroutineScope,
    private val lyrics: StateFlow<NowPlayingLyrics.Lyrics?>,
    private val position: () -> Long,
    private val playing: () -> Boolean,
    private val sinks: List<LyricSink>,
) {
    private var job: Job? = null
    private val last = HashMap<LyricSink, LyricTicker.Shown?>()

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                tick()
                delay(if (playing()) 120 else 600)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        for (s in sinks) push(s, null, "", "")
    }

    /** One look at the position; public for tests. */
    fun tick() {
        val now = lyrics.value
        val shown = if (now != null && playing()) LyricTicker.at(now.lines, position()) else null
        for (s in sinks) push(s, shown, now?.title.orEmpty(), now?.artist.orEmpty())
    }

    private fun push(sink: LyricSink, shown: LyricTicker.Shown?, title: String, artist: String) {
        if (last.containsKey(sink) && last[sink] == shown) return
        last[sink] = shown
        sink.show(shown, title, artist)
    }
}

/**
 * Meizu's status bar lyrics, which Flyme draws from a media notification's ticker text when two
 * of its own flags are set; the Lyric Getter and SuperLyric modules read the same on other
 * phones. Wraps the media notification provider so every notification Media3 posts carries the
 * current line, and [show] asks Media3 to post again when the line changes.
 */
@OptIn(UnstableApi::class)
class TickerLyrics(private val base: MediaNotification.Provider) : MediaNotification.Provider, LyricSink {
    @Volatile
    var enabled = true

    @Volatile
    private var line: String? = null

    /** The next notification only changes the lyric, so Flyme needn't animate the rest. */
    @Volatile
    private var lyricOnly = false

    /** Posts the media notification again; set by the service. */
    var repost: () -> Unit = {}

    override fun show(shown: LyricTicker.Shown?, title: String, artist: String) {
        val text = shown?.line.takeIf { enabled }
        if (text == line) return
        line = text
        lyricOnly = true
        repost()
    }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val callback = MediaNotification.Provider.Callback { onNotificationChangedCallback.onNotificationChanged(decorate(it, lyricOnly = false)) }
        val made = base.createNotification(mediaSession, mediaButtons, actionFactory, callback)
        val only = lyricOnly
        lyricOnly = false
        return decorate(made, only)
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        base.handleCustomCommand(session, action, extras)

    private fun decorate(n: MediaNotification, lyricOnly: Boolean): MediaNotification {
        apply(n.notification, line, lyricOnly)
        return n
    }

    companion object {
        /** Flyme: keep showing the ticker in the status bar. */
        const val FLAG_ALWAYS_SHOW_TICKER = 0x01000000

        /** Flyme: only the ticker changed. */
        const val FLAG_ONLY_UPDATE_TICKER = 0x02000000

        /** Puts [line] in [notification]'s ticker with Meizu's flags, or takes it out. */
        fun apply(notification: Notification, line: String?, lyricOnly: Boolean) {
            if (line == null) {
                notification.tickerText = null
                notification.flags = notification.flags and (FLAG_ALWAYS_SHOW_TICKER or FLAG_ONLY_UPDATE_TICKER).inv()
            } else {
                notification.tickerText = line
                notification.flags = notification.flags or FLAG_ALWAYS_SHOW_TICKER or (if (lyricOnly) FLAG_ONLY_UPDATE_TICKER else 0)
                if (!lyricOnly) notification.flags = notification.flags and FLAG_ONLY_UPDATE_TICKER.inv()
            }
        }
    }
}

/**
 * Android 16's Live Updates: an ongoing notification the system promotes to a capsule in the
 * status bar, whose short text here is the piece of the line being sung. Its card (always
 * expanded) shows the whole line with the song beneath.
 */
class LyricCapsule(private val context: Context) : LyricSink {
    @Volatile
    var enabled = true

    private val manager = NotificationManagerCompat.from(context)
    private var shown = false

    /** Live Updates exist from Android 16. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 36

    override fun show(shown: LyricTicker.Shown?, title: String, artist: String) {
        if (shown == null || !enabled || !supported) {
            if (this.shown) manager.cancel(NOTIFICATION_ID)
            this.shown = false
            return
        }
        ensureChannel()
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, io.lanyin.player.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify_lyric)
            .setContentTitle(shown.line)
            .setContentText(listOf(title, artist).filter { it.isNotBlank() }.joinToString(" · "))
            .setShortCriticalText(shown.piece)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
        this.shown = true
    }

    private fun ensureChannel() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        if (system.getNotificationChannel(CHANNEL_ID) != null) return
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.lyrics_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    /** Whether the system will show the capsule (the user can turn Live Updates off per app). */
    fun allowed(): Boolean = supported && runCatching { manager.canPostPromotedNotifications() }.getOrDefault(false)

    companion object {
        const val CHANNEL_ID = "lyrics"
        const val NOTIFICATION_ID = 3
    }
}
