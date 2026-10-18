package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PaneLayoutTest {
    @Test
    fun planForEveryCombination() {
        assertEquals(PanePlan(true, true, false, true), paneLayout(wide = true, chatOpen = false))
        assertEquals(PanePlan(true, true, true, false), paneLayout(wide = true, chatOpen = true))
        assertEquals(PanePlan(true, false, false, false), paneLayout(wide = false, chatOpen = false))
        assertEquals(PanePlan(false, false, true, false), paneLayout(wide = false, chatOpen = true))
    }
}
