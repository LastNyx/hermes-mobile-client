package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.sessions.HistoryMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryPagingTest {
    private fun m(text: String, id: Long?) = HistoryMessage("user", text, id)

    @Test
    fun olderRowsGoFirstAndOverlapIsDropped() {
        val current = listOf(m("c", 3), m("d", 4))
        val older = listOf(m("a", 1), m("b", 2), m("c2", 3))
        assertEquals(listOf("a", "b", "c", "d"), mergeHistory(current, older).map { it.text })
    }

    @Test
    fun nullIdRowsAreKeptAndEmptyInputsWork() {
        assertEquals(listOf("x", "y"), mergeHistory(listOf(m("y", null)), listOf(m("x", null))).map { it.text })
        assertEquals(emptyList<HistoryMessage>(), mergeHistory(emptyList(), emptyList()))
        assertEquals(listOf("a"), mergeHistory(emptyList(), listOf(m("a", 1))).map { it.text })
        assertEquals(listOf("a"), mergeHistory(listOf(m("a", 1)), emptyList()).map { it.text })
    }
}
