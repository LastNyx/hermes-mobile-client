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
    fun promptToReplyIs8AndInsideTheReplyIs2() {
        assertEquals(8, itemGapBefore(user, reply))
        assertEquals(8, itemGapBefore(user, reasoning))
        assertEquals(2, itemGapBefore(reasoning, reply))
        assertEquals(2, itemGapBefore(reply, note))
        assertEquals(8, itemGapBefore(note, nextUser))
        assertEquals(6, REASONING_VERTICAL_PADDING)
        assertEquals(4, TOOL_DETAIL_VERTICAL_PADDING)
        assertEquals(4, TOOL_HEADER_VERTICAL_PADDING)
        assertEquals(0, itemGapBefore(null, user))
    }
}
