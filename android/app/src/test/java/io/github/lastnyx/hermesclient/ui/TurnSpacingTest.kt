package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.ChatItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnSpacingTest {
    private val user = ChatItem.Message("m1", "user", "hi")
    private val reply = ChatItem.Message("m2", "assistant", "hello")
    private val reasoning = ChatItem.Reasoning("r1", "thinking")
    private val note = ChatItem.Note("n1", "note")
    private val nextUser = ChatItem.Message("m3", "user", "again")

    @Test
    fun promptToReplyIs12AndInsideTheReplyIs4() {
        assertEquals(12, itemGapBefore(user, reply))
        assertEquals(12, itemGapBefore(user, reasoning))
        assertEquals(4, itemGapBefore(reasoning, reply))
        assertEquals(4, itemGapBefore(reply, note))
        assertEquals(12, itemGapBefore(note, nextUser))
        assertEquals(0, itemGapBefore(null, user))
    }
}
