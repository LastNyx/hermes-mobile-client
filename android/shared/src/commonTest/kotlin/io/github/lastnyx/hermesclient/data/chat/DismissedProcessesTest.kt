package io.github.lastnyx.hermesclient.data.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class DismissedProcessesTest {
    @Test
    fun dismissIdsAndPrune() {
        DismissedProcesses.reset()
        DismissedProcesses.dismiss("a", "p1")
        DismissedProcesses.dismiss("a", "p2")
        assertEquals(setOf("p1", "p2"), DismissedProcesses.ids("a"))
        assertEquals(emptySet<String>(), DismissedProcesses.ids("b"))

        DismissedProcesses.prune("a", setOf("p2", "p3"))
        assertEquals(setOf("p2"), DismissedProcesses.ids("a"))

        DismissedProcesses.prune("unknown", setOf("p1"))
        DismissedProcesses.prune("a", emptySet())
        assertEquals(emptySet<String>(), DismissedProcesses.ids("a"))
    }
}
