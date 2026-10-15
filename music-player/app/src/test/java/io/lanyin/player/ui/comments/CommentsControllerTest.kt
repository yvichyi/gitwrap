package io.lanyin.player.ui.comments

import io.lanyin.player.core.model.Comment
import io.lanyin.player.core.model.CommentPage
import io.lanyin.player.core.model.CommentSort
import io.lanyin.player.core.model.CommentUser
import io.lanyin.player.core.netease.NeteaseException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paging, likes that undo themselves on failure, and posting, without NetEase. */
@OptIn(ExperimentalCoroutinesApi::class)
class CommentsControllerTest {

    private val me = CommentUser(1, "我", null)

    private fun comment(id: Long, likes: Int = 0, liked: Boolean = false) =
        Comment(id, CommentUser(100 + id, "听众$id", null), "第 $id 条", 1_700_000_000_000L - id, likes, liked)

    private class FakeSource : CommentSource {
        val asked = mutableListOf<Triple<CommentSort, Int, String?>>()
        var failLike = false
        var posted: Comment? = null
        var postedArgs: Pair<String, Long?>? = null

        override suspend fun page(songId: Long, sort: CommentSort, pageNo: Int, cursor: String?): CommentPage {
            asked += Triple(sort, pageNo, cursor)
            val base = (pageNo - 1) * 2L
            return CommentPage(
                listOf(
                    Comment(base + 1, CommentUser(1, "a", null), "c${base + 1}", 0, 5, false),
                    Comment(base + 2, CommentUser(2, "b", null), "c${base + 2}", 0, 0, false),
                ),
                total = 6, hasMore = pageNo < 3, cursor = "cur$pageNo",
            )
        }

        override suspend fun like(songId: Long, commentId: Long, like: Boolean) {
            if (failLike) throw NeteaseException(250, "操作频繁")
        }

        override suspend fun post(songId: Long, content: String, replyTo: Long?): Comment? {
            postedArgs = content to replyTo
            return posted
        }
    }

    private fun controller(source: FakeSource, user: CommentUser? = me) =
        CommentsController(7, source, TestScope(UnconfinedTestDispatcher()), user, now = { 1_700_000_000_000L })

    @Test
    fun `pages follow the cursor and stop at the end`() {
        val src = FakeSource()
        val c = controller(src)
        c.start()
        assertEquals(listOf(1L, 2L), c.comments.map { it.id })
        assertEquals(6, c.total)
        c.loadMore()
        c.loadMore()
        assertEquals((1L..6L).toList(), c.comments.map { it.id })
        assertFalse(c.hasMore)
        c.loadMore()
        assertEquals(listOf(null, "cur1", "cur2"), src.asked.map { it.third })
    }

    @Test
    fun `changing the order starts again from the first page`() {
        val src = FakeSource()
        val c = controller(src)
        c.start()
        c.loadMore()
        c.select(CommentSort.NEWEST)
        assertEquals(CommentSort.NEWEST, c.sort)
        assertEquals(listOf(1L, 2L), c.comments.map { it.id })
        assertEquals(Triple(CommentSort.NEWEST, 1, null), src.asked.last())
    }

    @Test
    fun `a like shows at once and goes back if refused`() {
        val src = FakeSource()
        val c = controller(src)
        c.start()
        c.toggleLike(c.comments.first())
        assertTrue(c.comments.first().liked)
        assertEquals(6, c.comments.first().likedCount)

        src.failLike = true
        c.toggleLike(c.comments.first())
        assertTrue(c.comments.first().liked)
        assertEquals(6, c.comments.first().likedCount)
        assertTrue(c.notice!!.contains("操作频繁"))
    }

    @Test
    fun `posting puts the comment on top and clears the box`() {
        val src = FakeSource()
        val c = controller(src)
        c.start()
        c.replyTo = c.comments[1]
        c.draft = "  同感  "
        c.send()
        assertEquals("同感" to 2L, src.postedArgs)
        val mine = c.comments.first()
        assertEquals("同感", mine.content)
        assertEquals("我", mine.user.nickname)
        assertEquals("b", mine.replyTo?.nickname)
        assertEquals(7, c.total)
        assertEquals("", c.draft)
        assertNull(c.replyTo)
    }

    @Test
    fun `signed out, liking and posting ask for a login instead`() {
        val src = FakeSource()
        val c = controller(src, user = null)
        c.start()
        c.toggleLike(c.comments.first())
        assertFalse(c.comments.first().liked)
        assertEquals("登录后才能点赞", c.notice)
        c.draft = "你好"
        c.send()
        assertNull(src.postedArgs)
        assertEquals("登录后才能发表评论", c.notice)
    }

    @Test
    fun `a failed first page offers a retry`() {
        var fail = true
        val src = object : CommentSource {
            override suspend fun page(songId: Long, sort: CommentSort, pageNo: Int, cursor: String?): CommentPage {
                if (fail) throw java.io.IOException("offline")
                return CommentPage(listOf(comment(1)), 1, false, null)
            }
            override suspend fun like(songId: Long, commentId: Long, like: Boolean) = Unit
            override suspend fun post(songId: Long, content: String, replyTo: Long?): Comment? = null
        }
        val c = CommentsController(7, src, TestScope(UnconfinedTestDispatcher()), me)
        c.start()
        assertEquals("连不上网易云，检查一下网络", c.error)
        fail = false
        c.retry()
        assertNull(c.error)
        assertEquals(1, c.comments.size)
    }
}
