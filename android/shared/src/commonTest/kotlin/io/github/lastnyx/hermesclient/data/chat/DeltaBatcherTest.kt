package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.LinkEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeltaBatcherTest {
    private var now = 0L
    private fun batcher() = DeltaBatcher(32) { now }

    private fun ev(type: String, text: String? = null, seq: Long? = null) =
        LinkEvent(type, "s1", JSONObject().also { if (text != null) it.put("text", text) }, seq)

    private fun texts(events: List<LinkEvent>) = events.map { it.type to it.payload?.optString("text") }

    @Test
    fun heldThenMergedWhenDue() {
        val b = batcher()
        assertTrue(b.offer(ev("message.delta", "a", 1)).isEmpty())
        assertEquals(32L, b.msUntilDue())
        now = 32
        val out = b.offer(ev("message.delta", "b", 2))
        assertEquals(listOf("message.delta" to "ab"), texts(out))
        assertEquals(2L, out.single().seq)
        assertNull(b.msUntilDue())
    }

    @Test
    fun insideWindowFlushMerges() {
        val b = batcher()
        assertTrue(b.offer(ev("message.delta", "a", 1)).isEmpty())
        assertTrue(b.offer(ev("message.delta", "b", 2)).isEmpty())
        assertEquals(listOf("message.delta" to "ab"), texts(b.flush()))
        assertTrue(b.flush().isEmpty())
    }

    @Test
    fun otherEventsFlushPendingFirst() {
        val b = batcher()
        assertEquals(listOf("tool.start"), b.offer(ev("tool.start")).map { it.type })
        b.offer(ev("message.delta", "a"))
        b.offer(ev("reasoning.delta", "r"))
        b.offer(ev("thinking.delta", "t"))
        assertEquals(
            listOf("message.delta", "reasoning.delta", "thinking.delta", "message.complete"),
            b.offer(ev("message.complete")).map { it.type },
        )
    }

    @Test
    fun nullTextIsEmpty() {
        val b = batcher()
        b.offer(LinkEvent("message.delta", "s1", JSONObject().put("text", JSONObject.NULL), null))
        b.offer(LinkEvent("message.delta", "s1", null, null))
        b.offer(ev("message.delta", "x"))
        assertEquals(listOf("message.delta" to "x"), texts(b.flush()))
    }

    @Test
    fun foldingBatchedEqualsFoldingRaw() {
        val sequences = listOf(
            listOf(ev("message.delta", "a"), ev("message.delta", "b"), ev("message.delta", "c")),
            listOf(ev("reasoning.delta", "r"), ev("thinking.delta", "t"), ev("message.delta", "m")),
            listOf(ev("message.delta", "a"), ev("tool.start"), ev("tool.complete"), ev("message.delta", "b")),
            listOf(ev("message.delta", "a"), ev("message.delta", "b"), ev("message.complete", "ab")),
        )
        for (seq in sequences) {
            val raw = seq.fold(ChatState()) { s, e -> reduceChat(s, e) }
            for (step in listOf(100L, 0L)) {
                now = 0
                val b = batcher()
                val out = seq.flatMap { now += step; b.offer(it) } + b.flush()
                assertEquals(raw, out.fold(ChatState()) { s, e -> reduceChat(s, e) })
            }
        }
    }
}
