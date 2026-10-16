package io.github.lastnyx.hermesclient.data.watch

import io.github.lastnyx.hermesclient.data.chat.ChatState
import io.github.lastnyx.hermesclient.data.live.ServerRequest
import org.json.JSONArray
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
        assertEquals(listOf<WatchAction>(WatchAction.NeedsApproval("s1", "bash", "srq-1"), WatchAction.NeedsAnswer("s1", "srq-2", null, false)), asked)
        assertTrue(policy.shouldRun)

        val cleared = policy.onState("s1", ChatState(storedId = "s1", running = true))
        assertEquals(listOf<WatchAction>(WatchAction.Clear("s1")), cleared)

        val failed = policy.onState("s1", ChatState(storedId = "s1", running = false, error = "boom"))
        assertEquals(listOf<WatchAction>(WatchAction.Failed("s1")), failed)
        assertFalse(policy.shouldRun)
    }

    private fun question(vararg qs: Pair<String, Boolean>) = ServerRequest(
        "srq-3", "clarify",
        JSONObject().put(
            "questions",
            JSONArray().also { arr ->
                qs.forEach { (qid, choices) ->
                    arr.put(JSONObject().put("qid", qid).put("question", "secret?").put("choices", JSONArray().also { if (choices) it.put("a") }))
                }
            },
        ),
    )

    @Test
    fun singleQuestionClarifyCarriesQidAndTwoQuestionsDoNot() {
        val policy = WatchPolicy()
        policy.turnStarted("s1")
        val one = policy.onState("s1", ChatState(storedId = "s1", running = true, openRequests = listOf(question("q1" to true))))
        assertEquals(listOf<WatchAction>(WatchAction.NeedsAnswer("s1", "srq-3", "q1", true)), one)

        val policy2 = WatchPolicy()
        policy2.turnStarted("s1")
        val two = policy2.onState("s1", ChatState(storedId = "s1", running = true, openRequests = listOf(question("q1" to false, "q2" to false))))
        assertEquals(listOf<WatchAction>(WatchAction.NeedsAnswer("s1", "srq-3", null, false)), two)
    }

    @Test
    fun notificationButtonsAreTwoSafeChoicesApprovalsNeedUnlockAndOnlyOneQuestionOffersReply() {
        assertEquals(listOf(ApprovalButton("Allow once", "once"), ApprovalButton("Deny", "deny")), approvalButtons())
        approvalButtons().forEach { assertTrue(watchAnswer("p", it.choice) != null) }

        assertFalse(actionNeedsUnlock(26))
        assertFalse(actionNeedsUnlock(30))
        assertTrue(actionNeedsUnlock(31))
        assertTrue(actionNeedsUnlock(33))
        assertTrue(actionNeedsUnlock(35))

        assertTrue(offersReply(WatchAction.NeedsAnswer("s1", "p", qid = "q1", hasChoices = false)))
        assertFalse(offersReply(WatchAction.NeedsAnswer("s1", "p", qid = null, hasChoices = false)))
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
    fun theServiceIsOnlyStoppedFromOutsideOnceItIsInTheForeground() {
        assertEquals(ServiceStop.StopNow, serviceStopRequest(true))
        assertEquals(ServiceStop.LeaveToService, serviceStopRequest(false))
    }

    @Test
    fun turnsNotStartedOnThePhoneAreIgnored() {
        val policy = WatchPolicy()
        assertEquals(emptyList<WatchAction>(), policy.onState("s9", ChatState(storedId = "s9", running = false, error = "x")))
        assertFalse(policy.shouldRun)
    }
}
