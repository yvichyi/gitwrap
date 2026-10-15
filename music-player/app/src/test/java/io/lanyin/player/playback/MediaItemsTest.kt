package io.lanyin.player.playback

import android.net.Uri
import io.lanyin.player.core.model.Album
import io.lanyin.player.core.model.Artist
import io.lanyin.player.core.model.Quality
import io.lanyin.player.core.model.Song
import io.lanyin.player.core.model.Stream
import io.lanyin.player.playback.MediaPlayerState.Companion.toMediaItem
import io.lanyin.player.playback.MediaPlayerState.Companion.toTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaItemsTest {

    private val song = Song(
        id = 186016, name = "晴天", artists = listOf(Artist(1, "周杰伦")),
        album = Album(2, "叶惠美", "https://p2.music.126.net/c.jpg"), durationMs = 269_000,
    )

    @Test
    fun `a song survives the trip through the media session`() {
        val item = song.toMediaItem()
        assertEquals("186016", item.mediaId)
        assertEquals(Uri.parse("lanyin://song/186016"), item.localConfiguration!!.uri)
        val track = item.toTrack()
        assertEquals(186016L, track.id)
        assertEquals("晴天", track.title)
        assertEquals("周杰伦", track.artist)
        assertEquals("叶惠美", track.album)
        assertEquals(269_000L, track.durationMs)
        assertEquals("https://p2.music.126.net/c.jpg?param=600y600", track.coverUrl)
    }

    @Test
    fun `queue addresses carry the song id`() {
        assertEquals(42L, AudioPipeline.songId(AudioPipeline.uriFor("42")))
        assertNull(AudioPipeline.songId(Uri.parse("https://m.music.126.net/x.mp3")))
    }

    @Test
    fun `labels for what is playing`() {
        val s = Stream(1, "u", Quality.LOSSLESS, 921_000, "flac", 1, 0, trialOnly = false, code = 200)
        assertEquals("无损", MediaPlayerState.qualityLabel(s))
        assertEquals("FLAC · 921kbps", MediaPlayerState.formatLabel(s))
        assertEquals("试听", MediaPlayerState.qualityLabel(s.copy(trialOnly = true)))
    }
}
