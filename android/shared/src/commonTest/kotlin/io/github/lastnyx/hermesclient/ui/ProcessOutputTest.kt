package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.BackgroundProcess
import io.github.lastnyx.hermesclient.data.chat.ProcessState
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessOutputTest {
    private fun text(state: ProcessState, exit: Int?, output: String?) =
        processOutputText(BackgroundProcess("x", "x", state, exit, output))

    @Test
    fun outputTextPerState() {
        val r = ProcessState.Running
        val d = ProcessState.Done
        val f = ProcessState.Failed
        assertEquals("No output yet", text(r, null, null))
        assertEquals("No output yet", text(r, null, "  \n"))
        assertEquals("No output", text(d, 0, null))
        assertEquals("a\nb", text(d, 0, "a\nb"))
        assertEquals("hi\nExit code 3", text(f, 3, "hi"))
        assertEquals("Exit code 3", text(f, 3, null))
        assertEquals("x", text(f, null, "x"))
        assertEquals("red", text(r, null, "\u001B[31mred\u001B[0m"))
        assertEquals("100%", text(r, null, "10%\r50%\r100%"))
        assertEquals("a\nb", text(r, null, "a\r\nb"))
        val twenty = (1..20).joinToString("\n")
        assertEquals((9..20).joinToString("\n"), text(r, null, twenty))
    }
}
