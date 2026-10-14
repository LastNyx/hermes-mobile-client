package io.github.lastnyx.hermesclient.data.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundProcessesParseTest {
    private fun parse(body: String) = parseProcesses(JSONObject(body))

    @Test
    fun runningProcess() {
        val p = parse("""{"processes":[{"session_id":"p1","command":"sleep 9","status":"running","uptime_seconds":3,"output_tail":"hi"}]}""").single()
        assertEquals(BackgroundProcess("p1", "sleep 9", ProcessState.Running, null, "hi"), p)
    }

    @Test
    fun exitedZeroIsDoneAndNonZeroIsFailed() {
        val list = parse("""{"processes":[{"session_id":"a","status":"exited","exit_code":0},{"session_id":"b","status":"exited","exit_code":3}]}""")
        assertEquals(ProcessState.Done, list[0].state)
        assertEquals(ProcessState.Failed, list[1].state)
        assertEquals(3, list[1].exitCode)
    }

    @Test
    fun oddRowsAreHandled() {
        val list = parse("""{"processes":[{"command":"x"},{"session_id":"a"},{"session_id":"b","command":"one\ntwo"}]}""")
        assertEquals(listOf("a", "b"), list.map { it.id })
        assertEquals("background process", list[0].title)
        assertEquals("one", list[1].title)
        assertEquals(emptyList<BackgroundProcess>(), parseProcesses(null))
    }

    @Test
    fun mergeDropsDismissedAndKeepsIncomingOrder() {
        fun p(id: String) = BackgroundProcess(id, id, ProcessState.Done, 0, null)
        val merged = mergeProcesses(listOf(p("z")), listOf(p("c"), p("a"), p("b")), setOf("a"))
        assertEquals(listOf("c", "b"), merged.map { it.id })
    }
}
