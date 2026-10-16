package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.LinkEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentsTest {
    private fun ev(type: String, json: String) = LinkEvent(type, "s1", JSONObject(json), null)

    private fun sub(
        status: SubagentStatus = SubagentStatus.Running,
        model: String? = null,
        dur: Double? = null,
        tool: String? = null,
        delegation: String? = null,
        key: String = "k",
    ) = Subagent(key, null, null, delegation, 0, "g", 0, 1, model, status, tool, null, emptyList(), null, dur)

    @Test
    fun subagentLifecycle() {
        var l = reduceSubagents(emptyList(), ev("subagent.spawn_requested", """{"goal":"look","task_index":1,"task_count":2}"""))
        assertEquals(1, l.size)
        assertEquals(SubagentStatus.Queued, l[0].status)
        assertEquals(1, l[0].taskIndex)
        assertEquals(2, l[0].taskCount)

        l = reduceSubagents(l, ev("subagent.start", """{"goal":"look","task_index":1,"task_count":2,"subagent_id":"a","model":"gpt-5"}"""))
        assertEquals(1, l.size)
        assertEquals("a", l[0].id)
        assertEquals(SubagentStatus.Running, l[0].status)

        l = reduceSubagents(l, ev("subagent.start", """{"goal":"other","task_index":2,"task_count":2,"subagent_id":"b"}"""))
        assertEquals(2, l.size)

        l = reduceSubagents(l, ev("subagent.tool", """{"goal":"look","task_index":1,"task_count":2,"subagent_id":"a","tool_name":"terminal","tool_preview":"ls","model":null}"""))
        assertEquals("terminal", l[0].lastTool)
        assertEquals("gpt-5", l[0].model)
        assertEquals(listOf("terminal: ls"), l[0].activity)
        l = reduceSubagents(l, ev("subagent.tool", """{"goal":"look","task_index":1,"task_count":2,"subagent_id":"a","tool_name":"terminal","tool_preview":"ls"}"""))
        assertEquals(1, l[0].activity.size)

        for (i in 1..30) {
            l = reduceSubagents(l, ev("subagent.tool", """{"goal":"look","task_index":1,"task_count":2,"subagent_id":"a","tool_name":"t","tool_preview":"p$i"}"""))
        }
        assertEquals(24, l[0].activity.size)
        assertEquals("t: p30", l[0].activity.last())

        l = reduceSubagents(l, ev("subagent.tool", """{"goal":"look","task_index":1,"task_count":2,"subagent_id":"a","tool_name":"t","tool_preview":"${"x".repeat(300)}"}"""))
        assertEquals(200, l[0].activity.last().length)
        assertTrue(l[0].activity.last().endsWith("…"))

        l = reduceSubagents(l, ev("subagent.thinking", """{"goal":"look","task_index":1,"task_count":2,"subagent_id":"a","text":"\n\n  first line\nsecond"}"""))
        assertEquals("first line", l[0].activity.last())
        val before = l[0].activity
        l = reduceSubagents(l, ev("subagent.thinking", """{"goal":"look","task_index":1,"task_count":2,"subagent_id":"a","text":"  \n "}"""))
        assertEquals(before, l[0].activity)

        l = reduceSubagents(l, ev("subagent.complete", """{"goal":"look","task_index":1,"task_count":2,"subagent_id":"a","status":"failed","summary":"boom","duration_seconds":12.0}"""))
        assertEquals(SubagentStatus.Failed, l[0].status)
        assertEquals("boom", l[0].summary)
        assertEquals(12.0, l[0].durationSeconds!!, 0.0)
        l = reduceSubagents(l, ev("subagent.progress", """{"goal":"look","task_index":1,"task_count":2,"subagent_id":"a","duration_seconds":null,"status":"weird"}"""))
        assertEquals(SubagentStatus.Failed, l[0].status)
        assertEquals(12.0, l[0].durationSeconds!!, 0.0)
        l = reduceSubagents(l, ev("subagent.complete", """{"goal":"other","task_index":2,"task_count":2,"subagent_id":"b"}"""))
        assertEquals(SubagentStatus.Completed, l[1].status)

        val nulls = reduceSubagents(emptyList(), ev("subagent.start", """{"goal":"g","subagent_id":null,"model":null}"""))
        assertEquals(0, nulls[0].taskIndex)
        assertEquals(null, nulls[0].id)
        assertEquals(null, nulls[0].model)
        assertSame(nulls, reduceSubagents(nulls, LinkEvent("subagent.start", "s1", null, null)))
        assertSame(nulls, reduceSubagents(nulls, ev("message.delta", """{"text":"x"}""")))
    }

    @Test
    fun finishedCap() {
        var l = reduceSubagents(emptyList(), ev("subagent.start", """{"goal":"live","task_index":0,"task_count":1,"subagent_id":"live"}"""))
        for (i in 1..105) {
            l = reduceSubagents(l, ev("subagent.complete", """{"goal":"g$i","task_index":0,"task_count":1,"subagent_id":"d$i"}"""))
        }
        assertEquals(101, l.size)
        assertEquals("live", l[0].id)
        assertFalse(l.any { it.id == "d5" })
        assertTrue(l.any { it.id == "d6" })
    }

    @Test
    fun metaAndGroups() {
        assertEquals("running · gpt-5 · 12s · terminal", subagentMeta(sub(model = "gpt-5", dur = 12.0, tool = "terminal")))
        assertEquals("running", subagentMeta(sub()))
        assertEquals("done · 1m 5s", subagentMeta(sub(SubagentStatus.Completed, dur = 65.0)))
        assertEquals("timed out", subagentMeta(sub(SubagentStatus.Timeout)))

        val g = groupSubagents(listOf(sub(delegation = "d1", key = "1"), sub(key = "2"), sub(delegation = "d1", key = "3"), sub(key = "4")))
        assertEquals(listOf(listOf("1", "3"), listOf("2", "4")), g.map { grp -> grp.map { it.key } })
    }

    @Test
    fun reduceChatHook() {
        val s = ChatState(running = false)
        val r = reduceChat(s, ev("subagent.start", """{"goal":"g","task_index":0,"task_count":1}"""))
        assertEquals(1, r.subagents.size)
        assertEquals(s.items, r.items)
        assertFalse(r.running)
        assertEquals(null, r.error)
        assertEquals(r.subagents, reduceChat(r, ev("message.delta", """{"text":"x"}""")).subagents)
    }
}
