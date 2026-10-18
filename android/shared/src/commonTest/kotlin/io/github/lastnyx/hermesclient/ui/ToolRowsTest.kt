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
        assertEquals("1 min", durationText(59.96))
        assertEquals("59.9 s", durationText(59.94))

        assertEquals("first", reasoningPreview("first\nsecond", false))
        assertEquals("second", reasoningPreview("first\nsecond", true))
        for (t in listOf("", " \n\t\n", "\n\n a \n b \n\n", "a\r\nb\r\n", "x\ry\rz", "   indented\n  more  ", "one", "\r\n\r\n", "a\n\r\nb")) {
            for (streaming in listOf(false, true)) {
                val lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val old = (if (streaming) lines.lastOrNull() else lines.firstOrNull()).orEmpty()
                assertEquals("'$t' $streaming", old, reasoningPreview(t, streaming))
            }
        }

        val msg = ChatItem.Message("m", "assistant", "x")
        val four = groupTools(listOf(tool(1), tool(2), tool(3), tool(4)))
        assertEquals(1, four.size)
        assertEquals(4, (four[0] as ChatRow.ToolGroup).tools.size)
        assertEquals(2, groupTools(listOf(tool(1), tool(2))).size)
        assertEquals(5, groupTools(listOf(tool(1), tool(2), msg, tool(3), tool(4))).size)
        val running = ChatItem.Tool("t9", "9", "x", io.github.lastnyx.hermesclient.data.chat.ToolStatus.Running)
        assertTrue((groupTools(listOf(tool(1), tool(2), running))[0] as ChatRow.ToolGroup).running)

        val done = io.github.lastnyx.hermesclient.data.chat.ToolStatus.Done
        val live = io.github.lastnyx.hermesclient.data.chat.ToolStatus.Running
        assertEquals("out", toolExpandedText(done, "out"))
        assertNull(toolExpandedText(done, ""))
        assertNull(toolExpandedText(done, "   \n"))
        assertEquals("Still running…", toolExpandedText(live, ""))
        assertEquals("out", toolExpandedText(live, "out"))
        assertEquals("Still running…", TOOL_STILL_RUNNING)
    }

    private fun user(n: Int) = ChatItem.Message("u$n", "user", "q")
    private fun live(n: Int) = ChatItem.Tool("t$n", "$n", "x", io.github.lastnyx.hermesclient.data.chat.ToolStatus.Running)
    private fun think(n: Int, streaming: Boolean = false) = ChatItem.Reasoning("r$n", "x", streaming)
    private fun keys(r: ChatRows) = r.rows.map { it.key }

    @Test
    fun runningReplyShowsOnlyLastFinishedSteps() {
        val mixed = listOf(user(1), think(1), tool(1), tool(2), tool(3), tool(4), ChatItem.Message("a", "assistant", "hi"))
        assertEquals(ChatRows(groupTools(mixed), 0, null), chatRows(mixed, running = false, showEarlier = false))
        assertEquals(ChatRows(emptyList(), 0, null), chatRows(emptyList(), running = false, showEarlier = false))

        val six = listOf(user(1)) + (1..6).map { tool(it) }
        val r = chatRows(six, running = true, showEarlier = false)
        assertEquals(listOf("u1", "t3", "t4", "t5", "t6"), keys(r))
        assertTrue(r.rows.all { it is ChatRow.Single })
        assertEquals(2, r.earlierSteps)
        assertEquals("t3", r.earlierBeforeKey)

        val four = chatRows(six.take(5), running = true, showEarlier = false)
        assertEquals(5, four.rows.size)
        assertEquals(ChatRows(four.rows, 0, null), four)
        assertTrue(four.rows.all { it is ChatRow.Single })
        val all = chatRows(six, running = true, showEarlier = true)
        assertEquals(7, all.rows.size)
        assertEquals(0, all.earlierSteps)
        assertNull(all.earlierBeforeKey)

        val liveMix = listOf(user(1)) + (1..5).map { tool(it) } + listOf(live(9), think(2, true))
        val l = chatRows(liveMix, running = true, showEarlier = false)
        assertEquals(listOf("u1", "t2", "t3", "t4", "t5", "t9", "r2"), keys(l))
        assertEquals(1, l.earlierSteps)
        val thinkSteps = chatRows(listOf(user(1), think(1), think(2), tool(1), tool(2), tool(3)), running = true, showEarlier = false)
        assertEquals(listOf("u1", "r2", "t1", "t2", "t3"), keys(thinkSteps))

        val a = ChatItem.Message("a1", "assistant", "mid")
        val n = ChatItem.Note("n1", "note")
        val c = ChatItem.CommandOutput("c1", "/x", "out")
        val withMsgs = chatRows(listOf(user(1), tool(1), a, tool(2), n, tool(3), c, tool(4), tool(5), tool(6)), running = true, showEarlier = false)
        assertEquals(listOf("u1", "a1", "n1", "t3", "c1", "t4", "t5", "t6"), keys(withMsgs))
        assertEquals(2, withMsgs.earlierSteps)
        assertEquals("a1", withMsgs.earlierBeforeKey)

        val twoTurns = listOf(user(1), tool(1), tool(2), tool(3), user(2), tool(4), tool(5), tool(6))
        val t = chatRows(twoTurns, running = true, showEarlier = false)
        assertTrue(t.rows[1] is ChatRow.ToolGroup)
        assertEquals(6, t.rows.size)
        assertTrue(t.rows.drop(3).all { it is ChatRow.Single })
        val noUser = chatRows((1..6).map { tool(it) }, running = true, showEarlier = false)
        assertEquals(4, noUser.rows.size)
        assertEquals(2, noUser.earlierSteps)

        assertEquals("u2", turnKey(twoTurns))
        assertNull(turnKey(emptyList()))
        assertNull(turnKey(listOf(tool(1))))
        assertEquals("1 earlier step", earlierStepsText(1))
        assertEquals("3 earlier steps", earlierStepsText(3))
    }
}
