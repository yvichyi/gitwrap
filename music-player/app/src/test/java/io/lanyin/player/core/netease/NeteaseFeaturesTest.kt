package io.lanyin.player.core.netease

import io.lanyin.player.core.model.CommentSort
import java.net.URLDecoder
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Cloud drive, comments, playlist management and play records: what is sent, and how replies read. */
class NeteaseFeaturesTest {

    private lateinit var server: MockWebServer
    private lateinit var api: NeteaseApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = NeteaseClient(
            OkHttpClient(), MemorySessionStore(), apiBase = server.url("/"), webBase = server.url("/"),
            now = { 1_700_000_000_123L }, weapiSecret = { SECRET },
        )
        api = NeteaseApi(client)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun reply(json: String) = server.enqueue(MockResponse().setBody(json))

    private fun form(r: RecordedRequest): Map<String, String> = r.body.readUtf8().split('&').associate {
        val (k, v) = it.split('=', limit = 2)
        k to URLDecoder.decode(v, "UTF-8")
    }

    /** The JSON a request carried, eapi or weapi. */
    private fun sent(r: RecordedRequest): JsonObject {
        val f = form(r)
        val plain = if (r.path!!.startsWith("/eapi/")) {
            String(aes(hex(f.getValue("params")), "e82ckenh8dichen8", null)).split("-36cd479b6b5-")[1]
        } else {
            val once = String(aes(Base64.getDecoder().decode(f.getValue("params")), SECRET, "0102030405060708"))
            String(aes(Base64.getDecoder().decode(once), "0CoJUm6Qyw8W8jud", "0102030405060708"))
        }
        return Json.parseToJsonElement(plain).jsonObject
    }

    private fun aes(data: ByteArray, key: String, iv: String?): ByteArray {
        val cipher = Cipher.getInstance(if (iv == null) "AES/ECB/PKCS5Padding" else "AES/CBC/PKCS5Padding")
        val spec = SecretKeySpec(key.toByteArray(), "AES")
        if (iv == null) cipher.init(Cipher.DECRYPT_MODE, spec) else cipher.init(Cipher.DECRYPT_MODE, spec, IvParameterSpec(iv.toByteArray()))
        return cipher.doFinal(data)
    }

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    // —— Cloud drive ——

    @Test
    fun `cloud pages read sizes sent as strings and fall back to the uploaded names`() = runBlocking {
        reply(
            """{"code":200,"count":2,"size":"5368709120","maxSize":"64424509440","hasMore":true,"data":[
              {"songId":11,"songName":"现场版","artist":"某乐队","album":"巡演","fileName":"现场版.flac","fileSize":31457280,"bitrate":999,"addTime":1690000000000,
               "simpleSong":{"id":11,"name":"现场版","ar":[{"id":0,"name":null}],"al":{"id":0,"name":null,"picUrl":null},"dt":245000,"fee":1}},
              {"songId":12,"songName":"x","fileName":"y.mp3","simpleSong":{"id":12,"name":"海屿你","ar":[{"id":5,"name":"马也_Crabbit"}],"al":{"id":7,"name":"海屿你","picUrl":"http://p1.music.126.net/c.jpg"},"dt":200000}}
            ]}""",
        )
        val page = api.cloud(offset = 30, limit = 30)
        val r = server.takeRequest()
        assertEquals("/weapi/v1/cloud/get", r.path)
        val body = sent(r)
        assertEquals("30", body.str("offset"))
        assertEquals("30", body.str("limit"))

        assertEquals(5_368_709_120L, page.usedBytes)
        assertEquals(64_424_509_440L, page.maxBytes)
        assertTrue(page.hasMore)
        val live = page.songs[0].song
        assertEquals("某乐队", live.artistLine)
        assertEquals("巡演", live.album.name)
        assertEquals(0, live.fee)
        assertEquals(245_000L, live.durationMs)
        assertEquals("现场版.flac", page.songs[0].fileName)
        val matched = page.songs[1].song
        assertEquals("马也_Crabbit", matched.artistLine)
        assertEquals("https://p1.music.126.net/c.jpg", matched.album.coverUrl)
    }

    @Test
    fun `cloud deletion sends the id as a number list`() = runBlocking {
        reply("""{"code":200}""")
        api.deleteFromCloud(42)
        val r = server.takeRequest()
        assertEquals("/weapi/cloud/del", r.path)
        assertEquals("42", sent(r).getValue("songIds").jsonArray.single().jsonPrimitive.content)
    }

    // —— Comments ——

    @Test
    fun `comments ask by thread and cursor and read replies and places`() = runBlocking {
        reply(
            """{"code":200,"data":{"totalCount":1234,"hasMore":true,"cursor":"1699999999000","comments":[
              {"commentId":9,"content":"副歌一出来就哭了","time":1699999999000,"likedCount":321,"liked":true,
               "user":{"userId":3,"nickname":"潮汐","avatarUrl":"http://p1.music.126.net/u.jpg"},
               "beReplied":[{"user":{"nickname":"岸"},"content":"同"}],"ipLocation":{"location":"浙江"}}
            ]}}""",
        )
        val page = api.comments(186016, CommentSort.NEWEST, pageNo = 2, cursor = "1700000000000")
        val r = server.takeRequest()
        assertEquals("/eapi/v2/resource/comments", r.path)
        val body = sent(r)
        assertEquals("R_SO_4_186016", body.str("threadId"))
        assertEquals("3", body.str("sortType"))
        assertEquals("1700000000000", body.str("cursor"))
        assertEquals("2", body.str("pageNo"))

        assertEquals(1234, page.total)
        assertEquals("1699999999000", page.cursor)
        val c = page.comments.single()
        assertEquals("潮汐", c.user.nickname)
        assertEquals("https://p1.music.126.net/u.jpg", c.user.avatarUrl)
        assertEquals("岸", c.replyTo?.nickname)
        assertEquals("浙江", c.location)
        assertTrue(c.liked)
    }

    @Test
    fun `recommended and hot comments page by offset`() = runBlocking {
        reply("""{"code":200,"data":{"comments":[],"totalCount":0,"hasMore":false}}""")
        reply("""{"code":200,"data":{"comments":[],"totalCount":0,"hasMore":false}}""")
        api.comments(1, CommentSort.RECOMMENDED, pageNo = 3)
        api.comments(1, CommentSort.HOT, pageNo = 2)
        val recommended = sent(server.takeRequest())
        assertEquals("40", recommended.str("cursor"))
        assertEquals("99", recommended.str("sortType"))
        assertEquals("normalHot#20", sent(server.takeRequest()).str("cursor"))
    }

    @Test
    fun `liking, posting and replying go to the comment endpoints`() = runBlocking {
        reply("""{"code":200}""")
        reply("""{"code":200,"comment":{"commentId":77,"content":"好听","time":1,"user":{"userId":1,"nickname":"我"}}}""")
        reply("""{"code":200}""")
        api.likeComment(5, 9, like = true)
        val posted = api.postComment(5, "好听")
        val reply = api.postComment(5, "同感", replyTo = 9)

        val like = server.takeRequest()
        assertEquals("/weapi/v1/comment/like", like.path)
        assertEquals("9", sent(like).str("commentId"))
        val add = server.takeRequest()
        assertEquals("/weapi/resource/comments/add", add.path)
        assertEquals("好听", sent(add).str("content"))
        assertEquals(77L, posted?.id)
        val r = server.takeRequest()
        assertEquals("/weapi/resource/comments/reply", r.path)
        assertEquals("9", sent(r).str("commentId"))
        assertNull(reply)
    }

    // —— Playlists ——

    @Test
    fun `creating a playlist returns it, private ones flagged`() = runBlocking {
        reply("""{"code":200,"id":555,"playlist":{"id":555,"name":"夜里听","trackCount":0,"creator":{"userId":1,"nickname":"我"}}}""")
        val p = api.createPlaylist("夜里听", private = true)
        val r = server.takeRequest()
        assertEquals("/weapi/playlist/create", r.path)
        val body = sent(r)
        assertEquals("10", body.str("privacy"))
        assertEquals("NORMAL", body.str("type"))
        assertEquals(555L, p.id)
        assertEquals("夜里听", p.name)
    }

    @Test
    fun `adding tracks retries once on 512 with the ids doubled`() = runBlocking {
        reply("""{"code":512}""")
        reply("""{"code":200}""")
        api.addToPlaylist(9, listOf(1, 2))
        val first = sent(server.takeRequest())
        assertEquals("add", first.str("op"))
        assertEquals("""["1","2"]""", first.str("trackIds"))
        assertEquals("""["1","2","1","2"]""", sent(server.takeRequest()).str("trackIds"))
    }

    @Test
    fun `a song already in the playlist says so`() = runBlocking {
        reply("""{"code":502,"message":"歌单内歌曲重复"}""")
        try {
            api.addToPlaylist(9, listOf(1))
            fail()
        } catch (e: NeteaseException) {
            assertEquals(502, e.code)
            assertEquals("歌单内歌曲重复", e.message)
        }
    }

    @Test
    fun `rename, delete and remove tracks`() = runBlocking {
        repeat(3) { reply("""{"code":200}""") }
        api.renamePlaylist(9, "新名字")
        api.deletePlaylist(9)
        api.removeFromPlaylist(9, listOf(3))
        val rename = server.takeRequest()
        assertEquals("/eapi/playlist/update/name", rename.path)
        assertEquals("新名字", sent(rename).str("name"))
        val delete = server.takeRequest()
        assertEquals("/weapi/playlist/remove", delete.path)
        assertEquals("[9]", sent(delete).str("ids"))
        assertEquals("del", sent(server.takeRequest()).str("op"))
    }

    @Test
    fun `collecting a playlist carries the anti-cheat token`() = runBlocking {
        reply("""{"code":200}""")
        reply("""{"code":200}""")
        api.subscribePlaylist(9, subscribe = true)
        api.subscribePlaylist(9, subscribe = false)
        val sub = server.takeRequest()
        assertEquals("/eapi/playlist/subscribe", sub.path)
        assertTrue(sub.getHeader("Cookie")!!.contains("X-antiCheatToken="))
        val body = sent(sub)
        assertEquals(NeteaseClient.CHECK_TOKEN, body.str("checkToken"))
        assertEquals(NeteaseClient.CHECK_TOKEN, body.getValue("header").jsonObject.str("X-antiCheatToken"))
        val unsub = server.takeRequest()
        assertEquals("/eapi/playlist/unsubscribe", unsub.path)
        assertFalse("checkToken" in sent(unsub))
    }

    // —— Play records ——

    @Test
    fun `a scrobble is one play log as a JSON string`() = runBlocking {
        reply("""{"code":200}""")
        api.scrobble(186016, seconds = 213, sourceId = 3778678, end = "ui")
        val r = server.takeRequest()
        assertEquals("/weapi/feedback/weblog", r.path)
        val log = Json.parseToJsonElement(sent(r).str("logs")).jsonArray.single().jsonObject
        assertEquals("play", log.str("action"))
        val json = log.getValue("json").jsonObject
        assertEquals("186016", json.str("id"))
        assertEquals("213", json.str("time"))
        assertEquals("3778678", json.str("sourceId"))
        assertEquals("ui", json.str("end"))
        assertEquals("song", json.str("type"))
    }

    @Test
    fun `recent plays and the ranking read their songs`() = runBlocking {
        reply("""{"code":200,"data":{"total":1,"list":[{"playTime":1700000000000,"resourceType":"SONG","data":{"id":5,"name":"晴天","ar":[{"id":1,"name":"周杰伦"}],"al":{"id":2,"name":"叶惠美"},"dt":269000}}]}}""")
        reply("""{"code":200,"weekData":[{"playCount":12,"score":100,"song":{"id":5,"name":"晴天","ar":[{"id":1,"name":"周杰伦"}],"al":{"id":2,"name":"叶惠美"},"dt":269000}}]}""")
        reply("""{"code":-2,"message":"无权限访问"}""")
        val recent = api.recentPlays(limit = 50)
        assertEquals("晴天", recent.single().song.name)
        assertEquals(1_700_000_000_000L, recent.single().playedAt)
        assertEquals("50", sent(server.takeRequest()).str("limit"))

        val week = api.listeningRanking(7, week = true)
        assertEquals(12, week.single().playCount)
        val body = sent(server.takeRequest())
        assertEquals("1", body.str("type"))
        assertEquals("7", body.str("uid"))

        try {
            api.listeningRanking(8, week = false)
            fail()
        } catch (e: NeteaseException) {
            assertEquals(-2, e.code)
        }
    }

    private companion object {
        const val SECRET = "0123456789abcdef"
    }
}
