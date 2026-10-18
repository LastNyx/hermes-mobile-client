package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.BackgroundProcess
import io.github.lastnyx.hermesclient.data.chat.ProcessState
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessSummaryTest {
    private fun p(state: ProcessState) = BackgroundProcess("x", "x", state, null, null)

    @Test
    fun summaries() {
        val r = ProcessState.Running
        val d = ProcessState.Done
        val f = ProcessState.Failed
        assertEquals("Background · 2 running", processSummary(listOf(p(r), p(r))))
        assertEquals("Background · 1 running, 1 failed", processSummary(listOf(p(f), p(r))))
        assertEquals("Background · 3 done", processSummary(listOf(p(d), p(d), p(d))))
        assertEquals("Background · 1 failed", processSummary(listOf(p(f))))
        assertEquals("Background", processSummary(emptyList()))
    }
}
