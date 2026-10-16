package io.github.lastnyx.hermesclient.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BranchCountTest {
    private fun m(key: String, role: String) = ChatItem.Message(key, role, key)

    private val items = listOf(
        m("u1", "user"), ChatItem.Note("n1", "note"), m("a1", "assistant"),
        m("u2", "user"), ChatItem.Note("n2", "note"), m("a2", "assistant"),
        m("u3", "user"), m("a3", "assistant"),
    )

    @Test
    fun countsMessagesThroughTheTurn() {
        assertEquals(BranchPoint(2, false), branchCount(items, "u1"))
        assertEquals(BranchPoint(4, false), branchCount(items, "u2"))
        assertEquals(BranchPoint(6, true), branchCount(items, "u3"))
        assertEquals(BranchPoint(1, true), branchCount(listOf(m("u", "user")), "u"))
        assertEquals(BranchPoint(2, false), branchCount(listOf(m("u", "user"), m("a", "assistant"), m("v", "user")), "u"))
        assertNull(branchCount(items, "nope"))
        assertNull(branchCount(items, "a1"))
        assertNull(branchCount(items, "n1"))
        assertNull(branchCount(emptyList(), "u1"))
    }
}
