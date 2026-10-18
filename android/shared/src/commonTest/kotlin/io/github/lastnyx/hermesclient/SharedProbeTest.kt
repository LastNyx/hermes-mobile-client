package io.github.lastnyx.hermesclient

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedProbeTest {
    @Test
    fun jvmLibrariesWorkInCommon() {
        assertEquals("{\"host\":\"example.com\"}", sharedProbe("https://example.com/x"))
    }
}
