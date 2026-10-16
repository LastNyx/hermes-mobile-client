package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.ChatItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageActionsTest {
    private fun user(key: String, rowId: Long? = null) = ChatItem.Message(key, "user", "q$key", rowId = rowId)
    private fun reply(key: String, text: String = "a", streaming: Boolean = false) = ChatItem.Message(key, "assistant", text, streaming)
    private val none = MessageActions(emptySet(), null, null)

    @Test
    fun idleChatOffersEditOnEveryUserMessageWithARowId() {
        val items = listOf(user("u1", 1), reply("a1"), user("u2"), reply("a2"), user("u3", 3), reply("a3"))
        assertEquals(MessageActions(setOf("u1", "u3"), "a3", "a3"), messageActions(items, running = false))
    }

    @Test
    fun onlyUserMessageOffersEditOnly() {
        assertEquals(MessageActions(setOf("u1"), null, null), messageActions(listOf(user("u1", 1)), running = false))
    }

    @Test
    fun streamingOrEmptyLastReplyOffersNoRetryOrRegenerate() {
        assertEquals(MessageActions(setOf("u1"), null, null), messageActions(listOf(user("u1", 1), reply("a1", streaming = true)), false))
        assertEquals(MessageActions(setOf("u1"), null, null), messageActions(listOf(user("u1", 1), reply("a1", text = "")), false))
    }

    @Test
    fun runningOffersNothing() {
        assertEquals(none, messageActions(listOf(user("u1", 1), reply("a1")), running = true))
    }

    @Test
    fun emptyChatOffersNothing() {
        assertEquals(none, messageActions(emptyList(), running = false))
    }

    @Test
    fun replyWithoutPriorUserRowIdHasRetryButNoRegenerate() {
        assertEquals(MessageActions(emptySet(), null, "a1"), messageActions(listOf(user("u1"), reply("a1")), running = false))
    }

    @Test
    fun promptBeforeFindsNearestEarlierUserMessageSkippingOtherItems() {
        val items = listOf(user("u1", 1), reply("a1"), user("u2", 2), ChatItem.Note("n1", "x"), reply("a2"))
        assertEquals("u2", promptBefore(items, "a2")?.key)
    }

    @Test
    fun promptBeforeIsNullWithoutEarlierUserMessage() {
        assertNull(promptBefore(listOf(reply("a1"), user("u1", 1)), "a1"))
    }
}
