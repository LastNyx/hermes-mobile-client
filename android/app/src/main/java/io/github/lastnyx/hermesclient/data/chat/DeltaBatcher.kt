package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.LinkEvent
import org.json.JSONObject

const val DELTA_BATCH_MS = 32L

private val DELTA_TYPES = setOf("message.delta", "reasoning.delta", "thinking.delta")

/** Holds streamed token deltas for one window so the reducer runs once per burst. Not thread-safe: callers hold the controller lock. */
class DeltaBatcher(private val intervalMs: Long = DELTA_BATCH_MS, private val clock: () -> Long) {
    private val pending = mutableListOf<LinkEvent>()
    private var windowStart = 0L

    fun offer(event: LinkEvent): List<LinkEvent> {
        if (event.type !in DELTA_TYPES) return flush() + event
        if (pending.isEmpty()) windowStart = clock()
        pending += event
        return if (clock() - windowStart >= intervalMs) flush() else emptyList()
    }

    fun flush(): List<LinkEvent> {
        val merged = mutableListOf<LinkEvent>()
        var text = StringBuilder()
        for ((i, e) in pending.withIndex()) {
            val p = e.payload
            text.append(if (p == null || p.isNull("text")) "" else p.optString("text"))
            val next = pending.getOrNull(i + 1)
            if (next == null || next.type != e.type || next.sessionId != e.sessionId) {
                merged += LinkEvent(e.type, e.sessionId, JSONObject().put("text", text.toString()), e.seq)
                text = StringBuilder()
            }
        }
        pending.clear()
        return merged
    }

    fun msUntilDue(): Long? = if (pending.isEmpty()) null else maxOf(0L, windowStart + intervalMs - clock())
}
