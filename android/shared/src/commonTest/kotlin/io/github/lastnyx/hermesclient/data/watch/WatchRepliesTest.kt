package io.github.lastnyx.hermesclient.data.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRepliesTest {
    @Test
    fun replyReachesOnlyTheRegisteredSessionAndStopsAfterForget() {
        val replies = WatchReplies()
        val got1 = mutableListOf<WatchReply>()
        val got2 = mutableListOf<WatchReply>()
        replies.register("s1") { got1 += it }
        replies.register("s2") { got2 += it }

        val answer = watchAnswer("srq-1", "once")
        assertEquals(WatchReply.Approve("srq-1", "once"), answer)
        assertEquals(WatchReply.Approve("srq-1", "deny"), watchAnswer("srq-1", "deny"))
        assertNull(watchAnswer("srq-1", "always"))

        assertTrue(replies.reply("s1", answer!!))
        assertEquals(listOf<WatchReply>(WatchReply.Approve("srq-1", "once")), got1)
        assertEquals(emptyList<WatchReply>(), got2)

        replies.forget("s1")
        assertFalse(replies.reply("s1", answer))
        assertEquals(1, got1.size)
    }

    @Test
    fun typedAnswerReachesCallbackOnceAndBlankDoesNot() {
        val replies = WatchReplies()
        val got = mutableListOf<WatchReply>()
        replies.register("s1") { got += it }

        assertNull(watchText("srq-2", "q1", "  "))
        assertNull(watchText("srq-2", "q1", ""))
        val answer = watchText("srq-2", "q1", "yes")
        assertEquals(WatchReply.Answer("srq-2", "q1", "yes"), answer)

        assertTrue(replies.reply("s1", answer!!))
        assertEquals(listOf<WatchReply>(WatchReply.Answer("srq-2", "q1", "yes")), got)
    }
}
