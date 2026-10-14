package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.ChatItem
import io.github.lastnyx.hermesclient.data.chat.ChatState
import io.github.lastnyx.hermesclient.data.chat.reduceChat
import io.github.lastnyx.hermesclient.data.live.LinkEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRowsTest {
    private fun ev(type: String, json: String) = LinkEvent(type, "s1", JSONObject(json), null)

    private fun tool(n: Int) = ChatItem.Tool("t$n", "$n", "terminal", io.github.lastnyx.hermesclient.data.chat.ToolStatus.Done)

    @Test
    fun toolRowsSayWhatHappened() {
        val s = listOf(
            ev("tool.start", """{"tool_id":"c1","name":"terminal","preview":"rm -rf build"}"""),
            ev("tool.complete", """{"tool_id":"c1","name":"terminal","duration_s":0.4,"summary":"exit 0"}"""),
            ev("tool.start", """{"tool_id":"c2","name":"read_file"}"""),
        ).fold(ChatState()) { st, e -> reduceChat(st, e) }
        val first = s.items[0] as ChatItem.Tool
        assertEquals("rm -rf build", first.preview)
        assertEquals(0.4, first.durationS!!, 0.0)
        val bare = s.items[1] as ChatItem.Tool
        assertEquals("", bare.preview)
        assertNull(bare.durationS)

        assertEquals("Terminal", toolLabel("terminal"))
        assertEquals("Read file", toolLabel("read_file"))
        assertEquals("Tool", toolLabel(""))
        assertEquals("0.4 s", durationText(0.4))
        assertEquals("12.0 s", durationText(12.0))
        assertEquals("1 min 3 s", durationText(63.0))

        assertEquals("first", reasoningPreview("first\nsecond", false))
        assertEquals("second", reasoningPreview("first\nsecond", true))

        val msg = ChatItem.Message("m", "assistant", "x")
        val four = groupTools(listOf(tool(1), tool(2), tool(3), tool(4)))
        assertEquals(1, four.size)
        assertEquals(4, (four[0] as ChatRow.ToolGroup).tools.size)
        assertEquals(2, groupTools(listOf(tool(1), tool(2))).size)
        assertEquals(5, groupTools(listOf(tool(1), tool(2), msg, tool(3), tool(4))).size)
        val running = ChatItem.Tool("t9", "9", "x", io.github.lastnyx.hermesclient.data.chat.ToolStatus.Running)
        assertTrue((groupTools(listOf(tool(1), tool(2), running))[0] as ChatRow.ToolGroup).running)
    }
}
