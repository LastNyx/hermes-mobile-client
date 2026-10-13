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
