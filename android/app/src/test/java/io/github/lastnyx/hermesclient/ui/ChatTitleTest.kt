package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.sessions.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTitleTest {
    private val sessions = listOf(
        Session("a", "  Plan the trip  ", "", 0.0),
        Session("b", "   ", "", 0.0),
    )

    @Test
    fun titleComesFromTheMatchingSessionOrFallsBack() {
        assertEquals("Plan the trip", chatTitle(sessions, "a"))
        assertEquals("New chat", chatTitle(sessions, "b"))
        assertEquals("New chat", chatTitle(sessions, "zzz"))
        assertEquals("New chat", chatTitle(sessions, null))
    }
}
