package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.LinkEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatReducerTest {
    private fun ev(type: String, text: String? = null, extra: Map<String, String> = emptyMap()): LinkEvent {
        val p = JSONObject()
        if (text != null) p.put("text", text)
        extra.forEach { (k, v) -> p.put(k, v) }
        return LinkEvent(type, "s1", p, null)
    }

    private fun run(vararg events: LinkEvent): ChatState =
        events.fold(ChatState()) { s, e -> reduceChat(s, e) }

    private fun view(s: ChatState): List<String> = s.items.map {
        when (it) {
            is ChatItem.Reasoning -> "R:${it.text}:${it.streaming}"
            is ChatItem.Message -> "M:${it.text}:${it.streaming}"
            is ChatItem.Tool -> "T:${it.name}"
            is ChatItem.Note -> "N:${it.text}"
            is ChatItem.CommandOutput -> "C:${it.text}"
        }
    }

    private fun detailOf(json: String, start: Boolean = true): String {
        val events = listOfNotNull(
            if (start) LinkEvent("tool.start", "s1", JSONObject("""{"tool_id":"c1","name":"ls"}"""), null) else null,
            LinkEvent("tool.complete", "s1", JSONObject(json), null),
        )
        return (run(*events.toTypedArray()).items.single() as ChatItem.Tool).detail
    }

    @Test
    fun toolCompleteCapsAndFallsBack() {
        val big = detailOf("""{"tool_id":"c1","result_text":"${"x".repeat(10000)}"}""")
        assertEquals(true, big.length <= 4000 && big.endsWith("more characters"))
        assertEquals("exit 0", detailOf("""{"tool_id":"c1","result_text":null,"summary":"exit 0"}"""))
        assertEquals("exit 0", detailOf("""{"tool_id":"c1","summary":"exit 0"}"""))
        val unknown = detailOf("""{"tool_id":"zz","result_text":"${"x".repeat(10000)}"}""", start = false)
        assertEquals(true, unknown.length <= 4000 && unknown.endsWith("more characters"))
    }

    @Test
    fun reasoningBlocks() {
        assertEquals(
            listOf("R:ab:false", "M:hi:true"),
            view(run(ev("reasoning.delta", "a"), ev("thinking.delta", "b"), ev("message.delta", "hi"))),
        )
        assertEquals(listOf("R:whole:false"), view(run(ev("reasoning.available", "whole"))))
        assertEquals(listOf("R:full:false"), view(run(ev("reasoning.delta", "par"), ev("reasoning.available", "full"))))
        assertEquals(emptyList<String>(), view(run(ev("reasoning.delta", ""))))
        assertEquals(
            listOf("R:x:false", "T:ls", "R:y:true"),
            view(run(ev("reasoning.delta", "x"), ev("tool.start", extra = mapOf("tool_id" to "1", "name" to "ls")), ev("reasoning.delta", "y"))),
        )
    }
}
