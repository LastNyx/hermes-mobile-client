package io.github.lastnyx.hermesclient.data.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointsTest {
    @Test
    fun parsesListRowsAndOddInput() {
        val two = parseCheckpoints(
            JSONObject("""{"enabled":true,"checkpoints":[{"hash":"aaa","timestamp":"t1","message":"m1"},{"hash":"bbb","timestamp":"t2","message":"m2"}]}"""),
        )
        assertTrue(two.enabled)
        assertEquals(listOf(Checkpoint("aaa", "t1", "m1"), Checkpoint("bbb", "t2", "m2")), two.checkpoints)

        val off = parseCheckpoints(JSONObject("""{"enabled":false,"checkpoints":[]}"""))
        assertFalse(off.enabled)
        assertTrue(off.checkpoints.isEmpty())

        val skipped = parseCheckpoints(JSONObject("""{"enabled":true,"checkpoints":[{"timestamp":"t","message":"m"},{"hash":"","message":"x"},{"hash":"ok"}]}"""))
        assertEquals(listOf(Checkpoint("ok", "", "")), skipped.checkpoints)

        val nulls = parseCheckpoints(JSONObject("""{"enabled":true,"checkpoints":[{"hash":"h","timestamp":null,"message":null}]}"""))
        assertEquals(Checkpoint("h", "", ""), nulls.checkpoints[0])

        assertEquals(CheckpointList(false, emptyList()), parseCheckpoints(null))
        assertFalse(parseCheckpoints(JSONObject("""{"checkpoints":[{"hash":"h"}]}""")).enabled)
    }

    @Test
    fun restoreSummaryLines() {
        val full = parseRestore(
            JSONObject("""{"success":true,"restored_to":"abc123","restored_files":["a.kt","b.kt"],"skipped_user_edits":["x.kt"],"history_removed":2}"""),
        )
        assertEquals(
            "Restored to abc123\nRestored: a.kt, b.kt\nSkipped (edited since): x.kt\nRemoved 2 turns from the chat",
            restoreSummary(full),
        )
        assertEquals(
            "Restored to the checkpoint\nRemoved 1 turn from the chat",
            restoreSummary(parseRestore(JSONObject("""{"success":true,"restored_to":null,"history_removed":1}"""))),
        )
        assertEquals("nope", restoreSummary(parseRestore(JSONObject("""{"success":false,"error":"nope"}"""))))
        assertEquals("Couldn't restore that checkpoint.", restoreSummary(parseRestore(JSONObject("""{"success":false}"""))))
        assertEquals("Couldn't restore that checkpoint.", restoreSummary(parseRestore(null)))
    }

    @Test
    fun capDiffCutsAndJoins() {
        val big = (1..500).joinToString("\n") { "line$it" }
        val capped = capDiff("stat", big)
        assertTrue(capped.endsWith("\n…"))
        assertEquals(120, capped.removeSuffix("\n…").lines().size)
        assertEquals("stat\n\ndiff", capDiff("stat", "diff"))
        assertEquals("diff", capDiff("", "diff"))
        assertEquals("No changes", capDiff("", ""))
    }
}
