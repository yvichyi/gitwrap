package io.lanyin.player.data.library

import io.lanyin.player.core.model.Album
import io.lanyin.player.core.model.Artist
import io.lanyin.player.core.model.Song
import io.lanyin.player.core.model.UserProfile
import io.lanyin.player.core.netease.MemorySessionStore
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.core.netease.NeteaseClient
import io.lanyin.player.core.netease.NeteaseSession
import io.lanyin.player.data.account.AccountRepository
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Playlist management as the screens use it: the calls made, and what is kept afterwards. */
class LibraryRepositoryTest {
    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val paths = CopyOnWriteArrayList<String>()
    private var subscribeReply = """{"code":200}"""
    private lateinit var library: LibraryRepository

    private val song = { id: Long -> """{"id":$id,"name":"歌$id","ar":[{"id":1,"name":"澜"}],"al":{"id":1,"name":"潮"},"dt":200000}""" }

    @Before
    fun setUp() = runBlocking {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                paths += path
                val body = when (path) {
                    "/eapi/song/like/get" -> """{"code":200,"ids":[1]}"""
                    "/weapi/user/playlist" -> """{"code":200,"playlist":[
                        {"id":5,"name":"我喜欢的音乐","specialType":5,"trackCount":1,"creator":{"userId":1}},
                        {"id":10,"name":"夜里听","trackCount":2,"creator":{"userId":1}},
                        {"id":20,"name":"别人的","trackCount":9,"creator":{"userId":2},"subscribed":true}]}"""
                    "/eapi/v6/playlist/detail" -> """{"code":200,"playlist":{"id":10,"name":"夜里听","trackCount":2,"creator":{"userId":1},
                        "tracks":[${song(1)},${song(2)}],"trackIds":[{"id":1},{"id":2}]}}"""
                    "/weapi/playlist/create" -> """{"code":200,"id":30,"playlist":{"id":30,"name":"新的","trackCount":0,"creator":{"userId":1}}}"""
                    "/eapi/playlist/subscribe", "/eapi/playlist/unsubscribe" -> subscribeReply
                    else -> """{"code":200}"""
                }
                return MockResponse().setBody(body)
            }
        }
        server.start()
        val signedIn = NeteaseSession.fresh().withCookies(mapOf("MUSIC_U" to "u")).copy(profile = UserProfile(1, "我", null, 11))
        val api = NeteaseApi(NeteaseClient(OkHttpClient(), MemorySessionStore(signedIn), apiBase = server.url("/"), webBase = server.url("/")))
        library = LibraryRepository(api, AccountRepository(api, scope), scope)
        api.currentSession()
        withTimeout(5_000) { library.playlists.first { it != null } }
        Unit
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun s(id: Long) = Song(id, "歌$id", listOf(Artist(1, "澜")), Album(1, "潮", null), 200_000)

    @Test
    fun `own playlists can be edited, others' collected`() {
        val all = library.playlists.value!!
        assertFalse(library.isOwn(all[0]))
        assertTrue(library.isOwn(all[1]))
        assertFalse(library.canCollect(all[1]))
        assertTrue(library.canCollect(all[2]))
        assertEquals(listOf(10L), library.created().map { it.id })
    }

    @Test
    fun `taking a song out updates the kept playlist at once`() = runBlocking {
        assertEquals(2, library.playlist(10).songs.size)
        val before = library.revision.value
        assertNull(library.removeFromPlaylist(10, 2))
        assertTrue("/eapi/playlist/manipulate/tracks" in paths)
        val kept = library.playlist(10)
        assertEquals(listOf(1L), kept.songs.map { it.id })
        assertEquals(1, kept.playlist.trackCount)
        assertTrue(library.revision.value > before)
    }

    @Test
    fun `renaming and deleting change the shelf`() = runBlocking {
        library.playlist(10)
        assertNull(library.renamePlaylist(10, "  深夜  "))
        assertEquals("深夜", library.playlists.value!!.first { it.id == 10L }.name)
        assertEquals("深夜", library.playlist(10).playlist.name)
        assertNull(library.deletePlaylist(10))
        assertTrue(library.playlists.value!!.none { it.id == 10L })
        assertTrue("/weapi/playlist/remove" in paths)
    }

    @Test
    fun `a new playlist takes the songs it was made for`() = runBlocking {
        val made = library.createPlaylist("新的", private = false, songs = listOf(s(1), s(2))).getOrThrow()
        assertEquals(30L, made.id)
        assertEquals(2, made.trackCount)
        val create = paths.indexOf("/weapi/playlist/create")
        assertTrue(create >= 0 && paths.indexOf("/eapi/playlist/manipulate/tracks") > create)
    }

    @Test
    fun `a refused collect says why, and changes nothing`() = runBlocking {
        subscribeReply = """{"code":301,"message":"需要登录"}"""
        val other = library.playlists.value!!.first { it.id == 20L }
        val before = library.revision.value
        assertEquals("登录已过期，重新登录后才能取消收藏", library.setSubscribed(other, false))
        assertEquals(before, library.revision.value)
        subscribeReply = """{"code":200}"""
        assertNull(library.setSubscribed(other, false))
        assertTrue("/eapi/playlist/unsubscribe" in paths)
    }
}
