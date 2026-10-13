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
    fun promptToReplyIs16AndInsideTheReplyIsTighter() {
        assertEquals(16, itemGapBefore(user, reply))
        assertEquals(16, itemGapBefore(user, reasoning))
        val inside = listOf(itemGapBefore(reasoning, reply), itemGapBefore(reply, note))
        assertTrue(inside.all { it < 16 })
        assertTrue(itemGapBefore(note, nextUser) >= inside.max())
        assertEquals(0, itemGapBefore(null, user))
    }
}
