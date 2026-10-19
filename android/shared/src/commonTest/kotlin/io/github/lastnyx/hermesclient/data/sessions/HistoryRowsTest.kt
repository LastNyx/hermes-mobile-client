package io.github.lastnyx.hermesclient.data.sessions

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryRowsTest {
    private fun row(json: String) = historyRow(JSONObject(json))

    private fun user(content: String, extra: String = "") =
        JSONObject("""{"id":7,"role":"user"$extra}""").put("content", content).let { historyRow(it) }

    @Test fun hiddenDropped() {
        assertNull(row("""{"id":1,"role":"user","content":"x","display_kind":"hidden"}"""))
    }

    @Test fun steerUsesDisplayContent() {
        val m = user("[OUT-OF-BAND USER MESSAGE — x]\napprove\n[/OUT-OF-BAND USER MESSAGE]", ""","display_kind":"steer","display_content":"approve it"""")
        assertEquals(HistoryMessage("user", "approve it", 7L), m)
    }

    @Test fun steerParsesWrapper() {
        val m = user("[OUT-OF-BAND USER MESSAGE — x]\napprove\n[/OUT-OF-BAND USER MESSAGE]", ""","display_kind":"steer"""")
        assertEquals(HistoryMessage("user", "approve", 7L), m)
    }

    @Test fun steerWithoutMarkerKeepsRaw() {
        assertEquals(HistoryMessage("user", "plain words", 7L), user("plain words", ""","display_kind":"steer""""))
    }

    @Test fun processCompleteObjectMetadata() {
        val m = row("""{"id":3,"role":"user","content":"long","display_kind":"process_complete","display_metadata":{"display_text":"Background Process Finished: ls"}}""")
        assertEquals(HistoryMessage("user", "Background Process Finished: ls", 3L, notice = true), m)
    }

    @Test fun processCompleteStringMetadata() {
        val m = row("""{"id":3,"role":"user","content":"long","display_kind":"process_complete","display_metadata":"{\"display_text\":\"Done: ls\"}"}""")
        assertEquals(HistoryMessage("user", "Done: ls", 3L, notice = true), m)
    }

    @Test fun processCompleteFallback() {
        val m = row("""{"id":3,"role":"user","content":"long","display_kind":"process_complete"}""")
        assertEquals(HistoryMessage("user", "Background process finished", 3L, notice = true), m)
    }

    @Test fun asyncDelegationFallback() {
        val m = row("""{"id":3,"role":"user","content":"long","display_kind":"async_delegation_complete","display_metadata":"not json"}""")
        assertEquals(HistoryMessage("user", "Background agent finished", 3L, notice = true), m)
    }

    @Test fun untypedHeartbeatDropped() {
        assertNull(user("[Background process proc_1 heartbeat #3: still running]\nmore"))
    }

    @Test fun untypedImportantBecomesNotice() {
        val m = user("[IMPORTANT: Background process proc_3e2e completed normally (exit code 0).\nCommand: ls\nOutput: x]")
        assertEquals(HistoryMessage("user", "Background process proc_3e2e completed normally (exit code 0)", 7L, notice = true), m)
    }

    @Test fun internalNotificationBecomesNotice() {
        val m = user("[IMPORTANT: Background process proc_9 completed normally (exit code 0).\nOutput: x]", ""","display_kind":"internal_notification"""")
        assertEquals(HistoryMessage("user", "Background process proc_9 completed normally (exit code 0)", 7L, notice = true), m)
    }

    @Test fun plainRowsUnchanged() {
        assertEquals(HistoryMessage("user", "hi", 7L), user("hi"))
        assertEquals(HistoryMessage("assistant", "yo", 8L), row("""{"id":8,"role":"assistant","content":"yo"}"""))
    }
}
