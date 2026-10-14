package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.ChatItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageActionsTest {
    private fun user(key: String) = ChatItem.Message(key, "user", "q")
    private fun reply(key: String) = ChatItem.Message(key, "assistant", "a")

    @Test
    fun idleWithReplyOffersRetryOnLastReplyAndMenuOnLastUserMessage() {
        val items = listOf(user("u1"), reply("a1"), user("u2"), reply("a2"))
        assertEquals(LastActions(retryKey = "a2", editKey = "u2"), lastActions(items, running = false))
    }

    @Test
    fun idleWithOnlyUserMessageOffersEditOnly() {
        assertEquals(LastActions(null, "u1"), lastActions(listOf(user("u1")), running = false))
    }

    @Test
    fun runningOffersNothing() {
        val items = listOf(user("u1"), reply("a1"))
        assertEquals(LastActions(null, null), lastActions(items, running = true))
    }

    @Test
    fun emptyChatOffersNothing() {
        assertEquals(LastActions(null, null), lastActions(emptyList(), running = false))
    }
}
