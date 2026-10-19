package io.github.lastnyx.hermesclient.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopVersionTest {
    @Test
    fun versionLine() {
        assertEquals("1.2.3", desktopVersionLine("1.2.3"))
        assertEquals("development build", desktopVersionLine(null))
        assertEquals("development build", desktopVersionLine("  "))
    }
}
