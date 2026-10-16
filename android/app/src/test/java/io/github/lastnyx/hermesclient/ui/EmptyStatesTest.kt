package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.ChatItem
import io.github.lastnyx.hermesclient.data.chat.ChatState
import io.github.lastnyx.hermesclient.data.sessions.Session
import io.github.lastnyx.hermesclient.data.sessions.SessionListState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmptyStatesTest {
    private val one = Session(id = "a", title = "t", preview = "", lastActive = 0.0)

    @Test
    fun listEmptyTextBranches() {
        assertNull(listEmptyText(SessionListState(loading = true), false))
        assertNull(listEmptyText(SessionListState(error = "boom"), false))
        assertNull(listEmptyText(SessionListState(sessions = listOf(one)), false))
        assertEquals("No chats match your search.", listEmptyText(SessionListState(query = "x"), false))
        assertEquals("Nothing archived.", listEmptyText(SessionListState(archivedPage = true), false))
        assertEquals("No chats match this filter.", listEmptyText(SessionListState(sessions = listOf(one)), true))
        assertEquals("No chats yet. Tap + to start one.", listEmptyText(SessionListState(), false))
    }

    @Test
    fun chatPlaceholderBranches() {
        assertEquals(ChatPlaceholder.Opening, chatPlaceholder(ChatState()))
        assertEquals(ChatPlaceholder.Empty, chatPlaceholder(ChatState(sessionId = "s")))
        assertEquals(ChatPlaceholder.None, chatPlaceholder(ChatState(sessionId = "s", running = true)))
        assertEquals(ChatPlaceholder.None, chatPlaceholder(ChatState(error = "e")))
        assertEquals(ChatPlaceholder.None, chatPlaceholder(ChatState(sessionId = "s", error = "e")))
        val item = ChatItem.Note("k", "hi")
        assertEquals(ChatPlaceholder.None, chatPlaceholder(ChatState(sessionId = "s", items = listOf(item))))
    }
}
