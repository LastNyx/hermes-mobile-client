package io.github.lastnyx.hermesclient.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class MainTest {
    @Test
    fun windowTitleIsHermes() {
        assertEquals("Hermes", WINDOW_TITLE)
    }
}
