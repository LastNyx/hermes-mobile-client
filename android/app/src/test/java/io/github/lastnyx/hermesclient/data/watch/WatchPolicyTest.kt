package io.github.lastnyx.hermesclient.data.watch

import io.github.lastnyx.hermesclient.data.chat.ChatState
import io.github.lastnyx.hermesclient.data.live.ServerRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPolicyTest {
    private fun approval() = ServerRequest("srq-1", "approval", JSONObject().put("tool_name", "bash").put("command", "rm -rf x"))
    private fun clarify() = ServerRequest("srq-2", "clarify", JSONObject())

    @Test
    fun phoneStartedTurnKeepsWatchUntilDoneThenNotifies() {
        val policy = WatchPolicy()
        assertFalse(policy.shouldRun)

        policy.turnStarted("s1")
        assertTrue(policy.shouldRun)
        assertEquals(emptyList<WatchAction>(), policy.onState("s1", ChatState(storedId = "s1", running = true)))

        val done = policy.onState("s1", ChatState(storedId = "s1", running = false))
        assertEquals(listOf<WatchAction>(WatchAction.Done("s1")), done)
        assertFalse(policy.shouldRun)
    }

    @Test
    fun failureApprovalQuestionAndClearAreEmitted() {
        val policy = WatchPolicy()
        policy.turnStarted("s1")
        val asked = policy.onState("s1", ChatState(storedId = "s1", running = true, openRequests = listOf(approval(), clarify())))
        assertEquals(listOf<WatchAction>(WatchAction.NeedsApproval("s1", "bash", "srq-1"), WatchAction.NeedsAnswer("s1")), asked)
        assertTrue(policy.shouldRun)

        val cleared = policy.onState("s1", ChatState(storedId = "s1", running = true))
        assertEquals(listOf<WatchAction>(WatchAction.Clear("s1")), cleared)

        val failed = policy.onState("s1", ChatState(storedId = "s1", running = false, error = "boom"))
        assertEquals(listOf<WatchAction>(WatchAction.Failed("s1")), failed)
        assertFalse(policy.shouldRun)
    }

    @Test
    fun leavingTheChatEndsTheWatchWithAClear() {
        val policy = WatchPolicy()
        policy.turnStarted("s1")
        policy.onState("s1", ChatState(storedId = "s1", running = true))

        assertEquals(listOf<WatchAction>(WatchAction.Clear("s1")), policy.chatClosed("s1"))
        assertFalse(policy.shouldRun)
        assertEquals(emptyList<WatchAction>(), policy.chatClosed("s1"))
    }

    @Test
    fun turnsNotStartedOnThePhoneAreIgnored() {
        val policy = WatchPolicy()
        assertEquals(emptyList<WatchAction>(), policy.onState("s9", ChatState(storedId = "s9", running = false, error = "x")))
        assertFalse(policy.shouldRun)
    }
}
